package com.sync.app.ui.screens.chat.components

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ThumbsDown
import com.composables.icons.lucide.ThumbsUp
import com.composables.icons.lucide.Volume2
import com.sync.app.model.AttachmentType
import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import com.sync.app.ui.components.MarkdownRenderer
import com.sync.app.ui.screens.chat.ContextUsage
import com.sync.app.ui.theme.DeepNavy
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    streamingContent: String = "",
    onRetry: (Long) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSpeak: (String) -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onSaveAttachmentToLibrary: (AttachmentUi) -> Unit,
    onSaveSnippetToLibrary: (String, String) -> Unit,
    contextUsage: Map<Long, ContextUsage> = emptyMap(),
    onContextChipClick: (Long) -> Unit = {},
    onRemember: (ChatMessage) -> Unit = {}
) {
    val lastMessage = messages.lastOrNull()
    val lastStatus = lastMessage?.status
    val isStreaming = lastMessage?.status == MessageStatus.Sending

    LaunchedEffect(messages.size, lastStatus) {
        if (messages.isNotEmpty()) {
            if (isStreaming) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                MessageRole.User -> UserBubble(
                    message = message,
                    onEdit = { onEditMessage(message) },
                    onSaveAttachment = onSaveAttachmentToLibrary,
                    onRemember = { onRemember(message) }
                )
                MessageRole.Assistant -> {
                    val isActiveStreaming =
                        message.status == MessageStatus.Sending &&
                        message.id == lastMessage?.id

                    val canRegenerate =
                        message.status == MessageStatus.Completed &&
                        message.id == lastMessage?.id

                    AssistantMessage(
                        message = message,
                        streamingContent = if (isActiveStreaming) streamingContent else null,
                        canRegenerate = canRegenerate,
                        onRetry = onRetry,
                        onRegenerate = onRegenerate,
                        onSpeak = onSpeak,
                        onSaveSnippet = onSaveSnippetToLibrary,
                        onRemember = { onRemember(message) }
                    )

                    // [Baru] Chip konteks hanya untuk jawaban yang punya catatan.
                    contextUsage[message.id]?.let { usage ->
                        ContextChip(
                            memoryCount = usage.memoryCount,
                            sourceCount = usage.sourceCount,
                            tokens = usage.tokens,
                            truncated = usage.truncated,
                            onClick = { onContextChipClick(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    message: ChatMessage,
    onEdit: () -> Unit,
    onSaveAttachment: (AttachmentUi) -> Unit,
    onRemember: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                ),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                modifier = Modifier.padding(start = 64.dp),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 5.dp,
                    bottomStart = 18.dp
                ),
                color = DeepNavy
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (message.attachments.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 8.dp else 0.dp)
                        ) {
                            message.attachments.forEach { attachment ->
                                AttachmentBubble(
                                    attachment = attachment,
                                    onSaveToLibrary = { onSaveAttachment(attachment) }
                                )
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        ) {
            DropdownMenuItem(
                text = { Text("Salin", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Copy, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Ingat ini", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Brain, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    onRemember()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Edit pesan", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    onEdit()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Bagikan prompt", color = DeepNavy) },
                leadingIcon = { Icon(Lucide.Share2, contentDescription = null, tint = DeepNavy) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, message.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    showMenu = false
                }
            )
        }
    }
}

@Composable
private fun AttachmentBubble(
    attachment: AttachmentUi,
    onSaveToLibrary: () -> Unit
) {
    if (attachment.type == AttachmentType.Image) {
        Box {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            IconButton(
                onClick = onSaveToLibrary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Lucide.Bookmark,
                    contentDescription = "Simpan ke Library",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Lucide.FileText,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Lucide.Bookmark,
                contentDescription = "Simpan ke Library",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onSaveToLibrary() }
            )
        }
    }
}

/** Saves a generated image to the public Downloads/Sync folder. */
private suspend fun saveImageToDownloads(context: android.content.Context, attachment: AttachmentUi): Result<String> =
    withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(attachment.uri)
            val input = when (source.scheme) {
                "file" -> File(source.path ?: error("Lokasi file gambar tidak valid")).inputStream()
                "content" -> context.contentResolver.openInputStream(source)
                    ?: error("File gambar tidak dapat dibaca")
                "http", "https" -> URL(attachment.uri).openStream()
                else -> error("Format sumber gambar tidak didukung")
            }
            val extension = when (attachment.mimeType.lowercase()) {
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "png"
            }
            val fileName = "Sync_${System.currentTimeMillis()}.$extension"
            input.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, attachment.mimeType.ifBlank { "image/png" })
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sync")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Gagal membuat file di folder Download")
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { output -> stream.copyTo(output) }
                            ?: error("Gagal menulis file gambar")
                        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                        context.contentResolver.update(uri, done, null, null)
                    } catch (error: Throwable) {
                        context.contentResolver.delete(uri, null, null)
                        throw error
                    }
                } else {
                    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: error("Folder penyimpanan tidak tersedia")
                    val folder = File(directory, "Sync").apply { mkdirs() }
                    File(folder, fileName).outputStream().use { output -> stream.copyTo(output) }
                }
            }
            Result.success("Download/Sync/$fileName")
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    streamingContent: String?,
    canRegenerate: Boolean,
    onRetry: (Long) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSpeak: (String) -> Unit,
    onSaveSnippet: (String, String) -> Unit,
    onRemember: () -> Unit
) {
    val context = LocalContext.current
    val downloadScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        val displayContent = streamingContent ?: message.content

        val textColor = if (message.status == MessageStatus.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        when {
            message.status == MessageStatus.Sending && displayContent.isEmpty() -> {
                TypingIndicator()
            }
            message.status == MessageStatus.Sending -> {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            else -> {
                if (message.attachments.any { it.type == AttachmentType.Image }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = if (displayContent.isNotBlank()) 8.dp else 0.dp)) {
                        message.attachments.filter { it.type == AttachmentType.Image }.forEach { attachment ->
                            Box {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = attachment.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                )
                                IconButton(
                                    onClick = {
                                        downloadScope.launch {
                                            val result = saveImageToDownloads(context, attachment)
                                            Toast.makeText(
                                                context,
                                                result.fold({ "Gambar disimpan: $it" }, { "Gagal mengunduh gambar: ${it.message ?: "Kesalahan tidak diketahui"}" }),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(40.dp)
                                        .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Lucide.Download,
                                        contentDescription = "Unduh gambar",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                if (displayContent.isNotBlank()) MarkdownRenderer(
                    content = displayContent,
                    textColor = textColor,
                    onSaveSnippet = onSaveSnippet
                )

                if (message.status == MessageStatus.Completed) {
                    InlineActions(
                        messageId = message.id,
                        content = message.content,
                        canRegenerate = canRegenerate,
                        onSpeak = onSpeak,
                        onRegenerate = onRegenerate,
                        onSaveSnippet = { onSaveSnippet(message.content, "text") },
                        onRemember = onRemember
                    )
                } else if (message.status == MessageStatus.Failed) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(
                            icon = Lucide.RefreshCw,
                            contentDescription = "Coba Lagi",
                            tint = DeepNavy,
                            onClick = { onRetry(message.id) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Coba Lagi",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = DeepNavy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineActions(
    messageId: Long,
    content: String,
    canRegenerate: Boolean,
    onSpeak: (String) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSaveSnippet: () -> Unit,
    onRemember: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var isCopied by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canRegenerate) {
            ActionIcon(
                icon = Lucide.RefreshCw,
                contentDescription = "Regenerate",
                onClick = { onRegenerate(messageId) }
            )
        }

        ActionIcon(
            icon = Lucide.Copy,
            contentDescription = "Copy",
            tint = if (isCopied) DeepNavy else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                clipboard.setText(AnnotatedString(content))
                isCopied = true
            }
        )

        ActionIcon(
            icon = Lucide.Brain,
            contentDescription = "Ingat ini",
            onClick = onRemember
        )

        ActionIcon(
            icon = Lucide.Bookmark,
            contentDescription = "Simpan ke Library",
            onClick = onSaveSnippet
        )

        ActionIcon(
            icon = Lucide.ThumbsUp,
            contentDescription = "Like",
            tint = if (isLiked) DeepNavy else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                isLiked = !isLiked
                if (isLiked) isDisliked = false
            }
        )
        ActionIcon(
            icon = Lucide.ThumbsDown,
            contentDescription = "Dislike",
            tint = if (isDisliked) DeepNavy else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                isDisliked = !isDisliked
                if (isDisliked) isLiked = false
            }
        )
        ActionIcon(
            icon = Lucide.Volume2,
            contentDescription = "Text to Speech",
            onClick = { onSpeak(content) }
        )
        ActionIcon(
            icon = Lucide.Share2,
            contentDescription = "Share",
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, content)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            }
        )
        ActionIcon(
            icon = Lucide.Ellipsis,
            contentDescription = "More options",
            onClick = { }
        )
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 120

            val yOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0
                        -5f at 350
                        0f at 700
                    },
                    initialStartOffset = StartOffset(delay)
                ),
                label = "yOffset"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0.5f at 0
                        1f at 350
                        0.5f at 700
                    },
                    initialStartOffset = StartOffset(delay)
                ),
                label = "alpha"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = yOffset
                        this.alpha = alpha
                    }
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}