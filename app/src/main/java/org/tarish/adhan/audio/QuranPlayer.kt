package org.tarish.adhan.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import org.tarish.adhan.data.quran.QuranPrefs
import org.tarish.adhan.domain.quran.QuranAudio
import org.tarish.adhan.domain.quran.Surah
import javax.inject.Inject
import javax.inject.Singleton

/** What the reader shows for Quran audio. [translation] is true while the spoken translation plays. */
data class QuranPlayback(
    val surah: Int,
    val verse: Int,
    val playing: Boolean,
    val loading: Boolean,
    val translation: Boolean,
    val failed: Boolean,
)

/**
 * The app's handle on [QuranPlaybackService]. A surah plays as one playlist from the chosen verse
 * to the end: each verse's recitation [QuranPrefs.repeat] times, then its spoken translation if one
 * is chosen. Call from the main thread.
 */
@Singleton
class QuranPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private val _state = MutableStateFlow<QuranPlayback?>(null)
    /** Null when nothing is loaded. */
    val state: StateFlow<QuranPlayback?> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    private suspend fun controller(): MediaController = controller ?: MediaController.Builder(
        context, SessionToken(context, ComponentName(context, QuranPlaybackService::class.java)),
    ).buildAsync().await().also {
        it.addListener(listener)
        controller = it
    }

    suspend fun play(surah: Surah, fromVerse: Int, prefs: QuranPrefs) {
        val c = controller()
        c.setMediaItems((fromVerse..surah.verseCount).flatMap { items(surah, it, prefs) })
        c.prepare()
        c.play()
    }

    /** Pause, resume, or retry after an error or the end of the surah. */
    fun toggle() {
        val c = controller ?: return
        when {
            c.playerError != null -> c.prepare()
            c.playbackState == Player.STATE_ENDED -> c.seekTo(0, 0)
            c.playWhenReady -> { c.pause(); return }
        }
        c.play()
    }

    /** Ends playback and lets the service stop. */
    fun stop() {
        controller?.run { stop(); clearMediaItems(); release() }
        controller = null
        _state.value = null
    }

    private fun publish(p: Player) {
        val id = p.currentMediaItem?.mediaId?.let(::parse) ?: run { _state.value = null; return }
        val failed = p.playerError != null
        _state.value = QuranPlayback(
            surah = id.surah,
            verse = id.verse,
            playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && !failed,
            loading = p.playbackState == Player.STATE_BUFFERING,
            translation = id.translation,
            failed = failed,
        )
    }

    private fun items(s: Surah, verse: Int, prefs: QuranPrefs): List<MediaItem> {
        val title = "${s.name} ${s.number}:$verse"
        val recitation = item(
            Id(s.number, verse, translation = false), QuranAudio.recitation(prefs.reciter, s.number, verse), title, prefs.reciter.displayName,
        )
        val spoken = prefs.playedTranslationAudio
        val translation = QuranAudio.translation(spoken, s.number, verse)?.let {
            item(Id(s.number, verse, translation = true), it, title, spoken.displayName)
        }
        return List(prefs.repeat) { recitation } + listOfNotNull(translation)
    }

    private fun item(id: Id, url: String, title: String, artist: String) = MediaItem.Builder()
        .setMediaId("${id.surah}:${id.verse}:${if (id.translation) "t" else "r"}")
        .setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        .build()

    private data class Id(val surah: Int, val verse: Int, val translation: Boolean)

    private fun parse(mediaId: String): Id? {
        val p = mediaId.split(':')
        if (p.size != 3) return null
        return Id(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2] == "t")
    }
}
