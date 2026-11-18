package com.example.cloudmusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.cloudmusic.data.drive.DriveAuthManager
import com.example.cloudmusic.data.drive.DriveRepository
import com.example.cloudmusic.data.local.ArtworkExtractor
import com.example.cloudmusic.data.local.DataSaverProvider
import com.example.cloudmusic.data.local.MediaCache
import com.example.cloudmusic.data.local.NetworkMonitor
import com.example.cloudmusic.data.local.NetworkType
import com.example.cloudmusic.data.local.PreferencesStore
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track
import com.example.cloudmusic.playback.PlaybackController
import com.example.cloudmusic.playback.TokenProvider
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.cloudmusic.ui.screens.SortOption
import java.util.UUID

data class LibraryUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val rawQuery: String = "",   // 검색창에 즉시 반영되는 텍스트
    val query: String = "",      // 300ms 디바운스 후 실제 필터링에 사용
    val errorMessage: String? = null,
)

enum class RepeatMode { OFF, ALL, ONE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager   = DriveAuthManager(app)
    private val driveRepo     = DriveRepository(app)
    private val prefs         = PreferencesStore(app)
    val playbackController    = PlaybackController(app)

    // ── 현재 로그인 계정 키 ─────────────────────────────────────────────────
    private var accountKey: String? = null
    private var currentAccount: GoogleSignInAccount? = null

    // ── 공개 UI 상태 ─────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(value = false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(value = RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(value = emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(value = emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 최근 재생: 앱 재시작 후에도 유지 (영속)
    private val _recentlyPlayedIds = MutableStateFlow<List<String>>(value = emptyList())
    val recentlyPlayedIds: StateFlow<List<String>> = _recentlyPlayedIds.asStateFlow()

