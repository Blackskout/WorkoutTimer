package ru.hopes.workouttimer.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Область корутин на всё время жизни процесса. Сейчас в ней живёт идущая тренировка. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    private const val TAG = "ApplicationScope"

    /**
     * Main.immediate — состояние сессии меняется только на главном потоке, как раньше во
     * viewModelScope: поля менеджера без синхронизации рассчитаны на это. SupervisorJob —
     * сбой одной корутины (записи заметки) не отменяет таймер отдыха. Обработчик — без него
     * непойманное исключение дочерней корутины уронило бы процесс.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Сбой корутины в области процесса", e)
        }
    )
}
