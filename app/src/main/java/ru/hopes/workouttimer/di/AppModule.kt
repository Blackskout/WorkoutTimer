package ru.hopes.workouttimer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import ru.hopes.workouttimer.data.ExerciseCatalogRepositoryImpl
import ru.hopes.workouttimer.data.ExportImportRepositoryImpl
import ru.hopes.workouttimer.data.MusicControlRepositoryImpl
import ru.hopes.workouttimer.data.WorkoutRepositoryImpl
import ru.hopes.workouttimer.data.dao.ALL_MIGRATIONS
import ru.hopes.workouttimer.data.dao.AppDatabase
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.MusicControlRepository
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.presentation.widget.GlanceWidgetUpdater

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
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            // Стирать базу можно только с довыпускных версий 2 и 4: миграций с
            // них нет и не будет. С 5-й и выше недостающая миграция, как и откат
            // на старую сборку, роняет приложение, а не молча удаляет историю
            // тренировок.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 4)
            .build()
    }

    @Singleton
    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    @Singleton
    fun provideWidgetUpdater(@ApplicationContext ctx: Context): WidgetUpdater {
        return GlanceWidgetUpdater(ctx)
    }

    @Provides
    @Singleton
    fun provideWorkoutRepository(
        dao: WorkoutDao,
        widgetUpdater: WidgetUpdater
    ): WorkoutRepository {
        return WorkoutRepositoryImpl(dao, widgetUpdater)
    }

    @Provides
    @Singleton
    fun provideExerciseCatalogRepository(dao: WorkoutDao): ExerciseCatalogRepository {
        return ExerciseCatalogRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideMusicControlRepository(@ApplicationContext ctx: Context): MusicControlRepository {
        return MusicControlRepositoryImpl(ctx)
    }

    @Provides
    @Singleton
    fun provideExportImportRepository(
        @ApplicationContext ctx: Context,
        dao: WorkoutDao,
        widgetUpdater: WidgetUpdater
    ): ExportImportRepository {
        return ExportImportRepositoryImpl(ctx, dao, widgetUpdater)
    }
}