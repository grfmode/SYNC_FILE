package com.sync.app.di

import android.content.Context
import androidx.room.Room
import com.sync.app.data.chat.AttachmentManagerImpl
import com.sync.app.data.local.ChatDao
import com.sync.app.data.local.ContextLogDao
import com.sync.app.data.local.KnowledgeDao
import com.sync.app.data.local.LibraryDao
import com.sync.app.data.local.Migrations
import com.sync.app.data.local.SyncDatabase
import com.sync.app.data.repository.ChatRepositoryImpl
import com.sync.app.data.repository.LibraryRepositoryImpl
import com.sync.app.domain.apikey.ApiKeyStore
import com.sync.app.domain.apikey.ApiKeyStoreImpl
import com.sync.app.domain.chat.AttachmentManager
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.domain.chat.StreamManager
import com.sync.app.domain.chat.StreamManagerImpl
import com.sync.app.domain.library.LibraryRepository
import com.sync.app.domain.provider.RemoteProviderRepository
import com.sync.app.domain.provider.RemoteProviderRepositoryImpl
import com.sync.app.domain.settings.RemotePromptRepository
import com.sync.app.domain.settings.RemotePromptRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: ApiKeyStoreImpl): ApiKeyStore

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindRemotePromptRepository(
        impl: RemotePromptRepositoryImpl
    ): RemotePromptRepository

    @Binds
    @Singleton
    abstract fun bindRemoteProviderRepository(
        impl: RemoteProviderRepositoryImpl
    ): RemoteProviderRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentManager(impl: AttachmentManagerImpl): AttachmentManager

    @Binds
    @Singleton
    abstract fun bindStreamManager(impl: StreamManagerImpl): StreamManager

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Daftar migrasi lengkap dan berurutan. Versi 1 kini punya jalur naik
     * sehingga database lama tidak lagi memicu pembuatan ulang.
     */
    @Provides
    @Singleton
    fun provideSyncDatabase(@ApplicationContext context: Context): SyncDatabase =
        Room.databaseBuilder(
            context,
            SyncDatabase::class.java,
            SyncDatabase.NAME
        )
        .addMigrations(
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
            Migrations.MIGRATION_3_4,
            Migrations.MIGRATION_4_5,
            Migrations.MIGRATION_5_6,
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_9_10
        )
        .build()

    @Provides
    fun provideChatDao(database: SyncDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideLibraryDao(database: SyncDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideKnowledgeDao(database: SyncDatabase): KnowledgeDao = database.knowledgeDao()

    @Provides
    fun provideContextLogDao(database: SyncDatabase): ContextLogDao = database.contextLogDao()
}