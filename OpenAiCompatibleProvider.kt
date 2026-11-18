package com.sync.app.domain.provider.openai

import android.util.Log
import com.sync.app.data.network.HttpErrorMapper
import com.sync.app.domain.chat.clampTemperature
import com.sync.app.domain.error.AiException
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.AiProvider
import com.sync.app.domain.provider.AttachmentPayload
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.domain.provider.ChatResponse
import com.sync.app.domain.provider.GeneratedImage
import com.sync.app.domain.provider.ProviderAuthType
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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line

class OpenAiCompatibleProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
    private val streamingClient: HttpClient,
    private val json: Json
) : AiProvider {

    init {
        require(config.authType != ProviderAuthType.QueryParam) { "OpenAiCompatibleProvider tidak mendukung QueryParam auth." }
    }

    override val id: ProviderId = config.id
    override val capabilities: Set<ProviderCapability> = config.capabilities
    override val supportsStreaming: Boolean = config.supportsStreaming

    override suspend fun listModels(apiKey: String): Result<List<AiModel>> =
        withContext(Dispatchers.IO) {
            try {
                val request = buildGetRequest(apiKey, "/models")
                val response = client.newCall(request).execute()
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, body))
                    val parsed = json.decodeFromString<OaiModelsResponse>(body)
                    val models = parsed.data.mapNotNull { dto ->
                        if (dto.id.isBlank()) return@mapNotNull null
                        // Guts AI menyertakan status model; model offline tidak ditawarkan.
                        if (dto.status.equals("offline", ignoreCase = true)) return@mapNotNull null
                        AiModel(dto.id, dto.name ?: dto.id, id, capabilities, dto.resolveContextLimit())
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
            val body = buildRequestBody(request, stream = false)
            val httpRequest = buildPostRequest(apiKey, "/chat/completions", body, false)
            val response = client.newCall(httpRequest).execute()
            response.use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // Beberapa gateway menolak model image-only pada endpoint chat dan
                    // mengembalikan instruksi eksplisit untuk memakai images/generations.
                    // Hanya error spesifik ini yang memicu route image; tidak ada provider fallback.
                    if (isImageOnlyRoutingError(raw)) {
                        return@withContext generateImage(apiKey, request)
                    }
                    return@withContext Result.failure(HttpErrorMapper.mapStatus(resp.code, raw))
                }
                val parsed = json.decodeFromString<OaiChatResponse>(raw)
                val rawJson = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                parsed.error?.let { providerError ->
                    val message = providerError.message ?: "Provider error"
                    if (isImageOnlyRoutingError(message)) {
                        return@withContext generateImage(apiKey, request)
                    }
                    return@withContext Result.failure(AiException.ProviderError(providerError.code ?: 0, message))
                }
                val choice = parsed.choices.firstOrNull()
                val message = choice?.message
                val content = extractText(message?.content)
                // Beberapa gateway/tool wrapper mengembalikan gambar sebagai resource_link,
                // bukan format standar OpenAI `images` atau `image_url`.
                val images = (extractImages(message?.content) +
                    message?.images.orEmpty().mapNotNull { parseImagePart(it) } +
                    extractImages(rawJson)).distinctBy { it.url ?: it.base64Data }
                if (content.isBlank() && images.isEmpty()) return@withContext Result.failure(AiException.InvalidResponse("Empty response content"))
                val displayContent = cleanImageMarkup(content, images)
                Result.success(ChatResponse(displayContent, parsed.model ?: request.modelId, choice?.finishReason, images))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(HttpErrorMapper.mapThrowable(t))
        }
    }

    /**
     * Streaming SSE berbasis Ktor.
     *
     * Penting: `client.post()` di Ktor membaca SELURUH body ke memori sebelum
     * mengembalikan respons, sehingga streaming tidak pernah terlihat bertahap.
     * Karena itu dipakai `preparePost().execute { }`, yang memberi akses ke body
     * saat masih mengalir. Event dikirim lewat `channelFlow` (`send`) karena
     * dipancarkan dari dalam lambda `execute`.
     *
     * Log diagnostik memakai tag [TAG] (filter logcat: `tag:SyncStream`).
     * Isi prompt, API key, dan isi jawaban TIDAK dicatat; hanya ukuran, jumlah,
     * dan waktu. Pengecualian: chunk yang gagal di-parse dicatat maksimal
     * [LOG_PAYLOAD_PREVIEW] karakter pertamanya agar format provider bisa dilihat.
     */
    override fun chatStream(apiKey: String, request: ChatRequest): Flow<StreamChunk> = channelFlow {
        val startedAt = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - startedAt

        var lines = 0
        var dataLines = 0
        var deltas = 0
        var emptyDeltas = 0
        var deltaChars = 0
        var skippedChunks = 0
        var firstLineAt = -1L
        var firstDeltaAt = -1L
        var doneSent = false

        try {
            val body = buildRequestBody(request, stream = true)
            val url = "${config.baseUrl}/chat/completions"
            Log.i(TAG, "START provider=${config.id.value} model=${request.modelId} bodyBytes=${body.length} url=$url")

            streamingClient.preparePost(url) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Text.EventStream)
                header("Cache-Control", "no-cache")
                setBody(body)
                if (apiKey.isNotBlank()) {
                    when (config.authType) {
                        ProviderAuthType.Bearer -> header("Authorization", "Bearer $apiKey")
                        ProviderAuthType.HeaderKey -> header("X-Api-Key", apiKey)
                        ProviderAuthType.QueryParam -> Unit
                    }
                }
            }.execute<Unit> { response ->
                Log.i(
                    TAG,
                    "HEADERS status=${response.status.value} contentType=${response.headers["Content-Type"]} " +
                        "encoding=${response.headers["Content-Encoding"]} transfer=${response.headers["Transfer-Encoding"]} " +
                        "t=${elapsed()}ms"
                )

                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Log.w(TAG, "HTTP_ERROR status=${response.status.value} bodyChars=${errorBody.length} t=${elapsed()}ms")
                    send(StreamChunk.Error(HttpErrorMapper.mapStatus(response.status.value, errorBody)))
                    doneSent = true
                    return@execute
                }

                val ct = response.headers["Content-Type"].orEmpty()
                if (!ct.contains("event-stream", ignoreCase = true)) {
                    // Bukan SSE: gateway mungkin mengabaikan stream=true. Dicatat agar mudah didiagnosis.
                    Log.w(TAG, "NOT_SSE contentType='$ct' (server mungkin tidak mendukung streaming)")
                }

                val channel = response.bodyAsChannel()
                while (true) {
                    // readUTF8Line suspend dan mengikuti pembatalan coroutine.
                    val line = channel.readUTF8Line() ?: break
                    lines++
                    if (firstLineAt < 0) {
                        firstLineAt = elapsed()
                        Log.i(TAG, "FIRST_LINE t=${firstLineAt}ms")
                    }
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    dataLines++

                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
                        Log.i(TAG, "DONE marker t=${elapsed()}ms")
                        send(StreamChunk.Done(null))
                        doneSent = true
                        return@execute
                    }

                    val chunk = try {
                        json.decodeFromString<OaiStreamChunk>(payload)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        // Satu chunk yang aneh tidak boleh mematikan seluruh stream.
                        skippedChunks++
                        Log.w(
                            TAG,
                            "PARSE_SKIP #$skippedChunks ${t.javaClass.simpleName}: ${t.message?.take(120)} " +
                                "payload[${payload.length}]=${payload.take(LOG_PAYLOAD_PREVIEW)}"
                        )
                        continue
                    }

                    chunk.error?.takeIf { it !is JsonNull }?.let { err ->
                        val mapped = parseStreamError(err)
                        Log.w(TAG, "PROVIDER_ERROR code=${mapped.statusCode} msg=${mapped.message?.take(160)} t=${elapsed()}ms")
                        send(StreamChunk.Error(mapped))
                        doneSent = true
                        return@execute
                    }

                    val chunkJson = runCatching { json.parseToJsonElement(payload) }.getOrNull()
                    extractImages(chunkJson).forEach { send(StreamChunk.Image(it)) }
                    val choice = chunk.choices.firstOrNull() ?: continue

                    val text = extractText(choice.delta?.content)
                    if (text.isNotEmpty()) {
                        if (firstDeltaAt < 0) {
                            firstDeltaAt = elapsed()
                            Log.i(TAG, "FIRST_DELTA t=${firstDeltaAt}ms")
                        }
                        deltas++
                        deltaChars += text.length
                        send(StreamChunk.Delta(text))
                    } else {
                        emptyDeltas++
                    }
                    extractImages(choice.delta?.content).forEach { send(StreamChunk.Image(it)) }
                    choice.delta?.images.orEmpty().mapNotNull { parseImagePart(it) }
                        .forEach { send(StreamChunk.Image(it)) }

                    // finish_reason kosong ("") dianggap belum selesai.
                    choice.finishReason?.takeIf { it.isNotBlank() }?.let {
                        Log.i(TAG, "FINISH reason=$it t=${elapsed()}ms")
                        send(StreamChunk.Done(it))
                        doneSent = true
                        return@execute
                    }
                }
                Log.i(TAG, "EOF tanpa [DONE]/finish_reason t=${elapsed()}ms")
            }
            if (!doneSent) send(StreamChunk.Done(null))
        } catch (t: Throwable) {
            if (t is CancellationException) {
                Log.i(TAG, "CANCELLED t=${elapsed()}ms")
                throw t
            }
            Log.e(TAG, "FAILED ${t.javaClass.name}: ${t.message} t=${elapsed()}ms", t)
            send(StreamChunk.Error(HttpErrorMapper.mapThrowable(t)))
        } finally {
            Log.i(
                TAG,
                "SUMMARY total=${elapsed()}ms lines=$lines dataLines=$dataLines deltas=$deltas " +
                    "emptyDeltas=$emptyDeltas chars=$deltaChars skipped=$skippedChunks " +
                    "firstLine=${firstLineAt}ms firstDelta=${firstDeltaAt}ms"
            )
        }
    }

    /** Error stream bisa berupa objek {code,message}, objek dengan code string, atau string biasa. */
    private fun parseStreamError(element: JsonElement): AiException.ProviderError = when (element) {
        is JsonObject -> {
            val message = (element["message"] as? JsonPrimitive)?.contentOrNull
                ?: (element["error"] as? JsonPrimitive)?.contentOrNull
                ?: "Provider error"
            val code = (element["code"] as? JsonPrimitive)?.intOrNull
                ?: (element["status"] as? JsonPrimitive)?.intOrNull
                ?: 0
            AiException.ProviderError(code, message)
        }
        is JsonPrimitive -> AiException.ProviderError(0, element.contentOrNull ?: "Provider error")
        else -> AiException.ProviderError(0, "Provider error")
    }

    /**
     * Model reasoning menolak `max_tokens` dan sebagian menolak `temperature`.
     * Keduanya dipetakan berbeda dari model biasa.
     */
    private fun isReasoningModel(modelId: String): Boolean =
        REASONING_MODEL.containsMatchIn(modelId.lowercase())

    private fun buildRequestBody(request: ChatRequest, stream: Boolean): String = buildJsonObject {
        put("model", request.modelId)
        putJsonArray("messages") {
            buildMessages(request).forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        }
        put("stream", stream)

        val reasoning = isReasoningModel(request.modelId)
        request.maxTokens?.let { limit ->
            if (reasoning) put("max_completion_tokens", limit) else put("max_tokens", limit)
        }
        if (!reasoning) {
            request.temperature?.let { put("temperature", clampTemperature(it, 0f, 2f).toDouble()) }
        }
    }.toString()

    private fun isImageOnlyRoutingError(raw: String): Boolean =
        raw.contains("image-only", ignoreCase = true) &&
            raw.contains("images/generations", ignoreCase = true)

    /** Jalur standar OpenAI Images API untuk model yang secara eksplisit menolak chat. */
    private fun generateImage(apiKey: String, request: ChatRequest): Result<ChatResponse> {
        return try {
            val prompt = request.messages.lastOrNull { it.role == MessageRole.User }
                ?.content?.trim().orEmpty()
            if (prompt.isBlank()) {
                return Result.failure(AiException.ImageGenerationFailed("Prompt gambar kosong."))
            }
            val body = buildJsonObject {
                put("model", request.modelId)
                put("prompt", prompt)
            }.toString()
            val httpRequest = buildPostRequest(apiKey, "/images/generations", body, false)
            val response = client.newCall(httpRequest).execute()
            response.use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val mapped = HttpErrorMapper.mapStatus(resp.code, raw)
                    return Result.failure(AiException.ImageGenerationFailed(mapped.message ?: "Image generation gagal."))
                }
                val parsed = json.decodeFromString<OaiImageGenerationResponse>(raw)
                val images = parsed.data.mapNotNull { item ->
                    val encoded = item.b64Json?.takeIf { it.isNotBlank() }
                    if (encoded != null) GeneratedImage("image/png", base64Data = encoded)
                    else parseImageUrl(item.url)
                }
                if (images.isEmpty()) {
                    Result.failure(AiException.ImageGenerationFailed("Endpoint image generation tidak mengembalikan gambar."))
                } else {
                    Result.success(ChatResponse("Gambar berhasil dibuat.", parsed.model ?: request.modelId, images = images))
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(AiException.ImageGenerationFailed(t.message ?: "Image generation gagal.", t))
        }
    }

    private fun extractText(element: JsonElement?): String = when (element) {
        is JsonPrimitive -> if (element.isString) element.content else ""
        is JsonArray -> element.mapNotNull { item -> (item as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.content } }.joinToString("")
        else -> ""
    }

    private fun cleanImageMarkup(content: String, images: List<GeneratedImage>): String {
        if (images.isEmpty()) return content

        var cleaned = content

        // Tool wrapper dari beberapa gateway (termasuk Pollinations/MCP) bukan
        // bagian dari jawaban assistant. Jangan tampilkan JSON/tool-call mentah.
        cleaned = cleaned.replace(
            Regex("(?is)<details\\b(?=[^>]*\\btype\\s*=\\s*['\"]tool_calls['\"])[^>]*>.*?</details>"),
            ""
        )

        // Hapus Markdown image yang menunjuk ke gambar yang sudah disimpan
        // sebagai attachment agar gambar tidak tampil dua kali sebagai URL.
        cleaned = cleaned.replace(
            Regex("!\\[[^]]*]\\(<?https?://[^\\s)>]+>?\\)"),
            ""
        )

        // Beberapa wrapper mengirim payload JSON gambar sebagai teks biasa.
        val trimmed = cleaned.trim()
        if (trimmed.startsWith("{") &&
            (trimmed.contains("resource_link") || trimmed.contains("media_type") || trimmed.contains("mimeType"))) {
            return "Gambar berhasil dibuat."
        }

        // Hilangkan baris kosong berlebih setelah blok tool dihapus.
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\\n\\n").trim()
        return cleaned.ifBlank { "Gambar berhasil dibuat." }
    }

    private fun extractImages(element: JsonElement?): List<GeneratedImage> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonArray -> element.flatMap { extractImages(it) }
            is JsonPrimitive -> {
                if (!element.isString) emptyList() else {
                    val value = element.content
                    val markdownImages = Regex("!\\[[^]]*]\\(<?(https?://[^\\s)>]+)>?\\)")
                        .findAll(value).mapNotNull { parseImageUrl(it.groupValues[1], "image/jpeg") }.toList()
                    val nestedJson = runCatching { json.parseToJsonElement(value) }.getOrNull()
                    markdownImages + if (nestedJson != null) extractImages(nestedJson) else emptyList()
                }
            }
            is JsonObject -> {
                val direct = parseImagePart(element)
                val nested = element.values.flatMap { extractImages(it) }
                listOfNotNull(direct) + nested
            }
            else -> emptyList()
        }
    }

    private fun parseImagePart(part: JsonElement): GeneratedImage? {
        val obj = part as? JsonObject ?: return null
        val resourceLink = obj["resource_link"] as? JsonObject
        val resourceMime = (resourceLink?.get("mimeType") as? JsonPrimitive)?.content
            ?: (obj["mimeType"] as? JsonPrimitive)?.content
            ?: (obj["media_type"] as? JsonPrimitive)?.content
            ?: (obj["mime_type"] as? JsonPrimitive)?.content
        val resourceUri = (resourceLink?.get("uri") as? JsonPrimitive)?.content
            ?: (obj["uri"] as? JsonPrimitive)?.content
        if (!resourceMime.isNullOrBlank() && resourceMime.startsWith("image/") && !resourceUri.isNullOrBlank()) {
            parseImageUrl(resourceUri, resourceMime)?.let { return it }
        }

        val imageUrl = obj["image_url"]
        val url = ((imageUrl as? JsonObject)?.get("url") as? JsonPrimitive)?.content
            ?: (imageUrl as? JsonPrimitive)?.content
        parseImageUrl(url, resourceMime ?: "image/png")?.let { return it }

        val directUrl = (obj["url"] as? JsonPrimitive)?.content
        if (!resourceMime.isNullOrBlank() && resourceMime.startsWith("image/")) {
            parseImageUrl(directUrl, resourceMime)?.let { return it }
        }

        val source = obj["source"] as? JsonObject
        val mime = (source?.get("media_type") as? JsonPrimitive)?.content ?: resourceMime
        val data = (source?.get("data") as? JsonPrimitive)?.content
            ?: (obj["data"] as? JsonPrimitive)?.content
            ?: (obj["b64_json"] as? JsonPrimitive)?.content
        if (!mime.isNullOrBlank() && !data.isNullOrBlank() && mime.startsWith("image/")) {
            return GeneratedImage(mime, base64Data = data)
        }

        // Format Pollinations: data[].url + data[].media_type.
        val dataItems = obj["data"] as? JsonArray
        dataItems?.firstNotNullOfOrNull { item ->
            val itemObj = item as? JsonObject ?: return@firstNotNullOfOrNull null
            val itemMime = (itemObj["media_type"] as? JsonPrimitive)?.content
                ?: (itemObj["mimeType"] as? JsonPrimitive)?.content
            val itemUrl = (itemObj["url"] as? JsonPrimitive)?.content
            if (!itemMime.isNullOrBlank() && itemMime.startsWith("image/")) parseImageUrl(itemUrl, itemMime) else null
        }?.let { return it }
        return null
    }

    private fun parseImageUrl(value: String?, mimeType: String = "image/png"): GeneratedImage? {
        if (value.isNullOrBlank()) return null
        val match = Regex("^data:(image/[a-zA-Z0-9.+-]+);base64,(.*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(value)
        return if (match != null) GeneratedImage(match.groupValues[1], base64Data = match.groupValues[2])
        else if (value.startsWith("https://") || value.startsWith("http://")) GeneratedImage(mimeType, url = value)
        else null
    }

    private fun buildMessages(request: ChatRequest): List<OaiMessage> {
        val result = mutableListOf<OaiMessage>()
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { result.add(OaiMessage("system", JsonPrimitive(it))) }

        val lastUserMessageId = request.messages.lastOrNull { it.role == MessageRole.User }?.id

        request.messages.forEach { m: ChatMessage ->
            if (m.status == MessageStatus.Failed) return@forEach
            val role = if (m.role == MessageRole.User) "user" else "assistant"
            val isLastUser = m.id == lastUserMessageId
            val hasAttachments = isLastUser && request.attachments.isNotEmpty()

            val contentElement = if (hasAttachments) buildContentArray(m.content, request.attachments) else JsonPrimitive(m.content)
            result.add(OaiMessage(role, contentElement))
        }
        return result
    }

    private fun buildContentArray(text: String, attachments: List<AttachmentPayload>): JsonElement {
        val list = mutableListOf<JsonElement>()
        if (text.isNotBlank()) list.add(buildJsonObject { put("type", "text"); put("text", text) })

        attachments.forEach { attachment ->
            // T-042: Pastikan hanya gambar murni yang dikirim sebagai Base64
            if (attachment.type == AttachmentType.Image && attachment.base64Data.isNotEmpty()) {
                list.add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}") })
                })
            }
        }
        return JsonArray(list)
    }

    private fun buildGetRequest(apiKey: String, path: String): Request {
        val builder = Request.Builder().url("${config.baseUrl}$path").addHeader("Accept", "application/json")
        applyAuth(builder, apiKey)
        return builder.get().build()
    }

    private fun buildPostRequest(apiKey: String, path: String, body: String, stream: Boolean): Request {
        val builder = Request.Builder().url("${config.baseUrl}$path").addHeader("Content-Type", "application/json").addHeader("Accept", if (stream) "text/event-stream" else "application/json")
        applyAuth(builder, apiKey)
        return builder.post(body.toRequestBody("application/json".toMediaType())).build()
    }

    private fun applyAuth(builder: Request.Builder, apiKey: String) {
        if (apiKey.isBlank()) return
        when (config.authType) {
            ProviderAuthType.Bearer -> builder.addHeader("Authorization", "Bearer $apiKey")
            ProviderAuthType.HeaderKey -> builder.addHeader("X-Api-Key", apiKey)
            ProviderAuthType.QueryParam -> {}
        }
    }

    private companion object {
        const val TAG = "SyncStream"
        const val LOG_PAYLOAD_PREVIEW = 160
        val REASONING_MODEL = Regex("(^|[-/])(o1|o3|o4|gpt-5)")
    }
}

