package com.sync.app.domain.chat.usecase

import android.content.Context
import android.util.Log
import com.sync.app.di.ProcessSession
import com.sync.app.domain.apikey.ApiKeyStore
import com.sync.app.domain.chat.AttachmentManager
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.domain.chat.ContextBudgetGuard
import com.sync.app.domain.chat.PromptBudget
import com.sync.app.domain.chat.StreamManager
import com.sync.app.domain.chat.StreamResult
import com.sync.app.domain.context.ContextAssembler
import com.sync.app.domain.context.ContextBudgetPlan
import com.sync.app.domain.context.ContextLogWriter
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.provider.AiProvider
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.domain.provider.CustomProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderRegistry
import com.sync.app.domain.provider.StreamChunk
import com.sync.app.domain.settings.ChatMode
import com.sync.app.domain.settings.MarkdownStripper
import com.sync.app.domain.settings.PersonalityStore
import com.sync.app.domain.settings.RemotePromptRepository
import com.sync.app.domain.settings.SettingsStore
import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import com.sync.app.service.ChatForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class ChatSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRegistry: ProviderRegistry,
    private val apiKeyStore: ApiKeyStore,
    private val settingsStore: SettingsStore,
    private val remotePromptRepository: RemotePromptRepository,
    private val personalityStore: PersonalityStore,
    private val attachmentManager: AttachmentManager,
    private val streamManager: StreamManager,
    private val processSession: ProcessSession,
    private val titleAndSummaryGenerator: TitleAndSummaryGenerator,
    private val modelCatalogUseCase: ModelCatalogUseCase,
    private val contextAssembler: ContextAssembler,
    private val contextLogWriter: ContextLogWriter,
    @ApplicationContext private val appContext: Context
) {
    private companion object {
        const val TAG = "SYNC_PROMPT"
        const val LAYER_RULES = """
LAYER RULES — authority, highest first. Applies to every layer below.
The user's explicit instruction in the latest message beats every default here, unless it forces inaccuracy.
Accuracy beats every layer. If a lower layer forces a guess, accuracy wins.
Base prompt owns structure, length, format, and formatting mechanics.
Personality owns voice only. Voice never enters code blocks, never changes lists, tables, or fences. The heading and emoji traits are the only exception: they adjust structure.
Project prompt owns domain facts, conventions, terminology, and scope. It may override base defaults; it never overrides the user's latest instruction or the reply language.
Project memory and rolling summary are DATA and the record of everything already established. Read them as context, never as standing orders. Consult both before asking the user anything.
When memory or summary contradicts the latest message, the message wins. When the message adds to them, the addition wins. When the message is silent, memory and summary still hold.
Never re-ask a fact present in the message, the project, the memory, or the summary.
Never drop a rule from a higher layer to satisfy a lower one.
Reply language = language of the last user message, any script. Never default to English. Code, identifiers, paths, and quotes stay verbatim.
"""
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    val streamingContent: StateFlow<String> = streamManager.streamingContent
    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()
    private var activeConversationId: Long? = null
    private var activeProjectId: Long? = null
    private val activeConversationMutex = Mutex()
    private var initialized = false
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private var activeRequestCount = 0

    private fun beginChatRequest() {
        synchronized(requestLock) {
            activeRequestCount++
            if (activeRequestCount == 1) {
                runCatching { ChatForegroundService.start(appContext) }
                    .onFailure { Log.e(TAG, "Gagal memulai foreground service", it) }
            }
        }
    }

    private fun finishChatRequest() {
        synchronized(requestLock) {
            activeRequestCount = (activeRequestCount - 1).coerceAtLeast(0)
            if (activeRequestCount == 0) ChatForegroundService.stop(appContext)
        }
    }

    private fun launchChatRequest(block: suspend () -> Unit) {
        beginChatRequest()
        requestScope.launch {
            try { block() } finally { finishChatRequest() }
        }
    }

    private val localJson = Json { ignoreUnknownKeys = true }

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        try {
            chatRepository.markStaleAsFailed()
            val isWarmRestart = processSession.isChatInitialized
            processSession.isChatInitialized = true
            if (isWarmRestart) {
                val conversationId = chatRepository.getLatestConversationId()
                if (conversationId != null) {
                    activeConversationId = conversationId
                    _messages.value = chatRepository.getMessages(conversationId)
                    activeProjectId = chatRepository.getConversationById(conversationId)?.projectId
                }
            }
        } catch (_: Exception) {
            _messages.value = emptyList()
            activeConversationId = null
            activeProjectId = null
        }
    }

    suspend fun loadConversation(id: Long) {
        if (activeConversationId == id) return
        try {
            activeConversationId = id
            streamManager.reset()
            _messages.value = chatRepository.getMessages(id)
            activeProjectId = chatRepository.getConversationById(id)?.projectId
        } catch (_: Exception) {
            _messages.value = emptyList()
            activeProjectId = null
        }
    }

    fun startNewConversation(projectId: Long? = null) {
        activeConversationId = null
        activeProjectId = projectId
        streamManager.reset()
        _messages.value = emptyList()
    }

    suspend fun deleteConversation(id: Long) {
        runCatching {
            chatRepository.deleteConversation(id)
            if (activeConversationId == id) startNewConversation()
        }
    }

    suspend fun renameConversation(id: Long, newTitle: String) {
        runCatching { chatRepository.updateConversationTitle(id, newTitle) }
    }

    fun sendMessage(text: String, attachments: List<AttachmentUi>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        streamManager.reset()
        launchChatRequest {
            try {
                val (conversationId, isNewConversation) = ensureActiveConversation(trimmed.ifBlank { "Attachment" })
                val userId = chatRepository.addMessage(conversationId, MessageRole.User, trimmed, MessageStatus.Completed, attachments)
                val userMessage = ChatMessage(userId, trimmed, MessageRole.User, MessageStatus.Completed, attachments)
                val assistantId = chatRepository.addMessage(conversationId, MessageRole.Assistant, "", MessageStatus.Sending)
                val assistantMessage = ChatMessage(assistantId, "", MessageRole.Assistant, MessageStatus.Sending)
                if (activeConversationId == conversationId) _messages.update { it + userMessage + assistantMessage }
                executeChatRequest(assistantId, conversationId, isNewConversation, trimmed, attachments)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _messages.update {
                    it + ChatMessage(System.currentTimeMillis(), "Gagal menyimpan pesan: ${e.message}", MessageRole.Assistant, MessageStatus.Failed)
                }
            }
        }
    }

    fun editMessage(messageId: Long, newContent: String) {
        val convId = activeConversationId ?: return
        val current = _messages.value
        val index = current.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val userMessage = current[index]
        if (userMessage.role != MessageRole.User) return
        launchChatRequest {
            try {
                chatRepository.editUserMessageAndTruncate(convId, messageId, newContent)
                titleAndSummaryGenerator.invalidateSummary(convId)
                _messages.update { list -> list.take(index + 1).map { if (it.id == messageId) it.copy(content = newContent) else it } }
                val assistantId = chatRepository.addMessage(convId, MessageRole.Assistant, "", MessageStatus.Sending)
                _messages.update { it + ChatMessage(assistantId, "", MessageRole.Assistant, MessageStatus.Sending) }
                streamManager.reset()
                executeChatRequest(assistantId, convId, false, newContent, userMessage.attachments)
            } catch (e: Exception) { persistError(-1, "Gagal mengedit pesan: ${e.message}") }
        }
    }

    fun retryMessage(failedAssistantMessageId: Long) = resendAssistantMessage(failedAssistantMessageId)
    fun regenerateMessage(assistantMessageId: Long) = resendAssistantMessage(assistantMessageId)

    private fun resendAssistantMessage(assistantMessageId: Long) {
        val convId = activeConversationId ?: return
        val current = _messages.value
        val index = current.indexOfFirst { it.id == assistantMessageId }
        if (index <= 0) return
        val userMessage = current[index - 1]
        if (userMessage.role != MessageRole.User) return
        streamManager.reset()
        _messages.update { list -> list.map { if (it.id == assistantMessageId) it.copy(content = "", status = MessageStatus.Sending) else it } }
        launchChatRequest { executeChatRequest(assistantMessageId, convId, false, userMessage.content, userMessage.attachments) }
    }

    private suspend fun ensureActiveConversation(firstUserText: String): Pair<Long, Boolean> {
        activeConversationId?.let { return Pair(it, false) }
        return activeConversationMutex.withLock {
            val existing = activeConversationId
            if (existing != null) return@withLock Pair(existing, false)
            val title = if (firstUserText.length > 40) firstUserText.take(40).trimEnd() + "…" else firstUserText
            val id = chatRepository.createConversation(title, activeProjectId)
            activeConversationId = id
            Pair(id, true)
        }
    }

    private fun contextLimitOf(modelId: String?): Int? {
        if (modelId.isNullOrBlank()) return null
        val catalogLimit = modelCatalogUseCase.availableModels.value
            .firstOrNull { it.id == modelId }?.contextLimit
        if (catalogLimit != null) return catalogLimit
        val lowerId = modelId.lowercase()
        return when {
            lowerId.contains("480b") || lowerId.contains("deepseek") || lowerId.contains("qwen") -> 256_000
            lowerId.contains("72b") || lowerId.contains("32b") || lowerId.contains("mistral") -> 128_000
            else -> 64_000
        }
    }

    private suspend fun executeChatRequest(
        assistantMessageId: Long,
        conversationId: Long,
        isNewConversation: Boolean,
        userPrompt: String,
        attachments: List<AttachmentUi>
    ) {
        try {
            val primaryProviderId = settingsStore.selectedProviderId.first()
            val primaryModelId = settingsStore.selectedModelId.first()
            val chatMode = settingsStore.selectedChatMode.first()
            val autoFallback = settingsStore.autoFallbackEnabled.first()
            val streamingEnabled = settingsStore.streamingEnabled.first()
            val summaryEnabled = settingsStore.summaryEnabled.first()
            val contextMode = settingsStore.contextMode.first()

            if (primaryModelId.isNullOrBlank()) {
                persistError(assistantMessageId, "Model belum dipilih. Ketuk chip di atas untuk memilih.")
                return
            }

            val plan = ContextBudgetPlan.from(contextLimitOf(primaryModelId), contextMode)
            val promptBudget = PromptBudget.fromPlan(plan)
            val projectAttachments = activeProjectId?.let { chatRepository.getProjectAttachments(it) }.orEmpty()
            val combined = attachments + projectAttachments
            val prepared = if (combined.isNotEmpty()) attachmentManager.preparePayloads(combined) else emptyList()
            val failures = prepared.filter { !it.ok }
            val textAttachments = prepared.filter { it.ok && it.payload?.textContent != null }
            val binaryAttachments = prepared.mapNotNull { it.payload }.filter { it.textContent == null }
            if (failures.isNotEmpty()) {
                Log.w(TAG, "Lampiran dilewati: ${failures.joinToString { "${it.ui.name} (${it.error})" }}")
            }

            val fullHistory = chatRepository.getMessages(conversationId).filter { it.id < assistantMessageId }
            val history = fullHistory.takeLast(plan.historyWindow).toMutableList()
            if (textAttachments.isNotEmpty()) {
                val lastUserIndex = history.indexOfLast { it.role == MessageRole.User }
                if (lastUserIndex != -1) {
                    val original = history[lastUserIndex]
                    val appended = textAttachments.joinToString("\n\n") { preparedItem ->
                        "--- File: ${preparedItem.ui.name} ---\n${preparedItem.payload?.textContent}"
                    }
                    history[lastUserIndex] = original.copy(content = original.content + "\n\n" + appended)
                }
            }

            val basePrompt = remotePromptRepository.resolveBasePrompt(chatMode)
            val (styles, traits) = remotePromptRepository.resolvePersonalityMaps()
            val personality = personalityStore.settingsFlow.first()
            val summaryContext = if (summaryEnabled && fullHistory.size >= promptBudget.summaryThreshold) {
                runCatching { chatRepository.getConversationSummary(conversationId)?.text }.getOrNull()
            } else null
            val activeProject = activeProjectId?.let { chatRepository.getProjectById(it) }

            val assembled = contextAssembler.assemble(
                ContextAssembler.Input(
                    basePrompt = basePrompt,
                    layerRules = LAYER_RULES,
                    personalityPrompt = personality.buildPromptSuffix(styles, traits),
                    projectPrompt = activeProject?.systemPrompt,
                    projectId = activeProjectId,
                    userPrompt = userPrompt,
                    history = history,
                    summary = summaryContext,
                    mode = contextMode,
                    plan = plan,
                    promptBudget = promptBudget,
                    knowledgeEnabled = settingsStore.indexEnabled.first()
                )
            )

            val request = ChatRequest(
                modelId = primaryModelId,
                messages = assembled.messages,
                systemPrompt = assembled.systemPrompt,
                attachments = binaryAttachments
            )

            val contextLimit = contextLimitOf(primaryModelId)
            if (contextLimit != null && contextLimit > 0) {
                val estimate = ContextBudgetGuard.estimate(request, contextLimit, contextMode)
                if (!estimate.fits) {
                    persistError(
                        assistantMessageId,
                        "⚠️ Konteks Terlalu Besar\n\n" +
                        "Estimasi: ${estimate.estimatedInputTokens} token\n" +
                        "Batas Model: ${estimate.maxInputTokens} token\n\n" +
                        "Solusi:\n" +
                        "1. Kurangi jumlah lampiran/file.\n" +
                        "2. Sebutkan nama file yang lebih spesifik.\n" +
                        "3. Ubah mode konteks ke 'Hemat' di pengaturan."
                    )
                    return
                }
            } else {
                Log.w(TAG, "Context limit '$primaryModelId' tidak tersedia; memakai anggaran konservatif")
            }

            contextLogWriter.write(assistantMessageId, contextMode, assembled.log)

            val primarySuccess = tryExecuteProvider(
                providerId = primaryProviderId,
                request = request,
                assistantMessageId = assistantMessageId,
                chatMode = chatMode,
                conversationId = conversationId,
                isNewConversation = isNewConversation,
                userPrompt = userPrompt,
                isFallback = false,
                streamingEnabled = streamingEnabled
            )
            if (primarySuccess) return

            if (autoFallback) {
                var attempted = false
                for (config in providerRegistry.allConfigs().filter { it.id != primaryProviderId }) {
                    val fallbackKey = apiKeyStore.load(config.id)
                    if (fallbackKey.isNullOrBlank()) continue
                    val fallbackProvider = providerRegistry.get(config.id) ?: continue
                    val fallbackModelId = fallbackProvider.listModels(fallbackKey).getOrNull()?.firstOrNull()?.id ?: continue
                    attempted = true
                    val fallbackPlan = ContextBudgetPlan.from(contextLimitOf(fallbackModelId), contextMode)
                    val fallbackRequest = request.copy(modelId = fallbackModelId)
                    val success = tryExecuteProvider(
                        providerId = config.id,
                        request = fallbackRequest,
                        assistantMessageId = assistantMessageId,
                        chatMode = chatMode,
                        conversationId = conversationId,
                        isNewConversation = isNewConversation,
                        userPrompt = userPrompt,
                        isFallback = true,
                        streamingEnabled = streamingEnabled
                    )
                    if (success) return
                    if (fallbackPlan.usable < plan.usable) {
                        Log.i(TAG, "Model cadangan punya jendela lebih kecil; anggaran disesuaikan ke ${fallbackPlan.usable}")
                    }
                }
                if (attempted) persistError(assistantMessageId, "Semua provider AI gagal merespons. Periksa koneksi atau API Key Anda.")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e(TAG, "Penyiapan request gagal: ${e.javaClass.simpleName}: ${e.message}", e)
            persistError(assistantMessageId, "Terjadi kesalahan saat menyiapkan request: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun tryExecuteProvider(
        providerId: ProviderId,
        request: ChatRequest,
        assistantMessageId: Long,
        chatMode: ChatMode,
        conversationId: Long,
        isNewConversation: Boolean,
        userPrompt: String,
        isFallback: Boolean,
        streamingEnabled: Boolean
    ): Boolean {
        val displayName = providerRegistry.getConfig(providerId)?.displayName ?: providerId.value
        val apiKey = apiKeyStore.load(providerId)
        if (apiKey.isNullOrBlank() && !CustomProviderConfig.isCustomId(providerId)) {
            if (!isFallback) persistError(assistantMessageId, "API Key untuk $displayName belum diatur.")
            return false
        }
        val provider = providerRegistry.get(providerId)
        if (provider == null) {
            if (!isFallback) persistError(assistantMessageId, "Provider $displayName tidak tersedia.")
            return false
        }
        return if (provider.supportsStreaming && streamingEnabled) {
            when (val result = consumeStreamWithProgress(assistantMessageId, provider.chatStream(apiKey.orEmpty(), request))) {
                is StreamResult.Success -> {
                    val content = applyMarkdownStripIfNeeded(result.content, chatMode)
                    finalizeAssistantMessage(assistantMessageId, content, conversationId, isNewConversation, userPrompt, result.images)
                    true
                }
                is StreamResult.Failed -> {
                    if (result.partialContent.isEmpty()) {
                        executeNonStreaming(provider, apiKey.orEmpty(), request, assistantMessageId, chatMode, conversationId, isNewConversation, userPrompt, isFallback)
                    } else {
                        persistError(assistantMessageId, "Koneksi terputus: ${result.exception.message}", existingContent = result.partialContent)
                        true
                    }
                }
                is StreamResult.NoContent ->
                    executeNonStreaming(provider, apiKey.orEmpty(), request, assistantMessageId, chatMode, conversationId, isNewConversation, userPrompt, isFallback)
            }
        } else {
            executeNonStreaming(provider, apiKey.orEmpty(), request, assistantMessageId, chatMode, conversationId, isNewConversation, userPrompt, isFallback)
        }
    }

    private suspend fun consumeStreamWithProgress(
        assistantMessageId: Long,
        flow: kotlinx.coroutines.flow.Flow<StreamChunk>
    ): StreamResult = coroutineScope {
        var lastSaved = 0
        val progressJob = launch(Dispatchers.IO) {
            while (isActive) {
                delay(900L)
                val snapshot = streamManager.streamingContent.value
                if (snapshot.isNotBlank() && snapshot.length > lastSaved) {
                    lastSaved = snapshot.length
                    runCatching {
                        chatRepository.updateMessage(assistantMessageId, snapshot, MessageStatus.Sending)
                        _messages.update { list -> list.map { if (it.id == assistantMessageId && it.status == MessageStatus.Sending) it.copy(content = snapshot) else it } }
                    }.onFailure { Log.w(TAG, "Gagal menyimpan progres stream", it) }
                }
            }
        }
        try { streamManager.consume(flow) } finally { progressJob.cancelAndJoin() }
    }

    private suspend fun executeNonStreaming(
        provider: AiProvider,
        apiKey: String,
        request: ChatRequest,
        assistantMessageId: Long,
        mode: ChatMode,
        conversationId: Long,
        isNewConversation: Boolean,
        userPrompt: String,
        isFallback: Boolean
    ): Boolean = provider.chat(apiKey, request).fold(
        onSuccess = { response ->
            val content = applyMarkdownStripIfNeeded(response.content, mode)
            finalizeAssistantMessage(assistantMessageId, content, conversationId, isNewConversation, userPrompt, response.images)
            true
        },
        onFailure = { error ->
            val imageFailure = error is com.sync.app.domain.error.AiException.ImageGenerationFailed
            if (!isFallback || imageFailure) persistError(assistantMessageId, error.message ?: "Gagal mendapatkan respons.")
            imageFailure
        }
    )

    private suspend fun finalizeAssistantMessage(
        assistantMessageId: Long,
        finalContent: String,
        conversationId: Long,
        isNewConversation: Boolean,
        userPrompt: String,
        generatedImages: List<com.sync.app.domain.provider.GeneratedImage> = emptyList()
    ) {
        val stored = generatedImages.mapNotNull { attachmentManager.storeGeneratedImage(it) }
        _messages.update { list -> list.map { if (it.id == assistantMessageId) it.copy(content = finalContent, status = MessageStatus.Completed, attachments = it.attachments + stored) else it } }
        val saved = _messages.value.firstOrNull { it.id == assistantMessageId }
        runCatching { chatRepository.updateMessage(assistantMessageId, finalContent, MessageStatus.Completed, saved?.attachments) }
        backgroundScope.launch {
            runCatching {
                titleAndSummaryGenerator.onAssistantMessageCompleted(
                    conversationId = conversationId,
                    isNewConversation = isNewConversation,
                    userPrompt = userPrompt,
                    assistantResponse = finalContent
                )
            }.onFailure { Log.w(TAG, "Gagal membuat judul/ringkasan", it) }
        }
    }

    private fun applyMarkdownStripIfNeeded(content: String, mode: ChatMode): String =
        if (mode.stripMarkdown) MarkdownStripper.strip(content) else content

    private suspend fun persistError(messageId: Long, errorText: String, existingContent: String? = null) {
        val friendly = formatErrorMessage(errorText)
        var finalContent = ""
        _messages.update { list -> list.map {
            if (it.id == messageId) {
                val existing = existingContent ?: it.content
                finalContent = if (existing.isBlank()) friendly else "$existing\n\n[Error: $friendly]"
                it.copy(content = finalContent, status = MessageStatus.Failed)
            } else it
        } }
        if (finalContent.isBlank()) {
            finalContent = if (existingContent.isNullOrBlank()) friendly else "$existingContent\n\n[Error: $friendly]"
        }
        if (messageId != -1L) runCatching { chatRepository.updateMessage(messageId, finalContent, MessageStatus.Failed) }
        _errorFlow.tryEmit(friendly)
    }

    private fun formatErrorMessage(raw: String): String = try {
        if (raw.trim().startsWith("{")) {
            val json = localJson.parseToJsonElement(raw).jsonObject
            val msg = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: raw
            if (msg.contains("Request too large", true) || msg.contains("TPM", true)) {
                val limit = Regex("Limit\\s+(\\d+)").find(msg)?.groupValues?.get(1) ?: "tidak diketahui"
                val requested = Regex("Requested\\s+(\\d+)").find(msg)?.groupValues?.get(1) ?: "tidak diketahui"
                "Batas token terlampaui. Pesan atau file terlalu besar ($requested token), batas $limit token per menit. Kurangi ukuran atau tunggu sebentar."
            } else msg
        } else raw
    } catch (_: Exception) { raw }
}