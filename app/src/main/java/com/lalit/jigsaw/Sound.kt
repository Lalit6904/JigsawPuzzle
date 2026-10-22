package com.lalit.jigsaw

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Two short synthesised cues: a piece click and a completion chime. */
class Sound(context: Context) {

    private val pool: SoundPool
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val snapId: Int
    private val winId: Int
    private var ready = 0

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) ready++ }
        snapId = pool.load(context, R.raw.snap, 1)
        winId = pool.load(context, R.raw.complete, 1)
    }

    fun snap() = play(snapId, 0.5f)

    fun win() = play(winId, 0.75f)

    private fun play(id: Int, volume: Float) {
        if (ready < 2) return
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
