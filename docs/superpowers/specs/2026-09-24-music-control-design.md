# Управление музыкой на экране выполнения — дизайн

**Дата:** 2026-09-24
**Ветка:** `feature/music-control`

## Проблема

До редизайна на экране выполнения были кнопки prev / play-pause / next. Они слали `KeyEvent` через `AudioManager.dispatchMediaKeyEvent()` — одностороннюю отправку без обратной связи. Отсюда два порока: команда срабатывала с задержкой, а иконка play/pause никогда не меняла вид, потому что приложение не знало, играет музыка или нет. Задача 15 редизайна (`docs/superpowers/plans/2026-09-22-app-redesign.md`) удалила эти кнопки вместе с `MediaButtonManager`, оставив одну кнопку запуска Яндекс Музыки (`SystemMediaController.kt:23`).

Сейчас, чтобы переключить трек посреди подхода, нужно уходить в шторку уведомлений.

## Цель

Мини-плеер на экране выполнения: видно, что играет, видно, играет ли вообще, и управление работает без ухода из приложения. Полоска внизу всегда на месте, по тапу раскрывается шторка с большой обложкой, перемоткой и лайком.

Управляем только Яндекс Музыкой (`ru.yandex.music`).

## Вне рамок

- Громкость, очередь воспроизведения, дизлайк.
- Управление с виджета и из уведомления таймера.
- Другие плееры. Выбор сессии по пакету зашит; расширение до «любого активного плеера» — отдельное решение, не техническая доработка.
- Экран настроек, где можно было бы отключить плеер. Появится вместе со спекой настроек.

## Что выяснено на устройстве

`adb shell dumpsys media_session` на живом треке (24.09.2026, телефон V2515, Яндекс Музыка играет «Мою волну»):

```
androidx.media3.session.id. ru.yandex.music/androidx.media3.session.id./257
  package=ru.yandex.music
  active=true
  rating type=1
  state=PlaybackState {state=PLAYING(3), position=11885, speed=1.0,
                       updated=569768443, actions=524283,
                       custom actions=['Не нравится Не выбрано', 'Нравится Не выбрано']}
  metadata: size=18, description=Ne Naprasno, Soft Blade
  queueTitle=Моя волна
```

Отсюда три факта, на которых стоит весь дизайн:

1. **`rating type=1` — это `RATING_HEART`,** и в маске действий есть `ACTION_SET_RATING` (бит 7). Значит лайк ставится документированным `transportControls.setRating(Rating.newHeartRating(true))`, а не отправкой кастомного действия с недокументированным строковым идентификатором.
2. **`ACTION_SEEK_TO` (бит 8) объявлен** — причём на «Моей волне», то есть перемотка доступна и на радио.
3. **В маске отсутствует `ACTION_PLAY` (бит 2, значение 4)**, хотя `ACTION_PAUSE` есть: Яндекс держит список действий актуальным текущему состоянию. Поэтому **иконка центральной кнопки выбирается по `state`, а не по маске действий.**

Яндекс работает через Media3, но публикует обычную платформенную сессию, поэтому мы ходим к ней через `android.media.session.MediaSessionManager` и новых зависимостей не добавляем.

## Архитектура

### Доступ к чужим медиа-сессиям

`MediaSessionManager.getActiveSessions()` требует передать `ComponentName` включённого `NotificationListenerService`. Другого пути к чужой сессии нет.

Поэтому в приложении появляется **`MediaAccessService : NotificationListenerService` с пустым телом.** Он не читает уведомления и ничего не делает — он существует ради `ComponentName`. В файле это должно быть написано комментарием, иначе следующий читатель решит, что класс забыли дописать.

Манифест:

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

`exported="true"` обязателен — сервис биндит системный процесс. Защищён он тем, что `BIND_NOTIFICATION_LISTENER_SERVICE` держит только система.

Разрешение диалогом не запрашивается. Проверка — `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`; выдача — переход в системные настройки:

- API 30+: `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` с `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` — открывает сразу карточку нашего приложения.
- Ниже: `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` — общий список.

Заодно чистится дубль: блок `<queries>` с `ru.yandex.music` записан в манифесте дважды.

### Где живёт логика

`MusicControlRepositoryImpl` — синглтон в `AppModule`, весь платформенный код заперт в нём. UI и ViewModel видят только `Flow<MusicState>` и шесть команд.

