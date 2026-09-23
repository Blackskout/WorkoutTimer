# Управление музыкой на экране выполнения — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** вернуть на экран выполнения управление музыкой — полоску мини-плеера Яндекс Музыки с живыми кнопками и раскрывающейся шторкой, где есть обложка, перемотка и лайк.

**Architecture:** пустой `NotificationListenerService` существует только ради `ComponentName`, под которым система пускает к чужим медиа-сессиям. Всю работу ведёт синглтон-репозиторий: слушает `MediaSessionManager`, выбирает сессию `ru.yandex.music`, снимает с неё `MediaSnapshot` и отдаёт наружу `Flow<MusicState>`. UI платформы не видит вовсе.

**Tech Stack:** Kotlin, Compose (BOM 2026.03.00), Hilt, kotlinx.coroutines, платформенные `android.media.session.*`. Новых зависимостей не добавляется.

**Spec:** `docs/superpowers/specs/2026-09-24-music-control-design.md`

## Global Constraints

- minSdk 24, compileSdk/targetSdk 36, Java 17. Ничего, что требует API выше 24, без проверки `Build.VERSION.SDK_INT`.
- Новые зависимости в `app/build.gradle.kts` **не добавляются**. Hilt-инфраструктуры для инструментальных тестов в проекте нет и она не заводится.
- Пакет плеера — строка `"ru.yandex.music"`.
- Весь пользовательский текст и комментарии в коде — на русском, как во всём проекте.
- Имена тестовых методов в `androidTest` — кириллицей через подчёркивания (`готово_без_прокрутки_возвращает_текущие_значения`). Пробелы в бэктиках недопустимы: dex их не принимает.
- Unit-тесты не трогают платформенные объекты: только `MediaSnapshot`, `TrackInfo` и инлайнящиеся константы.
- Инструментальные тесты гоняются только на stateless-composable'ах, без Hilt.
- Команды: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

## Review Focus

Пять условий, которые спека подразумевает, но которые легко не покрыть тестами. Для каждого ниже назначен тест в задаче, владеющей кодом.

1. **Радиопоток без длительности** (`durationMs == 0`): позиция не должна обрезаться нулём, слайдер не должен появляться. Это «Моя волна» — основной сценарий пользователя. → Task 1, Task 5.
2. **Пустые метаданные в момент переключения трека**: название и исполнитель пусты, кнопки обязаны остаться рабочими. → Task 1, Task 4.
3. **Пауза**: `playbackSpeed == 0f`, позиция не должна убегать вперёд. → Task 1.
4. **Очень длинное название трека**: полоска не должна ломать раскладку и выталкивать кнопки за экран. → Task 4.
5. **Команды при отсутствующем контроллере** (музыку убили, а пользователь жмёт): не падать. → Task 3, Task 4.

---

## Файловая структура

Создаются:

| Файл | Ответственность |
|---|---|
| `domain/model/MusicState.kt` | состояния плеера и `TrackInfo` |
| `domain/repository/MusicControlRepository.kt` | контракт для UI |
| `data/MediaSnapshot.kt` | плоский снимок сессии без платформенных типов (кроме `Bitmap`) |
| `data/mapper/MusicStateMapper.kt` | чистые функции: снимок → состояние, расчёт позиции |
| `data/MusicControlRepositoryImpl.kt` | весь платформенный код |
| `presentation/service/MediaAccessService.kt` | пустой listener ради разрешения |
| `presentation/components/music/MusicViewModel.kt` | мост UI ↔ репозиторий |
| `presentation/components/music/MusicBar.kt` | полоска: `MusicBar` (Hilt) и `MusicBarContent` (stateless) |
| `presentation/components/music/MusicSheet.kt` | шторка: `MusicSheet`, `MusicSheetContent`, `rememberMusicSheetVisibility` |

Изменяются: `AndroidManifest.xml`, `di/AppModule.kt`, `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt`.
Удаляется: `presentation/components/SystemMediaController.kt`.

---

## Task 1: Модель и чистый маппер

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/MusicState.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/MediaSnapshot.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/mapper/MusicStateMapper.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/mapper/MusicStateMapperTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `MusicState` (`PermissionRequired`, `NoSession`, `Playing(track)`, `Paused(track)`), `TrackInfo`, `MediaSnapshot`, `fun toMusicState(snapshot: MediaSnapshot?): MusicState`, `fun currentPositionMs(track: TrackInfo, nowElapsedRealtime: Long): Long`.

- [ ] **Step 1: Создать модель**

`domain/model/MusicState.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import android.graphics.Bitmap

/**
 * Состояние чужого плеера, каким его видит экран выполнения.
 * PermissionRequired и NoSession — не ошибки, а обычные рабочие состояния:
 * в первом случае не выдан доступ к уведомлениям, во втором плеер не запущен.
 */
sealed interface MusicState {
    data object PermissionRequired : MusicState
    data object NoSession : MusicState
    data class Playing(val track: TrackInfo) : MusicState
    data class Paused(val track: TrackInfo) : MusicState
}

/**
 * artwork приходит готовым Bitmap из метаданных сессии — это единственное
 * место, где android.graphics попадает в domain. Заворачивать его в обёртку
 * ради чистоты слоя не стоит того.
 *
 * isLiked = null означает «неизвестно»: сердце рисуется контуром, но нажимается.
 */
data class TrackInfo(
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val canSeek: Boolean,
    val canRate: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val isLiked: Boolean?
)
```

`data/MediaSnapshot.kt`:

```kotlin
package ru.hopes.workouttimer.data

import android.graphics.Bitmap

/**
 * Плоский снимок медиа-сессии. Существует затем, чтобы логика разбора
 * (откаты названий, флаги доступных действий) считалась чистой функцией и
 * проверялась обычными unit-тестами: сами MediaMetadata и PlaybackState —
 * final-классы платформы, на JVM их не создать.
 */
data class MediaSnapshot(
    val playbackState: Int,
    val actions: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val title: String?,
    val displayTitle: String?,
    val artist: String?,
    val displaySubtitle: String?,
    val albumArt: Bitmap?,
    val art: Bitmap?,
    val displayIcon: Bitmap?,
    val durationMs: Long,
    val ratingType: Int,
    val isLiked: Boolean?
)
```

- [ ] **Step 2: Написать падающий тест маппера**

`app/src/test/java/ru/hopes/workouttimer/data/mapper/MusicStateMapperTest.kt`:

