package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.FileCode // [FIX] Ganti FileBraces ke FileCode
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lucide
import com.sync.app.ui.theme.DeepNavy

private data class AttachmentOptionData(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    isMultimodalSupported: Boolean,
    onDismissRequest: () -> Unit,
    onCameraSelected: () -> Unit,
    onAlbumSelected: () -> Unit,
    onDocumentSelected: () -> Unit,
    onPromptSelected: () -> Unit,
    onLibrarySelected: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "File Lampiran",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
            )

            val options = listOf(
                AttachmentOptionData(Lucide.Camera, "Kamera", isMultimodalSupported, onCameraSelected),
                AttachmentOptionData(Lucide.Image, "Album", isMultimodalSupported, onAlbumSelected),
                AttachmentOptionData(Lucide.FileText, "Dokumen", true, onDocumentSelected),
                AttachmentOptionData(Lucide.FileCode, "Prompt", true, onPromptSelected),
                AttachmentOptionData(Lucide.Library, "Library", true, onLibrarySelected)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(options) { option ->
                    CircularAttachmentOption(option = option)
                }
            }
        }
    }
}

@Composable
private fun CircularAttachmentOption(option: AttachmentOptionData) {
    val alpha = if (option.enabled) 1f else 0.4f
    val bgColor = if (option.enabled) DeepNavy.copy(alpha = 0.08f) else DeepNavy.copy(alpha = 0.04f)
    val contentColor = if (option.enabled) DeepNavy else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(enabled = option.enabled) { option.onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(alpha)
            )
        }
        
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(alpha)
        )
        
        if (!option.enabled) {
            Icon(
                imageVector = Lucide.Info,
                contentDescription = "Tidak didukung",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp).padding(top = 2.dp)
            )
        }
    }
}