package com.whatchapp.hourlybuzz.prayer

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.whatchapp.hourlybuzz.Alerts
import com.whatchapp.hourlybuzz.R
import com.whatchapp.hourlybuzz.State
import com.whatchapp.hourlybuzz.insetForRoundScreen

/**
 * Records the imam's own قيام, ركوع, سجود and جلوس (4 s each). Buzz cues let
 * him do it without looking: one buzz = hold still, two buzzes = done.
 */
class CalibrationActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var stepText: TextView
    private lateinit var text: TextView
    private lateinit var status: TextView
    private lateinit var button: Button
    private val handler = Handler(Looper.getMainLooper())

    private val steps = listOf(R.string.calib_qiyam, R.string.calib_ruku, R.string.calib_sujood, R.string.calib_jalsa)
    private val results = mutableListOf<DoubleArray>() // gx, gy, gz, pressure
    private var step = -1
    private var recording = false
    private var done = false

    private val gravity = DoubleArray(3) { Double.NaN }
    private val sum = DoubleArray(3)
    private var samples = 0
    private var pressure = Double.NaN
    private var pressureSum = 0.0
    private var pressureSamples = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        insetForRoundScreen(findViewById(R.id.root))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stepText = findViewById(R.id.calib_step)
        text = findViewById(R.id.calib_text)
        status = findViewById(R.id.calib_status)
        button = findViewById(R.id.calib_button)
        sensors = getSystemService(SensorManager::class.java)
        button.setOnClickListener { if (done) finish() else begin() }
        // Sensors stay on for the whole screen, so a dimmed screen doesn't interrupt a step.
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sensors.unregisterListener(this)
        super.onDestroy()
    }

    private fun begin() {
        results.clear()
        button.visibility = Button.GONE
        nextStep(0)
    }

    private fun nextStep(i: Int) {
        step = i
        stepText.text = "${i + 1} / ${steps.size}"
        text.setText(steps[i])
        status.setText(R.string.calib_get_ready)
        handler.postDelayed({
            sum.fill(0.0); samples = 0
            pressureSum = 0.0; pressureSamples = 0
            recording = true
            status.setText(R.string.calib_hold)
            Alerts.vibratePattern(this, longArrayOf(150))
            handler.postDelayed({ finishStep() }, HOLD_MS)
        }, PREPARE_MS)
    }

    private fun finishStep() {
        recording = false
        val n = norm(sum[0], sum[1], sum[2])
        val p = if (pressureSamples > 0) pressureSum / pressureSamples else Double.NaN
        results += if (samples == 0) doubleArrayOf(Double.NaN, 0.0, 0.0, p)
        else doubleArrayOf(sum[0] / n, sum[1] / n, sum[2] / n, p)
        Alerts.vibratePattern(this, longArrayOf(120, 120, 120))
        if (step + 1 < steps.size) nextStep(step + 1) else complete()
    }

    private fun complete() {
        val base = results[0][3]
        fun centroid(r: DoubleArray) = Centroid(
            r[0], r[1], r[2],
            if (base.isNaN() || r[3].isNaN()) 0.0 else r[3] - base,
        )
        val cal = Calibration(centroid(results[0]), centroid(results[1]), centroid(results[2]), centroid(results[3]))
        button.visibility = Button.VISIBLE
        stepText.setText(R.string.calib_title)
        status.text = ""
        if (samplesMissing() || cal.problem() != null) {
            text.setText(R.string.calib_failed)
            button.setText(R.string.prayer_start)
            step = -1
        } else {
            State(this).calibration = cal.encode()
            text.setText(R.string.calib_done)
            button.setText(R.string.calib_ok)
            done = true
        }
    }

    private fun samplesMissing() = results.any { it[0].isNaN() }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) {
                    gravity[i] = if (gravity[i].isNaN()) v[i].toDouble() else gravity[i] + 0.1 * (v[i] - gravity[i])
                }
                if (recording) {
                    val n = norm(gravity[0], gravity[1], gravity[2])
                    for (i in 0..2) sum[i] += gravity[i] / n
                    samples++
                }
            }
            Sensor.TYPE_PRESSURE -> {
                pressure = v[0].toDouble()
                if (recording) {
                    pressureSum += pressure
                    pressureSamples++
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val PREPARE_MS = 6000L
        private const val HOLD_MS = 4000L
    }
}
