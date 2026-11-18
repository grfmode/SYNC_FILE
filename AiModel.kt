package com.sync.app.domain.provider

/**
 * Entri model pada Provider Catalog (BLUEPRINT §5.5).
 */
data class AiModel(
    val id: String,
    val displayName: String,
    val providerId: ProviderId,
    val capabilities: Set<ProviderCapability> = emptySet(),
    val contextLimit: Int? = null
)