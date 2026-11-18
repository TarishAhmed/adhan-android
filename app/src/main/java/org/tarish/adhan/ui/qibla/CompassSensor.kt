package org.tarish.adhan.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class CompassReading(val headingTrue: Double, val accuracy: Int, val tiltDeg: Int)

/** §8.4 Sensors — rotation vector preferred; true north via declination (D-11). No per-event allocations. */
class CompassSensor(private val context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)

    private val rotation: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accel: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean get() = rotation != null || (accel != null && magnet != null)

    fun readings(lat: Double, lng: Double): Flow<CompassReading> = callbackFlow {
        if (sm == null || !isAvailable) { close(); return@callbackFlow }
        val declination = GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        var haveG = false
        var haveM = false
        var sinF = 0.0
        var cosF = 1.0
        var init = false
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var tilt = 0

        fun emitFromRotation() {
            val (ax, ay) = when (displayRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
            SensorManager.getOrientation(remapped, orient)
            val az = orient[0].toDouble()
            // Low-pass on the unit vector to avoid wrap-around artefacts.
            if (!init) { sinF = sin(az); cosF = cos(az); init = true } else {
                sinF += ALPHA * (sin(az) - sinF); cosF += ALPHA * (cos(az) - cosF)
            }
            val magnetic = (Math.toDegrees(atan2(sinF, cosF)) + 360.0) % 360.0
            trySend(CompassReading((magnetic + declination + 360.0) % 360.0, accuracy, tilt))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rot, e.values)
                        emitFromRotation()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(e.values, 0, gravity, 0, 3); haveG = true
                        val z = abs(e.values[2]) / 9.81f
                        tilt = (90 - z.coerceIn(0f, 1f) * 90).toInt()
                        if (rotation == null && haveM && SensorManager.getRotationMatrix(rot, null, gravity, geomag)) emitFromRotation()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(e.values, 0, geomag, 0, 3); haveM = true
                        if (rotation == null && haveG && SensorManager.getRotationMatrix(rot, null, gravity, geomag)) emitFromRotation()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
                if (sensor.type != Sensor.TYPE_ACCELEROMETER) accuracy = acc
            }
        }
        rotation?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotation == null) magnet?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) context.display?.rotation ?: Surface.ROTATION_0
        else context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
    }.getOrDefault(Surface.ROTATION_0)

    companion object { private const val ALPHA = 0.15 }
}
