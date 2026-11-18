package com.sync.app.domain.provider.anthropic

import com.sync.app.data.network.HttpErrorMapper
import com.sync.app.domain.chat.clampTemperature
import com.sync.app.domain.error.AiException
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.AiProvider
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.domain.provider.ChatResponse
import com.sync.app.domain.provider.ProviderCapability
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.StreamChunk
import com.sync.app.model.AttachmentType
import com.sync.app.model.ChatMessage
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
    private val json: Json
) : AiProvider {

    override val id: ProviderId = config.id
    override val capabilities: Set<ProviderCapability> = config.capabilities
    override val supportsStreaming: Boolean = config.supportsStreaming

    // [P-037] Sekarang dinamis: mengambil dari endpoint /v1/models
    override suspend fun listModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${config.baseUrl}/models")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, raw))
                }

                val root = json.parseToJsonElement(raw) as? JsonObject
                    ?: return@withContext Result.failure(AiException.InvalidResponse("Invalid JSON for models list"))

                val data = root["data"] as? JsonArray
                    ?: return@withContext Result.failure(AiException.InvalidResponse("Missing 'data' array in models response"))

                val models = data.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val modelId = obj["id"]?.toString()?.trim('"') ?: return@mapNotNull null
                    val displayName = obj["display_name"]?.toString()?.trim('"') ?: modelId

                    AiModel(
                        id = modelId,
                        displayName = displayName,
                        providerId = id,
                        capabilities = setOf(ProviderCapability.Streaming, ProviderCapability.Multimodal)
                    )
                }
                Result.success(models)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(HttpErrorMapper.mapThrowable(t))
        }
    }

    override suspend fun chat(apiKey: String, request: ChatRequest): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildAnthropicRequest(request, stream = false)
            val httpRequest = buildHttpRequest(apiKey, bodyJson)

            client.newCall(httpRequest).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, raw))

                val parsed = json.parseToJsonElement(raw).let { element ->
                    element as? JsonObject ?: return@withContext Result.failure(AiException.InvalidResponse("Invalid JSON"))
                }

                val error = parsed["error"] as? JsonObject
                if (error != null) {
                    val message = error["message"]?.toString()?.trim('"') ?: "Unknown error"
                    return@withContext Result.failure(AiException.ProviderError(resp.code, message))
                }

                val contentArray = parsed["content"] as? JsonArray
                val content = contentArray?.firstOrNull()?.let { item ->
                    val obj = item as? JsonObject
                    obj?.get("text")?.toString()?.trim('"')
                }.orEmpty()

                if (content.isBlank()) return@withContext Result.failure(AiException.InvalidResponse("Empty response content"))

                val finishReason = parsed["stop_reason"]?.toString()?.trim('"')
                Result.success(ChatResponse(content, request.modelId, finishReason))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(HttpErrorMapper.mapThrowable(t))
        }
    }

    override fun chatStream(apiKey: String, request: ChatRequest): Flow<StreamChunk> = flow {
        val bodyJson = buildAnthropicRequest(request, stream = true)
        val httpRequest = buildHttpRequest(apiKey, bodyJson)

        val response = try { client.newCall(httpRequest).execute() } catch (t: Throwable) {
            if (t is CancellationException) throw t
            emit(StreamChunk.Error(HttpErrorMapper.mapThrowable(t)))
            return@flow
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                emit(StreamChunk.Error(HttpErrorMapper.mapStatus(resp.code, resp.body?.string().orEmpty())))
                return@use
            }

            val source = resp.body?.source() ?: run { emit(StreamChunk.Error(AiException.InvalidResponse("Empty streaming body"))); return@use }

            while (!source.exhausted()) {
                val line = try { source.readUtf8Line() } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    emit(StreamChunk.Error(HttpErrorMapper.mapThrowable(t)))
                    return@use
                } ?: break

                if (line.isBlank() || !line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") {
                    emit(StreamChunk.Done(null))
                    return@use
                }

                val chunk = try { json.parseToJsonElement(payload) as? JsonObject } catch (t: Throwable) {
                    null
                } ?: continue

                val type = chunk["type"]?.toString()?.trim('"')

                if (type == "error") {
                    val errorMsg = (chunk["error"] as? JsonObject)?.get("message")?.toString()?.trim('"') ?: "Stream error"
                    emit(StreamChunk.Error(AiException.ProviderError(500, errorMsg)))
                    return@use
                }

                if (type == "content_block_delta") {
                    val delta = chunk["delta"] as? JsonObject
                    val text = delta?.get("text")?.toString()?.trim('"')
                    if (!text.isNullOrEmpty()) {
                        emit(StreamChunk.Delta(text))
                    }
                } else if (type == "message_stop") {
                    emit(StreamChunk.Done(null))
                    return@use
                }
            }
            emit(StreamChunk.Done(null))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildAnthropicRequest(request: ChatRequest, stream: Boolean): String {
        return buildJsonObject {
            put("model", request.modelId)
            // Anthropic mewajibkan max_tokens.
            put("max_tokens", request.maxTokens ?: DEFAULT_MAX_TOKENS)
            put("stream", stream)
            request.temperature?.let { put("temperature", clampTemperature(it, 0f, 1f).toDouble()) }

            if (!request.systemPrompt.isNullOrBlank()) {
                put("system", request.systemPrompt)
            }

            putJsonArray("messages") {
                val lastUserMessageId = request.messages.lastOrNull { it.role == MessageRole.User }?.id

                request.messages.forEach { m ->
                    if (m.status == MessageStatus.Failed) return@forEach

                    val role = if (m.role == MessageRole.User) "user" else "assistant"

                    if (m.content.isNotBlank() && !(m.id == lastUserMessageId && request.attachments.isNotEmpty())) {
                        add(buildJsonObject {
                            put("role", role)
                            put("content", m.content)
                        })
                        return@forEach
                    }

                    if (m.id == lastUserMessageId && request.attachments.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", role)
                            putJsonArray("content") {
                                if (m.content.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", m.content)
                                    })
                                }
                                request.attachments.forEach { att ->
                                    if (att.textContent == null && att.base64Data.isNotEmpty() && att.type == AttachmentType.Image) {
                                        add(buildJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", att.mimeType)
                                                put("data", att.base64Data)
                                            }
                                        })
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }.toString()
    }

    private fun buildHttpRequest(apiKey: String, body: String): Request {
        return Request.Builder()
            .url("${config.baseUrl}/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
