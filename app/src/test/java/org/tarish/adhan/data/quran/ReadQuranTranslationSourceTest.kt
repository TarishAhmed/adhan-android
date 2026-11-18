package org.tarish.adhan.data.quran

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.tarish.adhan.data.remote.ReadQuranApi
import org.tarish.adhan.domain.quran.Footnote
import org.tarish.adhan.domain.quran.SearchHit
import org.tarish.adhan.domain.quran.Translation
import org.tarish.adhan.domain.quran.TranslationEdition

class ReadQuranTranslationSourceTest {

    /** Serves "zk" text "v<n>" for each requested verse, honouring the 10-per-call page cap. */
    private class FakeApi : ReadQuranApi {
        val ranges = mutableListOf<String>()
        var searchBody: JsonObject? = null

        override suspend fun chapter(range: String, options: JsonObject): List<JsonObject> {
            ranges += range
            val (from, to) = range.substringAfter(':').split('-').map(String::toInt)
            return (from..minOf(to, from + ReadQuranApi.PAGE - 1)).map { v ->
                buildJsonObject { put("ch", 2); put("v", v); put("v_", v - 1); put("zk", "v$v") }
            }
        }

        override suspend fun search(body: JsonObject): JsonObject {
            searchBody = body
            return Json.parseToJsonElement(
                """{"verses":[{"ch":2,"v":46,"zk":"Seek <mark>patience</mark>"},{"ch":3,"v":1}]}"""
            ).jsonObject
        }
    }

    @Test fun verses_requestsPageSizedRanges() = runBlocking {
        val api = FakeApi()
        val out = ReadQuranTranslationSource(api).verses(TranslationEdition.ZAFRULLA_KHAN, 2, 1, 25)
        assertThat(api.ranges).containsExactly("2:1-10", "2:11-20", "2:21-25").inOrder()
        assertThat(out.keys).containsExactlyElementsIn(1..25)
        assertThat(out[25]).isEqualTo(Translation("v25"))
    }

    @Test fun search_sendsEditionAndSkipsHitsWithoutText() = runBlocking {
        val api = FakeApi()
        val hits = ReadQuranTranslationSource(api).search(TranslationEdition.ZAFRULLA_KHAN, "patience")
        assertThat(hits).containsExactly(SearchHit(2, 46, "Seek <mark>patience</mark>"))
        assertThat(api.searchBody.toString()).isEqualTo("""{"s":"patience","options":{"zk":true},"concordance":""}""")
    }

    @Test fun parse_plainString() {
        assertThat(ReadQuranTranslationSource.parse(JsonPrimitive("I AM ALLAH"))).isEqualTo(Translation("I AM ALLAH"))
        assertThat(ReadQuranTranslationSource.parse(JsonPrimitive(""))).isNull()
        assertThat(ReadQuranTranslationSource.parse(null)).isNull()
    }

    @Test fun parse_textWithNotes() {
        val e = Json.parseToJsonElement("""{"text":"Alif Lam Mim[✛].","notes":[{"ref":"✛","note":"<p>I am Allah</p>"}]}""")
        assertThat(ReadQuranTranslationSource.parse(e))
            .isEqualTo(Translation("Alif Lam Mim[✛].", listOf(Footnote("✛", "<p>I am Allah</p>"))))
    }
}
