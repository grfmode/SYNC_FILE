package com.sync.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.sync.app.ui.theme.DeepNavy

/**
 * [P-035] Dialog untuk menambah / mengedit custom provider.
 *
 * Styling mengikuti pola dialog SYNC lain (HistoryBottomSheet, SaveToProjectSheet):
 * - Container: `Color.White`
 * - Aksen: `DeepNavy`
 * - Tidak memakai default M3 (ungu) — UI Freeze D-014 terjaga.
 */
@Composable
fun CustomProviderDialog(
    initialDisplayName: String = "",
    initialBaseUrl: String = "",
    initialApiKey: String = "",
    initialModels: List<String> = emptyList(),
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (displayName: String, baseUrl: String, apiKey: String, models: List<String>) -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelsText by remember { mutableStateOf(initialModels.joinToString("\n")) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Styling konsisten dengan pola Deep Navy SYNC
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DeepNavy,
        unfocusedBorderColor = DeepNavy.copy(alpha = 0.3f),
        focusedLabelColor = DeepNavy,
        unfocusedLabelColor = DeepNavy.copy(alpha = 0.6f),
        focusedTextColor = DeepNavy,
        unfocusedTextColor = DeepNavy,
        cursorColor = DeepNavy,
        focusedPlaceholderColor = DeepNavy.copy(alpha = 0.4f),
        unfocusedPlaceholderColor = DeepNavy.copy(alpha = 0.4f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        iconContentColor = DeepNavy,
        titleContentColor = DeepNavy,
        textContentColor = DeepNavy,
        icon = {
            Icon(
                imageVector = Lucide.Server,
                contentDescription = null,
                tint = DeepNavy,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isEditMode) "Edit Provider Custom" else "Tambah Provider Custom",
                style = MaterialTheme.typography.titleLarge,
                color = DeepNavy
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Provider OpenAI-compatible. Cocok untuk Ollama, LM Studio, vLLM, atau proxy lokal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepNavy.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Nama Provider
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        nameError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama Provider") },
                    placeholder = { Text("Ollama Lokal") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = nameError != null,
                    colors = fieldColors
                )
                if (nameError != null) {
                    Text(
                        text = nameError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        urlError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.1.10:11434/v1") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = urlError != null,
                    colors = fieldColors,
                    supportingText = {
                        Text(
                            text = urlError ?: "Contoh: https://api.example.com/v1",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (urlError != null) MaterialTheme.colorScheme.error
                                    else DeepNavy.copy(alpha = 0.6f)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key (opsional untuk lokal)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None
                                            else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Lucide.EyeOff else Lucide.Eye,
                                contentDescription = "Toggle visibility",
                                tint = DeepNavy
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Model manual hanya sebagai fallback jika endpoint /models tidak tersedia.
                Text(
                    text = "Model Manual (opsional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepNavy
                )
                Text(
                    text = "Sync akan mencoba mengambil daftar model otomatis dari provider. Isi bagian ini hanya jika provider tidak mendukung endpoint /models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepNavy.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = {
                        modelsText = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Model fallback (1 per baris)") },
                    placeholder = { Text("llama3.1:8b\nqwen2.5:7b") },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = displayName.trim()
                    val trimmedUrl = baseUrl.trim().trimEnd('/')
                    val modelsList = modelsText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                    var hasError = false
                    if (trimmedName.isBlank()) {
                        nameError = "Nama wajib diisi"
                        hasError = true
                    }
                    val urlValidation = validateBaseUrl(trimmedUrl)
                    if (urlValidation != null) {
                        urlError = urlValidation
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(trimmedName, trimmedUrl, apiKey.trim(), modelsList)
                    }
                }
            ) {
                Text("Simpan", color = DeepNavy)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = DeepNavy.copy(alpha = 0.6f))
            }
        }
    )
}

private fun validateBaseUrl(url: String): String? {
    if (url.isBlank()) return "Base URL wajib diisi"
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return "URL harus diawali http:// atau https://"
    }
    if (url.startsWith("https://")) return null

    val hostPart = url.removePrefix("http://").substringBefore('/').substringBefore(':')
    val isLocal = hostPart == "localhost" ||
        hostPart == "127.0.0.1" ||
        hostPart.startsWith("10.") ||
        hostPart.startsWith("192.168.") ||
        isPrivate172(hostPart)
    return if (isLocal) null else "HTTP hanya diizinkan untuk IP lokal. Gunakan HTTPS untuk server publik."
}

private fun isPrivate172(host: String): Boolean {
    if (!host.startsWith("172.")) return false
    val secondOctet = host.removePrefix("172.").substringBefore('.').toIntOrNull() ?: return false
    return secondOctet in 16..31
}