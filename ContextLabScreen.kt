package com.sync.app.ui.screens.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.sync.app.domain.context.ContextMode
import com.sync.app.ui.screens.chat.components.ContextModeSelector
import com.sync.app.ui.theme.DeepNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextLabScreen(
    onBack: () -> Unit = {},
    viewModel: ContextLabViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val selfTest by viewModel.selfTestResult.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(ContextMode.AUTOMATIC) }
    var projectText by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("Contoh pertanyaan untuk menguji perakitan konteks.") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Context Lab",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepNavy
                        )
                        Text(
                            text = if (viewModel.isolated) "Database uji terpisah" else "PERINGATAN: path tidak terpisah",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (viewModel.isolated) DeepNavy.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (viewModel.safeMode) {
                Text(
                    "SAFE_MODE aktif: database utama dalam mode baca.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = viewModel::seed) { Text("Isi dari utama", color = DeepNavy) }
                TextButton(onClick = viewModel::resetLab) { Text("Reset Lab", color = DeepNavy) }
            }

            ContextModeSelector(selected = mode, onSelect = { mode = it })

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            OutlinedTextField(
                value = projectText,
                onValueChange = { value -> projectText = value.filter { it.isDigit() }.take(12) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                label = { Text("Project ID (opsional, untuk menguji Knowledge)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            TextButton(onClick = { viewModel.run(mode, 32_000, prompt, projectText.toLongOrNull()) }) {
                Text("Jalankan perakitan", color = DeepNavy, fontWeight = FontWeight.Bold)
            }

            report?.let { current ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepNavy.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Estimasi ${current.estimatedTokens} / ${current.planUsable} token" +
                            if (current.overBudget) " · MELEBIHI" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current.overBudget) MaterialTheme.colorScheme.error else DeepNavy
                    )
                    current.sections.forEach { section ->
                        Text(
                            "${section.section.wireName}: ${section.items} item, ${section.tokens} token, ${section.truncated} dipotong",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavy.copy(alpha = 0.7f)
                        )
                    }
                    current.note?.let { note ->
                        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        current.preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepNavy.copy(alpha = 0.6f),
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = viewModel::selfTestBackup) { Text("Self-test cadangan", color = DeepNavy) }
                TextButton(onClick = viewModel::hotBackupNow) { Text("Cadangkan", color = DeepNavy) }
                TextButton(onClick = viewModel::restoreLatest) { Text("Pulihkan terbaru", color = DeepNavy) }
            }

            selfTest?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DeepNavy) }
            message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DeepNavy.copy(alpha = 0.7f)) }
        }
    }
}