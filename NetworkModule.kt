package com.sync.app.data.network

import dagger.Module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provider HTTP client dan Json singleton untuk seluruh adapter provider.
 *
 * Timeout diset eksplisit agar perilaku konsisten antar provider.
 * Retry otomatis hanya pada level koneksi (bukan HTTP status) sesuai default OkHttp.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // AI dapat membutuhkan waktu lebih lama untuk memproses prompt/lampiran besar.
            .readTimeout(180, TimeUnit.SECONDS)
            // JSON dengan lampiran dapat besar; beri waktu upload yang wajar.
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * HTTP client khusus streaming OpenAI-compatible (coroutine-native).
     *
     * Timeout diatur lewat plugin [HttpTimeout], bukan properti engine CIO:
     * nilai 0 pada properti engine tidak terdokumentasi jelas dan default
     * requestTimeout CIO dapat memutus respons yang panjang. Plugin ini
     * didukung resmi oleh seluruh engine.
     */
    @Provides
    @Singleton
    fun provideStreamingHttpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            // Total durasi satu respons tidak dibatasi (Long.MAX_VALUE = INFINITE_TIMEOUT_MS).
            requestTimeoutMillis = Long.MAX_VALUE
            // Jeda maksimum antar byte. Model reasoning bisa diam lama sebelum token
            // pertama, jadi 5 menit; tetap terdeteksi jika koneksi benar-benar macet.
            socketTimeoutMillis = 300_000
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}