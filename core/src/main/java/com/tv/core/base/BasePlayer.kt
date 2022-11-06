package com.tv.core.base

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tv.core.util.MediaSourceType
import java.util.*

abstract class BasePlayer(
    private val context: Context,
    playerView: PlayerView,
    playWhenReady: Boolean = true
) {

    private var trackSelector: DefaultTrackSelector
    var player: ExoPlayer

    init {
        val videoTrackSelectionFactory =
            AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(context.applicationContext, videoTrackSelectionFactory)
        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
        playerView.player = player
        player.playWhenReady = playWhenReady
    }

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun addMedia(media: MediaItem, index: Int = 0) {
        player.addMediaSource(index, buildMediaSource(media))
    }

    fun addMediaList(medias: List<MediaItem>, index: Int = 0) {
        player.addMediaSources(index, buildMediaSources(medias))
    }

    fun isThereSubtitle(): Boolean {
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                return true
            }
        }
        return false
    }

    private fun buildMediaSources(mediaItems: List<MediaItem>): List<MediaSource> {
        val mediaSources = mutableListOf<MediaSource>()
        mediaItems.forEach { mediaItem ->
            mediaSources.add(buildMediaSource(mediaItem))
        }
        return mediaSources
    }

    private fun buildMediaSource(mediaItem: MediaItem): MediaSource {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(context, context.packageName))
        val mediaSource: MediaSource =
            if (mediaItem.localConfiguration?.tag.toString() == MediaSourceType.Hls.name)
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem) else ProgressiveMediaSource.Factory(
                dataSourceFactory
            ).createMediaSource(mediaItem)
        val subtitleSources: ArrayList<MediaSource> = arrayListOf()

        mediaItem.localConfiguration?.subtitleConfigurations?.forEach { subtitleConfig ->
            subtitleSources.add(
                SingleSampleMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(subtitleConfig, C.TIME_UNSET)
            )
        }
        return MergingMediaSource(mediaSource, *subtitleSources.toTypedArray())
    }

    fun preparePlayer() {
        player.prepare()
    }

    fun play() {
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun pause() {
        player.pause()
    }

    fun fastForwardIncrement(duration: Int = 10) {
        val length = if (duration <= 1_000) duration * 1_000 else duration
        player.seekTo(player.currentPosition + length)
    }

    fun fastRewindIncrement(duration: Int = 10) {
        val length = if (duration <= 1_000) duration * 1_000 else duration
        player.seekTo(player.currentPosition - length)
    }

    abstract fun release()

    fun showSubtitle(overrideThemeResId: Int) {
        val subtitleLanguageList = ArrayList<String>()
        val subtitlesList = ArrayList<String>()
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val groupInfo = group.mediaTrackGroup
                for (i in 0 until groupInfo.length) {
                    subtitleLanguageList.add(groupInfo.getFormat(i).language.toString())
                    subtitlesList.add(
                        "${subtitlesList.size + 1}. " + Locale(groupInfo.getFormat(i).language.toString()).displayLanguage
                                + " (${
                            if (groupInfo.getFormat(i).label == null) "Subtitle" else groupInfo.getFormat(
                                i
                            ).label
                        })"
                    )
                }
            }
        }

        val tempTracks = subtitlesList.toArray(arrayOfNulls<CharSequence>(subtitlesList.size))
        val subtitleDialog =
            MaterialAlertDialogBuilder(context, overrideThemeResId)
                .setTitle("Select Subtitles")
                .setOnCancelListener { }
                .setPositiveButton("Off Subtitles") { self, _ ->
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().setRendererDisabled(
                            C.TRACK_TYPE_VIDEO, true
                        )
                    )
                    self.dismiss()
                }
                .setItems(tempTracks) { _, position ->
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                            .setPreferredTextLanguage(subtitleLanguageList[position])
                    )
                }
                .create()
        subtitleDialog.show()
        subtitleDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
        subtitleDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
    }

}