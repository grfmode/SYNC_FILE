package com.sync.app.data.repository

import com.sync.app.domain.memory.MemoryRepository
import com.sync.app.domain.memory.MemoryStoreImpl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Titik sambung tipis ke [MemoryStoreImpl].
 *
 * Delegasi penuh; tidak ada logika di sini agar aturan penulisan (dedup,
 * plafon, penyamaran rahasia) tetap satu tempat di lapisan domain.
 */
@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val store: MemoryStoreImpl
) : MemoryRepository by store