package com.sync.app.ui.screens.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sync.app.data.local.ContextLogEntity

/** Pembungkus tipis: mengambil catatan lalu menampilkan [ContextChipSheet]. */
@Composable
fun ChatContextSheet(
    messageId: Long,
    onDismiss: () -> Unit,
    loader: suspend (Long) -> List<ContextLogEntity> = { emptyList() }
) {
    var entries by remember { mutableStateOf<List<ContextLogEntity>>(emptyList()) }

    LaunchedEffect(messageId) {
        entries = loader(messageId)
    }

    ContextChipSheet(entries = entries, onDismiss = onDismiss)
}