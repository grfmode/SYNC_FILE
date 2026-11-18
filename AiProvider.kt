package com.sync.app.domain.provider

import com.sync.app.domain.error.AiException
import com.sync.app.model.AttachmentType
import com.sync.app.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak minimum provider AI.
 */
interface AiProvider {
    val id: ProviderId
    val capabilities: Set<ProviderCapability>
    val supportsStreaming: Boolean

    suspend fun listModels(apiKey: String): Result<List<AiModel>>

    suspend fun chat(apiKey: String, request: ChatRequest): Result<ChatResponse>

    fun chatStream(apiKey: String, request: ChatRequest): Flow<StreamChunk>
}

/**
 * Permintaan chat.
 *
 * [maxTokens] dan [temperature] null berarti provider memakai default-nya.
 * Keduanya hanya diisi oleh fitur internal (judul, ringkasan, memori), tidak
 * oleh percakapan utama.
 */
data class ChatRequest(
    val modelId: String,
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,
    val attachments: List<AttachmentPayload> = emptyList(),
    val maxTokens: Int? = null,
    val temperature: Float? = null
)

/**
 * Payload attachment yang sudah diproses.
 *
 * T-042: Menambahkan [textContent]. Jika file adalah teks (kode, markdown, dll),
 * isinya diekstrak ke sini dan dikirim sebagai bagian dari prompt (bukan Base64).
 * Jika [textContent] tidak null, [base64Data] akan kosong.
 */
data class AttachmentPayload(
    val mimeType: String,
    val base64Data: String,
    val type: AttachmentType,
    val textContent: String? = null
)

/**
 * Respons chat non-streaming.
 */
data class GeneratedImage(
    val mimeType: String,
    val base64Data: String? = null,
    val url: String? = null
)

data class ChatResponse(
    val content: String,
    val modelId: String,
    val finishReason: String? = null,
    val images: List<GeneratedImage> = emptyList()
)

/**
 * Event stream dari [AiProvider.chatStream].
 */
sealed class StreamChunk {
    data class Delta(val text: String) : StreamChunk()
    data class Image(val image: GeneratedImage) : StreamChunk()
    data class Done(val finishReason: String?) : StreamChunk()
    data class Error(val exception: AiException) : StreamChunk()
}
