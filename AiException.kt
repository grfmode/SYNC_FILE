package com.sync.app.domain.error

/**
 * Error ter-normalisasi untuk seluruh interaksi AI.
 *
 * Adapter provider wajib memetakan error provider ke salah satu tipe ini
 * agar UI dapat menampilkan pesan yang konsisten (BLUEPRINT §13, RULES §5).
 *
 * Tidak ada pesan error yang membocorkan API key atau header autentikasi.
 */
sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 401/403 — kredensial tidak valid atau tidak berhak. */
    class Unauthorized(message: String = "Unauthorized: check API key") : AiException(message)

    /** 429 — rate limit terlampaui. */
    class RateLimited(message: String = "Rate limit exceeded") : AiException(message)

    /** Status 4xx/5xx lain dari provider. */
    class ProviderError(val statusCode: Int, message: String) : AiException(message)

    /** Koneksi timeout (connect atau read). */
    class NetworkTimeout(cause: Throwable) : AiException("Network timeout", cause)

    /** Kegagalan jaringan lain (DNS, tidak terhubung, TLS, dll.). */
    class NetworkUnavailable(cause: Throwable) : AiException("Network unavailable", cause)

    /** Response provider tidak dapat diparse. */
    class InvalidResponse(message: String, cause: Throwable? = null) : AiException(message, cause)

    /** Image-only model gagal pada endpoint image; jangan pindah provider otomatis. */
    class ImageGenerationFailed(message: String, cause: Throwable? = null) : AiException(message, cause)

    /** Fallback. */
    class Unknown(cause: Throwable? = null) : AiException(cause?.message ?: "Unknown error", cause)
}