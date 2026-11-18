package com.sync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ContextLogDao {

    @Insert
    suspend fun insertAll(entries: List<ContextLogEntity>)

    @Query("SELECT * FROM context_log WHERE messageId = :messageId ORDER BY id ASC")
    suspend fun getForMessage(messageId: Long): List<ContextLogEntity>

    @Query("SELECT COALESCE(SUM(tokens), 0) FROM context_log WHERE messageId = :messageId")
    suspend fun totalTokens(messageId: Long): Int

    @Query("DELETE FROM context_log WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)

    @Transaction
    suspend fun replaceForMessage(messageId: Long, entries: List<ContextLogEntity>) {
        deleteForMessage(messageId)
        if (entries.isNotEmpty()) insertAll(entries)
    }
}