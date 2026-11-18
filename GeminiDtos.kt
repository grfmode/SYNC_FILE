package com.sync.app.domain.provider.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction? = null
)

@Serializable
internal data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null,
    @SerialName("inline_data") val inlineDataSnake: GeminiInlineData? = null
)

@Serializable
internal data class GeminiInlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null
)

@Serializable
internal data class GeminiError(
    val code: Int? = null,
    val message: String? = null
)

@Serializable
internal data class GeminiModelsResponse(
    val models: List<GeminiModelDto>? = null
)

@Serializable
internal data class GeminiModelDto(
    val name: String,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("inputTokenLimit") val inputTokenLimit: Int? = null
)