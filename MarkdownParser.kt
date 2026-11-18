package com.sync.app.domain.markdown

object MarkdownParser {

    fun parse(markdown: String): List<MarkdownNode> {
        val nodes = mutableListOf<MarkdownNode>()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 1. Code Block (```)
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                i++ // Skip closing ```
                nodes.add(MarkdownNode.CodeBlock(language, codeLines.joinToString("\n")))
                continue
            }

            // 2. Divider (--- atau ***)
            if (trimmed == "---" || trimmed == "***") {
                nodes.add(MarkdownNode.Divider)
                i++
                continue
            }

            // 3. Heading (#)
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                nodes.add(MarkdownNode.Heading(level, text))
                i++
                continue
            }

            // 4. Blockquote (>)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                nodes.add(MarkdownNode.Blockquote(quoteLines.joinToString("\n")))
                continue
            }

            // 5. Table (|)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headers = tableLines[0].split("|").filter { it.isNotBlank() }.map { it.trim() }
                    // tableLines[1] adalah separator (|---|---|), kita abaikan
                    val rows = tableLines.drop(2).map { row ->
                        row.split("|").filter { it.isNotBlank() }.map { it.trim() }
                    }
                    nodes.add(MarkdownNode.Table(headers, rows))
                }
                continue
            }

            // 6. List (-, *, atau 1.)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
                val isOrdered = trimmed.matches(Regex("^\\d+\\.\\s.*"))
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("- ") || l.startsWith("* ") || l.matches(Regex("^\\d+\\.\\s.*"))) {
                        items.add(l.replaceFirst(Regex("^(- |\\* |\\d+\\. )"), ""))
                        i++
                    } else {
                        break
                    }
                }
                nodes.add(MarkdownNode.ListBlock(items, isOrdered))
                continue
            }

            // 7. Paragraph (Fallback)
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty() && 
                   !lines[i].trim().startsWith("#") && 
                   !lines[i].trim().startsWith("```") && 
                   !lines[i].trim().startsWith(">") && 
                   !lines[i].trim().startsWith("|") &&
                   !lines[i].trim().startsWith("- ") &&
                   !lines[i].trim().startsWith("* ") &&
                   !lines[i].trim().matches(Regex("^\\d+\\.\\s.*"))) {
                paragraphLines.add(lines[i].trim())
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                nodes.add(MarkdownNode.Paragraph(paragraphLines.joinToString("\n")))
            }
        }

        return nodes
    }
}