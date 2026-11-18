package com.sync.app.ui.screens.projects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.FolderMinus
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.sync.app.model.Conversation
import com.sync.app.ui.theme.DeepNavy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit = {},
    onOpenChat: (Long) -> Unit,
    onOpenNewChatInProject: (Long) -> Unit,
    onOpenKnowledge: (Long) -> Unit = {},
    onOpenMemory: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val projectAttachments by viewModel.projectAttachments.collectAsStateWithLifecycle()

    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    var showInstructionDialog by remember { mutableStateOf(false) }
    var instructionText by remember { mutableStateOf("") }
    var showContextDialog by remember { mutableStateOf(false) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addAttachment(uri) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                editTitle = project?.name ?: ""
                                editDescription = project?.description ?: ""
                                showEditInfoDialog = true
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = project?.name ?: "Project",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = DeepNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(Lucide.Pencil, contentDescription = "Edit", tint = DeepNavy.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = project?.description ?: "Ketuk untuk menambahkan deskripsi...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepNavy.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = "Back", tint = DeepNavy) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectActionCard("Instruksi Khusus", Lucide.Sparkles, Modifier.weight(1f)) {
                            instructionText = project?.systemPrompt ?: ""
                            showInstructionDialog = true
                        }
                        ProjectActionCard("Konteks", Lucide.FileText, Modifier.weight(1f)) { showContextDialog = true }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectActionCard("Memori", Lucide.Brain, Modifier.weight(1f)) { onOpenMemory(projectId) }
                        ProjectActionCard("Knowledge", Lucide.Library, Modifier.weight(1f)) { onOpenKnowledge(projectId) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectActionCard("Chat Baru", Lucide.Plus, Modifier.weight(1f)) { onOpenNewChatInProject(projectId) }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (conversations.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada chat di project ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepNavy.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(conversations, key = { it.id }) { conv ->
                    ConversationInProjectCard(
                        conversation = conv,
                        formattedDate = dateFormat.format(Date(conv.updatedAt)),
                        onClick = { onOpenChat(conv.id) },
                        onRemove = { viewModel.removeFromProject(conv.id) }
                    )
                }
            }
        }
    }

    if (showEditInfoDialog) {
        CustomDialog({ showEditInfoDialog = false }, "Edit Info Project", "Ubah nama dan deskripsi project ini.") {
            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("Judul Project") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it },
                label = { Text("Deskripsi (opsional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.updateProjectInfo(editTitle, editDescription); showEditInfoDialog = false }) {
                    Text("Simpan", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showInstructionDialog) {
        CustomDialog({ showInstructionDialog = false }, "Instruksi Khusus", "Instruksi ini diterapkan ke semua chat di project ini.") {
            OutlinedTextField(
                value = instructionText,
                onValueChange = { instructionText = it },
                placeholder = { Text("Contoh: Bertindak sebagai ahli koding...", color = DeepNavy.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.saveInstruction(instructionText); showInstructionDialog = false }) {
                    Text("Simpan", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showContextDialog) {
        CustomDialog({ showContextDialog = false }, "Konteks Project", "File di bawah ini dibaca AI di setiap chat project ini.") {
            if (projectAttachments.isEmpty()) {
                Text("Belum ada file.", style = MaterialTheme.typography.bodyMedium, color = DeepNavy.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projectAttachments, key = { it.uri }) { att ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DeepNavy.copy(alpha = 0.05f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Lucide.FileText, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(att.name, style = MaterialTheme.typography.bodyMedium, color = DeepNavy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeAttachment(att) }, modifier = Modifier.size(24.dp)) {
                                Icon(Lucide.Trash2, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Lucide.Plus, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Tambah File", color = DeepNavy, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showContextDialog = false }) { Text("Tutup", color = DeepNavy) }
            }
        }
    }
}

@Composable
private fun ProjectActionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepNavy.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = DeepNavy, fontWeight = FontWeight.SemiBold)
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(DeepNavy.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun CustomDialog(onDismiss: () -> Unit, title: String, description: String, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = DeepNavy)
                Spacer(modifier = Modifier.height(12.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = DeepNavy.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

@Composable
private fun ConversationInProjectCard(conversation: Conversation, formattedDate: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(DeepNavy.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Lucide.FileText, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.title, style = MaterialTheme.typography.titleMedium, color = DeepNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = DeepNavy.copy(alpha = 0.5f))
        }
        IconButton(onClick = onRemove) {
            Icon(Lucide.FolderMinus, contentDescription = "Keluarkan", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}