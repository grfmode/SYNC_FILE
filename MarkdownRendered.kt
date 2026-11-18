package com.sync.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.sync.app.domain.markdown.MarkdownNode
import com.sync.app.domain.markdown.MarkdownParser
import com.sync.app.ui.theme.DeepNavy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// [UI Fix] Batas baris preview code block terlipat (gaya Qwen: tampilkan
// sebagian baris pertama + tombol "Lihat Semua" agar blok panjang tidak
// memanjang ke bawah).
private const val CODE_COLLAPSED_LINE_COUNT = 10

// [UI Fix] Intensitas warna inline code: warna asli (textColor) dengan alpha
// diturunkan agar inline code terlihat "pudar/soft" — pembeda tanpa background.
private const val INLINE_CODE_ALPHA = 0.7f

@Composable
fun MarkdownRenderer(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onSaveSnippet: ((String, String) -> Unit)? = null // [P-029] Callback Save per blok
) {
    val nodes = remember(content) { MarkdownParser.parse(content) }

    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth()) {
            nodes.forEach { node ->
                when (node) {
                    is MarkdownNode.Paragraph -> {
                        InlineMarkdownText(
                            text = node.text,
                            textColor = textColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    is MarkdownNode.Heading -> {
                        val fontSize = when (node.level) {
                            1 -> 24.sp
                            2 -> 20.sp
                            3 -> 18.sp
                            else -> 16.sp
                        }
                        Text(
                            text = node.text,
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            // [UI Fix] Ritme vertikal heading: jarak atas lebih besar
                            // agar heading tidak menempel dengan blok/paragraf di atasnya.
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    is MarkdownNode.CodeBlock -> {
                        if (node.language.equals("text", ignoreCase = true)) {
                            CopyTextBlock(
                                text = node.code,
                                onSaveSnippet = onSaveSnippet
                            )
                        } else {
                            CodeBlock(
                                language = node.language,
                                code = node.code,
                                onSaveSnippet = onSaveSnippet
                            )
                        }
                    }
                    is MarkdownNode.Blockquote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DeepNavy.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            InlineMarkdownText(
                                text = node.text,
                                textColor = textColor.copy(alpha = 0.8f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
                            )
                        }
                    }
                    is MarkdownNode.ListBlock -> {
                        Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                            node.items.forEachIndexed { index, item ->
                                // [UI Fix] Ritme list: jarak antar-item 4.dp, teks weight(1f)
                                // agar baris lanjutan rapi menggantung di bawah teks.
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = if (node.isOrdered) "${index + 1}." else "•",
                                        color = textColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    InlineMarkdownText(
                                        text = item,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    is MarkdownNode.Table -> {
                        MarkdownTable(headers = node.headers, rows = node.rows)
                    }
                    is MarkdownNode.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = DeepNavy.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    textColor: Color,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge.copy(
        // [UI Fix] LineHeight eksplisit agar body text tidak terasa rapat
        // dan tinggi baris stabil saat ada inline code.
        lineHeight = 22.sp
    ),
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    val annotatedString = remember(text, textColor, primaryColor, textStyle) {
        buildAnnotatedString {
            val regex = Regex("""(\*\*.*?\*\*|\*.*?\*|`.*?`|\[.*?\]\(.*?\))""")
            var lastIndex = 0
            
            regex.findAll(text).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                
                val matchText = match.value
                when {
                    matchText.startsWith("**") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(matchText.removeSurrounding("**"))
                        }
                    }
                    matchText.startsWith("*") -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(matchText.removeSurrounding("*"))
                        }
                    }
                    matchText.startsWith("`") -> {
                        // [UI Fix] Inline code tanpa background: pembeda lewat tiga lapis —
                        // font monospace, ukuran 90% body, dan warna asli (textColor)
                        // dengan alpha diturunkan sehingga terlihat pudar/soft namun
                        // tetap terbaca jelas di background terang.
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = textStyle.fontSize * 0.9f,
                            color = textColor.copy(alpha = INLINE_CODE_ALPHA)
                        )) {
                            append(matchText.removeSurrounding("`"))
                        }
                    }
                    matchText.startsWith("[") -> {
                        val linkText = matchText.substringAfter("[").substringBefore("]")
                        withStyle(SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                    }
                    else -> append(matchText)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    Text(
        text = annotatedString,
        color = textColor,
        style = textStyle,
        modifier = modifier
    )
}

@Composable
private fun CopyTextBlock(
    text: String,
    onSaveSnippet: ((String, String) -> Unit)?
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        color = DeepNavy.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, DeepNavy.copy(alpha = 0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEXT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DeepNavy.copy(alpha = 0.7f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSaveSnippet != null) {
                        Icon(
                            imageVector = Lucide.Bookmark,
                            contentDescription = "Simpan teks",
                            modifier = Modifier.size(16.dp).clickable { onSaveSnippet(text, "text") },
                            tint = DeepNavy.copy(alpha = 0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(text))
                            isCopied = true
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCopied) Lucide.Check else Lucide.Copy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = DeepNavy.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCopied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavy.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = DeepNavy,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    onSaveSnippet: ((String, String) -> Unit)?
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // [UI Fix] Code block terlipat: jika kode melebihi batas preview, tampilkan
    // hanya baris pertama + indikator sisa baris + tombol "Lihat Semua".
    // State di-reset per kode (remember(code)) agar hasil regenerate kembali terlipat.
    val codeLines = remember(code) { code.split("\n") }
    val needsCollapse = codeLines.size > CODE_COLLAPSED_LINE_COUNT
    var isExpanded by remember(code) { mutableStateOf(false) }
    val isCollapsed = needsCollapse && !isExpanded
    val hiddenLineCount = codeLines.size - CODE_COLLAPSED_LINE_COUNT

    val displayedCode = if (isCollapsed) {
        codeLines.take(CODE_COLLAPSED_LINE_COUNT).joinToString("\n")
    } else {
        code
    }

    Surface(
        color = DeepNavy,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, DeepNavy.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSaveSnippet != null) {
                        Icon(
                            imageVector = Lucide.Bookmark,
                            contentDescription = "Simpan kode",
                            modifier = Modifier.size(16.dp).clickable { onSaveSnippet(code, language) },
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Lucide.Check else Lucide.Copy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isCopied) "Copied" else "Copy code",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = highlightCode(displayedCode, language),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }

            // [UI Fix] Footer lipat: indikator sisa baris + tombol buka/tutup.
            // Copy & Bookmark selalu memakai kode penuh, terlepas dari lipatan.
            if (needsCollapse) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCollapsed) "Lihat Semua (+$hiddenLineCount baris)" else "Tutup",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(headers: List<String>, rows: List<List<String>>) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, DeepNavy.copy(alpha = 0.1f)), RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier.background(DeepNavy.copy(alpha = 0.08f))
            ) {
                headers.forEach { header ->
                    InlineMarkdownText(
                        text = header,
                        textColor = DeepNavy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(120.dp).padding(8.dp)
                    )
                }
            }
            HorizontalDivider(color = DeepNavy.copy(alpha = 0.1f))
            
            rows.forEach { row ->
                Row {
                    headers.indices.forEach { index ->
                        val cellText = row.getOrNull(index) ?: ""
                        InlineMarkdownText(
                            text = cellText,
                            textColor = DeepNavy.copy(alpha = 0.8f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(120.dp).padding(8.dp)
                        )
                    }
                }
                HorizontalDivider(color = DeepNavy.copy(alpha = 0.05f))
            }
        }
    }
}

private fun highlightCode(code: String, language: String): AnnotatedString {
    val lang = language.lowercase()
    if (lang != "kotlin" && lang != "kt" && lang != "java" && lang != "swift") {
        return AnnotatedString(code)
    }

    return buildAnnotatedString {
        val keywords = setOf(
            "fun", "val", "var", "class", "object", "interface", "if", "else", 
            "return", "import", "package", "when", "for", "while", "is", "in", 
            "private", "public", "protected", "override", "suspend", "data", "sealed"
        )
        
        var lastIndex = 0
        val wordRegex = Regex("\\b\\w+\\b")
        
        wordRegex.findAll(code).forEach { match ->
            if (match.range.first > lastIndex) {
                append(code.substring(lastIndex, match.range.first))
            }
            val word = match.value
            
            if (word in keywords) {
                withStyle(SpanStyle(color = Color(0xFF80CBC4))) {
                    append(word)
                }
            } else {
                append(word)
            }
            
            lastIndex = match.range.last + 1
        }
        if (lastIndex < code.length) {
            append(code.substring(lastIndex))
        }
    }
}