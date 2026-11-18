package com.sync.app.domain.backup

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penjaga status pemulihan yang dibaca seluruh aplikasi.
 *
 * `safeMode` hanya `true` bila pemulihan gagal berulang. Selama aktif,
 * penulisan ke database utama ditahan dan Lab menjadi satu-satunya jalan masuk.
 */
@Singleton
class RestoreRecovery @Inject constructor(
    private val machine: RestoreStateMachine
) {

    @Volatile
    var safeMode: Boolean = false
        private set

    @Volatile
    var lastMessage: String? = null
        private set

    suspend fun recoverOnStart() {
        val state = machine.recover()
        safeMode = state.step == RestoreStep.SAFE_MODE
        lastMessage = state.message
    }

    suspend fun restore(snapshotPath: String) {
        val state = machine.restore(snapshotPath)
        safeMode = state.step == RestoreStep.SAFE_MODE
        lastMessage = state.message
    }

    fun acknowledge() {
        machine.clearState()
        safeMode = false
        lastMessage = null
    }
}