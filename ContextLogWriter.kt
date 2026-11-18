package com.sync.app.domain.context

import com.sync.app.data.local.ContextLogDao
import com.sync.app.data.local.ContextLogEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penulis catatan konteks.
 *
 * Hanya menulis; pembacaan dilakukan ViewModel lewat DAO. Kegagalan menulis
 * tidak pernah menggagalkan request.
 */
@Singleton
class ContextLogWriter @Inject constructor(
    private val dao: ContextLogDao
) {

    suspend fun write(messageId: Long, mode: ContextMode, entries: List<ContextLogEntry>) {
        if (messageId <= 0 || entries.isEmpty()) return
        val entities: List<ContextLogEntity> = entries.map { it.toEntity(messageId, mode) }
        runCatching { dao.replaceForMessage(messageId, entities) }
    }

    suspend fun read(messageId: Long): List<ContextLogEntity> =
        runCatching { dao.getForMessage(messageId) }.getOrDefault(emptyList())

    suspend fun total(messageId: Long): Int =
        runCatching { dao.totalTokens(messageId) }.getOrDefault(0)
}