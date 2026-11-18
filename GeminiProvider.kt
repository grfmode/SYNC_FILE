package com.sync.app.domain.provider.gemini

import com.sync.app.data.network.HttpErrorMapper
import com.sync.app.domain.chat.clampTemperature
import com.sync.app.domain.error.AiException
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.AiProvider
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.domain.provider.ChatResponse
import com.sync.app.domain.provider.GeneratedImage
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
    private val json: Json
) : AiProvider {

    override val id: ProviderId = config.id
    override val capabilities: Set<ProviderCapability> = config.capabilities
    override val supportsStreaming: Boolean = config.supportsStreaming

    override suspend fun listModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${config.baseUrl}/models?key=$apiKey").get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, body))
                val parsed = json.decodeFromString<GeminiModelsResponse>(body)
                val models = parsed.models?.mapNotNull { dto ->
                    val idOnly = dto.name.removePrefix("models/")
                    if (idOnly.isBlank()) return@mapNotNull null
                    AiModel(idOnly, dto.displayName ?: idOnly, id, setOf(ProviderCapability.Streaming, ProviderCapability.Multimodal), dto.inputTokenLimit)
                }.orEmpty()
                Result.success(models)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(HttpErrorMapper.mapThrowable(t))
        }
    }

    override suspend fun chat(apiKey: String, request: ChatRequest): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val body = buildGeminiBody(request)
            val httpRequest = buildHttpRequest(apiKey, request.modelId, body, false)
            client.newCall(httpRequest).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, raw))
                val parsed = json.decodeFromString<GeminiResponse>(raw)
                parsed.error?.let { return@withContext Result.failure(AiException.ProviderError(it.code ?: 0, it.message ?: "Provider error")) }
                val candidate = parsed.candidates?.firstOrNull()
                val parts = candidate?.content?.parts.orEmpty()
                val content = parts.joinToString("") { it.text ?: "" }
                val images = parts.mapNotNull { part ->
                    (part.inlineData ?: part.inlineDataSnake)?.let { GeneratedImage(it.mimeType, base64Data = it.data) }
                }
                if (content.isBlank() && images.isEmpty()) return@withContext Result.failure(AiException.InvalidResponse("Empty response content"))
                Result.success(ChatResponse(content, request.modelId, candidate?.finishReason, images))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(HttpErrorMapper.mapThrowable(t))
        }
    }

    override fun chatStream(apiKey: String, request: ChatRequest): Flow<StreamChunk> = flow {
        val body = buildGeminiBody(request)
        val httpRequest = buildHttpRequest(apiKey, request.modelId, body, true)
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
                val chunk = try { json.decodeFromString<GeminiResponse>(payload) } catch (t: Throwable) {
                    emit(StreamChunk.Error(AiException.InvalidResponse("Failed to parse chunk", t)))
                    return@use
                }
                chunk.error?.let { emit(StreamChunk.Error(AiException.ProviderError(it.code ?: 0, it.message ?: "Provider error"))); return@use }
                val candidate = chunk.candidates?.firstOrNull() ?: continue
                val parts = candidate.content?.parts.orEmpty()
                val text = parts.joinToString("") { it.text ?: "" }
                if (text.isNotEmpty()) emit(StreamChunk.Delta(text))
                parts.forEach { part ->
                    (part.inlineData ?: part.inlineDataSnake)?.let { emit(StreamChunk.Image(GeneratedImage(it.mimeType, base64Data = it.data))) }
                }
                candidate.finishReason?.let { emit(StreamChunk.Done(it)); return@use }
            }
            emit(StreamChunk.Done(null))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Badan request dibangun sebagai JSON langsung agar `generationConfig`
     * bisa dikirim tanpa bergantung pada DTO request.
     */
    private fun buildGeminiBody(request: ChatRequest): String = buildJsonObject {
        val lastUserMessageId = request.messages.lastOrNull { it.role == MessageRole.User }?.id

        putJsonArray("contents") {
            request.messages.forEach { m: ChatMessage ->
                if (m.status == MessageStatus.Failed) return@forEach
                val isLastUserMessage = m.id == lastUserMessageId
                add(buildJsonObject {
                    put("role", if (m.role == MessageRole.User) "user" else "model")
                    putJsonArray("parts") {
                        if (m.content.isNotBlank()) {
                            add(buildJsonObject { put("text", m.content) })
                        }
                        if (isLastUserMessage && request.attachments.isNotEmpty()) {
                            request.attachments.forEach { attachment ->
                                // T-042: Pastikan hanya file biner (base64Data tidak kosong) yang dikirim via inlineData
                                if (attachment.textContent == null && attachment.base64Data.isNotEmpty()) {
                                    if (attachment.type == AttachmentType.Image ||
                                        attachment.mimeType == "application/pdf" ||
                                        attachment.type == AttachmentType.Document
                                    ) {
                                        add(buildJsonObject {
                                            putJsonObject("inlineData") {
                                                put("mimeType", attachment.mimeType)
                                                put("data", attachment.base64Data)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) }
            }
        }

        val maxTokens = request.maxTokens
        val temperature = request.temperature
        if (maxTokens != null || temperature != null) {
            putJsonObject("generationConfig") {
                maxTokens?.let { put("maxOutputTokens", it) }
                temperature?.let { put("temperature", clampTemperature(it, 0f, 2f).toDouble()) }
            }
        }
    }.toString()

    private fun buildHttpRequest(apiKey: String, modelId: String, body: String, stream: Boolean): Request {
        val endpoint = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        val accept = if (stream) "text/event-stream" else "application/json"
        return Request.Builder().url("${config.baseUrl}/models/$modelId:$endpoint&key=$apiKey")
            .addHeader("Content-Type", "application/json").addHeader("Accept", accept)
            .post(body.toRequestBody("application/json".toMediaType())).build()
    }
}