Поток строится как `callbackFlow { … }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)`. Подписка на систему живёт, только пока экран выполнения открыт; пятисекундный хвост переживает поворот экрана и возврат из системных настроек.

Внутри `callbackFlow`:

1. Нет разрешения → эмитим `PermissionRequired` и больше ничего не делаем.
2. Есть → `addOnActiveSessionsChangedListener(listener, componentName, Handler(Looper.getMainLooper()))`, из списка берём контроллер с `packageName == "ru.yandex.music"`.
3. На найденный контроллер вешаем `MediaController.Callback`: `onMetadataChanged`, `onPlaybackStateChanged`, `onSessionDestroyed`.
4. `awaitClose` снимает все три подписки.

Альтернатива — держать логику в самом `NotificationListenerService` — отвергнута: временем жизни сервиса управляет система, связь с UI потребовала бы моста или binder'а, а подписка висела бы всё время работы приложения. Единственное её преимущество, точный сигнал `onListenerConnected()`, заменяется перепроверкой разрешения при `ON_RESUME`.

## Часть A: Модель и слой данных

### `domain/model/MusicState.kt`

```kotlin
sealed interface MusicState {
    data object PermissionRequired : MusicState
    data object NoSession : MusicState
    data class Playing(val track: TrackInfo) : MusicState
    data class Paused(val track: TrackInfo) : MusicState
}

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
    val isLiked: Boolean?
)
```

`artwork` — `Bitmap?` прямо из метаданных, загрузчик картинок не нужен. `isLiked` нулевой, когда состояние лайка неизвестно: сердце рисуется контуром и остаётся нажимаемым.

### `domain/repository/MusicControlRepository.kt`

```kotlin
interface MusicControlRepository {
    val state: Flow<MusicState>
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

Интерфейс в домене нужен ровно затем же, зачем он у `WorkoutRepository`: в `androidTest` рядом с `FakeWorkoutRepository` кладётся `FakeMusicControlRepository`, и Compose-тесты гоняют настоящую `MusicViewModel` поверх него. Моки не нужны.

`openPlayer()` переносит без изменений нынешнюю логику `YandexMusicButton`: launch intent → `market://` → страница Play Store в браузере.

### `data/mapper/MusicStateMapper.kt`

Чистая функция, единственный кусок фичи, покрываемый обычными unit-тестами:

```kotlin
fun toMusicState(
    metadata: MediaMetadata?,
    playbackState: PlaybackState?,
    ratingType: Int
): MusicState
```

Правила:

- `playbackState == null` или `state` в `STATE_NONE`/`STATE_STOPPED` → `NoSession`.
- `state == STATE_PLAYING` → `Playing`, иначе `Paused`. `STATE_BUFFERING` считается `Playing`: музыка вот-вот пойдёт, мигать иконкой не нужно.
- Название: `METADATA_KEY_TITLE`, откат на `METADATA_KEY_DISPLAY_TITLE`. Исполнитель: `METADATA_KEY_ARTIST`, откат на `METADATA_KEY_DISPLAY_SUBTITLE`. Пусто → прочерк `—`, кнопки остаются рабочими (у Яндекса метаданные пустуют первые миллисекунды после переключения трека).
- Обложка: `METADATA_KEY_ALBUM_ART` → `METADATA_KEY_ART` → `METADATA_KEY_DISPLAY_ICON` → `null`.
- `durationMs` — `METADATA_KEY_DURATION`, отрицательное или отсутствующее приводится к `0`.
- `canSeek` — бит `ACTION_SEEK_TO` в `playbackState.actions` **и** `durationMs > 0`.
- `canRate` — бит `ACTION_SET_RATING` **и** `ratingType == Rating.RATING_HEART`.
- `isLiked` — `METADATA_KEY_USER_RATING`: `Rating.hasHeart()`, если рейтинг есть и `isRated`; иначе `null`.

Отдельная чистая функция для позиции:

```kotlin
fun currentPositionMs(track: TrackInfo, nowElapsedRealtime: Long): Long
```

Считает `positionMs + (now - positionUpdatedAt) * playbackSpeed`, обрезая по `durationMs`. `PlaybackState` отдаёт снимок, а не поток, поэтому между обновлениями позицию досчитываем сами.

### `data/MusicControlRepositoryImpl.kt`

Помимо описанного выше потока:

