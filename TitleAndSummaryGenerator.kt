package com.sync.app.domain.chat.usecase

import android.util.Log
import com.sync.app.domain.apikey.ApiKeyStore
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.domain.chat.PromptBudget
import com.sync.app.domain.memory.MemoryExtractor
import com.sync.app.domain.memory.MemoryRepository
import com.sync.app.domain.memory.MemoryScope
import com.sync.app.domain.memory.MemorySource
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.domain.provider.ProviderRegistry
import com.sync.app.domain.settings.SettingsStore
import com.sync.app.model.ChatMessage
import com.sync.app.model.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Judul, ringkasan bergulir, dan ekstraksi memori.
 *
 * Memori tidak lagi ditulis sebagai satu blok teks ke `projects.memoryText`.
 * Ekstraksi memakai [MemoryExtractor] sehingga tiap butir punya tipe, scope,
 * dan sumber sendiri.
 */
@Singleton
class TitleAndSummaryGenerator @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsStore: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val providerRegistry: ProviderRegistry,
    private val modelCatalogUseCase: ModelCatalogUseCase,
    private val memoryRepository: MemoryRepository
) {
    private companion object {
        const val TAG = "SYNC_PROMPT"
        const val TITLE_ASSISTANT_MAX_CHARS = 1200
        const val SUMMARY_MAX_TOKENS = 1024
        const val SUMMARY_TEMPERATURE = 0.3f
        const val TITLE_MAX_TOKENS = 40
        const val TITLE_TEMPERATURE = 0.4f

        const val TITLE_SYSTEM_PROMPT =
            "Write a conversation title. Output the title only — no quotes, no trailing punctuation, no prefix. " +
                "Same language as the user message. Max 4 words, max 40 characters. Name the topic; do not restate the request."

        const val SUMMARY_SYSTEM_PROMPT =
            "You maintain the running summary of a long conversation. It is the only record the model will have of " +
                "everything already discussed, so completeness matters more than brevity.\n\n" +
                "Merge the previous summary with the new messages into one updated summary.\n\n" +
                "Output the summary only — no preamble, no fences. Use exactly these headings, in this order:\n\n" +
                "CONTEXT: what this conversation is about, its goal, and where it stands.\n" +
                "FACTS: established facts — names, numbers, versions, paths, commands, identifiers, verbatim.\n" +
                "PREFERENCES: how the user wants things done, stated or demonstrated.\n" +
                "DECISIONS: what was chosen and why, in order.\n" +
                "OPEN: unresolved questions, pending tasks, next steps.\n\n" +
                "Rules:\n" +
                "- Same language as the conversation. Identifiers, paths, code, and numbers stay verbatim.\n" +
                "- Carry forward everything from the previous summary that is still true. Drop only what is resolved or contradicted.\n" +
                "- One item per line. Split rather than merge unrelated items.\n" +
                "- Never invent. Mark uncertain items with \"(?)\".\n" +
                "- No greetings, no restating full outputs, no commentary.\n" +
                "- Target 600–900 tokens."
    }

    private val summaryMutex = Mutex()
    private val memoryMutex = Mutex()

    suspend fun onAssistantMessageCompleted(
        conversationId: Long,
        isNewConversation: Boolean,
        userPrompt: String,
        assistantResponse: String
    ) {
        if (isNewConversation) generateTitle(conversationId, userPrompt, assistantResponse)
        refreshSummaryIfNeeded(conversationId)
    }

    suspend fun invalidateSummary(conversationId: Long) {
        runCatching { chatRepository.updateConversationSummary(conversationId, null, null) }
    }

    private fun contextLimitOf(modelId: String?): Int? =
        modelId?.let { id -> modelCatalogUseCase.availableModels.value.firstOrNull { it.id == id }?.contextLimit }

    private suspend fun generateTitle(conversationId: Long, userPrompt: String, assistantResponse: String) {
        runCatching {
            val providerId = settingsStore.selectedProviderId.first()
            val modelId = settingsStore.selectedModelId.first() ?: return@runCatching
            val apiKey = apiKeyStore.load(providerId) ?: return@runCatching
            val provider = providerRegistry.get(providerId) ?: return@runCatching

            val request = ChatRequest(
                modelId = modelId,
                messages = listOf(
                    ChatMessage(
                        0,
                        "User: $userPrompt\nAI: ${assistantResponse.take(TITLE_ASSISTANT_MAX_CHARS)}",
                        MessageRole.User
                    )
                ),
                systemPrompt = TITLE_SYSTEM_PROMPT,
                maxTokens = TITLE_MAX_TOKENS,
                temperature = TITLE_TEMPERATURE
            )

            provider.chat(apiKey, request).onSuccess { response ->
                if (response.images.isNotEmpty()) return@onSuccess
                val title = response.content.trim().removeSurrounding("\"").replace("\n", " ").take(40)
                if (title.isNotBlank()) chatRepository.updateConversationTitle(conversationId, title)
            }
        }.onFailure { Log.w(TAG, "Gagal membuat judul", it) }
    }

    private suspend fun refreshSummaryIfNeeded(conversationId: Long) {
        if (!summaryMutex.tryLock()) return
        try {
            if (!settingsStore.summaryEnabled.first()) return

            val modelId = settingsStore.selectedModelId.first()
            val budget = PromptBudget.forContextLimit(contextLimitOf(modelId))

            val fullHistory = chatRepository.getMessages(conversationId)
            if (fullHistory.size < budget.summaryThreshold) return

            val windowStartIndex = maxOf(0, fullHistory.size - budget.historyWindow)
            val windowStartId = fullHistory[windowStartIndex].id

            val existing = chatRepository.getConversationSummary(conversationId)
            val lastSummarizedId = existing?.upToMessageId ?: 0L

            val toSummarize = fullHistory.filter { it.id < windowStartId && it.id > lastSummarizedId }
            if (toSummarize.size < budget.summaryRefreshK) return

            val providerId = settingsStore.selectedProviderId.first()
            val resolvedModelId = modelId ?: return
            val apiKey = apiKeyStore.load(providerId) ?: return
            val provider = providerRegistry.get(providerId) ?: return

            val request = ChatRequest(
                modelId = resolvedModelId,
                messages = listOf(
                    ChatMessage(0, buildSummaryPrompt(existing?.text, toSummarize, budget), MessageRole.User)
                ),
                systemPrompt = SUMMARY_SYSTEM_PROMPT,
                maxTokens = SUMMARY_MAX_TOKENS,
                temperature = SUMMARY_TEMPERATURE
            )

            provider.chat(apiKey, request).onSuccess { response ->
                val text = response.content.trim()
                if (text.isNotBlank()) {
                    runCatching {
                        chatRepository.updateConversationSummary(conversationId, text, windowStartId - 1)
                        extractMemory(conversationId, toSummarize)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memperbarui ringkasan", e)
        } finally {
            summaryMutex.unlock()
        }
    }

    /**
     * Ekstraksi heuristik dari pesan mentah. Tanpa panggilan model tambahan.
     * Pesan asisten ikut dibaca karena aturan sering muncul di sana.
     */
    private suspend fun extractMemory(conversationId: Long, messages: List<ChatMessage>) {
        if (!memoryMutex.tryLock()) return
        try {
            if (!settingsStore.autoExtractEnabled.first()) return

            val conversation = chatRepository.getConversationById(conversationId) ?: return
            val projectId = conversation.projectId
            val scope = if (projectId != null) MemoryScope.PROJECT else MemoryScope.GLOBAL

            messages.forEach { message ->
                val candidates = MemoryExtractor.extract(
                    text = message.content,
                    scope = scope,
                    projectId = projectId,
                    source = MemorySource.AUTO,
                    provenance = "message:${message.id}"
                )
                memoryRepository.rememberAll(candidates)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengekstrak memori", e)
        } finally {
            memoryMutex.unlock()
        }
    }

    private fun truncateAtBoundary(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
        return text.substring(0, cut).trimEnd() + "…"
    }

    private fun buildSummaryPrompt(existing: String?, messages: List<ChatMessage>, budget: PromptBudget): String =
        buildString {
            if (!existing.isNullOrBlank()) {
                appendLine("RINGKASAN SEBELUMNYA:")
                appendLine(existing)
                appendLine()
                appendLine("PERCAKAPAN BARU:")
            } else {
                appendLine("PERCAKAPAN BARU:")
            }
            messages.forEach { message ->
                val role = if (message.role == MessageRole.User) "User" else "AI"
                appendLine("$role: ${truncateAtBoundary(message.content, budget.maxCharsPerMessage)}")
            }
            appendLine()
            append("Output hanya ringkasan gabungan.")
        }
}