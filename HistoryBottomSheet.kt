package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.sync.app.domain.chat.MessageSearchResult
import com.sync.app.model.Conversation
import com.sync.app.ui.theme.DeepNavy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    sheetState: SheetState,
    conversations: List<Conversation>,
    onDismissRequest: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenPrompts: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onDelete: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onShare: (Conversation) -> Unit,
    onSaveToProject: (Conversation) -> Unit,
    onExportMarkdown: (Conversation) -> Unit,
    onExportJson: (Conversation) -> Unit,
    onViewSummary: (Conversation) -> Unit,
    searchResults: List<MessageSearchResult> = emptyList(),
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onSelectSearchResult: (MessageSearchResult) -> Unit = {},
    selectedIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onMoveSelectedToProject: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val isSelectionMode = selectedIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            if (isSelectionMode) {
                SelectionHeader(
                    count = selectedIds.size,
                    onDelete = onDeleteSelected,
                    onMoveToProject = onMoveSelectedToProject,
                    onCancel = onClearSelection
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(DeepNavy.copy(alpha = 0.06f))
                            .clickable(onClick = onOpenProjects),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Folder,
                            contentDescription = "Projects",
                            modifier = Modifier.size(28.dp),
                            tint = DeepNavy
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(DeepNavy.copy(alpha = 0.06f))
                            .clickable(onClick = onOpenPrompts),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Braces,
                            contentDescription = "Prompts",
                            modifier = Modifier.size(28.dp),
                            tint = DeepNavy
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(DeepNavy.copy(alpha = 0.06f))
                            .clickable(onClick = onOpenLibrary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Library,
                            contentDescription = "Library",
                            modifier = Modifier.size(28.dp),
                            tint = DeepNavy
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(DeepNavy.copy(alpha = 0.06f))
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            onSearchQueryChange(it)
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = DeepNavy,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(DeepNavy),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search messages...",
                                    color = DeepNavy.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Icon(
                        imageVector = Lucide.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(22.dp),
                        tint = DeepNavy.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            val dateFormat = remember {
                SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            }

            val filteredConversations = remember(conversations, searchQuery) {
                if (searchQuery.isBlank()) conversations
                else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }

            when {
                isSelectionMode -> {
                    if (conversations.isEmpty()) {
                        EmptyStateText("No conversations")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(conversations, key = { "sel-${it.id}" }) { conv ->
                                ConversationRow(
                                    conversation = conv,
                                    formattedDate = dateFormat.format(Date(conv.updatedAt)),
                                    isSelectionMode = true,
                                    isSelected = selectedIds.contains(conv.id),
                                    onClick = { onToggleSelection(conv.id) },
                                    onLongClick = { onToggleSelection(conv.id) },
                                    onDelete = { onDelete(conv) },
                                    onRename = { onRename(conv) },
                                    onShare = { onShare(conv) },
                                    onSaveToProject = { onSaveToProject(conv) },
                                    onExportMarkdown = { onExportMarkdown(conv) },
                                    onExportJson = { onExportJson(conv) },
                                    onViewSummary = { onViewSummary(conv) }
                                )
                            }
                        }
                    }
                }

                searchQuery.isBlank() -> {
                    if (filteredConversations.isEmpty()) {
                        EmptyStateText("No conversations yet")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(filteredConversations, key = { "conv-${it.id}" }) { conv ->
                                ConversationRow(
                                    conversation = conv,
                                    formattedDate = dateFormat.format(Date(conv.updatedAt)),
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onSelectConversation(conv.id) },
                                    onLongClick = { onToggleSelection(conv.id) },
                                    onDelete = { onDelete(conv) },
                                    onRename = { onRename(conv) },
                                    onShare = { onShare(conv) },
                                    onSaveToProject = { onSaveToProject(conv) },
                                    onExportMarkdown = { onExportMarkdown(conv) },
                                    onExportJson = { onExportJson(conv) },
                                    onViewSummary = { onViewSummary(conv) }
                                )
                            }
                        }
                    }
                }

                isSearching && searchResults.isEmpty() && filteredConversations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = DeepNavy
                        )
                    }
                }

                searchResults.isEmpty() && filteredConversations.isEmpty() -> {
                    EmptyStateText("Not found")
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        if (searchResults.isNotEmpty()) {
                            item(key = "header-messages") {
                                SectionHeader("Messages")
                            }
                            items(
                                searchResults,
                                key = { "msg-${it.messageId}" }
                            ) { result ->
                                MessageSearchResultRow(
                                    result = result,
                                    formattedDate = dateFormat.format(Date(result.createdAt)),
                                    onClick = { onSelectSearchResult(result) }
                                )
                            }
                        }

                        if (filteredConversations.isNotEmpty()) {
                            item(key = "header-conversations") {
                                SectionHeader("Conversations")
                            }
                            items(
                                filteredConversations,
                                key = { "conv-${it.id}" }
                            ) { conv ->
                                ConversationRow(
                                    conversation = conv,
                                    formattedDate = dateFormat.format(Date(conv.updatedAt)),
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onSelectConversation(conv.id) },
                                    onLongClick = { onToggleSelection(conv.id) },
                                    onDelete = { onDelete(conv) },
                                    onRename = { onRename(conv) },
                                    onShare = { onShare(conv) },
                                    onSaveToProject = { onSaveToProject(conv) },
                                    onExportMarkdown = { onExportMarkdown(conv) },
                                    onExportJson = { onExportJson(conv) },
                                    onViewSummary = { onViewSummary(conv) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onDelete: () -> Unit,
    onMoveToProject: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count dipilih",
            style = MaterialTheme.typography.titleMedium,
            color = DeepNavy,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Lucide.Trash2,
                contentDescription = "Hapus terpilih",
                tint = MaterialTheme.colorScheme.error
            )
        }
        IconButton(onClick = onMoveToProject) {
            Icon(
                imageVector = Lucide.FolderPlus,
                contentDescription = "Pindah ke Project",
                tint = DeepNavy
            )
        }
        TextButton(onClick = onCancel) {
            Text("Batal", color = DeepNavy, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyStateText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = DeepNavy.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = DeepNavy.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun MessageSearchResultRow(
    result: MessageSearchResult,
    formattedDate: String,
    onClick: () -> Unit
) {
    val isProjectChat = result.projectId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.conversationTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isProjectChat) FontWeight.Bold else null,
                color = if (isProjectChat) DeepNavy else DeepNavy.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isProjectChat) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeepNavy.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PROJECT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepNavy,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = result.messageContent
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
                .let { if (result.messageContent.length > 160) "$it…" else it },
            style = MaterialTheme.typography.bodyMedium,
            color = DeepNavy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodySmall,
            color = DeepNavy.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    formattedDate: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onSaveToProject: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportJson: () -> Unit,
    onViewSummary: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) DeepNavy.copy(alpha = 0.08f) else Color.Transparent
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) DeepNavy else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) DeepNavy else DeepNavy.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "Terpilih",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DeepNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepNavy.copy(alpha = 0.5f)
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = { showMenu = true }) {
                    Text(
                        text = "\u22EE",
                        fontSize = 20.sp,
                        color = DeepNavy.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp
        ) {
            DropdownMenuItem(
                text = { Text("Lihat Memori AI", color = DeepNavy, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Lucide.Sparkles, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onViewSummary()
                }
            )
            DropdownMenuItem(
                text = { Text("Ubah Nama", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Bagikan", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Share2, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Simpan ke Project", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.FolderPlus, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onSaveToProject()
                }
            )
            DropdownMenuItem(
                text = { Text("Ekspor Markdown", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.FileText, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onExportMarkdown()
                }
            )
            DropdownMenuItem(
                text = { Text("Ekspor JSON", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Braces, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    showMenu = false
                    onExportJson()
                }
            )
            DropdownMenuItem(
                text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}