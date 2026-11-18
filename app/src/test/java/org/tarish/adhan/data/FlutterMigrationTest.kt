package org.tarish.adhan.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.data.migration.FlutterMigration
import org.tarish.adhan.domain.model.AdhanSound

class FlutterMigrationTest {
    @Test fun mapsAllKeys() {
        val flutter = mapOf<String, Any?>(
            "flutter.onboarding_done_v1" to true,
            "flutter.battery_opt_prompted_v1" to true,
            "flutter.settings_auto_location" to false,
            "flutter.stored_lat" to "51.51",
            "flutter.stored_lng" to "-0.13",
            "flutter.stored_city" to "London",
            "flutter.stored_timezone" to "Europe/London",
            "flutter.notification_pref_fajr" to false,
            "flutter.notification_sound_fajr" to "https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/Athan%20Mishary%20Alafasi.mp3",
            "flutter.notification_sound_zuhr" to "defaultNotificationSound",
            "flutter.notification_sound_asr" to "https://example.com/unknown.mp3",
            "flutter.notification_advance_fajr" to 12L,
            "flutter.notification_advance_asr" to 99L,
            "flutter.notification_days_fajr" to "Mon,Wed",
            "flutter.notification_days_isha" to "",
            "flutter.notification_pref_sunrise" to true,
            "flutter.needs_reschedule" to true,
        )
        val m = FlutterMigration.mapPrefs(flutter)
        assertThat(m["onboarding_done"]).isEqualTo(true)
        assertThat(m["battery_prompt_shown"]).isEqualTo(true)
        assertThat(m["auto_location"]).isEqualTo(false)
        assertThat(m["loc_lat"]).isEqualTo(51.51)
        assertThat(m["loc_lng"]).isEqualTo(-0.13)
        assertThat(m["loc_city"]).isEqualTo("London")
        assertThat(m["loc_zone"]).isEqualTo("Europe/London")
        assertThat(m["loc_source"]).isEqualTo("MANUAL")
        assertThat(m["notif_fajr_enabled"]).isEqualTo(false)
        assertThat(m).doesNotContainKey("notif_zuhr_enabled")   // missing → default true (D-01)
        assertThat(m["notif_fajr_sound"]).isEqualTo("mishary_alafasi")
        assertThat(m["notif_zuhr_sound"]).isEqualTo(AdhanSound.SYSTEM_DEFAULT_ID)
        assertThat(m["notif_asr_sound"]).isEqualTo(AdhanSound.DEFAULT_ID)
        assertThat(m["notif_fajr_advance"]).isEqualTo(10)
        assertThat(m["notif_asr_advance"]).isEqualTo(30)
        assertThat(m["notif_fajr_days"]).isEqualTo("1,3")
        assertThat(m["notif_isha_days"]).isEqualTo("1,2,3,4,5,6,7")
        assertThat(m.keys.none { "sunrise" in it || "reschedule" in it }).isTrue()
    }

    @Test fun soundFileNames() {
        assertThat(AdhanSound.idFromFileName("Athan Makkah.mp3")).isEqualTo("makkah")
        assertThat(AdhanSound.idFromFileName("Athan%20Makkah.mp3")).isEqualTo("makkah")
        assertThat(AdhanSound.idFromFileName("fajr.mp3")).isNull()
        assertThat(AdhanSound.urlFor(AdhanSound.byId("makkah")!!))
            .isEqualTo("https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/Athan%20Makkah.mp3")
    }
}
