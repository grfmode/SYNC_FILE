package com.sync.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.sync.app.domain.settings.PersonalityStyle
import com.sync.app.domain.settings.TraitLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityScreen(
    onBack: () -> Unit,
    viewModel: PersonalityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Personalisasi",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.Check, contentDescription = "Simpan & Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Gaya Dasar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TraitRow(
                    title = "Gaya dan nada dasar",
                    value = state.style.displayName,
                    onClick = { /* handled by state var inside */ },
                    content = { onDismiss ->
                        PersonalityStyle.entries.forEach { style ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(style.displayName)
                                        // PERBAIKAN: Menggunakan defaultPrompt, bukan prompt
                                        Text(style.defaultPrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.saveStyle(style)
                                    onDismiss()
                                },
                                trailingIcon = {
                                    if (style == state.style) Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preferensi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TraitRow("Kehangatan", state.warmth.displayName) { onDismiss ->
                    TraitLevel.entries.forEach { lvl ->
                        DropdownMenuItem(text = { Text(lvl.displayName) }, onClick = { viewModel.saveWarmth(lvl); onDismiss() }, trailingIcon = { if (lvl == state.warmth) Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                TraitRow("Antusiasme", state.enthusiasm.displayName) { onDismiss ->
                    TraitLevel.entries.forEach { lvl ->
                        DropdownMenuItem(text = { Text(lvl.displayName) }, onClick = { viewModel.saveEnthusiasm(lvl); onDismiss() }, trailingIcon = { if (lvl == state.enthusiasm) Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                TraitRow("Judul dan daftar", state.headings.displayName) { onDismiss ->
                    TraitLevel.entries.forEach { lvl ->
                        DropdownMenuItem(text = { Text(lvl.displayName) }, onClick = { viewModel.saveHeadings(lvl); onDismiss() }, trailingIcon = { if (lvl == state.headings) Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                TraitRow("Emoji", state.emojis.displayName) { onDismiss ->
                    TraitLevel.entries.forEach { lvl ->
                        DropdownMenuItem(text = { Text(lvl.displayName) }, onClick = { viewModel.saveEmojis(lvl); onDismiss() }, trailingIcon = { if (lvl == state.emojis) Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instruksi Khusus
            Text(
                text = "Instruksi khusus",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = state.customInstructions,
                onValueChange = { viewModel.saveCustomInstructions(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                placeholder = { Text("Bagikan hal lain yang perlu dipertimbangkan AI dalam responsnya.") },
                shape = RoundedCornerShape(24.dp),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TraitRow(
    title: String,
    value: String,
    onClick: () -> Unit = {},
    content: @Composable (onDismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(4.dp))
            Icon(
                imageVector = Lucide.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            content { expanded = false }
        }
    }
}