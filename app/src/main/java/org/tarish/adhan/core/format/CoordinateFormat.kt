package org.tarish.adhan.core.format

import java.math.BigDecimal
import java.math.RoundingMode

/** 2-decimal, HALF_UP, locale-independent (§4.3). Never use String.format here — "51,51" in de/id locales. */
fun formatCoord(v: Double): String {
    val s = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString()
    return if (s == "-0.00") "0.00" else s
}

/** The single cache/API key for a location, e.g. "51.51,-0.13" (BUG-02). */
fun locationKey(lat: Double, lng: Double): String = "${formatCoord(lat)},${formatCoord(lng)}"
