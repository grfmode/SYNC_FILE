package com.sync.app.domain.context

import com.sync.app.data.local.ContextLogEntity

/** Jenis rujukan pada satu catatan konteks. */
enum class ContextRefKind {
    MEMORY,
    KNOWLEDGE,
    SUMMARY,
    HISTORY,
    PROJECT,
    SYSTEM
}

/** Satu baris catatan konteks sebelum disimpan. */
data class ContextLogEntry(
    val section: ContextSectionId,
    val refId: Long?,
    val refKind: ContextRefKind,
    val label: String?,
    val tokens: Int,
    val truncated: Boolean
) {
    fun toEntity(messageId: Long, mode: ContextMode): ContextLogEntity = ContextLogEntity(
        messageId = messageId,
        section = section.wireName,
        refId = refId,
        refKind = refKind.name,
        label = label,
        tokens = tokens,
        truncated = truncated,
        mode = mode.name
    )
}