```kotlin
package ru.hopes.workouttimer.data.mapper

import android.graphics.Bitmap
import android.media.Rating
import android.media.session.PlaybackState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.data.MediaSnapshot
import ru.hopes.workouttimer.domain.model.MusicState

private fun snapshot(
    playbackState: Int = PlaybackState.STATE_PLAYING,
    actions: Long = PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_SET_RATING or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS,
    positionMs: Long = 0L,
    positionUpdatedAt: Long = 0L,
    playbackSpeed: Float = 1f,
    title: String? = "Ne Naprasno",
    displayTitle: String? = null,
    artist: String? = "Soft Blade",
    displaySubtitle: String? = null,
    albumArt: Bitmap? = null,
    art: Bitmap? = null,
    displayIcon: Bitmap? = null,
    durationMs: Long = 195_000L,
    ratingType: Int = Rating.RATING_HEART,
    isLiked: Boolean? = null
) = MediaSnapshot(
    playbackState, actions, positionMs, positionUpdatedAt, playbackSpeed,
    title, displayTitle, artist, displaySubtitle,
    albumArt, art, displayIcon, durationMs, ratingType, isLiked
)

class MusicStateMapperTest {

    @Test
    fun играющая_сессия_даёт_Playing() {
        val state = toMusicState(snapshot(playbackState = PlaybackState.STATE_PLAYING))

        assertTrue(state is MusicState.Playing)
    }

    @Test
    fun пауза_даёт_Paused() {
        val state = toMusicState(snapshot(playbackState = PlaybackState.STATE_PAUSED))

        assertTrue(state is MusicState.Paused)
    }

    /** Буферизация — это «сейчас заиграет», мигать иконкой play/pause незачем. */
    @Test
    fun буферизация_считается_воспроизведением() {
        val state = toMusicState(snapshot(playbackState = PlaybackState.STATE_BUFFERING))

        assertTrue(state is MusicState.Playing)
    }

    @Test
    fun отсутствие_снимка_и_остановка_дают_NoSession() {
        assertEquals(MusicState.NoSession, toMusicState(null))
        assertEquals(MusicState.NoSession, toMusicState(snapshot(playbackState = PlaybackState.STATE_NONE)))
        assertEquals(MusicState.NoSession, toMusicState(snapshot(playbackState = PlaybackState.STATE_STOPPED)))
    }

    /**
     * У Яндекса метаданные пустуют первые миллисекунды после переключения
     * трека. Показать прочерк можно, погасить кнопки — нельзя.
     */
    @Test
    fun пустые_метаданные_дают_прочерки_и_живые_кнопки() {
        val state = toMusicState(snapshot(title = "", artist = null)) as MusicState.Playing

        assertEquals("—", state.track.title)
        assertEquals("—", state.track.artist)
        assertTrue(state.track.canSkipNext)
    }

    @Test
    fun название_и_исполнитель_откатываются_на_display_поля() {
        val state = toMusicState(
            snapshot(title = null, displayTitle = "Из display", artist = "", displaySubtitle = "Тоже из display")
        ) as MusicState.Playing

        assertEquals("Из display", state.track.title)
        assertEquals("Тоже из display", state.track.artist)
    }

    @Test
    fun обложка_берётся_в_порядке_albumArt_art_displayIcon() {
        val albumArt = mockk<Bitmap>()
        val art = mockk<Bitmap>()
        val icon = mockk<Bitmap>()

        val first = toMusicState(snapshot(albumArt = albumArt, art = art, displayIcon = icon)) as MusicState.Playing
        val second = toMusicState(snapshot(albumArt = null, art = art, displayIcon = icon)) as MusicState.Playing
        val third = toMusicState(snapshot(albumArt = null, art = null, displayIcon = icon)) as MusicState.Playing
        val none = toMusicState(snapshot(albumArt = null, art = null, displayIcon = null)) as MusicState.Playing

        assertSame(albumArt, first.track.artwork)
        assertSame(art, second.track.artwork)
        assertSame(icon, third.track.artwork)
        assertNull(none.track.artwork)
    }

    @Test
    fun canSeek_требует_и_флага_и_ненулевой_длительности() {
        val withFlag = toMusicState(snapshot()) as MusicState.Playing
        val noFlag = toMusicState(snapshot(actions = PlaybackState.ACTION_SKIP_TO_NEXT)) as MusicState.Playing
        val noDuration = toMusicState(snapshot(durationMs = 0L)) as MusicState.Playing

        assertTrue(withFlag.track.canSeek)
        assertEquals(false, noFlag.track.canSeek)
        assertEquals(false, noDuration.track.canSeek)
    }

    @Test
    fun canRate_требует_флага_и_сердечного_типа_рейтинга() {
        val heart = toMusicState(snapshot()) as MusicState.Playing
        val thumbs = toMusicState(snapshot(ratingType = Rating.RATING_THUMB_UP_DOWN)) as MusicState.Playing
        val noFlag = toMusicState(snapshot(actions = PlaybackState.ACTION_SEEK_TO)) as MusicState.Playing

        assertTrue(heart.track.canRate)
        assertEquals(false, thumbs.track.canRate)
        assertEquals(false, noFlag.track.canRate)
    }

    @Test
    fun доступность_перемотки_треков_читается_из_маски() {
        val onlyNext = toMusicState(snapshot(actions = PlaybackState.ACTION_SKIP_TO_NEXT)) as MusicState.Playing

        assertTrue(onlyNext.track.canSkipNext)
        assertEquals(false, onlyNext.track.canSkipPrevious)
    }

    @Test
    fun состояние_лайка_пробрасывается_как_есть() {
        assertEquals(true, (toMusicState(snapshot(isLiked = true)) as MusicState.Playing).track.isLiked)
        assertEquals(false, (toMusicState(snapshot(isLiked = false)) as MusicState.Playing).track.isLiked)
        assertNull((toMusicState(snapshot(isLiked = null)) as MusicState.Playing).track.isLiked)
    }

    @Test
    fun отрицательная_длительность_приводится_к_нулю() {
        val state = toMusicState(snapshot(durationMs = -1L)) as MusicState.Playing

        assertEquals(0L, state.track.durationMs)
    }

    @Test
    fun позиция_досчитывается_по_скорости_воспроизведения() {
        val track = (toMusicState(
            snapshot(positionMs = 10_000L, positionUpdatedAt = 1_000L, playbackSpeed = 1f)
        ) as MusicState.Playing).track

        assertEquals(13_000L, currentPositionMs(track, nowElapsedRealtime = 4_000L))
    }

    /** На паузе скорость нулевая: позиция обязана стоять на месте. */
    @Test
    fun на_паузе_позиция_не_убегает() {
        val track = (toMusicState(
            snapshot(
                playbackState = PlaybackState.STATE_PAUSED,
                positionMs = 10_000L, positionUpdatedAt = 1_000L, playbackSpeed = 0f
            )
        ) as MusicState.Paused).track

        assertEquals(10_000L, currentPositionMs(track, nowElapsedRealtime = 60_000L))
    }

    @Test
    fun позиция_не_вылезает_за_длительность() {
        val track = (toMusicState(
            snapshot(positionMs = 190_000L, positionUpdatedAt = 0L, durationMs = 195_000L)
        ) as MusicState.Playing).track

        assertEquals(195_000L, currentPositionMs(track, nowElapsedRealtime = 60_000L))
    }

    /**
     * «Моя волна» — поток без длительности. Обрезка нулём пригвоздила бы
     * позицию к началу, поэтому при durationMs = 0 обрезки быть не должно.
     */
    @Test
    fun у_потока_без_длительности_позиция_не_обрезается() {
        val track = (toMusicState(
            snapshot(positionMs = 10_000L, positionUpdatedAt = 1_000L, durationMs = 0L)
        ) as MusicState.Playing).track

        assertEquals(69_000L, currentPositionMs(track, nowElapsedRealtime = 60_000L))
    }
}
```

