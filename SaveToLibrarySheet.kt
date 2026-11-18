package com.sync.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.sync.app.model.LibraryItemType
import com.sync.app.ui.theme.DeepNavy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaveToLibrarySheet(
    fileName: String,
    fileType: LibraryItemType,
    suggestedTags: List<String>,
    onDismissRequest: () -> Unit,
    onSave: (name: String, tags: List<String>) -> Unit // [UPDATE] Tambah parameter name
) {
    var currentName by remember { mutableStateOf(fileName) } // State untuk rename
    var currentTags by remember { mutableStateOf(suggestedTags) }
    var tagInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Simpan ke Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DeepNavy,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // File Preview & Rename
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (fileType) {
                    LibraryItemType.Image -> Lucide.Image
                    LibraryItemType.Document -> Lucide.FileText
                    LibraryItemType.Snippet -> Lucide.FileCode
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(DeepNavy, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                
                // [UPDATE] Editable TextField for Name
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Nama file", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeepNavy,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.padding(16.dp))

            // Tag Section
            Text(
                text = "Tag / Kategori",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DeepNavy,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Tag Input Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    currentTags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .background(DeepNavy, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = "Hapus tag",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { currentTags = currentTags - tag }
                            )
                        }
                    }
                }

                TextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Tambah tag...", fontSize = 12.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Tombol Tambah Tag (jika mengetik)
            if (tagInput.isNotBlank()) {
                Text(
                    text = "Tekan ini untuk menambah #$tagInput",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepNavy,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            val clean = tagInput.trim().replace("#", "").lowercase()
                            if (clean.isNotEmpty() && !currentTags.contains(clean)) {
                                currentTags = currentTags + clean
                            }
                            tagInput = ""
                        }
                )
            }

            Text(
                text = "✨ Tag disarankan secara otomatis. Klik × untuk menghapus.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Tombol Simpan
            Button(
                onClick = { onSave(currentName, currentTags) }, // [UPDATE] Kirim currentName
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DeepNavy),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Simpan ke Library",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}