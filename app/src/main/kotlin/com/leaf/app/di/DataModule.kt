package com.leaf.app.di

import android.content.Context
import androidx.room.Room
import com.leaf.data.db.LeafDatabase
import com.leaf.data.files.FileStore
import com.leaf.data.repo.NotebookRepositoryImpl
import com.leaf.data.texture.TexturePipeline
import com.leaf.domain.repo.NotebookRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Composition root (docs/02-ARCHITECTURE.md §6): the only place that sees
 * :data's implementations; everything downstream injects domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LeafDatabase =
        Room.databaseBuilder(context, LeafDatabase::class.java, "leaf.db").build()

    @Provides
    @Singleton
    fun fileStore(@ApplicationContext context: Context): FileStore =
        FileStore(File(context.filesDir, "store"))

    @Provides
    @Singleton
    fun texturePipeline(store: FileStore): TexturePipeline = TexturePipeline(store)

    @Provides
    @Singleton
    fun notebookRepository(
        database: LeafDatabase,
        store: FileStore,
        pipeline: TexturePipeline,
    ): NotebookRepository =
        NotebookRepositoryImpl(database.notebooks(), store, pipeline, Dispatchers.IO)
}
