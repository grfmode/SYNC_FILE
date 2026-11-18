package com.sync.app.domain.settings

/**
 * Gaya bahasa. Nilai adalah modifier, bukan deklarasi peran: peran sudah
 * dimiliki mode, panjang sudah dimiliki base prompt.
 */
enum class PersonalityStyle(val displayName: String, val defaultPrompt: String) {
    Default("Default", ""),
    Profesional("Profesional", "Calm, precise, confident, not stiff. Minimal humor. No profanity."),
    Ramah("Ramah", "Warm, supportive, casual. Light humor. No profanity. No fake sweetness, no over-reassurance."),
    Lugas("Lugas", "Blunt, flat. Rare humor. Conclusion first, short sentences, no small talk. Mild, occasional profanity."),
    Nyentrik("Nyentrik", "Odd, playful, unexpected but coherent analogies. Frequent absurd humor. Substance must survive. Light profanity."),
    Efisien("Efisien", "Terse, utilitarian, low warmth. No humor. Answer first, then only the detail needed to act. No profanity."),
    Sinis("Sinis", "Skeptical, sarcastic, foul-mouthed. Mockery, irony, sharp jabs. Free profanity in the reply language, never replacing the answer. Attack the work, not the user. Soften when the user is fragile.")
}

/**
 * Skalar intensitas. Satu teks melayani keempat dimensi (kehangatan,
 * antusiasme, judul/daftar, emoji), karena map remote memakai key yang sama.
 */
enum class TraitLevel(val displayName: String, val defaultPrompt: String) {
    Default("Default", ""),
    LebihSedikit("Lebih sedikit", "Minimum. Headings: at most one. Emoji: none unless the user uses them. Other dimensions unchanged."),
    LebihBanyak("Lebih banyak", "Maximum. Headings: whenever it aids navigation. Emoji: 1–3 at line start or after a key point. Other dimensions unchanged.")
}

data class PersonalitySettings(
    val style: PersonalityStyle = PersonalityStyle.Default,
    val warmth: TraitLevel = TraitLevel.Default,
    val enthusiasm: TraitLevel = TraitLevel.Default,
    val headings: TraitLevel = TraitLevel.Default,
    val emojis: TraitLevel = TraitLevel.Default,
    val customInstructions: String = ""
) {
    fun buildPromptSuffix(
        remoteStyles: Map<String, String> = emptyMap(),
        remoteTraits: Map<String, String> = emptyMap()
    ): String {
        if (style == PersonalityStyle.Default &&
            warmth == TraitLevel.Default &&
            enthusiasm == TraitLevel.Default &&
            headings == TraitLevel.Default &&
            emojis == TraitLevel.Default &&
            customInstructions.isBlank()
        ) {
            return ""
        }

        // Helper untuk mengambil dari remote (jika ada), fallback ke hardcode
        val stylePrompt = remoteStyles[style.name] ?: style.defaultPrompt
        val warmthPrompt = remoteTraits[warmth.name] ?: warmth.defaultPrompt
        val enthusiasmPrompt = remoteTraits[enthusiasm.name] ?: enthusiasm.defaultPrompt
        val headingsPrompt = remoteTraits[headings.name] ?: headings.defaultPrompt
        val emojisPrompt = remoteTraits[emojis.name] ?: emojis.defaultPrompt

        return buildString {
            append("\n\n[PERSONALITY]")
            if (style != PersonalityStyle.Default) {
                append("\n- Style: $stylePrompt")
            }
            if (warmth != TraitLevel.Default) {
                append("\n- Warmth: $warmthPrompt")
            }
            if (enthusiasm != TraitLevel.Default) {
                append("\n- Enthusiasm: $enthusiasmPrompt")
            }
            if (headings != TraitLevel.Default) {
                append("\n- Headings & lists: $headingsPrompt")
            }
            if (emojis != TraitLevel.Default) {
                append("\n- Emoji: $emojisPrompt")
            }
            if (customInstructions.isNotBlank()) {
                append("\n- Custom instructions (highest priority in this block): $customInstructions")
            }
        }
    }
}