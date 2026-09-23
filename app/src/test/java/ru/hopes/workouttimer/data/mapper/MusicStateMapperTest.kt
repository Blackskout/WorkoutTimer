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
