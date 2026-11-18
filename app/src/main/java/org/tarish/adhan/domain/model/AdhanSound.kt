package org.tarish.adhan.domain.model

import java.net.URLDecoder

data class AdhanSound(
    val id: String,
    val displayName: String,
    val remoteFile: String?,
    val bundled: Boolean,
    val kind: Kind,
) {
    enum class Kind { ADHAN, SYSTEM_DEFAULT, SILENT }

    companion object {
        const val DEFAULT_ID = "makkah"
        const val SYSTEM_DEFAULT_ID = "system_default"
        const val SILENT_ID = "silent"
        const val BASE_URL = "https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/"

        private fun a(id: String, name: String, file: String) = AdhanSound(id, name, file, false, Kind.ADHAN)

        /** Display order (§4.6). */
        val CATALOG: List<AdhanSound> = listOf(
            AdhanSound(SYSTEM_DEFAULT_ID, "Default notification sound", null, false, Kind.SYSTEM_DEFAULT),
            AdhanSound(SILENT_ID, "Silent (vibrate only)", null, false, Kind.SILENT),
            AdhanSound(DEFAULT_ID, "Makkah", "Athan Makkah.mp3", true, Kind.ADHAN),
            a("mishary_alafasi", "Mishary Alafasi", "Athan Mishary Alafasi.mp3"),
            a("mansoor_az_zahrani", "Mansoor Az-Zahrani", "Athan Mansoor Az-Zahrani.mp3"),
            a("hamdan_almalki", "Hamdan Almalki", "Athan Hamdan Almalki.mp3"),
            a("hamad_deghreri", "Hamad Deghreri", "Athan Hamad Deghreri.mp3"),
            a("ibrahim_al_arkani", "Ibrahim Al-Arkani", "Athan Ibrahim Al-Arkani.mp3"),
            a("majed_al_hamathani", "Majed Al-Hamathani", "Athan Majed Al-hamathani.mp3"),
            a("malek_chebae_fajr", "Malek Chebae (Fajr)", "Athan Al-fajer - Malek chebae.mp3"),
            a("mohammad_almenshawy", "Mohammad Almenshawy", "Athan Mohammad Almenshawy.mp3"),
            a("mohammad_refat", "Mohammad Ref'at", "Athan Mohammad Ref3at.mp3"),
            a("nasser_alqatami", "Nasser Alqatami", "Athan Nasser Alqatami.mp3"),
            a("suhaib_khatba", "Suhaib Khatba", "Athan Suhaib Khatba.mp3"),
            a("abed_albase", "Abed Albase", "Athan Abed Albase6.mp3"),
            a("ahmad_nuyne", "Ahmad Nuyne", "Athan Ahmad Nuyne3.mp3"),
            a("feroz_alam", "Feroz Alam", "Azan_FerozAlam.mp3"),
            a("mishary_rashid_alafasy", "Mishary Rashid Alafasy", "mishary_rashid_alafasy.mp3"),
            a("mishary_rashid_alafasy_2", "Mishary Rashid Alafasy 2", "mishary_rashid_alafasy_2.mp3"),
            a("mishary_rashid_alafasy_3", "Mishary Rashid Alafasy 3", "mishary_rashid_alafasy_3.mp3"),
            a("mansour_al_zahrani", "Mansour Al-Zahrani", "mansour-al-zahrani.mp3"),
            a("hafiz_mustafa_ozcan", "Hafiz Mustafa Ozcan", "hafiz_mustafa_ozcan.mp3"),
            a("karl_jenkins", "Karl Jenkins", "karl_jenkins.mp3"),
            a("ahmad_al_nafees", "Ahmad Al Nafees", "ahmad_al_nafees.mp3"),
        )

        fun byId(id: String): AdhanSound? = CATALOG.firstOrNull { it.id == id }

        /** URL-path-encode a file name (spaces → %20). */
        fun encodePath(file: String): String =
            java.net.URLEncoder.encode(file, "UTF-8").replace("+", "%20")

        fun urlFor(sound: AdhanSound): String? = sound.remoteFile?.let { BASE_URL + encodePath(it) }

        /** Flutter stored the full URL, or the sentinel "defaultNotificationSound" (§4.6). */
        fun idFromFlutter(value: String?): String {
            if (value.isNullOrBlank()) return DEFAULT_ID
            if (value == "defaultNotificationSound") return SYSTEM_DEFAULT_ID
            val last = runCatching { URLDecoder.decode(value.substringAfterLast('/'), "UTF-8") }.getOrNull()
                ?: return DEFAULT_ID
            return CATALOG.firstOrNull { it.remoteFile == last }?.id ?: DEFAULT_ID
        }

        /** Map a downloaded Flutter file name (possibly URL-encoded) to a catalog id. */
        fun idFromFileName(name: String): String? {
            val decoded = runCatching { URLDecoder.decode(name.replace("+", "%2B"), "UTF-8") }.getOrDefault(name)
            return CATALOG.firstOrNull { it.remoteFile == decoded || it.remoteFile == name }?.id
        }
    }
}
