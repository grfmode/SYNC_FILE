package com.sync.app.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.X
import com.sync.app.domain.context.ContextMode
import com.sync.app.model.AttachmentType
import com.sync.app.model.AttachmentUi
import com.sync.app.ui.theme.DeepNavy

@Composable
fun ChatComposer(
    textValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onAddAttachmentClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onMicClick: () -> Unit,
    pendingAttachments: List<AttachmentUi>,
    canAttach: Boolean,
    isListening: Boolean,
    rmsDb: Float = 0f,
    isEditing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
    contextMode: ContextMode = ContextMode.AUTOMATIC,
    onContextModeChange: (ContextMode) -> Unit = {}
) {
    val canSend = textValue.text.isNotBlank() || pendingAttachments.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Lucide.Pencil,
                    contentDescription = null,
                    tint = DeepNavy,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mengedit pesan",
                    style = MaterialTheme.typography.labelMedium,
                    color = DeepNavy
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Batal Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                items(pendingAttachments, key = { it.uri }) { attachment ->
                    AttachmentPreview(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.uri) }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 74.dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAddAttachmentClick,
                    enabled = canAttach && !isListening,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Paperclip,
                        contentDescription = "Attach",
                        modifier = Modifier.size(26.dp),
                        tint = if (canAttach && !isListening) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    if (textValue.text.isEmpty()) {
                        if (isListening) {
                            AudioSpectrum(rmsDb = rmsDb)
                        } else {
                            Text(
                                text = "Message anything...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BasicTextField(
                        value = textValue,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isListening,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            lineHeight = 24.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4
                    )
                }

                val actionButtonColor by animateColorAsState(
                    targetValue = if (isListening) MaterialTheme.colorScheme.error else DeepNavy,
                    label = "action_color"
                )

                FilledIconButton(
                    onClick = {
                        when {
                            isListening -> onMicClick()
                            canSend -> onSend()
                            else -> onMicClick()
                        }
                    },
                    enabled = canAttach || canSend || isListening,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = actionButtonColor,
                        contentColor = Color.White,
                        disabledContainerColor = DeepNavy.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = when {
                            isListening -> Lucide.Square
                            canSend -> Lucide.ArrowUp
                            else -> Lucide.Mic
                        },
                        contentDescription = when {
                            isListening -> "Stop Recording"
                            canSend -> "Send"
                            else -> "Voice Input"
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // [Baru] Pemilih mode konteks, muncul saat ada isi atau lampiran.
        if (canSend) {
            ContextModeSelector(
                selected = contextMode,
                onSelect = onContextModeChange,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun AudioSpectrum(
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val normalizedVolume = (rmsDb / 10f).coerceIn(0.05f, 1f)

    val barCount = 5

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val weight = when (index) {
                0, 4 -> 0.4f
                1, 3 -> 0.7f
                else -> 1.0f
            }

            val targetHeight = (8f + (normalizedVolume * 16f * weight)).dp

            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing),
                label = "spectrum_bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

@Composable
private fun AttachmentPreview(
    attachment: AttachmentUi,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (attachment.type == AttachmentType.Image) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Lucide.FileText,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(18.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}