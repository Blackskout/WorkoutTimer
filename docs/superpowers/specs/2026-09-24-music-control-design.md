# Управление музыкой на экране выполнения — дизайн

**Дата:** 2026-09-24
**Ветка:** `feature/music-control`

## Проблема

До редизайна на экране выполнения были кнопки prev / play-pause / next. Они слали `KeyEvent` через `AudioManager.dispatchMediaKeyEvent()` — одностороннюю отправку без обратной связи. Отсюда два порока: команда срабатывала с задержкой, а иконка play/pause никогда не меняла вид, потому что приложение не знало, играет музыка или нет. Задача 15 редизайна (`docs/superpowers/plans/2026-09-22-app-redesign.md:2636`) удалила эти кнопки вместе с `MediaButtonManager`, оставив одну кнопку запуска Яндекс Музыки (`SystemMediaController.kt:23`).

Сейчас, чтобы переключить трек посреди подхода, нужно уходить в шторку уведомлений.

## Цель

Мини-плеер на экране выполнения: видно, что играет, видно, играет ли вообще, и управление работает без ухода из приложения. Полоска присутствует всё время, пока идёт подход или отдых; по тапу раскрывается шторка с большой обложкой, перемоткой и лайком.

На экранах загрузки, ошибки и завершения полоски нет — там она бессмысленна, а нижний блок экрана в этих состояниях вообще не рисуется (`WorkoutExecutionScreen.kt:189-192`).

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

1. **`rating type=1` — это `RATING_HEART`,** и в маске действий есть `ACTION_SET_RATING` (бит 7, значение 128). Значит лайк ставится документированным `transportControls.setRating(Rating.newHeartRating(true))`, а не отправкой кастомного действия с недокументированным строковым идентификатором.
2. **`ACTION_SEEK_TO` (бит 8, значение 256) объявлен** — причём на «Моей волне», то есть перемотка доступна и на радио.
3. **В маске отсутствует `ACTION_PLAY` (бит 2, значение 4)**, хотя `ACTION_PAUSE` есть: `524283` — это биты 0–18, кроме второго. Яндекс держит список действий актуальным текущему состоянию, поэтому **иконка центральной кнопки выбирается по `state`, а не по маске действий**, а доступность остальных кнопок — наоборот, по маске.

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

Разрешение диалогом не запрашивается. Проверка — `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`. Метод отвечает про пакет целиком, а не про конкретный компонент; листенер у нас один, так что разницы нет.

Выдача — переход в системные настройки:

- API 30+: `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`, открывает сразу карточку нашего сервиса. Компонент кладётся в экстру **строкой**: `putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())`. Передача самого объекта `ComponentName` молча ни к чему не приводит.
- Ниже: `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` — общий список.

Заодно чистится дубль: блок `<queries>` с `ru.yandex.music` записан в манифесте дважды (`AndroidManifest.xml:11-13` и `:15-18`).

### Где живёт логика

`MusicControlRepositoryImpl` — синглтон в `AppModule`, весь платформенный код заперт в нём. UI и ViewModel видят только `Flow<MusicState>` и восемь команд.