- [ ] **Step 2a: Убедиться, что тест не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.mapper.MusicStateMapperTest"`
Expected: ошибка компиляции — `Unresolved reference: toMusicState`.

- [ ] **Step 3: Написать маппер**

`data/mapper/MusicStateMapper.kt`:

```kotlin
package ru.hopes.workouttimer.data.mapper

import android.media.Rating
import android.media.session.PlaybackState
import ru.hopes.workouttimer.data.MediaSnapshot
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

private const val ПРОЧЕРК = "—"

/**
 * Снимок сессии → состояние для UI. Чистая функция: ни одного обращения к
 * платформе, только инлайнящиеся константы, поэтому тестируется на JVM.
 */
fun toMusicState(snapshot: MediaSnapshot?): MusicState {
    if (snapshot == null) return MusicState.NoSession
    if (snapshot.playbackState == PlaybackState.STATE_NONE ||
        snapshot.playbackState == PlaybackState.STATE_STOPPED
    ) {
        return MusicState.NoSession
    }

    val duration = snapshot.durationMs.coerceAtLeast(0L)
    val track = TrackInfo(
        title = snapshot.title.orNull() ?: snapshot.displayTitle.orNull() ?: ПРОЧЕРК,
        artist = snapshot.artist.orNull() ?: snapshot.displaySubtitle.orNull() ?: ПРОЧЕРК,
        artwork = snapshot.albumArt ?: snapshot.art ?: snapshot.displayIcon,
        durationMs = duration,
        positionMs = snapshot.positionMs.coerceAtLeast(0L),
        positionUpdatedAt = snapshot.positionUpdatedAt,
        playbackSpeed = snapshot.playbackSpeed,
        canSeek = snapshot.actions.has(PlaybackState.ACTION_SEEK_TO) && duration > 0L,
        canRate = snapshot.actions.has(PlaybackState.ACTION_SET_RATING) &&
            snapshot.ratingType == Rating.RATING_HEART,
        canSkipNext = snapshot.actions.has(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = snapshot.actions.has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        isLiked = snapshot.isLiked
    )

    // Буферизация считается воспроизведением: музыка вот-вот пойдёт.
    val playing = snapshot.playbackState == PlaybackState.STATE_PLAYING ||
        snapshot.playbackState == PlaybackState.STATE_BUFFERING
    return if (playing) MusicState.Playing(track) else MusicState.Paused(track)
}

/**
 * PlaybackState отдаёт снимок позиции, а не поток, поэтому между
 * обновлениями её досчитываем сами. Обрезка по длительности — только когда
 * длительность известна: у радиопотока она нулевая.
 */
fun currentPositionMs(track: TrackInfo, nowElapsedRealtime: Long): Long {
    val elapsed = (nowElapsedRealtime - track.positionUpdatedAt).coerceAtLeast(0L)
    val projected = (track.positionMs + elapsed * track.playbackSpeed).toLong().coerceAtLeast(0L)
    return if (track.durationMs > 0L) projected.coerceAtMost(track.durationMs) else projected
}

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }

private fun Long.has(action: Long): Boolean = this and action != 0L
```

- [ ] **Step 4: Прогнать тесты**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.mapper.MusicStateMapperTest"`
Expected: `BUILD SUCCESSFUL`, все 15 тестов зелёные.

Если падает на `mockk<Bitmap>()` с ошибкой инлайн-агента на JDK 17 — заменить три битмапа на `mockk<Bitmap>(relaxed = true)`. Если и это не заводится, тест откатов обложки переносится в инструментальные, где `Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)` работает по-настоящему.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model/MusicState.kt \
        app/src/main/java/ru/hopes/workouttimer/data/MediaSnapshot.kt \
        app/src/main/java/ru/hopes/workouttimer/data/mapper/MusicStateMapper.kt \
        app/src/test/java/ru/hopes/workouttimer/data/mapper/MusicStateMapperTest.kt
git commit -m "feat: модель состояния плеера и разбор снимка сессии"
```

---

## Task 2: Сервис-заглушка и манифест

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/service/MediaAccessService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: ничего.
- Produces: класс `MediaAccessService`, пригодный для `ComponentName(context, MediaAccessService::class.java)`.

- [ ] **Step 1: Создать сервис**

```kotlin
package ru.hopes.workouttimer.presentation.service

import android.service.notification.NotificationListenerService

/**
 * Сервис намеренно пустой, и дописывать его не нужно.
 *
 * MediaSessionManager.getActiveSessions() пускает к чужим медиа-сессиям
 * только по ComponentName включённого NotificationListenerService. Этот класс
 * существует ровно ради такого ComponentName: уведомления он не читает и
 * никаких колбэков не переопределяет.
 */
class MediaAccessService : NotificationListenerService()
```

- [ ] **Step 2: Объявить сервис в манифесте и убрать дубль `<queries>`**

В `app/src/main/AndroidManifest.xml` два одинаковых блока `<queries>` с `ru.yandex.music` (строки 11-13 и 15-18) — оставить один, вместе с комментарием `<!--экспериментально-->` убрать второй.

Внутрь `<application>`, рядом с `TimerNotificationService`, добавить:

```xml
        <service
            android:name=".presentation.service.MediaAccessService"
            android:exported="true"
            android:label="Управление музыкой"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

