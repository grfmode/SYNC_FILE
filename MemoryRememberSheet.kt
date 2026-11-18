package com.sync.app.ui.screens.chat.components

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
import com.sync.app.domain.memory.MemoryType
import com.sync.app.ui.theme.DeepNavy

/**
 * Lembar "Ingat ini".
 *
 * Teks boleh disunting sebelum disimpan; jenis memori dipilih eksplisit agar
 * pengguna sadar apakah yang disimpan aturan, preferensi, atau fakta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryRememberSheet(
    draft: String,
    onConfirm: (String, MemoryType) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(draft) }
    var type by remember { mutableStateOf(MemoryType.PREFERENCE) }

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
                text = "Ingat ini",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepNavy
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 6
            )

            Text("Jenis", style = MaterialTheme.typography.labelMedium, color = DeepNavy.copy(alpha = 0.6f))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryType.entries.forEach { option ->
                    val active = option == type
                    Text(
                        text = option.name.lowercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else DeepNavy,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else DeepNavy.copy(alpha = 0.06f)
                            )
                            .clickable { type = option }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Batal", color = DeepNavy) }
                TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim(), type) }) {
                    Text("Simpan", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}