@Serializable private data class OaiMessage(val role: String, val content: JsonElement)
@Serializable private data class OaiResponseMessage(val role: String? = null, val content: JsonElement? = null, val images: List<JsonElement>? = null)
@Serializable private data class OaiChatResponse(val id: String? = null, val model: String? = null, val choices: List<OaiChoice> = emptyList(), val error: OaiError? = null)
@Serializable private data class OaiChoice(val index: Int? = null, val message: OaiResponseMessage? = null, @SerialName("finish_reason") val finishReason: String? = null)
@Serializable private data class OaiStreamChunk(val choices: List<OaiStreamChoice> = emptyList(), val error: JsonElement? = null)
@Serializable private data class OaiStreamChoice(val index: Int? = null, val delta: OaiDelta? = null, @SerialName("finish_reason") val finishReason: String? = null)
@Serializable private data class OaiDelta(val role: String? = null, val content: JsonElement? = null, val images: List<JsonElement>? = null)
@Serializable private data class OaiError(val code: Int? = null, val message: String? = null)
@Serializable private data class OaiModelsResponse(val data: List<OaiModelDto> = emptyList())
@Serializable private data class OaiModelDto(
    val id: String, val name: String? = null, val status: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("context_window") val contextWindow: Int? = null,
    @SerialName("max_context_length") val maxContextLength: Int? = null
) { fun resolveContextLimit(): Int? = contextLength ?: contextWindow ?: maxContextLength }
@Serializable private data class OaiImageGenerationRequest(val model: String, val prompt: String, val n: Int = 1)
@Serializable private data class OaiImageGenerationResponse(val model: String? = null, val data: List<OaiImageData> = emptyList())
@Serializable private data class OaiImageData(@SerialName("b64_json") val b64Json: String? = null, val url: String? = null)