`exported="true"` обязателен: сервис биндит системный процесс. Защищает его то, что `BIND_NOTIFICATION_LISTENER_SERVICE` держит только система.

- [ ] **Step 3: Собрать и поставить на телефон**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Проверить, что система видит сервис**

Run: `~/Library/Android/sdk/platform-tools/adb shell cmd notification allowed_listeners` — или открыть на телефоне «Настройки → Уведомления → Доступ к уведомлениям».
Expected: в списке приложений есть WorkoutTimer с подписью «Управление музыкой». **Включать пока не нужно** — это понадобится в Task 7.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/service/MediaAccessService.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: сервис-заглушка для доступа к медиа-сессиям"
```

---

## Task 3: Репозиторий

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/repository/MusicControlRepository.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/MusicControlRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt`

**Interfaces:**
- Consumes: `MusicState`, `MediaSnapshot`, `toMusicState` (Task 1); `MediaAccessService` (Task 2).
- Produces: интерфейс `MusicControlRepository` с `val state: Flow<MusicState>` и методами `refresh()`, `play()`, `pause()`, `next()`, `previous()`, `seekTo(positionMs: Long)`, `setLiked(liked: Boolean)`, `openPlayer()`, `openPermissionSettings()`.

- [ ] **Step 1: Объявить интерфейс**

```kotlin
package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.MusicState

interface MusicControlRepository {
    val state: Flow<MusicState>

    /**
     * Перепроверить доступ к уведомлениям и пересобрать подписку.
     * Нужен после возврата из системных настроек: Activity при этом не
     * умирает, подписчик не отваливается, и без внешнего толчка состояние
     * PermissionRequired осталось бы навсегда.
     */
    fun refresh()

    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setLiked(liked: Boolean)
    fun openPlayer()
    fun openPermissionSettings()
}
```

- [ ] **Step 2: Написать реализацию**

`data/MusicControlRepositoryImpl.kt`:

```kotlin
package ru.hopes.workouttimer.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import ru.hopes.workouttimer.data.mapper.toMusicState
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.repository.MusicControlRepository
import ru.hopes.workouttimer.presentation.service.MediaAccessService

private const val YANDEX_PACKAGE = "ru.yandex.music"

@OptIn(ExperimentalCoroutinesApi::class)
class MusicControlRepositoryImpl(
    private val context: Context
) : MusicControlRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val component = ComponentName(context, MediaAccessService::class.java)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val refreshTrigger = MutableStateFlow(0)

    // Пишется из главного потока в колбэках, читается оттуда же при командах;
    // видимость между потоками гарантируем явно.
    @Volatile
    private var controller: MediaController? = null

    override val state: Flow<MusicState> = refreshTrigger
        .flatMapLatest { sessionFlow() }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    private fun sessionFlow(): Flow<MusicState> = callbackFlow {
        if (!hasPermission()) {
            trySend(MusicState.PermissionRequired)
            awaitClose { }
            return@callbackFlow
        }

        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(currentState())
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(currentState())
            }

            // Переподключение делает слушатель сессий: система вызовет его
            // следом, и контроллер будет выбран заново.
            override fun onSessionDestroyed() {
                trySend(MusicState.NoSession)
            }
        }

        fun attach(controllers: List<MediaController>) {
            controller?.unregisterCallback(controllerCallback)
            val next = controllers.firstOrNull { it.packageName == YANDEX_PACKAGE }
            controller = next
            // Однопараметрический registerCallback строит Handler на текущем
            // потоке, а тело callbackFlow исполняется там, где Looper может
            // отсутствовать. Поэтому передаём главный явно.
            next?.registerCallback(controllerCallback, mainHandler)
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            attach(list.orEmpty())
            trySend(currentState())
        }

        try {
            // Слушатель срабатывает только на изменение, поэтому текущий
            // список читаем сразу — иначе до переключения трека полоска
            // показывала бы «плеер не запущен».
            attach(sessionManager.getActiveSessions(component))
            trySend(currentState())
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
        } catch (e: SecurityException) {
            // Доступ отозвали на ходу.
            trySend(MusicState.PermissionRequired)
            awaitClose { }
            return@callbackFlow
        }

        awaitClose {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            controller?.unregisterCallback(controllerCallback)
            controller = null
        }
    }

    private fun currentState(): MusicState = toMusicState(controller?.snapshot())

    private fun MediaController.snapshot(): MediaSnapshot? {
        val playback = playbackState ?: return null
        val md = metadata
        return MediaSnapshot(
            playbackState = playback.state,
            actions = playback.actions,
            positionMs = playback.position,
            positionUpdatedAt = playback.lastPositionUpdateTime,
            playbackSpeed = playback.playbackSpeed,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            displayTitle = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            displaySubtitle = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            albumArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            displayIcon = md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            ratingType = ratingType,
            isLiked = md?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
                ?.let { if (it.isRated) it.hasHeart() else null }
        )
    }

    private fun hasPermission(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    // Контроллера нет — команда просто не уходит: в этом состоянии кнопок на
    // экране и нет, но защищаемся и здесь.
    override fun play() { controller?.transportControls?.play() }
    override fun pause() { controller?.transportControls?.pause() }
    override fun next() { controller?.transportControls?.skipToNext() }
    override fun previous() { controller?.transportControls?.skipToPrevious() }
    override fun seekTo(positionMs: Long) { controller?.transportControls?.seekTo(positionMs) }

    override fun setLiked(liked: Boolean) {
        controller?.transportControls?.setRating(Rating.newHeartRating(liked))
    }

    override fun openPlayer() {
        val launch = context.packageManager.getLaunchIntentForPackage(YANDEX_PACKAGE)
        if (launch != null) {
            startSafely(launch)
            return
        }
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$YANDEX_PACKAGE".toUri())
        if (!startSafely(market)) {
            startSafely(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$YANDEX_PACKAGE".toUri()
                )
            )
        }
    }

    override fun openPermissionSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                // Экстра принимает строку, а не объект ComponentName.
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString()
                )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        if (!startSafely(intent)) {
            Toast.makeText(context, "Не нашёл экран доступа к уведомлениям", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Репозиторий держит application-контекст, поэтому каждому интенту нужен
     * FLAG_ACTIVITY_NEW_TASK: без него startActivity бросает
     * AndroidRuntimeException.
     */
    private fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
```

