package com.example.cloudmusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.cloudmusic.data.model.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackUiState(
    val currentTrackId: String? = null,
    val currentTrackTitle: String? = null,
    val currentArtist: String? = null,
    val currentArtworkUri: String? = null,
    val currentArtworkData: ByteArray? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentQueueIndex: Int = 0,
    val queueSize: Int = 0,
    val playbackSpeed: Float = 1f,
)

/**
 * Activity/ViewModel 쪽에서 MusicService(MediaSessionService)를 제어하기 위한 얇은 래퍼.
 * MediaController를 통해 명령을 보내면, 실제 재생은 서비스 프로세스(같은 프로세스)에서 이루어지고
 * 앱이 백그라운드로 가도 동일하게 동작한다.
 */
class PlaybackController(context: Context) {

    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(playerListener)
            // 서비스가 이미 재생 중인 상태로 연결되는 경우(예: 앱을 다시 열었을 때)
            // 다음 트랙 전환을 기다리지 않고 현재 상태를 바로 반영한다.
            _state.value = _state.value.copy(
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                currentTrackId = c.currentMediaItem?.mediaId,
                currentTrackTitle = c.mediaMetadata.title?.toString(),
                currentArtist = c.mediaMetadata.artist?.toString(),
                currentArtworkUri = c.mediaMetadata.artworkUri?.toString(),
                currentArtworkData = c.mediaMetadata.artworkData,
                currentQueueIndex = c.currentMediaItemIndex,
                queueSize = c.mediaItemCount,
            )
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _state.value = _state.value.copy(
                currentTrackTitle = mediaMetadata.title?.toString(),
                currentArtist = mediaMetadata.artist?.toString(),
                currentArtworkUri = mediaMetadata.artworkUri?.toString(),
                currentArtworkData = mediaMetadata.artworkData,
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.value = _state.value.copy(
                currentTrackId = mediaItem?.mediaId,
                currentArtworkUri = null,
                currentArtworkData = null,
                currentQueueIndex = controller?.currentMediaItemIndex ?: 0,
                queueSize = controller?.mediaItemCount ?: 0,
            )
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            _state.value = _state.value.copy(
                currentQueueIndex = controller?.currentMediaItemIndex ?: 0,
                queueSize = controller?.mediaItemCount ?: 0,
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.value = _state.value.copy(playbackSpeed = playbackParameters.speed)
        }
    }

    fun playTracks(
        tracks: List<Track>,
        streamUrlBuilder: (String) -> String,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(streamUrlBuilder(track.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.thumbnailUrl?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()
        }
        controller?.apply {
            setMediaItems(items, startIndex, startPositionMs)
            prepare()
            this.playWhenReady = playWhenReady
        }
    }

    fun togglePlayPause() {
        controller?.let { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    fun pause() = controller?.pause()

    fun seekToNext() = controller?.seekToNext()
    fun seekToPrevious() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun seekToQueueItem(index: Int) = controller?.seekTo(index, 0L)

    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    fun setRepeatMode(mode: Int) { controller?.repeatMode = mode }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(speed)
        )
    }

    fun stop() {
        controller?.apply { stop(); clearMediaItems() }
        _state.value = PlaybackUiState()
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long = controller?.duration?.coerceAtLeast(0L) ?: 0L

    /**
     * 재생 중일 때 1초마다 positionMs/durationMs를 state에 반영한다.
     * MiniPlayer의 진행 바처럼 매 화면 재구성 없이 state를 구독하는 UI에서 쓸 수 있다.
     * ViewModel의 viewModelScope에서 호출해야 한다.
     */
    suspend fun startPositionPolling() {
        while (true) {
            val c = controller
            if (c != null && c.isPlaying) {
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition,
                    durationMs = c.duration.coerceAtLeast(0L),
                )
            }
            kotlinx.coroutines.delay(1_000)
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
