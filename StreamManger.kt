package com.sync.app.domain.chat

import com.sync.app.domain.error.AiException
import com.sync.app.domain.provider.StreamChunk
import com.sync.app.domain.provider.GeneratedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P-026 Fase 2: Mengelola siklus hidup streaming SSE dan buffering UI.
 *
 * Tanggung jawab:
 * - Mengonsumsi [Flow] dari [StreamChunk] (Delta / Done / Error)
 * - Mengakumulasi Delta ke buffer internal
 * - Throttle UI (80ms sampling, T-043) via [streamingContent]
 * - Mengembalikan hasil akhir (Success / Failed / NoContent) ke pemanggil
 *
 * BUKAN tanggung jawab:
 * - Persistensi Room (dilakukan ViewModel lewat ChatRepository)
 * - Update `messages` list (dilakukan ViewModel)
 * - Markdown stripping (dilakukan ViewModel berdasarkan ChatMode)
 * - Fallback non-streaming (keputusan ViewModel)
 */
interface StreamManager {

    /**
     * Konten streaming terkini yang di-throttle. UI meng-observe ini.
     * Kosong saat tidak ada stream aktif.
     */
    val streamingContent: StateFlow<String>

    /**
     * Mengonsumsi [flow] sampai selesai.
     *
     * @return [StreamResult] yang menggambarkan hasil akhir streaming.
     *   - [StreamResult.Success] — Done diterima, [content] berisi teks lengkap
     *   - [StreamResult.Failed] — Error diterima, [partialContent] berisi teks sampai sebelum error
     *   - [StreamResult.NoContent] — flow selesai tanpa Delta (kosong)
     */
    suspend fun consume(flow: Flow<StreamChunk>): StreamResult

    /**
     * Reset state internal. Dipanggil saat user memulai chat baru atau pindah conversation.
     */
    fun reset()
}

/**
 * Hasil akhir dari satu sesi streaming.
 */
sealed class StreamResult {
    data class Success(val content: String, val images: List<GeneratedImage> = emptyList()) : StreamResult()
    data class Failed(val partialContent: String, val exception: AiException, val images: List<GeneratedImage> = emptyList()) : StreamResult()
    data object NoContent : StreamResult()
}

/**
 * Implementasi singleton (Opsi A).
 *
 * Karena SYNC hanya memiliki satu layar chat dan satu percakapan aktif
 * pada satu waktu, satu instance global aman dan mencegah kebocoran state
 * saat rotasi / navigation.
 */
@Singleton
class StreamManagerImpl @Inject constructor() : StreamManager {

    private companion object {
        const val STREAMING_UI_SAMPLE_MS = 80L
    }

    private val _streamingContent = MutableStateFlow("")
    override val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    // Scope internal untuk job sampling. Tidak memblokir caller.
    private val samplingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var samplingJob: Job? = null

    // Buffer aktif untuk stream saat ini. Semua akses ke buffer dan identitas sesi
    // dilindungi lock karena collector dan sampler berjalan pada dispatcher berbeda.
    private val bufferLock = Any()
    private var activeBuffer: StringBuilder? = null

    override suspend fun consume(flow: Flow<StreamChunk>): StreamResult {
        // Pastikan sampler sesi sebelumnya tidak lagi berjalan sebelum mengganti buffer.
        stopSampling()

        // Reset buffer baru untuk sesi streaming ini.
        val buffer = StringBuilder()
        synchronized(bufferLock) {
            activeBuffer = buffer
            _streamingContent.value = ""
        }

        // Mulai sampling job.
        startSampling(buffer)

        var receivedDelta = false
        val images = mutableListOf<GeneratedImage>()
        var lastError: AiException? = null

        try {
            flow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        receivedDelta = true
                        synchronized(bufferLock) {
                            buffer.append(chunk.text)
                        }
                    }
                    is StreamChunk.Image -> { images.add(chunk.image); receivedDelta = true }
                    is StreamChunk.Done -> {
                        // Flush final snapshot ke UI tepat sebelum return.
                        flushNow(buffer)
                        return@collect
                    }
                    is StreamChunk.Error -> {
                        lastError = chunk.exception
                        // Berhenti konsumsi lebih lanjut; flow diharapkan selesai.
                        return@collect
                    }
                }
            }
        } finally {
            stopSampling()
        }

        // Final flush terakhir (jika Done tidak sempat flush).
        flushNow(buffer)

        val finalContent = synchronized(bufferLock) {
            buffer.toString().also {
                if (activeBuffer === buffer) {
                    activeBuffer = null
                    _streamingContent.value = ""
                }
            }
        }

        return when {
            lastError != null && finalContent.isEmpty() -> {
                StreamResult.Failed(partialContent = "", exception = lastError!!, images = images)
            }
            lastError != null -> {
                StreamResult.Failed(partialContent = finalContent, exception = lastError!!, images = images)
            }
            receivedDelta -> {
                StreamResult.Success(finalContent, images)
            }
            else -> {
                StreamResult.NoContent
            }
        }
    }

    override fun reset() {
        stopSampling()
        synchronized(bufferLock) {
            activeBuffer = null
            _streamingContent.value = ""
        }
    }

    private fun startSampling(buffer: StringBuilder) {
        samplingJob = samplingScope.launch {
            var lastFlushedLength = 0
            while (true) {
                delay(STREAMING_UI_SAMPLE_MS)
                // Snapshot dan publikasi dilakukan di dalam lock yang sama dengan
                // append(), sehingga tidak ada pembacaan stale atau publish dari sesi lama.
                val shouldContinue = synchronized(bufferLock) {
                    if (activeBuffer !== buffer) {
                        false
                    } else {
                        val snapshot = buffer.toString()
                        if (snapshot.length != lastFlushedLength) {
                            lastFlushedLength = snapshot.length
                            _streamingContent.value = snapshot
                        }
                        true
                    }
                }
                if (!shouldContinue) break
            }
        }
    }

    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    private fun flushNow(buffer: StringBuilder) {
        // Hanya flush jika buffer masih aktif, agar tidak menimpa stream baru.
        synchronized(bufferLock) {
            if (activeBuffer === buffer) {
                _streamingContent.value = buffer.toString()
            }
        }
    }
}
