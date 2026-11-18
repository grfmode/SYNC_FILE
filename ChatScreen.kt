package com.sync.app.ui.screens.chat

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sync.app.domain.provider.CustomProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.model.AttachmentType
import com.sync.app.model.Conversation
import com.sync.app.model.LibraryItemType
import com.sync.app.model.MessageStatus
import com.sync.app.ui.components.CustomProviderDialog
import com.sync.app.ui.components.ModelSelectionSheet
import com.sync.app.ui.screens.chat.components.AttachmentPickerSheet
import com.sync.app.ui.screens.chat.components.ChatCompactHeader
import com.sync.app.ui.screens.chat.components.ChatComposer
import com.sync.app.ui.screens.chat.components.ChatContextSheet
import com.sync.app.ui.screens.chat.components.ChatEmptyState
import com.sync.app.ui.screens.chat.components.ChatMessageList
import com.sync.app.ui.screens.chat.components.ConversationSummarySheet
import com.sync.app.ui.screens.chat.components.DeleteConversationDialog
import com.sync.app.ui.screens.chat.components.HistoryBottomSheet
import com.sync.app.ui.screens.chat.components.HistorySwipeUpHint
import com.sync.app.ui.screens.chat.components.MemoryRememberSheet
import com.sync.app.ui.screens.chat.components.PromptShortcutSheet
import com.sync.app.ui.screens.chat.components.RenameConversationDialog
import com.sync.app.ui.screens.chat.components.SaveToProjectSheet
import com.sync.app.ui.screens.chat.components.ScrollToBottomButton
import com.sync.app.ui.screens.library.SaveToLibrarySheet
import com.sync.app.ui.screens.projects.PromptLibraryViewModel
import com.sync.app.ui.theme.DeepNavy
import kotlinx.coroutines.launch

