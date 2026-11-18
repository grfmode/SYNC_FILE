package com.sync.app.domain.knowledge

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Enumeration

/**
 * Merencanakan arsip zip dari daftar nama berkas yang dipilih.
 *
 * Mengenali tiga bentuk:
 * - Info-ZIP terpecah: `repo.z01`, `repo.z02`, …, lalu `repo.zip` sebagai bagian terakhir.
 * - Pecahan mentah (7-Zip/split): `repo.zip.001`, `repo.zip.002`, …
 * - Zip biasa: tiap `.zip` berdiri sendiri (termasuk `part1.zip`, `part2.zip` yang
 *   masing-masing zip utuh).
 *
 * Berkas selain itu dikembalikan sebagai [Plan.loose] untuk diimpor sebagai berkas biasa.
 * Kelas ini murni Kotlin (tanpa Android) agar mudah diuji.
 */
object SplitArchivePlanner {

    /** Satu arsip logis. [indices] adalah indeks pada daftar nama, sudah berurutan. */
    data class PlannedArchive(val label: String, val indices: List<Int>)

    data class Plan(
        val units: List<PlannedArchive>,
        val loose: List<Int>,
        val problems: List<String>
    )

    private val infoZipPart = Regex("""^(.*)\.z(\d{2,3})$""", RegexOption.IGNORE_CASE)
    private val rawPart = Regex("""^(.*)\.zip\.(\d{3})$""", RegexOption.IGNORE_CASE)
    private val finalPart = Regex("""^(.*)\.zip$""", RegexOption.IGNORE_CASE)

    fun isSplitPartName(name: String): Boolean {
        val n = name.trim()
        return infoZipPart.matches(n) || rawPart.matches(n)
    }

    fun plan(names: List<String>): Plan {
        val infoParts = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
        val rawParts = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
        val zips = linkedMapOf<String, MutableList<Int>>()
        val loose = mutableListOf<Int>()

        names.forEachIndexed { index, raw ->
            val name = raw.trim()
            val info = infoZipPart.matchEntire(name)
            val part = rawPart.matchEntire(name)
            val whole = finalPart.matchEntire(name)
            when {
                info != null -> infoParts
                    .getOrPut(info.groupValues[1].lowercase()) { mutableListOf() }
                    .add(info.groupValues[2].toInt() to index)
                part != null -> rawParts
                    .getOrPut(part.groupValues[1].lowercase()) { mutableListOf() }
                    .add(part.groupValues[2].toInt() to index)
                whole != null -> zips
                    .getOrPut(whole.groupValues[1].lowercase()) { mutableListOf() }
                    .add(index)
                else -> {
                    loose += index
                }
            }
        }

        val units = mutableListOf<PlannedArchive>()
        val problems = mutableListOf<String>()

        for ((base, parts) in infoParts) {
            val sorted = parts.sortedBy { it.first }
            val missing = missingNumbers(sorted.map { it.first })
            val last = zips[base]?.firstOrNull()
            when {
                missing.isNotEmpty() ->
                    problems += "$base: bagian ke-${missing.joinToString()} belum dipilih"
                last == null ->
                    problems += "$base: bagian terakhir $base.zip belum dipilih"
                else -> {
                    units += PlannedArchive(base, sorted.map { it.second } + last)
                    zips[base]?.removeAt(0)
                }
            }
        }

        for ((base, parts) in rawParts) {
            val sorted = parts.sortedBy { it.first }
            val missing = missingNumbers(sorted.map { it.first })
            if (missing.isNotEmpty()) {
                problems += "$base.zip: bagian ke-${missing.joinToString()} belum dipilih"
            } else {
                units += PlannedArchive("$base.zip", sorted.map { it.second })
            }
        }

        for ((base, indices) in zips) {
            indices.forEach { index -> units += PlannedArchive("$base.zip", listOf(index)) }
        }

        return Plan(units, loose, problems)
    }

    /** Nomor yang hilang pada deret yang seharusnya 1..max. */
    private fun missingNumbers(numbers: List<Int>): List<Int> {
        val max = numbers.maxOrNull() ?: return emptyList()
        val present = numbers.toSet()
        return (1..max).filter { it !in present }
    }
}

/** Penyambung aliran untuk arsip terpecah. */
object SplitArchiveStreams {

    private val SPAN_MARKER = byteArrayOf(0x50, 0x4B, 0x07, 0x08)

    /** Membuka bagian satu per satu, hanya saat dibutuhkan. */
    fun concat(openers: List<() -> InputStream>): InputStream {
        val iterator = openers.iterator()
        val enumeration = object : Enumeration<InputStream> {
            override fun hasMoreElements(): Boolean = iterator.hasNext()
            override fun nextElement(): InputStream = iterator.next().invoke()
        }
        return SequenceInputStream(enumeration)
    }

    /**
     * Arsip Info-ZIP terpecah diawali penanda 4 byte (PK 07 08) yang bukan
     * bagian dari entri. Penanda dibuang bila ada; bila tidak ada, aliran utuh.
     */
    fun skipSpanMarker(input: InputStream): InputStream {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        buffered.mark(4)
        val head = ByteArray(4)
        var filled = 0
        while (filled < 4) {
            val read = buffered.read(head, filled, 4 - filled)
            if (read < 0) break
            filled += read
        }
        if (!(filled == 4 && head.contentEquals(SPAN_MARKER))) buffered.reset()
        return buffered
    }
}
