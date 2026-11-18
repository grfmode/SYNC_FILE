package com.sync.app.di

import android.content.Context
import com.sync.app.domain.backup.BackupDirectory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideBackupDirectory(@ApplicationContext context: Context): BackupDirectory =
        BackupDirectory(context)
}