- [ ] **Step 3: Зарегистрировать в Hilt**

В `di/AppModule.kt` добавить импорты `ru.hopes.workouttimer.data.MusicControlRepositoryImpl` и `ru.hopes.workouttimer.domain.repository.MusicControlRepository`, затем:

```kotlin
    @Provides
    @Singleton
    fun provideMusicControlRepository(@ApplicationContext ctx: Context): MusicControlRepository {
        return MusicControlRepositoryImpl(ctx)
    }
```

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Автотестов у этой задачи нет: класс целиком состоит из вызовов платформы, его поведение проверяется руками в Task 7.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/repository/MusicControlRepository.kt \
        app/src/main/java/ru/hopes/workouttimer/data/MusicControlRepositoryImpl.kt \
        app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt
git commit -m "feat: репозиторий управления медиа-сессией Яндекс Музыки"
```

---

## Task 4: ViewModel, полоска и её тесты

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/components/music/MusicViewModel.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/components/music/MusicBar.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/FakeMusicControlRepository.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/MusicBarContentTest.kt`

**Interfaces:**
- Consumes: `MusicState`, `TrackInfo` (Task 1), `MusicControlRepository` (Task 3).
- Produces: `MusicViewModel` с `val state: StateFlow<MusicState>` и методами `refresh()`, `playPause()`, `next()`, `previous()`, `seekTo(Long)`, `setLiked(Boolean)`, `openPlayer()`, `grantPermission()`; composable `MusicBarContent(state, onPlayPause, onNext, onPrevious, onOpenPlayer, onGrantPermission, onExpand, modifier)` — без состояния, Hilt-обёртки в этой задаче нет.

- [ ] **Step 1: Написать ViewModel**

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.repository.MusicControlRepository
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicControlRepository
) : ViewModel() {

    val state: StateFlow<MusicState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicState.NoSession)

    /** Зовётся на ON_RESUME: пользователь мог вернуться из системных настроек. */
    fun refresh() = repository.refresh()

    fun playPause() {
        if (state.value is MusicState.Playing) repository.pause() else repository.play()
    }

    fun next() = repository.next()
    fun previous() = repository.previous()
    fun seekTo(positionMs: Long) = repository.seekTo(positionMs)
    fun setLiked(liked: Boolean) = repository.setLiked(liked)
    fun openPlayer() = repository.openPlayer()
    fun grantPermission() = repository.openPermissionSettings()
}
```

- [ ] **Step 2: Написать полоску**

`presentation/components/music/MusicBar.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

private val BarHeight = 56.dp

/**
 * Полоска без состояния — чтобы инструментальные тесты обходились без Hilt,
 * которого в androidTest этого проекта нет. Hilt-обёртка над ней появится в
 * Task 6 как MusicSection, вместе со шторкой.
 */
@Composable
fun MusicBarContent(
    state: MusicState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    onGrantPermission: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        MusicState.PermissionRequired -> HintRow(
            text = "Разрешить управление музыкой",
            onClick = onGrantPermission,
            modifier = modifier,
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
        )

        MusicState.NoSession -> HintRow(
            text = "Включить музыку",
            onClick = onOpenPlayer,
            modifier = modifier,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.yandex_icon_pain),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
        )

        is MusicState.Playing -> PlayerRow(state.track, true, onPlayPause, onNext, onPrevious, onExpand, modifier)
        is MusicState.Paused -> PlayerRow(state.track, false, onPlayPause, onNext, onPrevious, onExpand, modifier)
    }
}

@Composable
private fun HintRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerRow(
    track: TrackInfo,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onExpand)
                .semantics { contentDescription = "Открыть плеер" }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Artwork(track)
            Text(
                text = "${track.artist} — ${track.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        }
        IconButton(onClick = onPrevious, enabled = track.canSkipPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий трек")
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Играть"
            )
        }
        IconButton(onClick = onNext, enabled = track.canSkipNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Следующий трек")
        }
    }
}

