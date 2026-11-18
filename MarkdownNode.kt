package com.sync.app.domain.markdown

/**
 * Model AST (Abstract Syntax Tree) untuk Markdown.
 * Memisahkan logika teks dari logika UI (Compose).
 */
sealed class MarkdownNode {
    data class Paragraph(val text: String) : MarkdownNode()
    data class Heading(val level: Int, val text: String) : MarkdownNode()
    data class CodeBlock(val language: String, val code: String) : MarkdownNode()
    data class Blockquote(val text: String) : MarkdownNode()
    data class ListBlock(val items: List<String>, val isOrdered: Boolean) : MarkdownNode()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownNode()
    object Divider : MarkdownNode()
}