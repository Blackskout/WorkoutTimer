package ru.hopes.workouttimer.presentation.utils

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SoundPlayer @Inject constructor (@ApplicationContext private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playSound(@RawRes resourceId: Int) {
        try {
            mediaPlayer?.release() // если уже играет другой звук
            mediaPlayer = MediaPlayer.create(context, resourceId).apply {
                setOnCompletionListener { releaseMediaPlayer() }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun release() {
        releaseMediaPlayer()
    }
}