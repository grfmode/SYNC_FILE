package com.sync.app.di

import com.sync.app.data.local.KnowledgeDao
import com.sync.app.domain.knowledge.ChunkerRegistry
import com.sync.app.domain.knowledge.KnowledgeRepository
import com.sync.app.domain.knowledge.KnowledgeStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {

    @Binds
    @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeStoreImpl): KnowledgeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object KnowledgeProviderModule {

    @Provides
    @Singleton
    fun provideChunkerRegistry(): ChunkerRegistry = ChunkerRegistry()
}