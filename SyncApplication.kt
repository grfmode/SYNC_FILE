package com.sync.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point aplikasi SYNC.
 * Dikonfigurasi dengan Hilt untuk Dependency Injection (P-014).
 */
@HiltAndroidApp
class SyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inisialisasi awal aplikasi
    }
}