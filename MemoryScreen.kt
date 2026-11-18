package com.sync.app.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.sync.app.domain.memory.MemoryItem
import com.sync.app.domain.memory.MemoryStatus
import com.sync.app.ui.theme.DeepNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    projectId: Long? = null,
    onBack: () -> Unit = {},
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.setScope(null, projectId) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Memori",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepNavy
                        )
                        Text(
                            "${items.size} item aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepNavy.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Text("Impor", style = MaterialTheme.typography.labelMedium, color = DeepNavy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEdit = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Lucide.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Text("  Tambah", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada memori.", color = DeepNavy.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { editing = item; showEdit = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.type.name.lowercase()} · ${item.scope.name.lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepNavy.copy(alpha = 0.5f)
                            )
                            Text(item.content, style = MaterialTheme.typography.bodyMedium, color = DeepNavy)
                            item.provenance?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = DeepNavy.copy(alpha = 0.4f))
                            }
                        }
                        IconButton(onClick = { viewModel.togglePin(item) }) {
                            Icon(
                                Lucide.Pin,
                                contentDescription = "Sematkan",
                                tint = if (item.pinned) MaterialTheme.colorScheme.primary else DeepNavy.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { viewModel.archive(item) }) {
                            Icon(Lucide.Archive, contentDescription = "Arsipkan", tint = DeepNavy.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = { viewModel.delete(item) }) {
                            Icon(Lucide.Trash2, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        MemoryEditSheet(
            item = editing,
            projectId = projectId,
            onSave = { id, content, type, scope, targetProject ->
                viewModel.save(id, content, type, scope, targetProject)
                showEdit = false
            },
            onDismiss = { showEdit = false }
        )
    }

    if (showImport) {
        MemoryExportSheet(
            preview = preview,
            onPreview = { viewModel.previewImport(it, projectId) },
            onApply = { viewModel.applyImport(projectId) },
            onUndo = viewModel::undoImport,
            onDismiss = { showImport = false; viewModel.cancelImport() }
        )
    }
}