    // 아티스트 화면용: artist → tracks 맵. _uiState.tracks가 바뀌면 자동으로 재계산됨.
    val artistMap: StateFlow<Map<String, List<Track>>> = _uiState
        .map { state ->
            state.tracks
                .groupBy { it.artist }
                .toSortedMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // 다중 선택 모드 (라이브러리/아티스트 화면에서 플레이리스트 일괄 추가용)
    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTrackIds: StateFlow<Set<String>> = _selectedTrackIds.asStateFlow()

    // ── 네트워크 / 데이터 절약 모드 ─────────────────────────────────────────
    private val networkMonitor = NetworkMonitor(app)

    // 수동 데이터 절약 토글
    private val _manualDataSaver = MutableStateFlow(value = false)

    /** 실제 적용 여부: 수동으로 켰거나 모바일 데이터 환경이면 true */
    val isDataSaverActive: StateFlow<Boolean> = combine(
        _manualDataSaver,
        networkMonitor.networkType,
    ) { manual, networkType ->
        (manual || (networkType == NetworkType.CELLULAR))
            .also { DataSaverProvider.isDataSaverMode = it }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /** 현재 네트워크 타입 (UI에 Wi-Fi/데이터 뱃지 표시용) */
    val networkType: StateFlow<NetworkType> =
        networkMonitor.networkType
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkType.NONE)

    fun toggleManualDataSaver() { _manualDataSaver.value = !_manualDataSaver.value }
    /** null = 꺼짐, 양수 = 남은 ms */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    // 스낵바로 띄울 한 번짜리 안내 (모바일 데이터 재생 등)
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices

    // ── 권한 재동의 인텐트 (UserRecoverableAuthException 처리) ──────────────
    private val _reAuthIntent = MutableStateFlow<android.content.Intent?>(null)
    val reAuthIntent: StateFlow<android.content.Intent?> = _reAuthIntent.asStateFlow()
    fun clearReAuthIntent() { _reAuthIntent.value = null }

    // ── 정렬 ─────────────────────────────────────────────────────────────────
    private val _sortOption = MutableStateFlow(SortOption.TITLE_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
    fun setSortOption(option: SortOption) { _sortOption.value = option }

    // ── 현재 큐 추적 ─────────────────────────────────────────────────────────
    /** 현재 ExoPlayer에 세팅된 재생 목록 (업 넥스트 UI용) */
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    init {
        checkExistingSignIn()
        viewModelScope.launch { playbackController.startPositionPolling() }
    }

    // ── 로그인 ───────────────────────────────────────────────────────────────
    private fun checkExistingSignIn() {
        authManager.getLastSignedInAccount()?.let { onSignedIn(it) }
    }

    fun getSignInIntent() = authManager.getSignInIntent()

    fun onSignedIn(account: GoogleSignInAccount) {
        switchAccountScope(account)
        _uiState.value = _uiState.value.copy(isSignedIn = true)
        refreshTokenAndLoadLibrary(account, isRefresh = false)
    }

    fun onSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    private fun switchAccountScope(account: GoogleSignInAccount) {
        val newKey = prefs.sanitizeAccountKey(account.email ?: account.id)
        if (newKey == accountKey) return

        accountKey = newKey
        currentAccount = account
        _favoriteTrackIds.value   = prefs.loadFavoriteIds(newKey)
        _playlists.value          = prefs.loadPlaylists(newKey)
        _recentlyPlayedIds.value  = prefs.loadRecentlyPlayedIds(newKey)
        _selectedTrackIds.value   = emptySet()
        // 저장해 둔 목록을 먼저 보여줘서 Drive 응답을 기다리지 않고(오프라인이어도) 바로 쓸 수 있게 한다.
        _uiState.value = _uiState.value.copy(tracks = prefs.loadTracks(newKey))
    }

    // ── 새로고침 ─────────────────────────────────────────────────────────────
    /** 라이브러리 새로고침: Drive에 새로 추가/삭제된 파일을 반영 */
    fun refreshLibrary() {
        val account = currentAccount ?: authManager.getLastSignedInAccount() ?: return
        refreshTokenAndLoadLibrary(account, isRefresh = true)
    }

    private fun refreshTokenAndLoadLibrary(account: GoogleSignInAccount, isRefresh: Boolean) {
        viewModelScope.launch {
            // 저장된 목록이 이미 화면에 있으면 스피너 대신 조용히 갱신한다.
            if (isRefresh || _uiState.value.tracks.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            try {
                when (val tokenResult = authManager.fetchAccessToken(account)) {
                    is DriveAuthManager.TokenResult.Success -> {
                        TokenProvider.accessToken = tokenResult.token
                    }
                    is DriveAuthManager.TokenResult.NeedsConsent -> {
                        // 사용자가 권한을 다시 동의해야 함 → Activity가 인텐트를 띄우도록 전달
                        _reAuthIntent.value = tokenResult.recoveryIntent
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "Drive 접근 권한 재동의가 필요해요.",
                        )
                        return@launch
                    }
                    is DriveAuthManager.TokenResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "인증에 실패했어요: ${tokenResult.message}",
                        )
                        return@launch
                    }
                }
                val tracks = driveRepo.listAudioTracks(account)
                accountKey?.let { key -> withContext(Dispatchers.IO) { prefs.saveTracks(key, tracks) } }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    tracks = tracks,
                )
                restoreLastPositionIfNeeded(tracks)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false,
                    errorMessage = "라이브러리를 불러오지 못했어요: ${e.message}"
                )
            }
        }
    }

    // ── 재생 위치 복원 ───────────────────────────────────────────────────────
    /**
     * 앱 재시작 시 이전에 듣던 곡의 위치를 복원한다.
     * 라이브러리 로드가 끝나야 트랙 목록을 알 수 있어서 여기서 처리한다.
     */
    private fun restoreLastPositionIfNeeded(tracks: List<Track>) {
        val key = accountKey ?: return
        val saved = prefs.loadLastPosition(key) ?: return
        val startIndex = tracks.indexOfFirst { it.id == saved.trackId }
        if (startIndex == -1) return  // 파일이 Drive에서 삭제된 경우

        // 전체 라이브러리를 큐에 세팅해서 이어듣기 이후에도 다음 곡 재생이 가능하게 함
        playbackController.playTracks(
            tracks = tracks,
            streamUrlBuilder = driveRepo::streamUrlFor,
            startIndex = startIndex,
            startPositionMs = saved.positionMs,
            playWhenReady = false,   // 자동재생 없이 "이어듣기 대기" 상태로만 세팅
        )
        _currentQueue.value = tracks
    }

    /** 현재 재생 위치를 저장한다. Activity.onStop() 등에서 호출하면 된다. */
    fun saveCurrentPosition() {
        val key = accountKey ?: return
        val trackId = playbackController.state.value.currentTrackId ?: return
        val pos = playbackController.currentPosition()
        if (pos > 3_000) { // 3초 이상 들었을 때만 저장
            prefs.saveLastPosition(key, trackId, pos)
        }
    }

    // ── 검색 ─────────────────────────────────────────────────────────────────
    private var searchDebounceJob: Job? = null

    fun onQueryChanged(query: String) {
        // rawQuery는 입력 즉시 반영 (검색창 텍스트 표시용)
        _uiState.value = _uiState.value.copy(rawQuery = query)
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(timeMillis = 300)
            _uiState.value = _uiState.value.copy(query = query)
        }
    }

    // ── 재생 ─────────────────────────────────────────────────────────────────
    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOf(track).coerceAtLeast(0)
        playbackController.playTracks(tracks, driveRepo::streamUrlFor, index)
        _currentQueue.value = tracks
        recordRecentlyPlayed(track.id)
        clearLastPosition()
        noticeIfCellular(track)
    }

    fun playPlaylist(playlist: Playlist, startTrack: Track? = null) {
        val allTracks = _uiState.value.tracks
        val byId = allTracks.associateBy { it.id }
        val ordered = playlist.trackIds.mapNotNull { byId[it] }
        if (ordered.isEmpty()) return
        val idx = startTrack?.let { ordered.indexOf(it) }?.coerceAtLeast(0) ?: 0
        playbackController.playTracks(ordered, driveRepo::streamUrlFor, idx)
        _currentQueue.value = ordered
        recordRecentlyPlayed(ordered[idx].id)
        clearLastPosition()
        noticeIfCellular(ordered[idx])
    }

    fun playFavorites(startTrack: Track? = null) {
        val favTracks = _uiState.value.tracks.filter { it.id in _favoriteTrackIds.value }
        if (favTracks.isEmpty()) return
        val idx = startTrack?.let { favTracks.indexOf(it) }?.coerceAtLeast(0) ?: 0
        playbackController.playTracks(favTracks, driveRepo::streamUrlFor, idx)
        _currentQueue.value = favTracks
        recordRecentlyPlayed(favTracks[idx].id)
        clearLastPosition()
        noticeIfCellular(favTracks[idx])
    }

    fun playArtist(artist: String, startTrack: Track? = null) {
        val artistTracks = _uiState.value.tracks.filter { it.artist == artist }
        if (artistTracks.isEmpty()) return
        val idx = startTrack?.let { artistTracks.indexOf(it) }?.coerceAtLeast(0) ?: 0
        playbackController.playTracks(artistTracks, driveRepo::streamUrlFor, idx)
        _currentQueue.value = artistTracks
        recordRecentlyPlayed(artistTracks[idx].id)
        clearLastPosition()
        noticeIfCellular(artistTracks[idx])
    }

    /** 모바일 데이터이고 아직 캐시에 없는 곡이면, 재생 전에 대략 얼마나 받는지 알려준다. */
    private fun noticeIfCellular(track: Track) {
        if (networkMonitor.currentNetworkType() != NetworkType.CELLULAR) return
        if (MediaCache.isCached(getApplication(), DriveRepository.streamUrlFor(track.id), track.sizeBytes)) return
        val size = if (track.sizeBytes > 0) " (최대 약 %.1fMB)".format(track.sizeBytes / 1_048_576.0) else ""
        _notices.tryEmit("모바일 데이터로 재생해요$size")
    }

    // ── 재생 대기열 편집 ─────────────────────────────────────────────────────
    fun playNext(track: Track) {
        if (playbackController.state.value.queueSize == 0) {
            playbackController.playTracks(listOf(track), driveRepo::streamUrlFor, 0)
            _currentQueue.value = listOf(track)
            recordRecentlyPlayed(track.id)
            noticeIfCellular(track)
            return
        }
        val index = playbackController.insertAfterCurrent(track, driveRepo::streamUrlFor)
        if (index < 0) return
        _currentQueue.value = _currentQueue.value.toMutableList().apply { add(index.coerceAtMost(size), track) }
        _notices.tryEmit("다음에 재생할게요")
    }

    fun moveQueueItem(from: Int, to: Int) {
        val queue = _currentQueue.value
        if (from !in queue.indices || to !in queue.indices || from == to) return
        playbackController.moveQueueItem(from, to)
        _currentQueue.value = queue.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun removeQueueItem(index: Int) {
        val queue = _currentQueue.value
        if (index !in queue.indices) return
        playbackController.removeQueueItem(index)
        _currentQueue.value = queue.toMutableList().apply { removeAt(index) }
    }

    // ── 저장공간 / 데이터 사용량 ─────────────────────────────────────────────
    data class StorageInfo(val cacheUsedBytes: Long = 0, val cacheMaxMb: Int = PreferencesStore.DEFAULT_CACHE_MB, val cellularBytes: Long = 0)

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    fun refreshStorageInfo() {
        viewModelScope.launch {
            _storageInfo.value = withContext(Dispatchers.IO) {
                StorageInfo(MediaCache.usedBytes(getApplication()), prefs.loadCacheMaxMb(), prefs.loadCellularBytesThisMonth())
            }
        }
    }

    fun setCacheMaxMb(mb: Int) {
        prefs.saveCacheMaxMb(mb)
        _storageInfo.value = _storageInfo.value.copy(cacheMaxMb = mb)
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { MediaCache.clear(getApplication()) }
            refreshStorageInfo()
        }
    }

    /**
     * [minutes] 분 후에 재생을 자동으로 멈추는 슬립 타이머를 시작한다.
     * 이미 타이머가 실행 중이면 교체한다.
     */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endMs = System.currentTimeMillis() + (minutes * 60_000L)
        sleepTimerJob = viewModelScope.launch {
            while (true) {
                val remaining = endMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    playbackController.pause()
                    _sleepTimerRemainingMs.value = null
                    sleepTimerJob = null
                    break
                }
                _sleepTimerRemainingMs.value = remaining
                delay(timeMillis = 1_000)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    // ── 재생 속도 ─────────────────────────────────────────────────────────────
    fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
    }

    private fun recordRecentlyPlayed(trackId: String) {
        val key = accountKey ?: return
        val updated = (listOf(trackId) + _recentlyPlayedIds.value.filterNot { it == trackId }).take(30)
        _recentlyPlayedIds.value = updated
        prefs.saveRecentlyPlayedIds(key, updated)
    }

    private fun clearLastPosition() {
        accountKey?.let { prefs.clearLastPosition(it) }
    }

    // ── 즐겨찾기 ─────────────────────────────────────────────────────────────
    fun toggleFavorite(track: Track) {
        val key = accountKey ?: return
        val updated = if (track.id in _favoriteTrackIds.value)
            _favoriteTrackIds.value - track.id
        else
            _favoriteTrackIds.value + track.id
        _favoriteTrackIds.value = updated
        prefs.saveFavoriteIds(key, updated)
    }

    // ── 재생목록 ─────────────────────────────────────────────────────────────
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        updatePlaylists(_playlists.value + Playlist(UUID.randomUUID().toString(), name.trim()))
    }

    fun deletePlaylist(playlist: Playlist) {
        updatePlaylists(_playlists.value.filterNot { it.id == playlist.id })
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        updatePlaylists(_playlists.value.map { pl ->
            val shouldUpdate = (pl.id == playlistId) && (track.id !in pl.trackIds)
            if (shouldUpdate) {
                pl.copy(trackIds = pl.trackIds + track.id)
            } else {
                pl
            }
        })
    }

    /** 여러 트랙을 한 번에 재생목록에 추가 */
    fun addTracksToPlaylist(playlistId: String, trackIds: Set<String>) {
        updatePlaylists(_playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val newIds = pl.trackIds + trackIds.filterNot { it in pl.trackIds }
                pl.copy(trackIds = newIds)
            } else {
                pl
            }
        })
    }

    fun removeTrackFromPlaylist(playlistId: String, track: Track) {
        updatePlaylists(_playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filterNot { it == track.id })
            } else {
                pl
            }
        })
    }

    private fun updatePlaylists(updated: List<Playlist>) {
        val key = accountKey ?: return
        _playlists.value = updated
        prefs.savePlaylists(key, updated)
    }

    // ── 다중 선택 ─────────────────────────────────────────────────────────────
    fun toggleTrackSelection(trackId: String) {
        _selectedTrackIds.value = if (trackId in _selectedTrackIds.value)
            _selectedTrackIds.value - trackId
        else
            _selectedTrackIds.value + trackId
    }

    fun selectAllArtistTracks(artist: String) {
        val ids = _uiState.value.tracks
            .asSequence()
            .filter { it.artist == artist }
            .map { it.id }
            .toSet()
        _selectedTrackIds.value += ids
    }

    fun clearSelection() {
        _selectedTrackIds.value = emptySet()
    }

    fun addSelectionToPlaylist(playlistId: String) {
        addTracksToPlaylist(playlistId, _selectedTrackIds.value)
        clearSelection()
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        playbackController.setShuffle(_shuffleEnabled.value)
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        playbackController.setRepeatMode(when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        })
    }

    // ── 토큰 갱신 루프 ───────────────────────────────────────────────────────
    fun startTokenRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                delay(timeMillis = 50 * 60 * 1000L)
                val account = authManager.getLastSignedInAccount() ?: continue
                when (val result = authManager.fetchAccessToken(account)) {
                    is DriveAuthManager.TokenResult.Success ->
                        TokenProvider.accessToken = result.token
                    is DriveAuthManager.TokenResult.NeedsConsent ->
                        _reAuthIntent.value = result.recoveryIntent
                    is DriveAuthManager.TokenResult.Error ->
                        Unit // 조용히 무시: 다음 루프에서 재시도됨
                }
            }
        }
    }

    // ── 로그아웃 ─────────────────────────────────────────────────────────────
    fun signOut() {
        saveCurrentPosition()
        accountKey?.let { prefs.clearTracks(it) }  // 로그아웃 후 기기에 목록이 남지 않게
        driveRepo.clearCache()
        ArtworkExtractor.evictAll()
        authManager.signOut {
            playbackController.stop()
            _uiState.value          = LibraryUiState()
            TokenProvider.accessToken = null
            accountKey              = null
            currentAccount          = null
            _favoriteTrackIds.value = emptySet()
            _playlists.value        = emptyList()
            _recentlyPlayedIds.value = emptyList()
            _selectedTrackIds.value = emptySet()
        }
    }

    override fun onCleared() {
        saveCurrentPosition()
        sleepTimerJob?.cancel()
        playbackController.release()
        super.onCleared()
    }
}
