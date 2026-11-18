package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.app.domain.context.ContextMode
import com.sync.app.ui.theme.DeepNavy

/**
 * Pemilih mode konteks berbentuk pil.
 *
 * Mode memengaruhi banyaknya bahan pendukung yang dikirim, bukan urutan
 * instruksi. Karena itu pilihan ini diletakkan dekat kolom tulis, bukan di
 * dalam layar pengaturan.
 */
@Composable
fun ContextModeSelector(
    selected: ContextMode,
    onSelect: (ContextMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContextMode.entries.forEach { mode ->
            val active = mode == selected
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onPrimary else DeepNavy.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else DeepNavy.copy(alpha = 0.06f)
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}