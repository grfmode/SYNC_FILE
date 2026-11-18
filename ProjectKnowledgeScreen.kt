package com.sync.app.ui.screens.knowledge

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.FileArchive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash2
import com.sync.app.domain.knowledge.KnowledgeStatus
import com.sync.app.ui.theme.DeepNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectKnowledgeScreen(
    onBack: () -> Unit = {},
    viewModel: ProjectKnowledgeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refreshStats() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val names = uris.map { uri ->
                var name = "file"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index != -1) name = cursor.getString(index) ?: name
                }
                name
            }
            viewModel.importPicked(uris, names)
        }
    }

    summary?.let { current ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSummary,
            confirmButton = { TextButton(onClick = viewModel::dismissSummary) { Text("Tutup") } },
            title = { Text("Rincian") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(current.headline, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    current.details.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Knowledge",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepNavy
                        )
                        Text(
                            "${stats.first} berkas · ${stats.second} potongan",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepNavy.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = "Kembali") } },
                actions = {
                    IconButton(onClick = viewModel::deleteAll) {
                        Icon(Lucide.Trash2, contentDescription = "Hapus semua", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { pickLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Lucide.FileArchive, contentDescription = null, tint = DeepNavy)
                    Text("  Impor zip / berkas", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = viewModel::index) {
                    Icon(Lucide.RefreshCw, contentDescription = null, tint = DeepNavy)
                    Text("  Indeks", color = DeepNavy)
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada berkas. Impor zip repo atau pilih berkas.\nZip terpecah (.z01, .zip.001): pilih semua bagiannya sekaligus.",
                        color = DeepNavy.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.id }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.relPath, style = MaterialTheme.typography.bodySmall, color = DeepNavy)
                                Text(
                                    text = buildString {
                                        append(file.status.name.lowercase())
                                        append(" · ${file.chunkCount} potongan")
                                        file.error?.let { append(" · $it") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (file.status == KnowledgeStatus.FAILED) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        DeepNavy.copy(alpha = 0.5f)
                                    }
                                )
                            }
                            Switch(
                                checked = file.status != KnowledgeStatus.SKIPPED,
                                onCheckedChange = { viewModel.toggle(file) }
                            )
                            IconButton(onClick = { viewModel.reindex(file) }) {
                                Icon(Lucide.RefreshCw, contentDescription = "Indeks ulang", tint = DeepNavy.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = { viewModel.delete(file) }) {
                                Icon(Lucide.Trash2, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}