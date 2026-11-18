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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Zap
import com.sync.app.domain.settings.ChatMode
import com.sync.app.ui.theme.DeepNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenPersonality: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenLab: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showChatModeMenu by remember { mutableStateOf(false) }
    var showTimeoutMenu by remember { mutableStateOf(false) }

    val timeoutOptions = listOf("High-Speed", "Balanced", "Deep-Reasoning")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Layanan AI",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            message ?: "Konfigurasi mesin kecerdasan buatan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = "Back") } },
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
            SectionLabel("PREFERENSI AI")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp)
            ) {
                SettingsRow(Lucide.User, "Kepribadian AI", "Sesuaikan gaya, nada, dan instruksi khusus AI.", Modifier.clickable { onOpenPersonality() }) {
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingsRow(Lucide.RefreshCw, "Mode Otomatis", "Pakai layanan AI cadangan jika utama gagal.") {
                    Switch(
                        checked = state.autoFallback,
                        onCheckedChange = viewModel::toggleAutoFallback,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Divider()
                SettingsRow(Lucide.Zap, "Streaming AI", "Tampilkan respons real-time. Jika mati, jawaban muncul utuh.") {
                    Switch(
                        checked = state.streamingEnabled,
                        onCheckedChange = viewModel::toggleStreaming,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Divider()
                SettingsRow(Lucide.Layers, "Ringkasan Konteks", "AI merangkum percakapan panjang agar konteks awal tidak hilang.") {
                    Switch(
                        checked = state.summaryEnabled,
                        onCheckedChange = viewModel::toggleSummary,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Divider()
                SettingsRow(Lucide.Timer, "AI Timeout Profile", "Kecepatan respons server AI.") {
                    Box {
                        OutlinedButton(onClick = { showTimeoutMenu = true }, shape = CircleShape) { Text(state.timeoutProfile) }
                        DropdownMenu(expanded = showTimeoutMenu, onDismissRequest = { showTimeoutMenu = false }, shape = RoundedCornerShape(16.dp), containerColor = Color.White) {
                            timeoutOptions.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile, color = DeepNavy) },
                                    onClick = { showTimeoutMenu = false; viewModel.setTimeoutProfile(profile) },
                                    trailingIcon = {
                                        if (profile == state.timeoutProfile) Icon(Lucide.Check, contentDescription = null, tint = DeepNavy)
                                    }
                                )
                            }
                        }
                    }
                }
                Divider()
                SettingsRow(Lucide.MessageSquare, "Mode Percakapan", "Gaya bahasa respons AI.") {
                    Box {
                        OutlinedButton(onClick = { showChatModeMenu = true }, shape = CircleShape) { Text(state.selectedChatMode.displayName) }
                        DropdownMenu(expanded = showChatModeMenu, onDismissRequest = { showChatModeMenu = false }, shape = RoundedCornerShape(16.dp), containerColor = Color.White) {
                            ChatMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName, color = DeepNavy) },
                                    onClick = { showChatModeMenu = false; viewModel.changeChatMode(mode) },
                                    trailingIcon = {
                                        if (mode == state.selectedChatMode) Icon(Lucide.Check, contentDescription = null, tint = DeepNavy)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("MEMORI & KONTEKS")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp)
            ) {
                SettingsRow(Lucide.Brain, "Memori", "Kelola butir memori global dan per proyek.", Modifier.clickable { onOpenMemory() }) {
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingsRow(Lucide.Brain, "Ekstraksi Memori Otomatis", "Ambil aturan dan preferensi dari percakapan.") {
                    Switch(
                        checked = state.autoExtract,
                        onCheckedChange = viewModel::toggleAutoExtract,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Divider()
                SettingsRow(Lucide.Layers, "Kirim Knowledge", "Sertakan potongan berkas proyek ke model.") {
                    Switch(
                        checked = state.indexEnabled,
                        onCheckedChange = viewModel::toggleIndex,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Divider()
                SettingsRow(Lucide.RefreshCw, "Impor Memori Lama", "Pindahkan memori teks proyek ke tabel memori.", Modifier.clickable { viewModel.importLegacyMemory() }) {
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingsRow(Lucide.FlaskConical, "Context Lab", "Uji perakitan konteks dan cadangan.", Modifier.clickable { onOpenLab() }) {
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(DeepNavy.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}