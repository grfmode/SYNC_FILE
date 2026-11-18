package com.sync.app.di

import com.sync.app.data.local.MemoryDao
import com.sync.app.data.repository.MemoryRepositoryImpl
import com.sync.app.domain.memory.MemoryRepository
import com.sync.app.domain.memory.MemoryStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object MemoryDaoModule {

    @Provides
    fun provideMemoryDao(database: com.sync.app.data.local.SyncDatabase): MemoryDao = database.memoryDao()
}