@Composable
private fun Artwork(track: TrackInfo, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val bitmap = track.artwork
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 3: Написать фейк репозитория**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/FakeMusicControlRepository.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.repository.MusicControlRepository

/**
 * Моки не нужны: MusicControlRepository — интерфейс, поверх фейка собирается
 * настоящая MusicViewModel. Заодно считает команды, чтобы тесты могли
 * проверить, что кнопка не отправила лишнего.
 */
class FakeMusicControlRepository(
    initial: MusicState = MusicState.NoSession
) : MusicControlRepository {

    private val flow = MutableStateFlow(initial)

    var playCount = 0; private set
    var pauseCount = 0; private set
    var nextCount = 0; private set
    var previousCount = 0; private set
    var refreshCount = 0; private set
    var lastSeek: Long? = null; private set
    var lastLiked: Boolean? = null; private set
    var openPlayerCount = 0; private set
    var openSettingsCount = 0; private set

    fun emit(state: MusicState) {
        flow.value = state
    }

    override val state: Flow<MusicState> = flow

    override fun refresh() { refreshCount++ }
    override fun play() { playCount++ }
    override fun pause() { pauseCount++ }
    override fun next() { nextCount++ }
    override fun previous() { previousCount++ }
    override fun seekTo(positionMs: Long) { lastSeek = positionMs }
    override fun setLiked(liked: Boolean) { lastLiked = liked }
    override fun openPlayer() { openPlayerCount++ }
    override fun openPermissionSettings() { openSettingsCount++ }
}
```

- [ ] **Step 4: Написать падающие тесты полоски**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/MusicBarContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

private fun track(
    title: String = "Ne Naprasno",
    artist: String = "Soft Blade",
    canSkipNext: Boolean = true,
    canSkipPrevious: Boolean = true
) = TrackInfo(
    title = title,
    artist = artist,
    artwork = null,
    durationMs = 195_000L,
    positionMs = 0L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = true,
    canRate = true,
    canSkipNext = canSkipNext,
    canSkipPrevious = canSkipPrevious,
    isLiked = null
)

@RunWith(AndroidJUnit4::class)
class MusicBarContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: MusicState,
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onOpenPlayer: () -> Unit = {},
        onGrantPermission: () -> Unit = {},
        onExpand: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicBarContent(
                    state = state,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onOpenPlayer = onOpenPlayer,
                    onGrantPermission = onGrantPermission,
                    onExpand = onExpand,
                    modifier = modifier
                )
            }
        }
    }

    @Test
    fun без_доступа_полоска_зовёт_в_настройки() {
        var granted = 0
        setContent(MusicState.PermissionRequired, onGrantPermission = { granted++ })

        composeRule.onNodeWithText("Разрешить управление музыкой").performClick()

        assertEquals(1, granted)
    }

    @Test
    fun без_сессии_полоска_запускает_плеер() {
        var opened = 0
        setContent(MusicState.NoSession, onOpenPlayer = { opened++ })

        composeRule.onNodeWithText("Включить музыку").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun во_время_игры_показана_пауза() {
        setContent(MusicState.Playing(track()))

        composeRule.onNodeWithContentDescription("Пауза").assertIsDisplayed()
    }

    @Test
    fun на_паузе_показана_кнопка_играть() {
        setContent(MusicState.Paused(track()))

        composeRule.onNodeWithContentDescription("Играть").assertIsDisplayed()
    }

    @Test
    fun тап_по_треку_раскрывает_шторку_а_тап_по_кнопке_нет() {
        var expanded = 0
        var nexts = 0
        setContent(MusicState.Playing(track()), onNext = { nexts++ }, onExpand = { expanded++ })

        composeRule.onNodeWithContentDescription("Следующий трек").performClick()
        assertEquals(0, expanded)
        assertEquals(1, nexts)

        composeRule.onNodeWithContentDescription("Открыть плеер").performClick()
        assertEquals(1, expanded)
    }

    /**
     * Яндекс убирает действие из маски, когда оно недоступно. Кнопки при этом
     * не исчезают — иначе раскладка прыгала бы на каждом треке, — а гаснут.
     */
    @Test
    fun недоступная_перемотка_треков_гасит_кнопки() {
        setContent(MusicState.Playing(track(canSkipNext = false, canSkipPrevious = false)))

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Предыдущий трек").assertIsNotEnabled()
    }

    @Test
    fun доступная_перемотка_треков_оставляет_кнопки_живыми() {
        setContent(MusicState.Playing(track()))

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsEnabled()
    }

    /**
     * Длинное название не должно выталкивать кнопки за край: текст ужимается
     * бегущей строкой, а управление остаётся на экране.
     */
    @Test
    fun длинное_название_не_выталкивает_кнопки_за_экран() {
        setContent(
            MusicState.Playing(track(title = "Очень длинное название трека, которое никуда не помещается целиком")),
            modifier = Modifier.width(320.dp)
        )

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Пауза").assertIsDisplayed()
    }

    /** Метаданные пустуют при переключении трека — кнопки обязаны работать. */
    @Test
    fun прочерки_вместо_метаданных_не_ломают_кнопки() {
        var nexts = 0
        setContent(MusicState.Playing(track(title = "—", artist = "—")), onNext = { nexts++ })

        composeRule.onNodeWithContentDescription("Следующий трек").performClick()

        assertEquals(1, nexts)
    }

    /** Пользователь может жать next очередью — падать нельзя. */
    @Test
    fun повторные_тапы_по_кнопке_не_ломают_полоску() {
        var nexts = 0
        setContent(MusicState.Playing(track()), onNext = { nexts++ })

        repeat(5) { composeRule.onNodeWithContentDescription("Следующий трек").performClick() }

        assertEquals(5, nexts)
    }
}
```

- [ ] **Step 5: Прогнать тесты полоски**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.components.music.MusicBarContentTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные. Эмулятор `Pixel_8_Pro` или подключённый телефон — годится любой.

- [ ] **Step 6: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/components/music/ \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/
git commit -m "feat: полоска мини-плеера и её тесты"
```

---

## Task 5: Шторка и её тесты

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/components/music/MusicSheet.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/MusicSheetContentTest.kt`

**Interfaces:**
- Consumes: `TrackInfo`, `MusicState`, `currentPositionMs` (Task 1).
- Produces: `rememberMusicSheetVisibility(isResting: Boolean, state: MusicState): MutableState<Boolean>`; composable `ColumnScope.MusicSheetContent(track, playing, onPlayPause, onNext, onPrevious, onSeek, onLike)` — расширение `ColumnScope`, поэтому в тесте зовётся внутри `Column { }`; composable `MusicSheet(state, onDismiss, …)`.

- [ ] **Step 1: Написать шторку**

`presentation/components/music/MusicSheet.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.hopes.workouttimer.data.mapper.currentPositionMs
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding

/**
 * Видимость шторки. Закрывается сама в двух случаях: кончился отдых (иначе
 * после звонка на экране была бы обложка вместо «пора работать») и состояние
 * перестало быть Playing/Paused — показывать тогда нечего.
 *
 * isResting — именно Boolean, а не состояние экрана: WorkoutExecutionState.Rest
 * пересоздаётся copy() на каждом тике таймера, и эффект на нём перезапускался
 * бы раз в секунду, закрывая шторку сразу после открытия.
 */
@Composable
fun rememberMusicSheetVisibility(isResting: Boolean, state: MusicState): MutableState<Boolean> {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(isResting) {
        if (!isResting) visible.value = false
    }

    val hasTrack = state is MusicState.Playing || state is MusicState.Paused
    LaunchedEffect(hasTrack) {
        if (!hasTrack) visible.value = false
    }

    return visible
}

@Composable
fun MusicSheet(
    track: TrackInfo,
    playing: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: (Boolean) -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        MusicSheetContent(
            track = track,
            playing = playing,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onLike = onLike
        )
    }
}

/**
 * Извлечено из MusicSheet по той же причине, что и ActionSheetContent:
 * ModalBottomSheet требует Window и не поднимается ни в @Preview, ни в тесте.
 */
@Composable
fun ColumnScope.MusicSheetContent(
    track: TrackInfo,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 8.dp)
            .size(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = track.artwork
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(220.dp)
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
        }
    }

    Text(
        text = track.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = ScreenPadding, end = ScreenPadding)
    )
    Text(
        text = track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = ScreenPadding, end = ScreenPadding)
    )

    if (track.canSeek) {
        SeekRow(track = track, playing = playing, onSeek = onSeek)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = track.canSkipPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий трек", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Играть",
                modifier = Modifier.size(44.dp)
            )
        }
        IconButton(onClick = onNext, enabled = track.canSkipNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Следующий трек", modifier = Modifier.size(36.dp))
        }
        if (track.canRate) {
            IconButton(onClick = { onLike(track.isLiked != true) }) {
                Icon(
                    imageVector = if (track.isLiked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isLiked == true) "Убрать лайк" else "Лайк",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun SeekRow(
    track: TrackInfo,
    playing: Boolean,
    onSeek: (Long) -> Unit
) {
    // Пока палец на слайдере, входящие обновления игнорируются: иначе
    // ползунок дёргался бы назад на каждом обновлении позиции.
    var dragging by remember { mutableStateOf(false) }
    var draggedMs by remember { mutableStateOf(0f) }
    var tickedMs by remember(track.positionMs, track.positionUpdatedAt) {
        mutableStateOf(track.positionMs.toFloat())
    }

    // Тик нужен только пока играет: на паузе скорость нулевая.
    LaunchedEffect(track.positionUpdatedAt, playing) {
        while (playing) {
            tickedMs = currentPositionMs(track, android.os.SystemClock.elapsedRealtime()).toFloat()
            delay(1_000)
        }
    }

    val shown = if (dragging) draggedMs else tickedMs

    Slider(
        value = shown.coerceIn(0f, track.durationMs.toFloat()),
        onValueChange = {
            dragging = true
            draggedMs = it
        },
        onValueChangeFinished = {
            dragging = false
            onSeek(draggedMs.toLong())
        },
        valueRange = 0f..track.durationMs.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = ScreenPadding, end = ScreenPadding)
            .semantics { contentDescription = "Перемотка" }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatMs(shown.toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatMs(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
```

- [ ] **Step 2: Написать падающие тесты шторки**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/MusicSheetContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

private fun track(
    canSeek: Boolean = true,
    canRate: Boolean = true,
    isLiked: Boolean? = null,
    durationMs: Long = 195_000L
) = TrackInfo(
    title = "Ne Naprasno",
    artist = "Soft Blade",
    artwork = null,
    durationMs = durationMs,
    positionMs = 42_000L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = canSeek,
    canRate = canRate,
    canSkipNext = true,
    canSkipPrevious = true,
    isLiked = isLiked
)

@RunWith(AndroidJUnit4::class)
class MusicSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        trackInfo: TrackInfo,
        playing: Boolean = true,
        onLike: (Boolean) -> Unit = {}
    ) {
        composeRule.setContent {
            WorkoutTimerTheme {
                Column {
                    MusicSheetContent(
                        track = trackInfo,
                        playing = playing,
                        onPlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                        onLike = onLike
                    )
                }
            }
        }
    }

    @Test
    fun шторка_показывает_трек_и_исполнителя() {
        setContent(track())

        composeRule.onNodeWithText("Ne Naprasno").assertIsDisplayed()
        composeRule.onNodeWithText("Soft Blade").assertIsDisplayed()
    }

    @Test
    fun слайдер_есть_когда_перемотка_доступна() {
        setContent(track(canSeek = true))

        composeRule.onNodeWithContentDescription("Перемотка").assertIsDisplayed()
    }

    /** Радиопоток без длительности: слайдера быть не должно. */
    @Test
    fun слайдера_нет_когда_перемотка_недоступна() {
        setContent(track(canSeek = false, durationMs = 0L))

        composeRule.onNodeWithContentDescription("Перемотка").assertDoesNotExist()
    }

    @Test
    fun сердечка_нет_когда_рейтинг_недоступен() {
        setContent(track(canRate = false))

        composeRule.onNodeWithContentDescription("Лайк").assertDoesNotExist()
    }

    @Test
    fun лайкнутый_трек_показывает_закрашенное_сердце() {
        setContent(track(isLiked = true))

        composeRule.onNodeWithContentDescription("Убрать лайк").assertIsDisplayed()
    }

    @Test
    fun неизвестное_состояние_лайка_показывает_контур_и_ставит_лайк() {
        var liked: Boolean? = null
        setContent(track(isLiked = null), onLike = { liked = it })

        composeRule.onNodeWithContentDescription("Лайк").performClick()

        assertEquals(true, liked)
    }

    @Test
    fun конец_отдыха_закрывает_шторку_а_начало_нет() {
        var resting by mutableStateOf(false)
        var visibleNow = false

        composeRule.setContent {
            val visible = rememberMusicSheetVisibility(isResting = resting, state = MusicState.Playing(track()))
            LaunchedEffect(Unit) { visible.value = true }
            visibleNow = visible.value
        }

        composeRule.runOnIdle { resting = true }
        composeRule.runOnIdle { assertEquals(true, visibleNow) }

        composeRule.runOnIdle { resting = false }
        composeRule.runOnIdle { assertEquals(false, visibleNow) }
    }

    @Test
    fun пропавшая_сессия_закрывает_шторку() {
        var state by mutableStateOf<MusicState>(MusicState.Playing(track()))
        var visibleNow = false

        composeRule.setContent {
            val visible = rememberMusicSheetVisibility(isResting = true, state = state)
            LaunchedEffect(Unit) { visible.value = true }
            visibleNow = visible.value
        }

        composeRule.runOnIdle { state = MusicState.NoSession }
        composeRule.runOnIdle { assertEquals(false, visibleNow) }
    }
}
```

`assertDoesNotExist()` — метод на `SemanticsNodeInteraction` из уже импортированного пакета `androidx.compose.ui.test`, отдельного импорта не требует.

- [ ] **Step 3: Прогнать тесты шторки**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.components.music.MusicSheetContentTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 4: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/components/music/MusicSheet.kt \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/components/music/MusicSheetContentTest.kt
git commit -m "feat: шторка плеера с перемоткой и лайком"
```

