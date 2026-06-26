package ru.hopes.workouttimer.presentation.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SoundPlayer @Inject constructor (@ApplicationContext private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    fun playSound(@RawRes resourceId: Int) {
        try {
            mediaPlayer?.release()
            
            // Запрашиваем аудио фокус для уведомлений
            requestAudioFocus()

            mediaPlayer = MediaPlayer().apply {
                // Настраиваем аудио атрибуты для уведомлений (работает при выключенном экране)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)

                val afd = context.resources.openRawResourceFd(resourceId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                
                prepare()
                setOnCompletionListener { releaseMediaPlayer() }
                setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            releaseMediaPlayer()
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseMediaPlayer() {
        abandonAudioFocus()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    fun release() {
        releaseMediaPlayer()
    }
}