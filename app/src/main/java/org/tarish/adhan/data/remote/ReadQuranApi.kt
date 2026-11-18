package org.tarish.adhan.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Backend of alislam.org's Quran reader. Not a documented public API: requests mirror what the
 * reader itself sends. Verses are numbered the Ahmadiyya way and at most [PAGE] come per call.
 */
interface ReadQuranApi {
    /** [range] is "surah:from-to"; [options] maps edition keys to true, e.g. {"zk": true}. */
    @POST("chapter/{range}")
    suspend fun chapter(@Path("range", encoded = true) range: String, @Body options: JsonObject): List<JsonObject>

    /** Body: {"s": query, "options": {edition: true}, "concordance": ""}. Returns {"verses": [...]}. */
    @POST("rq_search")
    suspend fun search(@Body body: JsonObject): JsonObject

    companion object {
        const val BASE_URL = "https://api.readquran.app/"
        const val PAGE = 10
    }
}