- Команды идут в `controller.transportControls`. Контроллера нет → ничего не делаем: в этом состоянии кнопок на экране и нет.
- `setLiked(liked)` → `setRating(Rating.newHeartRating(liked))`.
- `getActiveSessions()` бросает `SecurityException`, если доступ отозвали на ходу, — ловим и эмитим `PermissionRequired`.

### Биндинг

В `AppModule` — `@Provides @Singleton fun provideMusicControlRepository(@ApplicationContext ctx: Context): MusicControlRepository`.

## Часть B: UI

### Расположение файлов

`WorkoutExecutionScreen.kt` уже 635 строк, `WorkoutExecutionViewModel.kt` — 492. Музыка не имеет отношения ни к таймеру, ни к подходам, поэтому получает свои файлы в `presentation/components/music/`: `MusicViewModel.kt`, `MusicBar.kt`, `MusicSheet.kt`. Экран выполнения вставляет в нижний `Row` одну строку вместо нынешнего `YandexMusicButton()`.

`MusicViewModel` (`@HiltViewModel`) зависит только от `MusicControlRepository`, отдаёт `state` наружу и пробрасывает команды. Разрешение перепроверяется при `ON_RESUME`: вернулся из настроек — полоска сама превращается в плеер, кнопки «я разрешил» не нужно.

### Полоска `MusicBar`

Высота 56dp, встаёт над строкой с «Закончить подход» / «Пропустить отдых», отступы — по `ScreenPadding`. Четыре вида:

| Состояние | Вид | Тап |
|---|---|---|
| `PermissionRequired` | иконка уведомлений, «Разрешить управление музыкой» | системные настройки |
| `NoSession` | иконка Яндекса (`R.drawable.yandex_icon_pain`), «Включить музыку» | запуск плеера |
| `Playing` / `Paused` | обложка 40dp (скругление 8dp), «Исполнитель — Трек» бегущей строкой (`basicMarquee`), справа `⏮ ⏯ ⏭` | по области трека — раскрыть шторку |

Иконка центральной кнопки следует `state` сессии, а не маске действий (см. «Что выяснено на устройстве»). Тапы по кнопкам шторку не открывают.

Обложки нет → плейсхолдер-нота на `surfaceVariant`.

### Шторка `MusicSheet`

Поверх существующего `AppBottomSheet` (`ui/components/Sheets.kt`) — фон, скругления, скролл и затемнение достаются даром, вид согласован с остальными шторками приложения.

Сверху вниз: обложка 220dp по центру, название, исполнитель, слайдер с таймингами `00:42 / 03:15`, ряд кнопок `⏮ ⏯ ⏭` крупнее, чем в полоске, и сердечко лайка.

- Слайдер рисуется только при `canSeek`, сердечко — только при `canRate`. Действие пропало из маски — элемент исчезает, а не молчит в ответ на нажатие. Это тот же порок, из-за которого удаляли старые кнопки.
- Пока палец на слайдере, входящие обновления позиции игнорируются, иначе ползунок дёргается назад. `seekTo` уходит один раз, на отпускании.
- Позиция тикает раз в секунду внутри шторки и перестаёт тикать вместе с её закрытием.
- Сердце закрашено при `isLiked == true`, контурное при `false` и при `null`.

**Автозакрытие.** Отдых может кончиться, пока шторка открыта: звук и вибрация сработают, но на экране будет обложка. Тогда шторка закрывается сама, и экран возвращается к тренировке.

Состоянием шторки владеет `MusicBar`, а не экран выполнения, — иначе флаг пришлось бы тащить в и без того плотный `WorkoutExecutionScreen`. Сигнал о смене фазы приходит параметром:

```kotlin
@Composable
fun MusicBar(
    collapseOn: Any?,          // экран передаёт текущую фазу тренировки
    modifier: Modifier = Modifier
)
```

Внутри — `LaunchedEffect(collapseOn) { expanded = false }`. Фаза сменилась — шторка закрылась. Срабатывание при первой композиции безвредно: шторка и так закрыта. Благодаря этому автозакрытие проверяется тестом в `components/music/`, без подъёма всего экрана выполнения.

Дублировать таймер отдыха внутри шторки не нужно — её открывают на несколько секунд.

## Краевые случаи

