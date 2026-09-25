package com.whatchapp.hourlybuzz

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

enum class AlertKind { TICK, CARD }

sealed class Sound {
    /** A file bundled in app/src/main/res/raw, e.g. Sound.Raw(R.raw.chime). */
    data class Raw(val resId: Int) : Sound()

    /** A sound streamed from the internet. */
    data class Url(val url: String) : Sound()
}

class AlertSettings(
    val vibrate: LongArray? = null,
    val sound: Sound? = null,
    val volume: Float = 1f,
)

/** One way of alerting the user. Add new ones to [Alerts.handlers]. */
fun interface AlertHandler {
    fun run(context: Context, kind: AlertKind, settings: AlertSettings)
}

object Alerts {
    private const val TAG = "Alerts"

    private val usage: Int
        get() = if (Config.RESPECT_DO_NOT_DISTURB) AudioAttributes.USAGE_NOTIFICATION
        else AudioAttributes.USAGE_ALARM

    val vibrate = AlertHandler { context, _, settings ->
        val pattern = settings.vibrate ?: return@AlertHandler
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(
                if (Config.RESPECT_DO_NOT_DISTURB) VibrationAttributes.USAGE_NOTIFICATION
                else VibrationAttributes.USAGE_ALARM
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
        }
    }

    val sound = AlertHandler { context, kind, settings ->
        val source = settings.sound ?: return@AlertHandler
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            when (source) {
                is Sound.Raw -> player.setDataSource(
                    context, Uri.parse("android.resource://${context.packageName}/${source.resId}")
                )
                is Sound.Url -> player.setDataSource(source.url)
            }
            player.setVolume(settings.volume, settings.volume)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "Sound for $kind failed ($what/$extra)")
                mp.release()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Sound for $kind could not play", e)
            player.release()
        }
    }

    /** Every handler runs for every alert. Add new alert types here. */
    val handlers = mutableListOf(vibrate, sound)

    fun fire(context: Context, kind: AlertKind) {
        val settings = Config.ALERTS[kind] ?: return
        for (handler in handlers) {
            try {
                handler.run(context, kind, settings)
            } catch (e: Exception) {
                Log.e(TAG, "Alert handler failed for $kind", e)
            }
        }
    }
}
