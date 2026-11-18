package com.sync.app.domain.knowledge

class KotlinJavaChunker : Chunker {
    private val declaration = Regex(
        """^\s*(?:@\w+(?:\([^)]*\))?\s+)?(?:public|private|protected|internal|abstract|open|final|sealed|data|inline|suspend|override|operator|external|lateinit|const|static|synchronized|native|default|actual|expect|annotation)?\s*(class|object|interface|enum|fun|val|var|typealias|record)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )

    override fun supports(kind: KnowledgeKind): Boolean = kind == KnowledgeKind.KOTLIN_JAVA

    override fun chunk(text: String): List<RawChunk> {
        val lines = text.lines()
        val result = mutableListOf<RawChunk>()
        var start = 0
        var symbol: String? = null
        var depth = 0
        var seenDeclaration = false
        var inBlockComment = false

        fun flush(endExclusive: Int) {
            if (endExclusive <= start) return
            val block = lines.subList(start, endExclusive).joinToString("\n")
            if (block.isBlank()) {
                start = endExclusive
                return
            }
            result += ChunkMath.splitOversized(block, start + 1, symbol)
            start = endExclusive
            symbol = null
            seenDeclaration = false
        }

        lines.forEachIndexed { index, line ->
            val match = declaration.find(line)
            if (match != null && depth == 0 && !inBlockComment) {
                if (seenDeclaration) flush(index)
                symbol = "${match.groupValues[1]} ${match.groupValues[2]}"
                seenDeclaration = true
            }
            val (braceDelta, newBlockCommentState) = countBraces(line, inBlockComment)
            depth += braceDelta
            inBlockComment = newBlockCommentState
            if (depth < 0) depth = 0
            if (depth == 0 && index == lines.lastIndex) flush(index + 1)
        }
        if (start <= lines.lastIndex) flush(lines.size)
        return result.ifEmpty { listOf(RawChunk(1, lines.size, null, text)) }
    }

    private fun countBraces(line: String, startInBlockComment: Boolean): Pair<Int, Boolean> {
        var count = 0
        var i = 0
        var inString = false
        var inChar = false
        var inLineComment = false
        var inBlockComment = startInBlockComment
        var stringChar = ' '

        while (i < line.length) {
            val c = line[i]
            val next = if (i + 1 < line.length) line[i + 1] else ' '

            if (inLineComment) break
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (inString || inChar) {
                if (c == '\\') { i += 2; continue }
                if (c == stringChar) { inString = false; inChar = false }
                i++
                continue
            }
            if (c == '/' && next == '/') { inLineComment = true; i += 2; continue }
            if (c == '/' && next == '*') { inBlockComment = true; i += 2; continue }
            if (c == '"' || c == '\'') {
                inString = c == '"'
                inChar = c == '\''
                stringChar = c
                i++
                continue
            }
            if (c == '{') count++
            if (c == '}') count--
            i++
        }
        return count to inBlockComment
    }
}