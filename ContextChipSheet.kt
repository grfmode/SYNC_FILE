package com.sync.app.ui.screens.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sync.app.data.local.ContextLogEntity
import com.sync.app.ui.theme.DeepNavy

/**
 * Rincian konteks satu jawaban.
 *
 * Ditampilkan apa adanya: seksi, label rujukan, jumlah token, dan penanda bila
 * isinya dipotong. Berguna untuk menjawab "kenapa model tidak tahu X".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextChipSheet(
    entries: List<ContextLogEntity>,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Konteks yang dikirim",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepNavy
            )
            Text(
                text = "${entries.sumOf { it.tokens }} token · mode ${entries.firstOrNull()?.mode ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = DeepNavy.copy(alpha = 0.6f)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.label ?: entry.section,
                                style = MaterialTheme.typography.bodySmall,
                                color = DeepNavy
                            )
                            Text(
                                text = "${entry.section} · ${entry.refKind}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepNavy.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = buildString {
                                append("${entry.tokens} t")
                                if (entry.truncated) append(" · potong")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.truncated) {
                                MaterialTheme.colorScheme.error
                            } else {
                                DeepNavy.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
        }
    }
}