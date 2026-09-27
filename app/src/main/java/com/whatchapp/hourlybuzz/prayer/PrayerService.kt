package com.whatchapp.hourlybuzz.prayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.whatchapp.hourlybuzz.Alerts
import com.whatchapp.hourlybuzz.Config
import com.whatchapp.hourlybuzz.R
import com.whatchapp.hourlybuzz.State
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs prayer mode in the background (the screen can be off): reads the
 * motion sensors, barometer and mic loudness, counts rak'ahs, and answers a
 * hand shake with buzzes = rak'ahs left after the current one.
 */
class PrayerService : Service(), SensorEventListener, PrayerEngine.Listener {

    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var voice: VoiceOnsetDetector? = null
    private var log: BufferedWriter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastLoggedAccel = 0L
    private var lastLoggedGyro = 0L

    private val watchdog = object : Runnable {
        override fun run() {
            val e = engine ?: return
            val now = SystemClock.elapsedRealtime()
            if (e.counter.finished(now, Config.PRAYER_END_SIT_SECONDS * 1000L) ||
                now - startedAt > Config.PRAYER_MAX_MINUTES * 60_000L
            ) {
                stopSelf()
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Every startForegroundService() must be answered with startForeground().
        if (engine == null) prayerName = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        goForeground()
        if (engine != null) return START_NOT_STICKY // already running

        val total = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0
        val calibration = Calibration.decode(State(this).calibration)
        if (total <= 0 || calibration == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startedAt = SystemClock.elapsedRealtime()
        State(this).prayerActiveUntil = System.currentTimeMillis() + Config.PRAYER_MAX_MINUTES * 60_000L
        engine = PrayerEngine(total, calibration, this)
        openLog(total)

        sensors = getSystemService(SensorManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HourlyBuzz:prayer")
            .apply { acquire(Config.PRAYER_MAX_MINUTES * 60_000L + 60_000L) }
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SAMPLE_US) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SAMPLE_US) }
        sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (hasMic()) {
            voice = VoiceOnsetDetector { t ->
                handler.post {
                    engine?.onVoice(t)
                    writeLog(t, "voice")
                }
            }.also { if (!it.start()) voice = null }
        }
        handler.postDelayed(watchdog, 1000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::sensors.isInitialized) sensors.unregisterListener(this)
        voice?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        writeLog(SystemClock.elapsedRealtime(), "end", engine?.counter?.rakah?.toString().orEmpty())
        log?.close()
        engine = null
        State(this).prayerActiveUntil = 0
        super.onDestroy()
    }

    // ---- sensors -------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val e = engine ?: return
        val t = event.timestamp / 1_000_000 // ns since boot -> ms, same clock as elapsedRealtime
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                e.onAccel(t, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                if (t - lastLoggedAccel >= 100) {
                    lastLoggedAccel = t
                    writeLog(t, "acc", "${v[0]},${v[1]},${v[2]}")
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                e.onGyro(t, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                if (t - lastLoggedGyro >= 100) {
                    lastLoggedGyro = t
                    writeLog(t, "gyr", "${v[0]},${v[1]},${v[2]}")
                }
            }
            Sensor.TYPE_PRESSURE -> {
                e.onPressure(t, v[0].toDouble())
                writeLog(t, "prs", "${v[0]}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- engine events -------------------------------------------------------

    override fun onShake(remainingAfterCurrent: Int) {
        writeLog(SystemClock.elapsedRealtime(), "shake", remainingAfterCurrent.toString())
        Alerts.vibratePattern(this, answerPattern(remainingAfterCurrent))
    }

    override fun onStableChange(posture: Posture, rakah: Int) {
        writeLog(SystemClock.elapsedRealtime(), "stable", "$posture,$rakah")
    }

    override fun onRawPosture(posture: Posture) {
        writeLog(SystemClock.elapsedRealtime(), "raw", posture.name)
    }

    // ---- helpers -------------------------------------------------------------

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.prayer_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PrayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.prayer_mode))
            .setContentText(prayerName)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        var types = 0
        if (Build.VERSION.SDK_INT >= 34) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        if (hasMic()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (types != 0) startForeground(NOTIFICATION_ID, notification, types)
        else startForeground(NOTIFICATION_ID, notification)
    }

    /** Optional CSV of the session, for tuning. */
    private fun openLog(total: Int) {
        if (!Config.PRAYER_LOG) return
        try {
            // Android/data/<package>/files/prayer_logs: readable with `adb pull`.
            val dir = File(getExternalFilesDir(null) ?: filesDir, "prayer_logs").apply { mkdirs() }
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
            val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            log = File(dir, "prayer-$name.csv").bufferedWriter()
            writeLog(SystemClock.elapsedRealtime(), "start", "$total,$prayerName")
        } catch (e: Exception) {
            Log.w("PrayerService", "No log", e)
        }
    }

    private fun writeLog(t: Long, type: String, data: String = "") {
        try {
            log?.apply {
                write("$t,$type,$data")
                newLine()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL = "prayer"
        private const val NOTIFICATION_ID = 10
        private const val SAMPLE_US = 20_000 // 50 Hz
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_NAME = "name"
        private const val ACTION_STOP = "stop"

        /** The running session, if any (same process as the screens). */
        @Volatile
        var engine: PrayerEngine? = null
            private set
        var prayerName = ""
            private set
        var startedAt = 0L
            private set

        /** Buzzes for "rak'ahs left": n × long buzz, or 3 quick ticks for none. */
        fun answerPattern(remaining: Int): LongArray =
            if (remaining <= 0) longArrayOf(80, 120, 80, 120, 80)
            else LongArray(remaining * 2 - 1) { i -> if (i % 2 == 0) 250L else 450L }

        fun start(context: Context, total: Int, name: String) {
            context.startForegroundService(
                Intent(context, PrayerService::class.java)
                    .putExtra(EXTRA_TOTAL, total)
                    .putExtra(EXTRA_NAME, name)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PrayerService::class.java).setAction(ACTION_STOP))
        }
    }
}
