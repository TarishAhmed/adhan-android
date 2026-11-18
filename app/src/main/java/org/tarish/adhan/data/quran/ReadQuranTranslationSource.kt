package org.tarish.adhan.data.quran

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.tarish.adhan.data.remote.ReadQuranApi
import org.tarish.adhan.domain.quran.Footnote
import org.tarish.adhan.domain.quran.SearchHit
import org.tarish.adhan.domain.quran.Translation
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.TranslationSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadQuranTranslationSource @Inject constructor(private val api: ReadQuranApi) : TranslationSource {

    override suspend fun verses(edition: TranslationEdition, surah: Int, from: Int, to: Int): Map<Int, Translation> {
        val options = buildJsonObject { put(edition.key, true) }
        val out = HashMap<Int, Translation>()
        var start = from
        while (start <= to) {
            val end = minOf(start + ReadQuranApi.PAGE - 1, to)
            api.chapter("$surah:$start-$end", options).forEach { row ->
                val v = row["v"]?.jsonPrimitive?.intOrNull ?: return@forEach
                parse(row[edition.key])?.let { out[v] = it }
            }
            start = end + 1
        }
        return out
    }

    override suspend fun search(edition: TranslationEdition, query: String): List<SearchHit> {
        val body = buildJsonObject {
            put("s", query)
            putJsonObject("options") { put(edition.key, true) }
            put("concordance", "")
        }
        val verses = api.search(body)["verses"] as? JsonArray ?: return emptyList()
        return verses.mapNotNull { e ->
            val row = e.jsonObject
            val text = parse(row[edition.key])?.text ?: return@mapNotNull null
            SearchHit(row["ch"]!!.jsonPrimitive.int, row["v"]!!.jsonPrimitive.int, text)
        }
    }

    companion object {
        /** "zk" is a plain string; "en" and "v5" are {"text": ..., "notes": [{"ref", "note"}]}. */
        fun parse(e: JsonElement?): Translation? = when (e) {
            is JsonPrimitive -> e.content.takeIf { e.isString && it.isNotBlank() }?.let { Translation(it) }
            is JsonObject -> e["text"]?.jsonPrimitive?.content?.let { text ->
                val notes = (e["notes"] as? JsonArray).orEmpty().mapNotNull { n ->
                    val o = n as? JsonObject ?: return@mapNotNull null
                    val ref = o["ref"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    Footnote(ref, o["note"]?.jsonPrimitive?.content.orEmpty())
                }
                Translation(text, notes)
            }
            else -> null
        }
    }
}
