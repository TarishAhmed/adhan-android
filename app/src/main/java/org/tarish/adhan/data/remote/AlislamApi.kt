package org.tarish.adhan.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface AlislamApi {
    @GET("adhan/api/timings/month")
    suspend fun month(
        @Query("lat") lat: String,
        @Query("lng") lng: String,
        @Query("month") month: Int,
        @Query("year") year: Int,
    ): MonthResponseDto

    companion object {
        const val BASE_URL = "https://www.alislam.org/"
    }
}

@Serializable data class MonthResponseDto(val locationInfo: LocationInfoDto? = null, val multiDayTimings: List<DayDto> = emptyList())
@Serializable data class LocationInfoDto(val lat: String? = null, val lng: String? = null, val timezone: String? = null)
@Serializable data class DayDto(val prayers: List<PrayerDto> = emptyList(), val coordinates: CoordinatesDto? = null, val date: Long? = null)
@Serializable data class PrayerDto(val name: String, val time: Long? = null, val audio: String? = null)
@Serializable data class CoordinatesDto(val latitude: Double? = null, val longitude: Double? = null)
