package com.sync.app.domain.knowledge

/**
 * Deteksi jenis dari ekstensi, dengan pemeriksaan isi untuk kasus ambigu.
 * Berkas biner ditandai [KnowledgeKind.UNSUPPORTED] agar tidak diindeks.
 */
object FileTypeDetector {

    private val kotlinLike = setOf(
        "kt", "kts", "java", "gradle", "groovy", "scala", "swift", "cs",
        "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "php", "c", "cc",
        "cpp", "h", "hpp", "m", "mm", "sql", "sh", "bat", "ps1"
    )

    private val markdown = setOf("md", "markdown", "mdx", "rst", "adoc")

    private val structuredJson = setOf("json", "json5", "jsonc")
    private val structuredXml = setOf("xml", "html", "xhtml", "svg", "plist", "xsd", "xsl")

    private val plain = setOf("txt", "log", "csv", "tsv", "ini", "cfg", "conf", "properties", "toml", "yaml", "yml", "env")

    private val ignoredDirs = setOf(
        "build", ".gradle", ".git", ".idea", "node_modules", "dist", "out",
        ".kotlin", "intermediates", "generated"
    )

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "ttf", "otf", "woff", "woff2",
        "mp3", "mp4", "wav", "ogg", "webm", "zip", "jar", "aar", "apk", "class", "so",
        "dex", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "psd", "db", "sqlite"
    )

    fun extensionOf(relPath: String): String =
        relPath.substringAfterLast('.', "").lowercase()

    fun isIgnoredDirectory(segment: String): Boolean =
        segment.lowercase() in ignoredDirs

    /** True bila salah satu folder induk path termasuk folder yang diabaikan. */
    fun isIgnoredPath(relPath: String): Boolean =
        relPath.split('/').dropLast(1).any { isIgnoredDirectory(it) }

    fun detect(relPath: String, sample: ByteArray): KnowledgeKind {
        val ext = extensionOf(relPath)
        if (ext.isNotEmpty() && ext in binaryExtensions) return KnowledgeKind.UNSUPPORTED
        if (sample.take(2048).contains(0)) return KnowledgeKind.UNSUPPORTED

        return when (ext) {
            in kotlinLike -> KnowledgeKind.KOTLIN_JAVA
            in markdown -> KnowledgeKind.MARKDOWN
            in structuredJson -> KnowledgeKind.JSON
            in structuredXml -> KnowledgeKind.XML
            in plain -> KnowledgeKind.PLAIN_TEXT
            "" -> KnowledgeKind.PLAIN_TEXT
            else -> KnowledgeKind.PLAIN_TEXT
        }
    }
}