package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Paperclip
import com.sync.app.ui.theme.DeepNavy

/**
 * Ringkasan konteks di bawah satu jawaban.
 *
 * Angka berasal dari `context_log`, bukan perkiraan UI. Bila tidak ada catatan
 * untuk pesan itu, chip tidak dipanggil sama sekali oleh pemanggil.
 */
@Composable
fun ContextChip(
    memoryCount: Int,
    sourceCount: Int,
    tokens: Int,
    truncated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (memoryCount == 0 && sourceCount == 0) return

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(DeepNavy.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Lucide.Paperclip,
            contentDescription = null,
            tint = DeepNavy.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = buildString {
                if (memoryCount > 0) append("$memoryCount memori")
                if (memoryCount > 0 && sourceCount > 0) append(" · ")
                if (sourceCount > 0) append("$sourceCount sumber")
                append(" · ")
                append("$tokens token")
                if (truncated) append(" · dipotong")
            },
            style = MaterialTheme.typography.labelSmall,
            color = DeepNavy.copy(alpha = 0.7f)
        )
    }
}