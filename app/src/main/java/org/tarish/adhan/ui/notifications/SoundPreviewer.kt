package org.tarish.adhan.ui.notifications

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.tarish.adhan.R
import org.tarish.adhan.data.SoundSource
import org.tarish.adhan.data.rawResourceUri

/** Sound preview in the editor: USAGE_MEDIA with audio focus (§8.6.1). */
class SoundPreviewer(private val context: Context) {
    var playingId by mutableStateOf<String?>(null)
        private set
    private var player: ExoPlayer? = null

    fun play(id: String, source: SoundSource) {
        stop()
        val uri: Uri = when (source) {
            is SoundSource.FileSource -> Uri.fromFile(source.file)
            is SoundSource.RawRes -> context.rawResourceUri(source.resId)
            SoundSource.SystemDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            SoundSource.Missing -> context.rawResourceUri(R.raw.adhan_makkah)
            SoundSource.Silent -> return
        }
        player = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) stop()
                }
            })
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
        playingId = id
    }

    fun stop() {
        player?.release()
        player = null
        playingId = null
    }
}