---

## Task 6: Встраивание в экран выполнения

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt:48,189-220`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/components/music/MusicBar.kt`
- Delete: `app/src/main/java/ru/hopes/workouttimer/presentation/components/SystemMediaController.kt`

**Interfaces:**
- Consumes: `MusicBar` (Task 4), `MusicSheet`, `rememberMusicSheetVisibility` (Task 5), `MusicViewModel` (Task 4).
- Produces: `MusicSection(isResting: Boolean, modifier: Modifier = Modifier)` — единственное, что экран выполнения знает о музыке.

- [ ] **Step 1: Собрать секцию в `MusicBar.kt`**

Дописать в конец `presentation/components/music/MusicBar.kt`:

```kotlin
/**
 * Полоска вместе со шторкой. Экран выполнения знает только про неё и
 * передаёт одну вещь — идёт ли сейчас отдых.
 */
@Composable
fun MusicSection(
    isResting: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val expanded = rememberMusicSheetVisibility(isResting = isResting, state = state)
    val lifecycleOwner = LocalLifecycleOwner.current

    // Разрешение выдаётся в системных настройках, Activity при этом не
    // умирает, поэтому проверяем его заново при каждом возврате на экран.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MusicBarContent(
        state = state,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onOpenPlayer = viewModel::openPlayer,
        onGrantPermission = viewModel::grantPermission,
        onExpand = { expanded.value = true },
        modifier = modifier
    )

    val current = state
    if (expanded.value && (current is MusicState.Playing || current is MusicState.Paused)) {
        val track = when (current) {
            is MusicState.Playing -> current.track
            is MusicState.Paused -> current.track
            else -> null
        }
        if (track != null) {
            MusicSheet(
                track = track,
                playing = current is MusicState.Playing,
                onDismiss = { expanded.value = false },
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeek = viewModel::seekTo,
                onLike = viewModel::setLiked
            )
        }
    }
}
```

