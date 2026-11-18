package com.sync.app.domain.context

/**
 * Cara assembler membagi anggaran konteks.
 *
 * Mode tidak pernah mengubah urutan seksi dan tidak pernah memotong base
 * prompt, layer rules, atau kepribadian. Yang berubah hanya seberapa banyak
 * bahan pendukung (memori, knowledge, ringkasan, riwayat) yang diambil.
 */
enum class ContextMode(val displayName: String, val description: String) {
    AUTOMATIC(
        "Otomatis",
        "Seimbang. Bahan pendukung diambil sesuai kebutuhan topik."
    ),
    COMPLETE(
        "Lengkap",
        "Ambil sebanyak mungkin. Berguna saat butuh seluruh konteks proyek."
    ),
    ECONOMICAL(
        "Hemat",
        "Ambil seperlunya. Berguna untuk model berjendela kecil."
    );

    companion object {
        fun fromName(name: String?): ContextMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTOMATIC
    }
}