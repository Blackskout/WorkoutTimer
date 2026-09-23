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
