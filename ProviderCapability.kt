package com.sync.app.domain.provider

/**
 * Kemampuan opsional provider (BLUEPRINT §5.6, RULES §4.4).
 * UI tidak boleh menampilkan fitur yang tidak didukung provider/model.
 */
enum class ProviderCapability {
    Streaming,
    Multimodal,
    ToolCalling,
    StructuredOutput
}