Альтернатива — держать логику в самом `NotificationListenerService` — отвергнута: временем жизни сервиса управляет система, связь с UI потребовала бы моста или binder'а, а подписка висела бы всё время работы приложения.

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
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val isLiked: Boolean?
)
```

`artwork` — `Bitmap?` прямо из метаданных, загрузчик картинок не нужен. Это единственное место, где `android.graphics` попадает в `domain/`, который в остальном свободен от платформы; сознательная уступка — заворачивать битмап в обёртку ради чистоты слоя не стоит того.

`isLiked` нулевой, когда состояние лайка неизвестно: сердце рисуется контуром и остаётся нажимаемым.

### `domain/repository/MusicControlRepository.kt`

```kotlin
interface MusicControlRepository {
    val state: Flow<MusicState>
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

Интерфейс в домене нужен ровно затем же, зачем он у `WorkoutRepository`: в `androidTest` появляется `FakeMusicControlRepository`, и Compose-тесты гоняют настоящую `MusicViewModel` поверх него. Моки не нужны. Общего пакета для фейков в проекте нет — существующий `FakeWorkoutRepository` лежит рядом со своими тестами (`androidTest/.../screen/creation/`), новый ляжет рядом со своими (`androidTest/.../components/music/`).

`refresh()` существует ради одного сценария — возврата из системных настроек, см. ниже.

### `data/MediaSnapshot.kt` и `data/mapper/MusicStateMapper.kt`

Чтение платформенных объектов отделено от логики, иначе unit-тесты потребовали бы моков `final`-классов `MediaMetadata`, `PlaybackState` и `Rating`. `mockk` это умеет, но в проекте им мокают только интерфейсы (`WorkoutDao`, `WidgetUpdater`), а инлайн-агент на JDK 17 самоподключается с предупреждениями. Дешевле разрезать надвое:

```kotlin
data class MediaSnapshot(
    val playbackState: Int,          // PlaybackState.STATE_*
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

Снимок собирает тонкая функция-адаптер в `MusicControlRepositoryImpl` — она только читает поля контроллера и ничего не решает. Вся логика живёт в чистой функции, которая тестируется обычными unit-тестами без единого мока:

```kotlin
fun toMusicState(snapshot: MediaSnapshot?): MusicState
```

Правила:

- `snapshot == null` или `playbackState` в `STATE_NONE` / `STATE_STOPPED` → `NoSession`.
- `STATE_PLAYING` → `Playing`, иначе `Paused`. `STATE_BUFFERING` считается `Playing`: музыка вот-вот пойдёт, мигать иконкой не нужно.
- Название: `title`, откат на `displayTitle`. Исполнитель: `artist`, откат на `displaySubtitle`. Пусто → прочерк `—`, кнопки остаются рабочими (у Яндекса метаданные пустуют первые миллисекунды после переключения трека).
- Обложка: `albumArt` → `art` → `displayIcon` → `null`.
- `durationMs` — отрицательное или отсутствующее приводится к `0`.
- `canSeek` — бит `ACTION_SEEK_TO` **и** `durationMs > 0`.
- `canRate` — бит `ACTION_SET_RATING` **и** `ratingType == Rating.RATING_HEART`.
- `canSkipNext` / `canSkipPrevious` — биты `ACTION_SKIP_TO_NEXT` / `ACTION_SKIP_TO_PREVIOUS`.

Ключи метаданных (`METADATA_KEY_TITLE`, `METADATA_KEY_USER_RATING` и прочие) читает адаптер; в чистую функцию приходят уже готовые значения. `isLiked` адаптер берёт из `METADATA_KEY_USER_RATING`: `hasHeart()`, если рейтинг есть и `isRated`, иначе `null`.

Отдельная чистая функция для позиции:

```kotlin
fun currentPositionMs(track: TrackInfo, nowElapsedRealtime: Long): Long
```

Считает `positionMs + (now - positionUpdatedAt) * playbackSpeed`. **Обрезка по `durationMs` — только когда `durationMs > 0`:** у потокового радио длительность нулевая, и безусловный кламп пригвоздил бы позицию к нулю. `lastPositionUpdateTime` у `PlaybackState` — в шкале `elapsedRealtime`, её и передаём.

### `data/MusicControlRepositoryImpl.kt`

Поток строится так:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private val refreshTrigger = MutableStateFlow(0)

override val state: Flow<MusicState> = refreshTrigger
    .flatMapLatest { sessionFlow() }
    .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
```

`refreshTrigger` — не украшение, а единственный способ пережить возврат из системных настроек. Activity при этом не уничтожается, подписчик не отваливается, `WhileSubscribed` ничего не перезапускает, и без внешнего толчка полоска навсегда осталась бы в состоянии `PermissionRequired`. `MusicViewModel` дёргает `refresh()` на `ON_RESUME`, инкремент триггера пересоздаёт `sessionFlow()`, и разрешение проверяется заново.

Внутри `sessionFlow()` — `callbackFlow`:

1. Нет разрешения → `send(PermissionRequired)` и `awaitClose {}`, больше ничего не делаем.
2. Есть → **сразу** `getActiveSessions(componentName)` и выбор контроллера. Без этого первого чтения listener сработал бы только на следующей смене сессии, и до переключения трека полоска показывала бы `NoSession`.
3. `addOnActiveSessionsChangedListener(listener, componentName, mainHandler)` — на каждом срабатывании контроллер **выбирается заново**: старый колбэк снимается, новый вешается. Иначе перезапуск Яндекса (убили и открыли снова) навсегда оставил бы полоску мёртвой.
4. На выбранный контроллер вешается `MediaController.Callback` — обязательно через перегрузку `registerCallback(callback, Handler(Looper.getMainLooper()))`. Однопараметрический вариант строит `Handler` на текущем потоке, а тело `callbackFlow` исполняется на диспетчере сборщика, где `Looper` может отсутствовать — будет исключение.
5. `awaitClose` снимает обе подписки: слушатель сессий и колбэк контроллера.

Первое значение поток отдаёт сразу — либо `PermissionRequired`, либо результат первого `getActiveSessions`, — поэтому у UI нет «пустого» состояния до первой эмиссии.

Прочее:

- Ссылка на текущий контроллер помечается `@Volatile`: пишется из главного потока в колбэках, читается оттуда же при командах, но межпоточная видимость должна быть гарантирована явно.
- Команды идут в `controller.transportControls`. Контроллера нет → ничего не делаем: в этом состоянии кнопок на экране и нет.
- `setLiked(liked)` → `setRating(Rating.newHeartRating(liked))`.
- `getActiveSessions()` бросает `SecurityException`, если доступ отозвали на ходу, — ловим и эмитим `PermissionRequired`.
- `openPlayer()` повторяет нынешнюю логику `YandexMusicButton` (launch intent → `market://` → страница Play Store в браузере) **с одной обязательной поправкой:** репозиторий держит application-контекст, поэтому каждому интенту, включая переход в настройки, нужен `FLAG_ACTIVITY_NEW_TASK`. Без него `startActivity` бросит `AndroidRuntimeException` — нынешний код обходится без флага только потому, что берёт контекст из `LocalContext`, то есть Activity.

### Биндинг

В `AppModule` — `@Provides @Singleton fun provideMusicControlRepository(@ApplicationContext ctx: Context): MusicControlRepository`.

## Часть B: UI

### Расположение файлов

`WorkoutExecutionScreen.kt` уже 635 строк, `WorkoutExecutionViewModel.kt` — 492. Музыка не имеет отношения ни к таймеру, ни к подходам, поэтому получает свои файлы в `presentation/components/music/`: `MusicViewModel.kt`, `MusicBar.kt`, `MusicSheet.kt`.

`MusicViewModel` (`@HiltViewModel`) зависит только от `MusicControlRepository`, отдаёт `state` наружу, пробрасывает команды и вызывает `refresh()` на `ON_RESUME`.

### Разделение на «с состоянием» и «без состояния»

Hilt-инфраструктуры для инструментальных тестов в проекте нет: ни `hilt-android-testing`, ни `HiltTestApplication`, раннер обычный `AndroidJUnitRunner`. Поэтому каждый composable существует в двух видах, как уже сделано для `WeightRepsSheet`:

```kotlin
@Composable
fun MusicBar(                       // подключён к Hilt, зовётся из экрана
    isResting: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel()
)

@Composable
fun MusicBarContent(                // без состояния, зовётся из тестов
    state: MusicState,
    isResting: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    onGrantPermission: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: (Boolean) -> Unit,
    modifier: Modifier = Modifier
)
```

Так же `MusicSheet` / `MusicSheetContent`. Отдельная причина вынести содержимое шторки: `ModalBottomSheet` требует собственного окна и в Compose-тесте не поднимается — об этом прямо сказано в `Sheets.kt:84-86`, и `WeightRepsSheetContentTest` устроен именно так.

### Где полоска стоит на экране

Нижний блок экрана (`WorkoutExecutionScreen.kt:192-218`) — это `Row` с `padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp)`, внутри `YandexMusicButton()` и `PrimaryButton` с `weight(1f)`.

Полоска **не помещается в этот `Row`** — она во всю ширину. Она встаёт отдельной строкой **над** ним, в той же `Column`, со своими отступами `start`/`end` по `ScreenPadding` и `bottom = 10.dp`. Из самого `Row` уходит `YandexMusicButton()`, и `PrimaryButton` занимает всю ширину. Условие `if (currentState is Active || currentState is Rest)` охватывает обе строки — вне этих фаз нижнего блока нет вовсе.

### Полоска `MusicBar`

Высота 56dp. Четыре вида:

| Состояние | Вид | Тап по строке |
|---|---|---|
| `PermissionRequired` | иконка уведомлений, «Разрешить управление музыкой» | системные настройки |
| `NoSession` | иконка Яндекса (`R.drawable.yandex_icon_pain`), «Включить музыку» | запуск плеера |
| `Playing` | обложка 40dp (скругление 8dp), «Исполнитель — Трек» бегущей строкой (`basicMarquee`), справа `⏮ ⏸ ⏭` | раскрыть шторку |
| `Paused` | то же, но центральная кнопка `▶` | раскрыть шторку |

Иконка центральной кнопки следует `state` сессии, а не маске действий. Тапы по кнопкам шторку не открывают.

`⏮` и `⏭` при отсутствующих `canSkipPrevious` / `canSkipNext` **не исчезают, а становятся неактивными** (`enabled = false`). Для трёх постоянных кнопок исчезновение хуже: раскладка прыгала бы на каждом переключении трека. Это отличается от правила для слайдера и сердечка в шторке — те появляются и пропадают целиком, потому что необязательны.

Обложки нет → плейсхолдер-нота на `surfaceVariant`.

### Шторка `MusicSheet`

Поверх существующего `AppBottomSheet` (`ui/components/Sheets.kt:38`) — фон, скругления, скролл и затемнение достаются даром, вид согласован с остальными шторками приложения.

Сверху вниз: обложка 220dp по центру, название, исполнитель, слайдер с таймингами `00:42 / 03:15`, ряд управления.

Ряд устроен как в самой Яндекс Музыке: `⏮`, крупная круглая кнопка play/pause акцентного цвета (68dp, как `PrimaryButton` на экране) и `⏭` — строго по центру экрана, а сердечко лайка отдельно у правого края. Четвёртой кнопкой в общем ряду сердечко уводило бы центр группы влево от обложки.

- Слайдер рисуется только при `canSeek`, сердечко — только при `canRate`. Действие пропало из маски — элемент исчезает, а не молчит в ответ на нажатие. Это тот же порок, из-за которого удаляли старые кнопки.
- Пока палец на слайдере, входящие обновления позиции игнорируются, иначе ползунок дёргается назад. `seekTo` уходит один раз, на отпускании.
- Позиция пересчитывается раз в секунду, пока шторка открыта **и** состояние — `Playing`. На паузе тик не нужен: скорость нулевая, позиция не меняется.
- Сердце закрашено при `isLiked == true`, контурное при `false` и при `null`.

**Шторка закрывается сама в двух случаях:**

1. Состояние перестало быть `Playing`/`Paused` — сессия умерла или отозвали доступ. Показывать нечего: `TrackInfo` больше нет.
2. Кончился отдых. Звук и вибрация сработают, но на экране была бы обложка вместо «пора работать».

Второй случай приходит параметром `isResting: Boolean`, а закрытие делает `LaunchedEffect(isResting) { if (!isResting) expanded = false }` — то есть только на переходе «отдых → подход». Обратный переход шторку не трогает.

**Тип параметра важен.** Передавать в него `currentState` нельзя: `WorkoutExecutionState.Rest` — data class, который пересоздаётся `copy()` на каждом тике таймера (`WorkoutExecutionViewModel.kt:206`), и эффект перезапускался бы раз в секунду, закрывая шторку сразу после открытия. Ключ должен быть именно `Boolean`.

Состоянием раскрытия владеет `MusicBar`, а не экран выполнения: иначе флаг пришлось бы тащить в и без того плотный `WorkoutExecutionScreen`. Повторно шторка сама не открывается.

## Краевые случаи

| Случай | Поведение |
|---|---|
| Доступ отозвали, пока приложение открыто | `SecurityException` пойман, состояние → `PermissionRequired`, шторка закрывается |
| Вернулись из системных настроек, выдав доступ | `ON_RESUME` → `refresh()` → поток пересобран, полоска становится плеером |
| Системного экрана доступа к уведомлениям нет (урезанная прошивка) | `ActivityNotFoundException` пойман, короткий тост |
| Яндекс Музыка не установлена | `openPlayer()` ведёт в Play Store, затем в браузер — как сейчас |
| Метаданные пусты при переключении трека | прочерки вместо названия, кнопки работают |
| Сессия уничтожена (`onSessionDestroyed`) | `NoSession`; новая сессия подхватывается через `onActiveSessionsChanged` |
| Яндекс перезапущен | контроллер выбирается заново на каждом `onActiveSessionsChanged` |
| Длительность неизвестна (радио) | `canSeek = false`, слайдера нет, позиция не обрезается |
| Несколько сессий Яндекса | берётся первая: `getActiveSessions()` отдаёт их в порядке приоритета |
| Экран выполнения закрыт | подписка снимается через 5 секунд |

## Тестирование

**Unit** (`app/src/test/.../data/mapper/MusicStateMapperTest.kt`). Обе функции принимают `MediaSnapshot` и `TrackInfo` — обычные data class, так что моки почти не нужны. Платформенные константы (`RATING_HEART`, `ACTION_*`, `STATE_*`) компилятор инлайнит, поэтому ни одна заглушка из `android.jar` не исполняется. Единственное исключение — проверка откатов обложки: нужны три различимых объекта `Bitmap`, а создать его на JVM нельзя, поэтому там берутся `mockk<Bitmap>()`. Методы на них не вызываются, сравниваются только ссылки.

- `STATE_PLAYING` → `Playing`, `STATE_PAUSED` → `Paused`, `STATE_BUFFERING` → `Playing`, `null` / `STATE_STOPPED` → `NoSession`
- пустые название и исполнитель → прочерки, кнопки доступны
- откаты: `displayTitle` вместо `title`, `displaySubtitle` вместо `artist`, `art` и `displayIcon` вместо `albumArt`
- `canSeek` истинно только при `ACTION_SEEK_TO` и `durationMs > 0`
- `canRate` истинно только при `ACTION_SET_RATING` и `RATING_HEART`
- `canSkipNext` / `canSkipPrevious` по своим битам
- `isLiked` = `true` / `false` / `null`
- `currentPositionMs`: досчёт по скорости; обрезка по длительности; **отсутствие обрезки при `durationMs == 0`**; нулевая скорость на паузе

**Инструментальные** (`app/src/androidTest/.../components/music/`), поверх stateless-composable'ов и нового `FakeMusicControlRepository`. Имена методов через подчёркивания — пробелы в dex недопустимы:

- `MusicBarContentTest` — четыре вида полоски (`PermissionRequired`, `NoSession`, `Playing`, `Paused`); центральная иконка следует состоянию; `⏭` неактивна при `canSkipNext = false`; тап по области трека раскрывает шторку, тап по `⏭` — нет
- `MusicSheetContentTest` — слайдер отсутствует при `canSeek = false`, сердечко при `canRate = false`; сердце закрашено только при `isLiked = true`
- закрытие шторки: переход `isResting` из `true` в `false` закрывает, из `false` в `true` — нет; уход состояния в `NoSession` закрывает

Гоняются как `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

**Руками на телефоне** (эмулятор не годится — нужен залогиненный Яндекс):

- выдача доступа в системных настройках и возврат: полоска превращается в плеер без перезапуска приложения — это проверка `refresh()`, автотестом не покрывается
- переключение треков в самом Яндексе: полоска следует за ним
- убить Яндекс и запустить снова: полоска подхватывает новую сессию
- **проверить `METADATA_KEY_USER_RATING`:** приходит ли он в метаданных Яндекса. Если нет, `isLiked` всегда `null` — сердце остаётся контурным, лайк при этом ставится. Запасной путь, если состояние окажется важным: Яндекс пишет его в подпись кастомного действия (`'Нравится Не выбрано'`), откуда его можно прочитать. Решение принимается по факту проверки, в спеку не закладывается.
- лайк действительно добавляет трек в «Мне нравится»

## Затронутые файлы

Новые:

- `domain/model/MusicState.kt`
- `domain/repository/MusicControlRepository.kt`
- `data/MediaSnapshot.kt`
- `data/MusicControlRepositoryImpl.kt`
- `data/mapper/MusicStateMapper.kt`
- `presentation/service/MediaAccessService.kt`
- `presentation/components/music/MusicViewModel.kt`
- `presentation/components/music/MusicBar.kt`
- `presentation/components/music/MusicSheet.kt`
- `app/src/test/.../data/mapper/MusicStateMapperTest.kt`
- `app/src/androidTest/.../components/music/FakeMusicControlRepository.kt`
- `app/src/androidTest/.../components/music/MusicBarContentTest.kt`
- `app/src/androidTest/.../components/music/MusicSheetContentTest.kt`

Изменяются:

- `AndroidManifest.xml` — сервис, чистка дубля `<queries>`
- `di/AppModule.kt` — биндинг репозитория
- `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` — `MusicBar` отдельной строкой над нижним `Row`, `YandexMusicButton` оттуда убран, передаётся `isResting`
- `res/values/strings.xml` — подпись сервиса и текст тоста, если строки выносятся в ресурсы (нынешний код держит их inline — допустимо оба варианта, но одинаково по всей фиче)

Удаляется:

- `presentation/components/SystemMediaController.kt` — логика запуска плеера переезжает в `MusicControlRepositoryImpl.openPlayer()`, кнопка — в `MusicBar`
