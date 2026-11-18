package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.sync.app.ui.theme.DeepNavy

/**
 * Header ringkas: chip model, aksi percakapan baru, dan pintu ke pengaturan.
 *
 * [contextModeLabel] opsional; bila kosong tidak ada yang ditampilkan, sehingga
 * pemanggil lama tetap berjalan tanpa perubahan.
 */
@Composable
fun ChatCompactHeader(
    modelName: String,
    onClick: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contextModeLabel: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(DeepNavy.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DeepNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Lucide.ChevronDown,
                contentDescription = null,
                tint = DeepNavy.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (contextModeLabel.isNotBlank()) {
                Text(
                    text = contextModeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepNavy.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            IconButton(onClick = onNewConversation, modifier = Modifier.size(36.dp)) {
                Icon(Lucide.Plus, contentDescription = "Percakapan baru", tint = DeepNavy, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(Lucide.Settings, contentDescription = "Pengaturan", tint = DeepNavy, modifier = Modifier.size(18.dp))
            }
        }
    }
}