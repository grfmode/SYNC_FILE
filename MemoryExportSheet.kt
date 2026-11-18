package com.sync.app.ui.screens.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.app.domain.memory.MemoryExportImport
import com.sync.app.ui.theme.DeepNavy

/** Impor memori: tempel berkas, lihat pratinjau, baru terapkan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryExportSheet(
    preview: MemoryExportImport.Preview?,
    onPreview: (String) -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var raw by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Impor memori", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepNavy)
            Text(
                "Tempel isi berkas ekspor. Pratinjau tidak menyentuh database.",
                style = MaterialTheme.typography.bodySmall,
                color = DeepNavy.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                maxLines = 10
            )

            preview?.let { current ->
                Text(
                    text = "Baru ${current.newCount} · duplikat ${current.duplicateCount} · " +
                        "perbarui ${current.updateCount} · tidak valid ${current.invalidCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current.invalidCount > 0) MaterialTheme.colorScheme.error else DeepNavy
                )
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    itemsIndexed(current.rows) { _, row ->
                        Text(
                            text = "${row.action} · ${row.content.take(70)}${row.note?.let { " ($it)" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavy.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onUndo) { Text("Batalkan impor", color = DeepNavy) }
                TextButton(onClick = { onPreview(raw) }) { Text("Pratinjau", color = DeepNavy) }
                TextButton(onClick = onApply, enabled = preview?.canApply == true) {
                    Text("Terapkan", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}