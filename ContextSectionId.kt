package com.sync.app.domain.context

/**
 * Seksi konteks dalam urutan kirim.
 *
 * [neverTrimmed] menandai seksi yang tidak boleh dipotong diam-diam.
 */
enum class ContextSectionId(val wireName: String, val neverTrimmed: Boolean) {
    BASE("base", true),
    LAYER_RULES("layer_rules", true),
    PERSONALITY("personality", true),
    PROJECT_PROMPT("project_prompt", false),
    MEMORY_GLOBAL("memory_global", false),
    MEMORY_PROJECT("memory_project", false),
    KNOWLEDGE("knowledge", false),
    SUMMARY("summary", false),
    HISTORY("history", false)
}