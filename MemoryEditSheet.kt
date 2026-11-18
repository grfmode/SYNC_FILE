package com.sync.app.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.app.domain.memory.MemoryItem
import com.sync.app.domain.memory.MemoryScope
import com.sync.app.domain.memory.MemoryType
import com.sync.app.ui.theme.DeepNavy

/** Penyunting satu memori: isi, jenis, dan lingkup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryEditSheet(
    item: MemoryItem?,
    projectId: Long?,
    onSave: (Long?, String, MemoryType, MemoryScope, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember { mutableStateOf(item?.content.orEmpty()) }
    var type by remember { mutableStateOf(item?.type ?: MemoryType.PREFERENCE) }
    var scope by remember { mutableStateOf(item?.scope ?: MemoryScope.GLOBAL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (item == null) "Memori baru" else "Sunting memori",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepNavy
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 8
            )

            Text("Jenis", style = MaterialTheme.typography.labelMedium, color = DeepNavy.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MemoryType.entries.forEach { option ->
                    Pill(text = option.name.lowercase(), active = option == type) { type = option }
                }
            }

            Text("Lingkup", style = MaterialTheme.typography.labelMedium, color = DeepNavy.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MemoryScope.entries.forEach { option ->
                    Pill(text = option.name.lowercase(), active = option == scope) { scope = option }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal", color = DeepNavy) }
                TextButton(onClick = {
                    if (content.isNotBlank()) onSave(item?.id, content.trim(), type, scope, projectId)
                }) {
                    Text("Simpan", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.onPrimary else DeepNavy,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else DeepNavy.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}