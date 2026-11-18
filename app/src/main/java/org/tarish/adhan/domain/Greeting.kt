package org.tarish.adhan.domain

/** §7.8 — device-local hour. */
enum class Greeting {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun forHour(hour: Int): Greeting = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}
