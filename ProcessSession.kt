package com.sync.app.di

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penanda state yang hidup selama satu proses aplikasi.
 *
 * Dipakai untuk membedakan cold start (proses mati → force close / swipe from
 * recent / OS kill) dari warm restart (proses masih hidup). Setelah proses
 * dibunuh, instance ini ikut hilang sehingga flag kembali ke `false`.
 */
@Singleton
class ProcessSession @Inject constructor() {

    /**
     * `true` jika [com.sync.app.ui.screens.chat.ChatViewModel] sudah pernah
     * diinisialisasi di proses ini. Digunakan untuk memutuskan apakah
     * percakapan terakhir perlu dimuat ulang saat `init`.
     */
    var isChatInitialized: Boolean = false
}