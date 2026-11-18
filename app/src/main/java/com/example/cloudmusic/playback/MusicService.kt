package com.example.cloudmusic.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.cloudmusic.data.local.DataSaverProvider
import com.example.cloudmusic.data.local.MediaCache
import com.example.cloudmusic.data.local.NetworkMonitor
import com.example.cloudmusic.data.local.NetworkType
import com.example.cloudmusic.data.local.PreferencesStore
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

class MusicService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var pendingCellularBytes = 0L
    private var lastNetCheckMs = 0L
    private var onCellular = false

    companion object {
        // ── 버퍼 프리셋 ──────────────────────────────────────────────────────
        // Wi-Fi: 넉넉하게 — 데이터 걱정 없고 끊김 최소화
        private const val WIFI_MIN_BUFFER_MS  = 20_000
        private const val WIFI_MAX_BUFFER_MS  = 60_000   // 1분 (이전 120초에서 절반으로)
        private const val WIFI_FOR_PLAYBACK_MS = 2_500
        private const val WIFI_AFTER_REBUFFER_MS = 5_000

        // 모바일 데이터 / 데이터 절약 모드: 최소한만 — 재생에 필요한 만큼만 받아둠
        private const val DATA_MIN_BUFFER_MS  = 10_000
        private const val DATA_MAX_BUFFER_MS  = 20_000   // 20초
        private const val DATA_FOR_PLAYBACK_MS = 2_000
        private const val DATA_AFTER_REBUFFER_MS = 4_000
    }

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = TokenProvider.accessToken
                val req = if (token != null)
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token").build()
                else chain.request()
                chain.proceed(req)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val networkDataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient).setTransferListener(cellularCounter)
        val dataSourceFactory: DataSource.Factory = buildCachingDataSourceFactory(networkDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(buildAdaptiveLoadControl())
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(NetworkResilientLoadErrorHandlingPolicy())
            )
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /**
     * 데이터 절약 모드(또는 모바일 데이터 환경)이면 버퍼를 줄이고,
     * Wi-Fi이면 조금 더 넉넉하게 설정한다.
     * DataSaverProvider가 런타임에 바뀌면 다음 곡부터 적용된다.
     */
    private fun buildAdaptiveLoadControl(): DefaultLoadControl {
        val isDataSaver = DataSaverProvider.isDataSaverMode
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isDataSaver) DATA_MIN_BUFFER_MS  else WIFI_MIN_BUFFER_MS,
                if (isDataSaver) DATA_MAX_BUFFER_MS  else WIFI_MAX_BUFFER_MS,
                if (isDataSaver) DATA_FOR_PLAYBACK_MS else WIFI_FOR_PLAYBACK_MS,
                if (isDataSaver) DATA_AFTER_REBUFFER_MS else WIFI_AFTER_REBUFFER_MS,
            )
            .build()
    }

    private class NetworkResilientLoadErrorHandlingPolicy : LoadErrorHandlingPolicy {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (loadErrorInfo.exception !is IOException) return C.TIME_UNSET
            val attempt = loadErrorInfo.errorCount
            if (attempt > 5) return C.TIME_UNSET
            return (500L * (1 shl (attempt - 1))).coerceAtMost(8_000L)
        }
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 5
        override fun getFallbackSelectionFor(
            fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
            loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
        ): LoadErrorHandlingPolicy.FallbackSelection? = null
    }

    private fun buildCachingDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(MediaCache.get(this))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** 실제로 네트워크에서 받은 바이트(캐시 적중 제외)를 모바일 데이터일 때만 월 사용량에 더한다. */
    private val cellularCounter = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) { flushCellularBytes() }
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            val now = System.currentTimeMillis()
            // 청크마다 시스템 호출을 하지 않도록 네트워크 종류는 1초에 한 번만 확인한다.
            if (now - lastNetCheckMs > 1_000) {
                lastNetCheckMs = now
                onCellular = NetworkMonitor(this@MusicService).currentNetworkType() == NetworkType.CELLULAR
            }
            if (onCellular) pendingCellularBytes += bytesTransferred
            // 곡 하나가 끝날 때까지 안 쓰면 강제 종료 시 유실되므로 일정량마다 저장한다.
            if (pendingCellularBytes > 512 * 1024) flushCellularBytes()
        }
    }

    @Synchronized
    private fun flushCellularBytes() {
        if (pendingCellularBytes > 0) {
            PreferencesStore(this).addCellularBytes(pendingCellularBytes)
            pendingCellularBytes = 0
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    fun setPlaylist(items: List<MediaItem>, startIndex: Int = 0) {
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onDestroy() {
        mediaSession?.run { player.release(); release(); mediaSession = null }
        flushCellularBytes()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = mediaSession?.player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
