package com.sync.app.domain.chat.usecase

import com.sync.app.domain.chat.ChatRepository
import com.sync.app.model.MessageRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [P-034b] Use case khusus untuk menangani ekspor dan pembagian percakapan.
 * 
 * Memisahkan tanggung jawab formatter (Markdown, JSON, Share Text) dari
 * [ChatSessionUseCase] agar file tersebut fokus pada runtime chat & provider.
 */
@Singleton
class ConversationExportUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {

    suspend fun getShareText(conversationId: Long): String {
        return try {
            val msgs = chatRepository.getMessages(conversationId)
            msgs.joinToString("\n\n") {
                val attText = if (it.attachments.isNotEmpty()) "\n[Attachment: ${it.attachments.size} file(s)]" else ""
                if (it.role == MessageRole.User) "User:\n${it.content}$attText" else "AI:\n${it.content}"
            }
        } catch (_: Exception) { "" }
    }

    // [P-033] Ekspor ke Markdown
    suspend fun getExportMarkdown(conversationId: Long): String {
        return try {
            val msgs = chatRepository.getMessages(conversationId)
            buildString {
                appendLine("# Percakapan SYNC")
                appendLine("ID: $conversationId")
                appendLine("----------------------------------------")
                msgs.forEach { msg ->
                    appendLine("### ${if (msg.role == MessageRole.User) "User" else "AI"}")
                    appendLine(msg.content)
                    if (msg.attachments.isNotEmpty()) {
                        appendLine("\n*Lampiran:*")
                        msg.attachments.forEach { appendLine("- ${it.name} (${it.type})") }
                    }
                    appendLine()
                }
            }
        } catch (_: Exception) { "" }
    }

    // [P-033] Ekspor ke JSON
    suspend fun getExportJson(conversationId: Long): String {
        return try {
            val msgs = chatRepository.getMessages(conversationId)
            Json { prettyPrint = true }.encodeToString(msgs)
        } catch (_: Exception) { "[]" }
    }
}