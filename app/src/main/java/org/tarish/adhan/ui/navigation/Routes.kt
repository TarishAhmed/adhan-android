package org.tarish.adhan.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object Onboarding
@Serializable object Main
@Serializable object Home
@Serializable object Qibla
@Serializable object Settings
@Serializable object Notifications
@Serializable object Location
@Serializable object Quran
@Serializable data class SurahReader(val surah: Int, val verse: Int = 1)
@Serializable data class QuranSearch(val query: String = "")