// Helper data class for SaveToLibrarySheet state
data class LibrarySaveData(
    val fileName: String,
    val fileType: LibraryItemType,
    val suggestedTags: List<String>,
    val onSave: (String, List<String>) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long? = null,
    projectId: Long? = null,
    onOpenSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    promptViewModel: PromptLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streamingContent by viewModel.streamingContent.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isMultimodalSupported by viewModel.isMultimodalSupported.collectAsStateWithLifecycle()
    val selectedProviderName by viewModel.selectedProviderName.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()

    // [Baru] konteks
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    val contextMode by viewModel.contextMode.collectAsStateWithLifecycle()
    val contextSheet by viewModel.contextSheet.collectAsStateWithLifecycle()
    val rememberDraft by viewModel.rememberDraft.collectAsStateWithLifecycle()

    val selectedProviderId: ProviderId by viewModel.selectedProviderId.collectAsStateWithLifecycle()
    val providerConfigs by viewModel.providerConfigs.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
    val modelLoadError by viewModel.modelLoadError.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()

    val customProviders by viewModel.customProviders.collectAsStateWithLifecycle()
    val savedPrompts by promptViewModel.prompts.collectAsStateWithLifecycle()

    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val selectedConversationIds by viewModel.selectedConversationIds.collectAsStateWithLifecycle()
    val errorDialogMsg by viewModel.errorDialog.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    var isHidingHistorySheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showPromptSheet by remember { mutableStateOf(false) }

    var saveLibraryData by remember { mutableStateOf<LibrarySaveData?>(null) }

    var showSaveToProjectSheet by remember { mutableStateOf(false) }
    var conversationToSave by remember { mutableStateOf<Conversation?>(null) }

    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }

    var showCustomProviderDialog by remember { mutableStateOf(false) }
    var editingCustomProvider by remember { mutableStateOf<CustomProviderConfig?>(null) }
    var editingCustomProviderApiKey by remember { mutableStateOf("") }

    var showSummarySheet by remember { mutableStateOf(false) }
    var summaryConversation by remember { mutableStateOf<Conversation?>(null) }
    var summaryText by remember { mutableStateOf<String?>(null) }

    var inputText by remember { mutableStateOf(TextFieldValue("")) }

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversationId, projectId) {
        if (conversationId != null) {
            viewModel.loadConversation(conversationId)
        } else if (projectId != null) {
            viewModel.startNewConversation(projectId)
        }
    }

    val attachments = rememberAttachmentLaunchers(
        onUriPicked = { uri -> viewModel.addAttachment(uri) },
        onError = { scope.launch { snackbarHostState.showSnackbar("Gagal membuka kamera") } }
    )

    val stt = rememberSpeechRecognizer(
        onResult = { spokenText ->
            val current = inputText.text
            val newText = if (current.isBlank()) spokenText else "$current $spokenText"
            inputText = TextFieldValue(newText, selection = TextRange(newText.length))
        },
        onPermissionDenied = {
            scope.launch { snackbarHostState.showSnackbar("Izin mikrofon diperlukan untuk fitur ini") }
        }
    )

    val speak = rememberTextToSpeech()

    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            inputText = TextFieldValue(
                text = editingMessage!!.content,
                selection = TextRange(editingMessage!!.content.length)
            )
        } else {
            inputText = TextFieldValue("")
        }
    }

    val isStreaming = messages.lastOrNull()?.status == MessageStatus.Sending

    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            if (isStreaming) {
                listState.scrollToItem(messages.size - 1, scrollOffset = Int.MAX_VALUE)
            } else {
                listState.animateScrollToItem(messages.size - 1, scrollOffset = Int.MAX_VALUE)
            }
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf { listState.canScrollForward }
    }

    var conversationToRename by remember { mutableStateOf<Conversation?>(null) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val hideHistorySheet: (action: () -> Unit) -> Unit = { action ->
        if (!isHidingHistorySheet) {
            isHidingHistorySheet = true
            scope.launch {
                try { sheetState.hide() } finally {
                    showHistorySheet = false
                    isHidingHistorySheet = false
                }
                action()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ChatCompactHeader(
                modelName = selectedProviderName ?: "Pilih Provider",
                onClick = { viewModel.loadModels(); showModelSheet = true },
                onNewConversation = { viewModel.startNewConversation() },
                onOpenSettings = onOpenSettings,
                contextModeLabel = contextMode.displayName
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (messages.isEmpty()) {
                    ChatEmptyState()
                } else {
                    ChatMessageList(
                        messages = messages,
                        listState = listState,
                        streamingContent = streamingContent,
                        onRetry = { id -> viewModel.retryMessage(id) },
                        onRegenerate = { id -> viewModel.regenerateMessage(id) },
                        onSpeak = { text -> speak(text) },
                        onEditMessage = { msg -> viewModel.startEditing(msg) },
                        onSaveAttachmentToLibrary = { att ->
                            val ext = att.name.substringAfterLast('.', "").lowercase()
                            val tags = mutableListOf<String>()
                            if (att.type == AttachmentType.Image) tags.add("Gambar")
                            else if (ext in listOf("kt", "py", "js", "java", "cpp", "c", "json", "xml")) tags.add("Kode")
                            else tags.add("Dokumen")

                            saveLibraryData = LibrarySaveData(
                                fileName = att.name,
                                fileType = if (att.type == AttachmentType.Image) LibraryItemType.Image else LibraryItemType.Document,
                                suggestedTags = tags,
                                onSave = { newName, finalTags ->
                                    val libType = if (att.type == AttachmentType.Image) LibraryItemType.Image else LibraryItemType.Document
                                    val item = com.sync.app.model.LibraryItem(
                                        id = 0,
                                        uri = att.uri,
                                        name = newName,
                                        mimeType = att.mimeType,
                                        type = libType,
                                        content = null,
                                        createdAt = System.currentTimeMillis(),
                                        tags = finalTags
                                    )
                                    viewModel.saveToLibrary(item)
                                }
                            )
                        },
                        onSaveSnippetToLibrary = { content, lang ->
                            val ext = when (lang.lowercase()) {
                                "kotlin", "kt" -> "kt"
                                "python", "py" -> "py"
                                "javascript", "js" -> "js"
                                "java" -> "java"
                                "cpp", "c++" -> "cpp"
                                "c" -> "c"
                                "json" -> "json"
                                "xml" -> "xml"
                                "html" -> "html"
                                "css" -> "css"
                                "text", "" -> "txt"
                                else -> lang.lowercase()
                            }

                            saveLibraryData = LibrarySaveData(
                                fileName = "Snippet_${System.currentTimeMillis()}.$ext",
                                fileType = LibraryItemType.Snippet,
                                suggestedTags = listOf(if (lang.isBlank()) "Teks" else "Kode"),
                                onSave = { newName, finalTags ->
                                    viewModel.saveSnippetToLibrary(newName, content, finalTags)
                                }
                            )
                        },
                        contextUsage = contextUsage,
                        onContextChipClick = viewModel::openContextSheet,
                        onRemember = viewModel::startRemember
                    )
                }

                ScrollToBottomButton(
                    visible = showScrollToBottom,
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1, scrollOffset = Int.MAX_VALUE)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }

            HistorySwipeUpHint(
                onSwipeUp = {
                    viewModel.clearSelection()
                    showHistorySheet = true
                }
            )

            ChatComposer(
                textValue = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.text.isNotBlank() || pendingAttachments.isNotEmpty()) {
                        if (editingMessage != null) {
                            viewModel.submitEdit(inputText.text)
                        } else {
                            viewModel.sendMessage(inputText.text)
                        }
                        inputText = TextFieldValue("")
                        keyboardController?.hide()
                    }
                },
                onAddAttachmentClick = { showAttachMenu = true },
                onRemoveAttachment = viewModel::removeAttachment,
                onMicClick = stt.toggleListening,
                pendingAttachments = pendingAttachments,
                canAttach = selectedModelId != null,
                isListening = stt.isListening,
                rmsDb = stt.rmsDb,
                isEditing = editingMessage != null,
                onCancelEdit = {
                    viewModel.cancelEditing()
                    inputText = TextFieldValue("")
                },
                modifier = Modifier.fillMaxWidth(),
                contextMode = contextMode,
                onContextModeChange = viewModel::setContextMode
            )
        }
    }

    // === [Baru] Chip konteks ===
    contextSheet?.let { messageId ->
        ChatContextSheet(messageId = messageId, onDismiss = viewModel::closeContextSheet)
    }

    // === [Baru] Lembar "Ingat ini" ===
    rememberDraft?.let { draft ->
        MemoryRememberSheet(
            draft = draft,
            onConfirm = { content, type -> viewModel.confirmRemember(content, type, projectId) },
            onDismiss = viewModel::cancelRemember
        )
    }

    if (errorDialogMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            title = { Text("Pemberitahuan", color = DeepNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Text(
                    text = errorDialogMsg!!,
                    color = DeepNavy
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                    Text("Mengerti", color = DeepNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.White
        )
    }

    if (showAttachMenu) {
        AttachmentPickerSheet(
            isMultimodalSupported = isMultimodalSupported,
            onDismissRequest = { showAttachMenu = false },
            onCameraSelected = { showAttachMenu = false; attachments.launchCamera() },
            onAlbumSelected = { showAttachMenu = false; attachments.launchGallery() },
            onDocumentSelected = { showAttachMenu = false; attachments.launchDocument() },
            onPromptSelected = { showAttachMenu = false; showPromptSheet = true },
            onLibrarySelected = { showAttachMenu = false; onOpenLibrary() }
        )
    }

    if (showPromptSheet) {
        PromptShortcutSheet(
            prompts = savedPrompts,
            onDismissRequest = { showPromptSheet = false },
            onPromptSelected = { prompt ->
                showPromptSheet = false
                val current = inputText.text
                val newText = if (current.isBlank()) prompt.content else "$current\n\n${prompt.content}"
                inputText = TextFieldValue(newText, selection = TextRange(newText.length))
            }
        )
    }

    if (saveLibraryData != null) {
        val data = saveLibraryData!!
        SaveToLibrarySheet(
            fileName = data.fileName,
            fileType = data.fileType,
            suggestedTags = data.suggestedTags,
            onDismissRequest = { saveLibraryData = null },
            onSave = { newName, finalTags ->
                data.onSave(newName, finalTags)
                saveLibraryData = null
                scope.launch { snackbarHostState.showSnackbar("Tersimpan di Library") }
            }
        )
    }

    if (showHistorySheet) {
        HistoryBottomSheet(
            sheetState = sheetState,
            conversations = conversations,
            onDismissRequest = {
                showHistorySheet = false
                viewModel.clearSearch()
                viewModel.clearSelection()
            },
            onSelectConversation = { id -> hideHistorySheet { viewModel.loadConversation(id) } },
            onOpenProjects = { hideHistorySheet { onOpenProjects() } },
            onOpenPrompts = { hideHistorySheet { onOpenPrompts() } },
            onOpenLibrary = { hideHistorySheet { onOpenLibrary() } },
            onDelete = { conv -> conversationToDelete = conv },
            onRename = { conv -> conversationToRename = conv; renameText = conv.title },
            onShare = { conv ->
                viewModel.getShareText(conv.id) { text ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Percakapan: ${conv.title}\n\n$text")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            },
            onSaveToProject = { conv ->
                conversationToSave = conv
                showSaveToProjectSheet = true
            },
            onExportMarkdown = { conv ->
                viewModel.getExportMarkdown(conv.id) { text ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, "Ekspor Markdown - ${conv.title}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Markdown"))
                }
            },
            onExportJson = { conv ->
                viewModel.getExportJson(conv.id) { text ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, "Ekspor JSON - ${conv.title}")
                        type = "application/json"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan JSON"))
                }
            },
            onViewSummary = { conv ->
                summaryConversation = conv
                viewModel.getConversationSummary(conv.id) { text ->
                    summaryText = text
                    showSummarySheet = true
                }
            },
            searchResults = searchResults,
            isSearching = isSearching,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSelectSearchResult = { result ->
                hideHistorySheet {
                    viewModel.loadConversation(result.conversationId)
                    viewModel.clearSearch()
                    viewModel.clearSelection()
                }
            },
            selectedIds = selectedConversationIds,
            onToggleSelection = viewModel::toggleConversationSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = { showBulkDeleteConfirm = true },
            onMoveSelectedToProject = { showBulkMoveSheet = true }
        )
    }

    if (showSaveToProjectSheet && conversationToSave != null) {
        SaveToProjectSheet(
            projects = projects,
            onDismissRequest = { showSaveToProjectSheet = false },
            onProjectSelected = { project ->
                conversationToSave?.let { viewModel.saveConversationToProject(it.id, project.id) }
                showSaveToProjectSheet = false
                scope.launch { snackbarHostState.showSnackbar("Disimpan ke ${project.name}") }
            }
        )
    }

    if (showBulkDeleteConfirm) {
        val count = selectedConversationIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Hapus $count percakapan?") },
            text = { Text("Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedConversations()
                    showBulkDeleteConfirm = false
                    scope.launch { snackbarHostState.showSnackbar("$count percakapan dihapus") }
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Batal", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showBulkMoveSheet) {
        val count = selectedConversationIds.size
        SaveToProjectSheet(
            projects = projects,
            onDismissRequest = { showBulkMoveSheet = false },
            onProjectSelected = { project ->
                viewModel.moveSelectedConversationsToProject(project.id)
                showBulkMoveSheet = false
                scope.launch { snackbarHostState.showSnackbar("$count percakapan dipindah ke ${project.name}") }
            }
        )
    }

    if (showModelSheet) {
        ModelSelectionSheet(
            selectedProvider = selectedProviderId,
            providerConfigs = providerConfigs,
            customProviders = customProviders,
            models = availableModels,
            selectedModelId = selectedModelId,
            isLoading = isLoadingModels,
            error = modelLoadError,
            hasApiKey = hasApiKey,
            onProviderSelected = viewModel::changeProvider,
            onModelSelected = { model -> viewModel.selectModel(model); showModelSheet = false },
            onSaveApiKey = viewModel::saveApiKey,
            onAddCustomProvider = {
                editingCustomProvider = null
                editingCustomProviderApiKey = ""
                showCustomProviderDialog = true
            },
            onEditCustomProvider = { config ->
                editingCustomProvider = config
                viewModel.getApiKey(config.id) { key ->
                    editingCustomProviderApiKey = key
                    showCustomProviderDialog = true
                }
            },
            onDeleteCustomProvider = { config ->
                viewModel.deleteCustomProvider(config.id)
                scope.launch { snackbarHostState.showSnackbar("Provider dihapus") }
            },
            onDismissRequest = { showModelSheet = false }
        )
    }

    if (showCustomProviderDialog) {
        val editing = editingCustomProvider
        CustomProviderDialog(
            initialDisplayName = editing?.displayName ?: "",
            initialBaseUrl = editing?.baseUrl ?: "",
            initialApiKey = editingCustomProviderApiKey,
            initialModels = editing?.models ?: emptyList(),
            isEditMode = editing != null,
            onDismiss = {
                showCustomProviderDialog = false
                editingCustomProvider = null
                editingCustomProviderApiKey = ""
            },
            onSave = { name, url, apiKey, models ->
                if (editing != null) {
                    viewModel.updateCustomProvider(editing.id, name, url, apiKey, models)
                    scope.launch { snackbarHostState.showSnackbar("Provider diupdate") }
                } else {
                    viewModel.addCustomProvider(name, url, apiKey, models)
                    scope.launch { snackbarHostState.showSnackbar("Provider ditambahkan") }
                }
                showCustomProviderDialog = false
                editingCustomProvider = null
                editingCustomProviderApiKey = ""
            }
        )
    }

    if (showSummarySheet && summaryConversation != null) {
        ConversationSummarySheet(
            conversationTitle = summaryConversation!!.title,
            summaryText = summaryText,
            onDismissRequest = {
                showSummarySheet = false
                summaryConversation = null
                summaryText = null
            },
            onClearSummary = {
                summaryConversation?.let { viewModel.clearConversationSummary(it.id) }
            }
        )
    }

    if (conversationToRename != null) {
        RenameConversationDialog(
            conversation = conversationToRename!!,
            renameText = renameText,
            onRenameTextChange = { renameText = it },
            onConfirm = { newName -> viewModel.renameConversation(conversationToRename!!.id, newName) },
            onDismiss = { conversationToRename = null }
        )
    }

    if (conversationToDelete != null) {
        DeleteConversationDialog(
            conversation = conversationToDelete!!,
            onConfirm = { viewModel.deleteConversation(conversationToDelete!!.id) },
            onDismiss = { conversationToDelete = null }
        )
    }
}