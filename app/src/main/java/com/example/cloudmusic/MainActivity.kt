package com.example.cloudmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.cloudmusic.ui.MainViewModel
import com.example.cloudmusic.ui.components.MiniPlayer
import com.example.cloudmusic.ui.screens.ArtistsScreen
import com.example.cloudmusic.ui.screens.FavoritesScreen
import com.example.cloudmusic.ui.screens.LibraryScreen
import com.example.cloudmusic.ui.screens.PlayerScreen
import com.example.cloudmusic.ui.screens.PlaylistsScreen
import com.example.cloudmusic.ui.theme.CloudMusicTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

private enum class MainTab(val label: String) {
    LIBRARY("라이브러리"),
    ARTISTS("아티스트"),
    PLAYLISTS("재생목록"),
    FAVORITES("즐겨찾기"),
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.onSignedIn(account)
        } catch (e: ApiException) {
            viewModel.onSignInFailed("로그인에 실패했어요 (code=${e.statusCode})")
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        viewModel.startTokenRefreshLoop()

        setContent {
            CloudMusicTheme {
                var showFullPlayer by remember { mutableStateOf(false) }
                var currentTab by remember { mutableStateOf(MainTab.LIBRARY) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val libraryState   by viewModel.uiState.collectAsState()
                val playbackState  by viewModel.playbackController.state.collectAsState()
                val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
                val repeatMode     by viewModel.repeatMode.collectAsState()
                val favoriteIds    by viewModel.favoriteTrackIds.collectAsState()
                val playlists      by viewModel.playlists.collectAsState()
                val isDataSaverActive by viewModel.isDataSaverActive.collectAsState()
                val networkType        by viewModel.networkType.collectAsState()
                val sortOption      by viewModel.sortOption.collectAsState()
                val reAuthIntent   by viewModel.reAuthIntent.collectAsState()

                // 권한 재동의가 필요한 경우 Activity에서 인텐트를 띄운다
                LaunchedEffect(reAuthIntent) {
                    reAuthIntent?.let { intent ->
                        signInLauncher.launch(intent)
                        viewModel.clearReAuthIntent()
                    }
                }
                val selectedIds    by viewModel.selectedTrackIds.collectAsState()
                val sleepTimerMs   by viewModel.sleepTimerRemainingMs.collectAsState()
                val currentQueue   by viewModel.currentQueue.collectAsState()
                val recentlyIds    by viewModel.recentlyPlayedIds.collectAsState()

                val recentlyPlayedTracks = remember(recentlyIds, libraryState.tracks) {
                    recentlyIds.mapNotNull { id -> libraryState.tracks.find { it.id == id } }
                }
                val artistMap      by viewModel.artistMap.collectAsState()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentTab) {
                                    MainTab.LIBRARY -> LibraryScreen(
                                        state = libraryState,
                                        nowPlayingTrackId = playbackState.currentTrackId,
                                        isPlaying = playbackState.isPlaying,
                                        favoriteTrackIds = favoriteIds,
                                        playlists = playlists,
                                        recentlyPlayedTracks = recentlyPlayedTracks,
                                        sortOption = sortOption,
                                        onSortOptionChanged = viewModel::setSortOption,
                                        isDataSaverActive = isDataSaverActive,
                                        networkType = networkType,
                                        onToggleDataSaver = viewModel::toggleManualDataSaver,
                                        onSignInClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                                        onQueryChanged = viewModel::onQueryChanged,
                                        onTrackClick = {
                                            viewModel.playTrack(it)
                                            showFullPlayer = true
                                        },
                                        onSignOutClick = viewModel::signOut,
                                        onToggleFavorite = { track ->
                                            viewModel.toggleFavorite(track)
                                            val msg = if (track.id in favoriteIds) "즐겨찾기에서 제거했어요" else "즐겨찾기에 추가했어요"
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        onAddToPlaylist = { playlist, track ->
                                            viewModel.addTrackToPlaylist(playlist.id, track)
                                            scope.launch { snackbarHostState.showSnackbar("\"${playlist.name}\"에 추가했어요") }
                                        },
                                        onCreatePlaylist = viewModel::createPlaylist,
                                        onRefresh = viewModel::refreshLibrary,
                                        selectedTrackIds = selectedIds,
                                        onToggleSelect = viewModel::toggleTrackSelection,
                                        onClearSelection = viewModel::clearSelection,
                                        onAddSelectionToPlaylist = viewModel::addSelectionToPlaylist,
                                    )

                                    MainTab.ARTISTS -> ArtistsScreen(
                                        artistMap = artistMap,
                                        nowPlayingTrackId = playbackState.currentTrackId,
                                        isPlaying = playbackState.isPlaying,
                                        selectedTrackIds = selectedIds,
                                        playlists = playlists,
                                        onPlayArtist = { artist, track ->
                                            viewModel.playArtist(artist, track)
                                            showFullPlayer = true
                                        },
                                        onTrackClick = {
                                            viewModel.playTrack(it)
                                            showFullPlayer = true
                                        },
                                        onToggleSelect = viewModel::toggleTrackSelection,
                                        onSelectAllArtist = viewModel::selectAllArtistTracks,
                                        onClearSelection = viewModel::clearSelection,
                                        onAddSelectionToPlaylist = viewModel::addSelectionToPlaylist,
                                        onCreatePlaylistAndAdd = onCreatePlaylistAndAdd@{ name ->
                                            viewModel.createPlaylist(name)
                                            val newId = viewModel.playlists.value.lastOrNull()?.id
                                                ?: return@onCreatePlaylistAndAdd
                                            viewModel.addSelectionToPlaylist(newId)
                                        },
                                    )

                                    MainTab.PLAYLISTS -> PlaylistsScreen(
                                        playlists = playlists,
                                        allTracks = libraryState.tracks,
                                        onCreatePlaylist = viewModel::createPlaylist,
                                        onDeletePlaylist = viewModel::deletePlaylist,
                                        onPlayPlaylist = { playlist, track ->
                                            viewModel.playPlaylist(playlist, track)
                                            showFullPlayer = true
                                        },
                                        onRemoveTrack = { playlist, track ->
                                            viewModel.removeTrackFromPlaylist(playlist.id, track)
                                        },
                                    )

                                    MainTab.FAVORITES -> FavoritesScreen(
                                        favoriteTracks = libraryState.tracks.filter { it.id in favoriteIds },
                                        nowPlayingTrackId = playbackState.currentTrackId,
                                        isPlaying = playbackState.isPlaying,
                                        onTrackClick = {
                                            viewModel.playFavorites(it)
                                            showFullPlayer = true
                                        },
                                        onToggleFavorite = viewModel::toggleFavorite,
                                    )
                                }

                                MiniPlayer(
                                    state = playbackState,
                                    onTogglePlay = { viewModel.playbackController.togglePlayPause() },
                                    onNext = { viewModel.playbackController.seekToNext() },
                                    onExpand = { showFullPlayer = true },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }

                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == MainTab.LIBRARY,
                                    onClick = { currentTab = MainTab.LIBRARY },
                                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                                    label = { Text(MainTab.LIBRARY.label) },
                                )
                                NavigationBarItem(
                                    selected = currentTab == MainTab.ARTISTS,
                                    onClick = { currentTab = MainTab.ARTISTS },
                                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    label = { Text(MainTab.ARTISTS.label) },
                                )
                                NavigationBarItem(
                                    selected = currentTab == MainTab.PLAYLISTS,
                                    onClick = { currentTab = MainTab.PLAYLISTS },
                                    icon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                                    label = { Text(MainTab.PLAYLISTS.label) },
                                )
                                NavigationBarItem(
                                    selected = currentTab == MainTab.FAVORITES,
                                    onClick = { currentTab = MainTab.FAVORITES },
                                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                                    label = { Text(MainTab.FAVORITES.label) },
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showFullPlayer,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                        PlayerScreen(
                            state = playbackState,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            isFavorite = playbackState.currentTrackId?.let { it in favoriteIds } ?: false,
                            playlists = playlists,
                            queue = currentQueue,
                            sleepTimerRemainingMs = sleepTimerMs,
                            onTogglePlay  = { viewModel.playbackController.togglePlayPause() },
                            onNext        = { viewModel.playbackController.seekToNext() },
                            onPrevious    = { viewModel.playbackController.seekToPrevious() },
                            onSeek        = { viewModel.playbackController.seekTo(it) },
                            onSeekRelative = { delta ->
                                val newPos = (viewModel.playbackController.currentPosition() + delta)
                                    .coerceAtLeast(0L)
                                viewModel.playbackController.seekTo(newPos)
                            },
                            onToggleShuffle  = viewModel::toggleShuffle,
                            onCycleRepeat    = viewModel::cycleRepeatMode,
                            onToggleFavorite = {
                                val track = libraryState.tracks.find { it.id == playbackState.currentTrackId }
                                if (track != null) {
                                    viewModel.toggleFavorite(track)
                                    val msg = if (track.id in favoriteIds) "즐겨찾기에서 제거했어요" else "즐겨찾기에 추가했어요"
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onAddToPlaylist = { playlist ->
                                val track = libraryState.tracks.find { it.id == playbackState.currentTrackId }
                                if (track != null) {
                                    viewModel.addTrackToPlaylist(playlist.id, track)
                                    scope.launch { snackbarHostState.showSnackbar("\"${playlist.name}\"에 추가했어요") }
                                }
                            },
                            onCreatePlaylistAndAdd = onPlayerCreatePlaylistAndAdd@{ name ->
                                viewModel.createPlaylist(name)
                                val newId = viewModel.playlists.value.lastOrNull()?.id
                                    ?: return@onPlayerCreatePlaylistAndAdd
                                val track = libraryState.tracks.find { it.id == playbackState.currentTrackId }
                                if (track != null) viewModel.addTrackToPlaylist(newId, track)
                            },
                            onSetSpeed         = viewModel::setPlaybackSpeed,
                            onStartSleepTimer  = viewModel::startSleepTimer,
                            onCancelSleepTimer = viewModel::cancelSleepTimer,
                            onJumpToQueueItem  = { viewModel.playbackController.seekToQueueItem(it) },
                            currentPositionProvider = { viewModel.playbackController.currentPosition() },
                            durationProvider        = { viewModel.playbackController.duration() },
                            onClose = { showFullPlayer = false },
                        )
                    }
                }
                } // Scaffold content lambda
        }
    }

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 가거나 화면이 꺼질 때 재생 위치를 저장한다.
        viewModel.saveCurrentPosition()
    }
}
