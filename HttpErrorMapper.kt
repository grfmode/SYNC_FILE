package com.sync.app.data.network

import com.sync.app.domain.error.AiException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Pemetaan status HTTP dan exception ke [AiException].
 *
 * Body error provider TIDAK pernah dimasukkan ke log. Body hanya dipakai
 * sebagai bagian dari message exception untuk ditampilkan ke UI.
 */
object HttpErrorMapper {

    fun mapStatus(code: Int, body: String?): AiException = when (code) {
        401, 403 -> AiException.Unauthorized()
        429 -> AiException.RateLimited()
        in 400..499 -> AiException.ProviderError(
            code,
            body?.takeIf { it.isNotBlank() } ?: "Client error (HTTP $code)"
        )
        in 500..599 -> AiException.ProviderError(
            code,
            body?.takeIf { it.isNotBlank() } ?: "Provider error (HTTP $code)"
        )
        else -> AiException.ProviderError(code, "Unexpected status $code")
    }

    fun mapThrowable(t: Throwable): AiException = when (t) {
        is AiException -> t
        is SocketTimeoutException -> AiException.NetworkTimeout(t)
        is SerializationException -> AiException.InvalidResponse("Failed to parse response", t)
        is IOException -> AiException.NetworkUnavailable(t)
        else -> AiException.Unknown(t)
    }
}