package ru.hopes.workouttimer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import ru.hopes.workouttimer.data.ExportImportRepositoryImpl
import ru.hopes.workouttimer.data.WorkoutRepositoryImpl
import ru.hopes.workouttimer.data.dao.AppDatabase
import ru.hopes.workouttimer.data.dao.MIGRATION_5_6
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase {
        return Room.databaseBuilder(
            ctx,
            AppDatabase::class.java,
            "workout_db"
        )
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Singleton
    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    @Singleton
    fun provideWorkoutRepository(dao: WorkoutDao): WorkoutRepository {
        return WorkoutRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideExportImportRepository(
        @ApplicationContext ctx: Context,
        dao: WorkoutDao
    ): ExportImportRepository {
        return ExportImportRepositoryImpl(ctx, dao)
    }
}