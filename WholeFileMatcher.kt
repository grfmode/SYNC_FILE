package com.sync.app.domain.knowledge

object WholeFileMatcher {
    private val fileLike = Regex("""[A-Za-z0-9_\-./]+\.[A-Za-z][A-Za-z0-9]{0,4}""")
    private val knownExts = setOf(
        "kt", "kts", "java", "py", "md", "json", "xml", "yaml", "yml", "toml",
        "gradle", "txt", "sql", "html", "css", "js", "ts", "c", "h", "cpp", "dart"
    )

    fun candidates(message: String): List<String> {
        val out = LinkedHashSet<String>()
        fileLike.findAll(message).forEach { m ->
            val token = m.value.trimStart('/').lowercase()
            val ext = token.substringAfterLast('.', "")
            if (ext in knownExts) {
                out += token
                out += token.substringBeforeLast('.')
            }
        }
        return out.toList()
    }

    fun match(files: List<Pair<String, String>>, candidates: List<String>): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val matched = LinkedHashSet<String>()
        for ((relPath, displayName) in files) {
            val base = displayName.substringBeforeLast('.').lowercase()
            val nameLower = displayName.lowercase()
            val relLower = relPath.lowercase()
            for (c in candidates) {
                val hit = c == nameLower || c == base ||
                    relLower == c || relLower.endsWith("/$c") ||
                    (c.contains('.') && relLower.endsWith(c))
                if (hit) {
                    matched += relPath
                    break
                }
            }
        }
        return matched.toList()
    }
}