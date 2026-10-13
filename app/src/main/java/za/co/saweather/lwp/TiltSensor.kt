package za.co.saweather.lwp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Gravity-based tilt for gyro parallax, reported relative to how the phone was first held. */
class TiltSensor(context: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var baseX = Float.NaN
    private var baseY = Float.NaN
    private var registered = false

    fun start() {
        if (registered || sensor == null) return
        baseX = Float.NaN
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!registered) return
        sm.unregisterListener(this)
        registered = false
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0]
        val gy = e.values[1]
        if (baseX.isNaN()) { baseX = gx; baseY = gy }
        // Slowly re-centre so the resting pose always reads as neutral.
        baseX += (gx - baseX) * 0.01f
        baseY += (gy - baseY) * 0.01f
        onTilt(((baseX - gx) / 3f).coerceIn(-1f, 1f), ((gy - baseY) / 3f).coerceIn(-1f, 1f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
