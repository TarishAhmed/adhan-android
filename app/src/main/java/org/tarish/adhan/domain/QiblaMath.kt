package org.tarish.adhan.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** §7.7 — bearings are degrees from TRUE north. */
object QiblaMath {
    const val KAABA_LAT = 21.4225
    const val KAABA_LNG = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0088

    fun qiblaBearing(lat: Double, lng: Double): Double = bearing(lat, lng, KAABA_LAT, KAABA_LNG)

    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun distanceKm(lat: Double, lng: Double): Double = haversineKm(lat, lng, KAABA_LAT, KAABA_LNG)

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** -180 (inclusive) .. 180 (exclusive). relativeAngle(180, 0) == -180. */
    fun relativeAngle(qibla: Double, headingTrue: Double): Double = ((qibla - headingTrue + 540.0) % 360.0) - 180.0

    fun isValid(lat: Double, lng: Double) = lat in -90.0..90.0 && lng in -180.0..180.0

    private val DIRS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun directionName(deg: Double): String {
        val d = ((deg % 360) + 360) % 360
        return DIRS[(((d + 22.5) / 45.0).toInt()) % 8]
    }

    /** Great-circle points from (lat1,lng1) to (lat2,lng2), inclusive, [n] intermediate points. */
    fun greatCircle(lat1: Double, lng1: Double, lat2: Double, lng2: Double, n: Int = 64): List<Pair<Double, Double>> {
        val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lng1)
        val p2 = Math.toRadians(lat2); val l2 = Math.toRadians(lng2)
        val d = haversineKm(lat1, lng1, lat2, lng2) / EARTH_RADIUS_KM
        if (d == 0.0) return listOf(lat1 to lng1)
        return (0..n + 1).map { i ->
            val f = i.toDouble() / (n + 1)
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)
            val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
            val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
            val z = a * sin(p1) + b * sin(p2)
            Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to Math.toDegrees(atan2(y, x))
        }
    }
}
