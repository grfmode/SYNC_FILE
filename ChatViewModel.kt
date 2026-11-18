package com.sync.app.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.data.customprovider.CustomProviderStore
import com.sync.app.data.local.ContextLogDao
import com.sync.app.domain.apikey.ApiKeyStore
import com.sync.app.domain.chat.AttachmentManager
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.domain.chat.MessageSearchResult
import com.sync.app.domain.chat.StreamManager
import com.sync.app.domain.chat.usecase.ChatSessionUseCase
import com.sync.app.domain.chat.usecase.ConversationExportUseCase
import com.sync.app.domain.chat.usecase.ModelCatalogUseCase
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.library.LibraryRepository
import com.sync.app.domain.memory.MemoryCandidate
import com.sync.app.domain.memory.MemoryRepository
import com.sync.app.domain.memory.MemoryScope
import com.sync.app.domain.memory.MemorySource
import com.sync.app.domain.memory.MemoryType
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.CustomProviderConfig
import com.sync.app.domain.provider.ProviderCapability
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderRegistry
import com.sync.app.domain.settings.ChatMode
import com.sync.app.domain.settings.SettingsStore
import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.Conversation
import com.sync.app.model.LibraryItem
import com.sync.app.model.LibraryItemType
import com.sync.app.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ringkasan konteks satu jawaban; angka dari `context_log`. */
data class ContextUsage(
    val memoryCount: Int,
    val sourceCount: Int,
    val tokens: Int,
    val truncated: Boolean
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatSessionUseCase: ChatSessionUseCase,
    private val conversationExportUseCase: ConversationExportUseCase,
    private val modelCatalogUseCase: ModelCatalogUseCase,
    private val attachmentManager: AttachmentManager,
    private val settingsStore: SettingsStore,
    private val providerRegistry: ProviderRegistry,
    private val chatRepository: ChatRepository,
    private val streamManager: StreamManager,
    private val customProviderStore: CustomProviderStore,
    private val apiKeyStore: ApiKeyStore,
    private val libraryRepository: LibraryRepository,
    private val memoryRepository: MemoryRepository,
    private val contextLogDao: ContextLogDao
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = chatSessionUseCase.messages
    val streamingContent: StateFlow<String> = streamManager.streamingContent

    private val _errorDialog = MutableStateFlow<String?>(null)
    val errorDialog: StateFlow<String?> = _errorDialog.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    val providerConfigs: StateFlow<List<ProviderConfig>> = modelCatalogUseCase.providerConfigs
    val hasApiKey: StateFlow<Boolean> = modelCatalogUseCase.hasApiKey
    val availableModels: StateFlow<List<AiModel>> = modelCatalogUseCase.availableModels
    val isLoadingModels: StateFlow<Boolean> = modelCatalogUseCase.isLoadingModels
    val modelLoadError: StateFlow<String?> = modelCatalogUseCase.modelLoadError

    val customProviders: StateFlow<List<CustomProviderConfig>> = customProviderStore.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingAttachments = MutableStateFlow<List<AttachmentUi>>(emptyList())
    val pendingAttachments: StateFlow<List<AttachmentUi>> = _pendingAttachments.asStateFlow()

    val selectedProviderId: StateFlow<ProviderId> = settingsStore.selectedProviderId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderId.Groq)

    val selectedProviderName: StateFlow<String?> = settingsStore.selectedProviderId
        .mapLatest { id -> providerRegistry.getConfig(id)?.displayName ?: id.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedModelId: StateFlow<String?> = settingsStore.selectedModelId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedChatMode: StateFlow<ChatMode> = settingsStore.selectedChatMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatMode.Normal)

    val contextMode: StateFlow<ContextMode> = settingsStore.contextMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextMode.AUTOMATIC)

    val conversations: StateFlow<List<Conversation>> = chatRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<Project>> = chatRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Konteks per pesan asisten, dimuat saat pesan berubah. */
    private val _contextUsage = MutableStateFlow<Map<Long, ContextUsage>>(emptyMap())
    val contextUsage: StateFlow<Map<Long, ContextUsage>> = _contextUsage.asStateFlow()

    private val _contextSheet = MutableStateFlow<Long?>(null)
    val contextSheet: StateFlow<Long?> = _contextSheet.asStateFlow()

    private val _rememberDraft = MutableStateFlow<String?>(null)
    val rememberDraft: StateFlow<String?> = _rememberDraft.asStateFlow()

    val isMultimodalSupported: StateFlow<Boolean> = combine(
        settingsStore.selectedProviderId,
        settingsStore.selectedModelId,
        availableModels
    ) { providerId, modelId, models ->
        if (modelId.isNullOrBlank()) return@combine false
        val visionId = listOf("vision", "multimodal", "gpt-4o", "claude-3", "gemini-1.5", "gemini-2.0")
            .any { modelId.contains(it, ignoreCase = true) }
        val modelCap = models.find { it.id == modelId }?.capabilities?.contains(ProviderCapability.Multimodal) == true
        val providerCap = providerRegistry.get(providerId)?.capabilities?.contains(ProviderCapability.Multimodal) == true
        visionId || modelCap || providerCap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MessageSearchResult>>(emptyList())
    val searchResults: StateFlow<List<MessageSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    private val _selectedConversationIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedConversationIds: StateFlow<Set<Long>> = _selectedConversationIds.asStateFlow()

    init {
        viewModelScope.launch { chatSessionUseCase.initialize() }
        viewModelScope.launch { modelCatalogUseCase.initialize() }
        viewModelScope.launch {
            chatSessionUseCase.errorFlow.collect { _errorDialog.value = it }
        }
        viewModelScope.launch {
            messages.collect { list ->
                val completed = list.filter { it.role == com.sync.app.model.MessageRole.Assistant && it.id > 0 }
                val usage = mutableMapOf<Long, ContextUsage>()
                completed.forEach { message ->
                    val entries = contextLogDao.getForMessage(message.id)
                    if (entries.isEmpty()) return@forEach
                    usage[message.id] = ContextUsage(
                        memoryCount = entries.count { it.refKind == "MEMORY" },
                        sourceCount = entries.count { it.refKind == "KNOWLEDGE" },
                        tokens = entries.sumOf { it.tokens },
                        truncated = entries.any { it.truncated }
                    )
                }
                _contextUsage.value = usage
            }
        }
    }

    fun dismissErrorDialog() { _errorDialog.value = null }

    fun openContextSheet(messageId: Long) { _contextSheet.value = messageId }
    fun closeContextSheet() { _contextSheet.value = null }

    fun setContextMode(mode: ContextMode) {
        viewModelScope.launch { settingsStore.saveContextMode(mode) }
    }

    // --- ingat ini

    fun startRemember(message: ChatMessage) {
        _rememberDraft.value = message.content.take(400)
    }

    fun cancelRemember() { _rememberDraft.value = null }

    fun confirmRemember(content: String, type: MemoryType, projectId: Long?) {
        val scope = if (projectId != null) MemoryScope.PROJECT else MemoryScope.GLOBAL
        viewModelScope.launch {
            val result = memoryRepository.remember(
                MemoryCandidate(
                    content = content,
                    type = type,
                    scope = scope,
                    projectId = projectId,
                    source = MemorySource.MANUAL,
                    confidence = 1f,
                    provenance = "manual"
                )
            )
            _rememberDraft.value = null
            _errorDialog.value = when (result) {
                is com.sync.app.domain.memory.MemoryWriteResult.Duplicate -> "Sudah tersimpan sebagai memori."
                is com.sync.app.domain.memory.MemoryWriteResult.Rejected -> "Memori tidak disimpan: ${result.reason}"
                else -> null
            }
        }
    }

    // --- lampiran

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            attachmentManager.loadAttachment(uri).onSuccess { attachment ->
                if (_pendingAttachments.value.none { it.uri == attachment.uri }) {
                    _pendingAttachments.update { it + attachment }
                }
            }.onFailure {
                _errorDialog.value = it.message ?: "Gagal memuat lampiran"
            }
        }
    }

    fun removeAttachment(uriString: String) {
        _pendingAttachments.update { list -> list.filterNot { it.uri == uriString } }
    }

    fun saveToLibrary(item: LibraryItem) {
        viewModelScope.launch {
            runCatching { libraryRepository.saveLibraryItem(item) }
                .onFailure { _errorDialog.value = "Gagal menyimpan ke Library: ${it.message}" }
        }
    }

    fun saveSnippetToLibrary(name: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            runCatching {
                libraryRepository.saveLibraryItem(
                    LibraryItem(
                        id = 0,
                        uri = "",
                        name = name.ifBlank { "Snippet_${System.currentTimeMillis()}" },
                        mimeType = "text/plain",
                        type = LibraryItemType.Snippet,
                        content = content,
                        createdAt = System.currentTimeMillis(),
                        tags = tags
                    )
                )
            }.onFailure { _errorDialog.value = "Gagal menyimpan snippet: ${it.message}" }
        }
    }

    fun sendMessage(text: String) {
        val current = _pendingAttachments.value
        val trimmed = text.trim()
        if (trimmed.isEmpty() && current.isEmpty()) return
        _pendingAttachments.value = emptyList()
        chatSessionUseCase.sendMessage(trimmed, current)
    }

    fun startEditing(message: ChatMessage) { _editingMessage.value = message }
    fun cancelEditing() { _editingMessage.value = null }

    fun submitEdit(newContent: String) {
        val message = _editingMessage.value ?: return
        chatSessionUseCase.editMessage(message.id, newContent)
        _editingMessage.value = null
    }

    fun retryMessage(failedAssistantMessageId: Long) = chatSessionUseCase.retryMessage(failedAssistantMessageId)
    fun regenerateMessage(assistantMessageId: Long) = chatSessionUseCase.regenerateMessage(assistantMessageId)

    fun loadConversation(id: Long) {
        viewModelScope.launch {
            _pendingAttachments.value = emptyList()
            _editingMessage.value = null
            chatSessionUseCase.loadConversation(id)
        }
    }

    fun startNewConversation(projectId: Long? = null) {
        _pendingAttachments.value = emptyList()
        _editingMessage.value = null
        chatSessionUseCase.startNewConversation(projectId)
    }

    fun deleteConversation(id: Long) { viewModelScope.launch { chatSessionUseCase.deleteConversation(id) } }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch { chatSessionUseCase.renameConversation(id, newTitle) }
    }

    fun getShareText(conversationId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(conversationExportUseCase.getShareText(conversationId)) }
    }

    fun getExportMarkdown(conversationId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(conversationExportUseCase.getExportMarkdown(conversationId)) }
    }

    fun getExportJson(conversationId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(conversationExportUseCase.getExportJson(conversationId)) }
    }

    fun saveConversationToProject(conversationId: Long, projectId: Long?) {
        viewModelScope.launch { chatRepository.updateConversationProject(conversationId, projectId) }
    }

    fun changeMode(mode: ChatMode) { viewModelScope.launch { settingsStore.saveChatMode(mode) } }
    fun loadModels() { modelCatalogUseCase.loadModels(viewModelScope) }
    fun selectModel(model: AiModel) { viewModelScope.launch { modelCatalogUseCase.selectModel(model) } }
    fun changeProvider(providerId: ProviderId) { viewModelScope.launch { modelCatalogUseCase.changeProvider(providerId, viewModelScope) } }
    fun saveApiKey(apiKey: String) { viewModelScope.launch { modelCatalogUseCase.saveApiKey(apiKey, viewModelScope) } }

    fun addCustomProvider(displayName: String, baseUrl: String, apiKey: String, models: List<String>) {
        viewModelScope.launch {
            runCatching {
                val saved = customProviderStore.add(displayName, baseUrl, models) ?: return@launch
                if (apiKey.isNotBlank()) apiKeyStore.save(ProviderId(saved.id), apiKey.trim())
                providerRegistry.refresh()
                modelCatalogUseCase.refreshProviderConfigs()
                modelCatalogUseCase.changeProvider(ProviderId(saved.id), viewModelScope)
            }.onFailure { _errorDialog.value = "Gagal menambah provider: ${it.message}" }
        }
    }

    fun updateCustomProvider(id: String, displayName: String, baseUrl: String, apiKey: String, models: List<String>) {
        viewModelScope.launch {
            runCatching {
                if (!customProviderStore.update(id, displayName, baseUrl, models)) return@launch
                if (apiKey.isNotBlank()) apiKeyStore.save(ProviderId(id), apiKey.trim())
                providerRegistry.refresh()
                modelCatalogUseCase.refreshProviderConfigs()
                if (settingsStore.selectedProviderId.first().value == id) modelCatalogUseCase.loadModels(viewModelScope)
            }.onFailure { _errorDialog.value = "Gagal memperbarui provider: ${it.message}" }
        }
    }

    fun deleteCustomProvider(id: String) {
        viewModelScope.launch {
            runCatching {
                customProviderStore.delete(id)
                apiKeyStore.delete(ProviderId(id))
                providerRegistry.refresh()
                modelCatalogUseCase.refreshProviderConfigs()
                if (settingsStore.selectedProviderId.first().value == id) {
                    modelCatalogUseCase.changeProvider(ProviderId.Groq, viewModelScope)
                }
            }.onFailure { _errorDialog.value = "Gagal menghapus provider: ${it.message}" }
        }
    }

    fun getApiKey(providerId: String, onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(runCatching { apiKeyStore.load(ProviderId(providerId)) }.getOrNull().orEmpty()) }
    }

    fun getConversationSummary(conversationId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(runCatching { chatRepository.getConversationSummary(conversationId)?.text }.getOrNull()) }
    }

    fun clearConversationSummary(conversationId: Long) {
        viewModelScope.launch { runCatching { chatRepository.updateConversationSummary(conversationId, null, null) } }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                delay(250)
                _searchResults.value = chatRepository.searchMessages(trimmed)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun toggleConversationSelection(id: Long) {
        _selectedConversationIds.update { if (it.contains(id)) it - id else it + id }
    }

    fun clearSelection() { _selectedConversationIds.value = emptySet() }

    fun deleteSelectedConversations() {
        val ids = _selectedConversationIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { chatRepository.deleteConversations(ids) }
            _selectedConversationIds.value = emptySet()
        }
    }

    fun moveSelectedConversationsToProject(projectId: Long?) {
        val ids = _selectedConversationIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { chatRepository.moveConversationsToProject(ids, projectId) }
            _selectedConversationIds.value = emptySet()
        }
    }
}