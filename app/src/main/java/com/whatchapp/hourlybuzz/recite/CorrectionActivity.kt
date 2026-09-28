package com.whatchapp.hourlybuzz.recite

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.whatchapp.hourlybuzz.Cards
import com.whatchapp.hourlybuzz.R
import com.whatchapp.hourlybuzz.insetForRoundScreen

/** Shows a recitation mistake with the correct mushaf words. Closes itself after a few seconds. */
class CorrectionActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_correction)
        insetForRoundScreen(findViewById(R.id.root))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val arabic = resources.getFont(R.font.amiri)
        listOf(R.id.kind, R.id.place, R.id.correct, R.id.heard).forEach {
            findViewById<TextView>(it).typeface = arabic
        }
        findViewById<View>(R.id.root).setOnClickListener { finish() }
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    override fun onResume() {
        super.onResume()
        Cards.visibleScreens++
    }

    override fun onPause() {
        Cards.visibleScreens--
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun show(intent: Intent) {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val kind = ReciteEvent.Kind.entries.find { it.name == intent.getStringExtra(EXTRA_KIND) }
        findViewById<TextView>(R.id.kind).setText(
            when (kind) {
                ReciteEvent.Kind.WRONG_WORD -> R.string.mistake_wrong_word
                ReciteEvent.Kind.SKIPPED -> R.string.mistake_skipped
                ReciteEvent.Kind.WRONG_PLACE -> R.string.mistake_wrong_place
                ReciteEvent.Kind.FORGOT, null -> R.string.mistake_forgot
            }
        )
        findViewById<TextView>(R.id.place).text = intent.getStringExtra(EXTRA_PLACE)
        findViewById<TextView>(R.id.correct).text = intent.getStringExtra(EXTRA_CORRECT)
        val heard = intent.getStringExtra(EXTRA_HEARD).orEmpty()
        findViewById<TextView>(R.id.heard).apply {
            visibility = if (heard.isEmpty()) View.GONE else View.VISIBLE
            text = getString(R.string.mistake_heard, heard)
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, SHOW_MS)
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PLACE = "place"
        private const val EXTRA_CORRECT = "correct"
        private const val EXTRA_HEARD = "heard"
        private const val CHANNEL = "recite"
        private const val NOTIFICATION_ID = 20
        private const val SHOW_MS = 12_000L

        /** Opens the correction screen, turning the watch screen on if needed. */
        fun show(context: Context, m: ReciteEvent.Mistake) {
            val intent = Intent(context, CorrectionActivity::class.java)
                .putExtra(EXTRA_KIND, m.kind.name)
                .putExtra(EXTRA_PLACE, m.place)
                .putExtra(EXTRA_CORRECT, m.correct)
                .putExtra(EXTRA_HEARD, m.heard)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Cards.visibleScreens > 0) {
                context.startActivity(intent)
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.recite_channel), NotificationManager.IMPORTANCE_HIGH)
                        .apply { setSound(null, null); enableVibration(false) }
                )
            }
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(m.place)
                .setContentText(m.correct)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setTimeoutAfter(SHOW_MS * 2)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