| Случай | Поведение |
|---|---|
| Доступ отозвали, пока приложение открыто | `SecurityException` пойман, состояние → `PermissionRequired` |
| Системного экрана доступа к уведомлениям нет (урезанная прошивка) | `ActivityNotFoundException` пойман, короткий тост |
| Яндекс Музыка не установлена | `openPlayer()` ведёт в Play Store, затем в браузер — как сейчас |
| Метаданные пусты при переключении трека | прочерки вместо названия, кнопки работают |
| Сессия уничтожена (`onSessionDestroyed`) | `NoSession` |
| Длительность неизвестна | `canSeek = false`, слайдера нет |
| Несколько сессий Яндекса | берётся первая активная |
| Экран выполнения закрыт | подписка снимается через 5 секунд |

## Тестирование

**Unit** (`app/src/test/.../data/mapper/MusicStateMapperTest.kt`).

`MediaMetadata`, `PlaybackState` и `Rating` — платформенные классы, на JVM их не создать. Мокаются через `mockk` (уже в зависимостях), что для `final`-классов работает; `testOptions.unitTests.isReturnDefaultValues = true` в `app/build.gradle.kts:45-47` тоже уже включён. Состояние лайка задаётся как `mockk<Rating>` с `isRated` и `hasHeart()`. Собственных обращений к платформе у самой функции нет — она только читает переданные объекты, поэтому Robolectric не нужен.

Случаи:

- `STATE_PLAYING` → `Playing`, `STATE_PAUSED` → `Paused`, `STATE_BUFFERING` → `Playing`, `null`/`STATE_STOPPED` → `NoSession`
- пустые метаданные → прочерки, кнопки доступны
- `canSeek` истинно только при `ACTION_SEEK_TO` и `durationMs > 0`
- `canRate` истинно только при `ACTION_SET_RATING` и `RATING_HEART`
- `isLiked` = `true` / `false` / `null` по `METADATA_KEY_USER_RATING`
- `currentPositionMs` — досчёт по скорости, обрезка по длительности, нулевая скорость на паузе

**Инструментальные** (`app/src/androidTest/.../components/music/`), поверх нового `FakeMusicControlRepository` и настоящей `MusicViewModel`. Имена методов через подчёркивания — пробелы в dex недопустимы:

- четыре вида полоски
- тап по области трека открывает шторку, тап по `⏭` — нет
- слайдер отсутствует при `canSeek = false`, сердечко — при `canRate = false`
- переход `Rest → Active` закрывает шторку

Гоняются как `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

**Руками на телефоне** (эмулятор не годится — нужен залогиненный Яндекс):

- выдача доступа в системных настройках и возврат: полоска превращается в плеер без перезапуска
- переключение треков в самом Яндексе: полоска следует за ним
- **проверить `METADATA_KEY_USER_RATING`:** приходит ли он в метаданных Яндекса. Если нет, `isLiked` всегда `null` — сердце остаётся контурным, лайк при этом ставится. Запасной путь, если состояние окажется важным: Яндекс пишет его в подпись кастомного действия (`'Нравится Не выбрано'`), откуда его можно прочитать. Решение принимается по факту проверки, в спеку не закладывается.
- лайк действительно добавляет трек в «Мне нравится»

## Затронутые файлы

Новые:

- `domain/model/MusicState.kt`
- `domain/repository/MusicControlRepository.kt`
- `data/MusicControlRepositoryImpl.kt`
- `data/mapper/MusicStateMapper.kt`
- `presentation/service/MediaAccessService.kt`
- `presentation/components/music/MusicViewModel.kt`
- `presentation/components/music/MusicBar.kt`
- `presentation/components/music/MusicSheet.kt`
- `app/src/test/.../data/mapper/MusicStateMapperTest.kt`
- `app/src/androidTest/.../components/music/FakeMusicControlRepository.kt`
- `app/src/androidTest/.../components/music/MusicBarTest.kt`
- `app/src/androidTest/.../components/music/MusicSheetTest.kt`

Изменяются:

- `AndroidManifest.xml` — сервис, чистка дубля `<queries>`
- `di/AppModule.kt` — биндинг репозитория
- `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` — `MusicBar` вместо `YandexMusicButton`, в него передаётся текущая фаза как `collapseOn`

Удаляется:

- `presentation/components/SystemMediaController.kt` — логика запуска плеера переезжает в `MusicControlRepositoryImpl.openPlayer()`, кнопка — в `MusicBar`