Добавить импорты: `androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.platform.LocalLifecycleOwner`, `androidx.hilt.navigation.compose.hiltViewModel`, `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.LifecycleEventObserver`.

`collectAsStateWithLifecycle` использовать нельзя: он живёт в `lifecycle-runtime-compose`, которого в проекте нет, а новые зависимости запрещены. Весь проект пользуется обычным `collectAsState` — так же и здесь.

- [ ] **Step 2: Встроить в экран**

В `WorkoutExecutionScreen.kt` заменить импорт `ru.hopes.workouttimer.presentation.components.YandexMusicButton` (строка 48) на `ru.hopes.workouttimer.presentation.components.music.MusicSection`.

Блок на строках 189-220 переписать так: полоска — **отдельная строка над** существующим `Row`, потому что во всю ширину в него не помещается; из самого `Row` уходит `YandexMusicButton()`, и кнопка занимает всю ширину.

```kotlin
            if (currentState is WorkoutExecutionState.Active ||
                currentState is WorkoutExecutionState.Rest
            ) {
                MusicSection(
                    isResting = currentState is WorkoutExecutionState.Rest,
                    modifier = Modifier.padding(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        bottom = 10.dp
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentState is WorkoutExecutionState.Active) {
                        PrimaryButton(
                            text = "Закончить подход",
                            onClick = {
                                if (viewModel.isLastSetOfWorkout) {
                                    showFinishDialog = true
                                } else {
                                    viewModel.onExerciseFinished()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = "Пропустить отдых",
                            onClick = { viewModel.skipRest() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
```

Если `Arrangement` перестал использоваться в файле после удаления `horizontalArrangement = Arrangement.spacedBy(10.dp)` — убрать и его импорт.

- [ ] **Step 3: Удалить старый компонент**

```bash
git rm app/src/main/java/ru/hopes/workouttimer/presentation/components/SystemMediaController.kt
```

- [ ] **Step 4: Проверить, что ссылок не осталось**

Run: `grep -rn "YandexMusicButton" app/src`
Expected: пустой вывод.

- [ ] **Step 5: Собрать и прогнать все тесты**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL` — включая прежние тесты проекта, которые не должны сломаться.

- [ ] **Step 6: Коммит**

```bash
git add -A app/src/main/java/ru/hopes/workouttimer/
git commit -m "feat: мини-плеер встроен в экран выполнения"
```

---

## Task 7: Проверка на живом плеере

Автотесты не покрывают ничего из того, что связано с настоящей сессией Яндекса. Эта задача — единственное место, где проверяется, что фича вообще работает. Эмулятор не годится: нужен залогиненный аккаунт.

**Files:** изменений нет, кроме возможных правок по итогам.

- [ ] **Step 1: Поставить сборку на телефон**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Проверить состояние без доступа**

Доступ к уведомлениям ещё не выдан. Открыть любую тренировку.
Expected: внизу полоска «Разрешить управление музыкой».

- [ ] **Step 3: Проверить выдачу доступа и возврат**

Нажать полоску → откроется системный экран (на Android 11+ — сразу карточка WorkoutTimer) → включить доступ → вернуться кнопкой «назад».
Expected: **без перезапуска приложения** полоска сменилась: если Яндекс не играет — «Включить музыку», если играет — плеер с обложкой. Это проверка `refresh()`; она не покрыта автотестами и ломалась бы молча.

- [ ] **Step 4: Проверить живое управление**

Включить музыку. Прогнать: play/pause, next, previous.
Expected: иконка центральной кнопки меняется следом за настоящим состоянием; название и обложка обновляются при смене трека; задержки нет.

- [ ] **Step 5: Проверить шторку**

Тапнуть по названию трека.
Expected: шторка с обложкой 220dp, слайдером и сердечком. Перемотка работает, после отпускания трек играет с новой позиции.

- [ ] **Step 6: Проверить лайк и выяснить судьбу `METADATA_KEY_USER_RATING`**

Нажать сердечко.
Expected: трек появился в «Мне нравится» в Яндекс Музыке.

Затем посмотреть, отражается ли состояние: переоткрыть шторку на уже лайкнутом треке.

- Сердце закрашено → `METADATA_KEY_USER_RATING` приходит, всё работает.
- Сердце контурное → Яндекс рейтинг не отдаёт, `isLiked` всегда `null`. **Это допустимо и переделки не требует.** Если состояние окажется нужным, запасной путь — читать подпись кастомного действия (`'Нравится Не выбрано'` / `'Нравится Выбрано'`) через `playbackState.customActions`; решать это отдельно, в план не входит.

Заодно снять дамп для протокола:

Run: `~/Library/Android/sdk/platform-tools/adb shell dumpsys media_session | grep -A 3 "ru.yandex.music"`

- [ ] **Step 7: Проверить автозакрытие шторки**

Дождаться отдыха, открыть шторку и дождаться конца отдыха, не закрывая её.
Expected: шторка закрылась сама, на экране снова тренировка.

- [ ] **Step 8: Проверить смерть и воскрешение сессии**

Смахнуть Яндекс Музыку из недавних приложений.
Expected: полоска стала «Включить музыку». Запустить музыку снова — полоска подхватила новую сессию без перезапуска WorkoutTimer.

- [ ] **Step 9: Проверить отзыв доступа на ходу**

В системных настройках отключить доступ к уведомлениям, вернуться в приложение.
Expected: полоска вернулась к «Разрешить управление музыкой», приложение не упало.

- [ ] **Step 10: Зафиксировать результат**

Если всё прошло — записать в коммит-сообщение факт проверки. Если что-то из шагов 3-9 не сработало, это правится в рамках этой же задачи, с тестом там, где поведение поддаётся автотесту.

```bash
git commit --allow-empty -m "test: ручная проверка плеера на живой сессии Яндекс Музыки"
```
