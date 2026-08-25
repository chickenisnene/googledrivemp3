package com.example.cloudmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cloudmusic.R
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track
import com.example.cloudmusic.ui.LibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    nowPlayingTrackId: String?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<String>,
    playlists: List<Playlist>,
    recentlyPlayedTracks: List<Track>,
    sortOption: SortOption,
    onSortOptionChanged: (SortOption) -> Unit,
    isDataSaverActive: Boolean,
    networkType: com.example.cloudmusic.data.local.NetworkType,
    onToggleDataSaver: () -> Unit,
    onSignInClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onSignOutClick: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Playlist, Track) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRefresh: () -> Unit,
    selectedTrackIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var showBulkPlaylistDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedTrackIds.isNotEmpty()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                NetworkBadge(
                    networkType = networkType,
                    isDataSaverActive = isDataSaverActive,
                    onToggle = onToggleDataSaver,
                )
            }
            if (state.isSignedIn) {
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                }
                TextButton(onClick = onSignOutClick) { Text(stringResource(R.string.sign_out)) }
            }
        }

        // 다중 선택 중일 때 배너
        if (isSelectionMode) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedTrackIds.size}곡 선택됨",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showBulkPlaylistDialog = true }) { Text("재생목록에 추가") }
                    TextButton(onClick = onClearSelection) { Text("취소") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!state.isSignedIn) {
            SignInPrompt(onSignInClick)
            return@Column
        }

        OutlinedTextField(
            value = state.rawQuery,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("곡 제목 검색") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.rawQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "검색어 지우기")
                    }
                } else {
                    // 정렬 버튼
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "정렬")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            color = if (sortOption == option)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    onClick = {
                                        onSortOptionChanged(option)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )

        Spacer(Modifier.height(12.dp))

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = state.tracks
                        .filter { state.query.isBlank() || it.title.contains(state.query, ignoreCase = true) || it.artist.contains(state.query, ignoreCase = true) }
                        .applySortOption(sortOption)
                    if (filtered.isEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // 최근 재생은 빈 목록 상태에서도 보임
                            if (state.query.isBlank() && recentlyPlayedTracks.isNotEmpty()) {
                                item {
                                    RecentlyPlayedRow(
                                        tracks = recentlyPlayedTracks,
                                        nowPlayingTrackId = nowPlayingTrackId,
                                        onTrackClick = onTrackClick,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(stringResource(R.string.empty_library), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        LazyColumn {
                            // 검색 중이 아닐 때만 최근 재생 섹션을 첫 번째 아이템으로 표시.
                            // LazyColumn 안에 있으므로 목록을 내리면 자연스럽게 위로 스크롤되어 사라짐.
                            if (state.query.isBlank() && recentlyPlayedTracks.isNotEmpty()) {
                                item(key = "recently_played") {
                                    RecentlyPlayedRow(
                                        tracks = recentlyPlayedTracks,
                                        nowPlayingTrackId = nowPlayingTrackId,
                                        onTrackClick = onTrackClick,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                            items(filtered, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    isPlaying = isPlaying && track.id == nowPlayingTrackId,
                                    isCurrent = track.id == nowPlayingTrackId,
                                    isFavorite = track.id in favoriteTrackIds,
                                    isSelected = track.id in selectedTrackIds,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) onToggleSelect(track.id)
                                        else onTrackClick(track)
                                    },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onAddToPlaylistClick = { trackForPlaylistDialog = track },
                                    onToggleSelect = { onToggleSelect(track.id) },
                                )
                            }
                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }
                }
            }
        }
    }

    val pendingTrack = trackForPlaylistDialog
    if (pendingTrack != null) {
        AddToPlaylistDialog(
            track = pendingTrack,
            playlists = playlists,
            onDismiss = { trackForPlaylistDialog = null },
            onPickPlaylist = { playlist ->
                onAddToPlaylist(playlist, pendingTrack)
                trackForPlaylistDialog = null
            },
            onCreatePlaylist = onCreatePlaylist,
        )
    }

    if (showBulkPlaylistDialog) {
        BulkAddToPlaylistDialog(
            count = selectedTrackIds.size,
            playlists = playlists,
            onDismiss = { showBulkPlaylistDialog = false },
            onPickPlaylist = { playlist ->
                onAddSelectionToPlaylist(playlist.id)
                showBulkPlaylistDialog = false
            },
            onCreatePlaylist = onCreatePlaylist,
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPickPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"${track.title}\" 추가") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text(
                        "아직 만든 재생목록이 없어요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickPlaylist(playlist) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${playlist.trackIds.size}곡",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("새 재생목록 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreatePlaylist(newPlaylistName) },
                enabled = newPlaylistName.isNotBlank(),
            ) { Text("새로 만들고 추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun BulkAddToPlaylistDialog(
    count: Int,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPickPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${count}곡을 재생목록에 추가") },
        text = {
            Column {
                if (playlists.isNotEmpty()) {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickPlaylist(playlist) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text("${playlist.trackIds.size}곡", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("새 재생목록 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreatePlaylist(newName) },
                enabled = newName.isNotBlank(),
            ) { Text("새로 만들고 추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun RecentlyPlayedRow(
    tracks: List<Track>,
    nowPlayingTrackId: String?,
    onTrackClick: (Track) -> Unit,
) {
    Column {
        Text(
            "최근 재생",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks) { track ->
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clickable { onTrackClick(track) },
                ) {
                    val thumbShape = RoundedCornerShape(12.dp)
                    Box(modifier = Modifier.size(84.dp)) {
                        com.example.cloudmusic.ui.components.TrackThumbnail(track = track, size = 84.dp)
                        if (track.id == nowPlayingTrackId) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(thumbShape)
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignInClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Google Drive에 있는 음악을\n바로 스트리밍해보세요",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onSignInClick) { Text(stringResource(R.string.sign_in_with_google)) }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    isFavorite: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val rowShape = RoundedCornerShape(14.dp)
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        isCurrent  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else       -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbShape = RoundedCornerShape(10.dp)
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(44.dp),
            )
        } else {
            Box(modifier = Modifier.size(44.dp)) {
                com.example.cloudmusic.ui.components.TrackThumbnail(track = track, size = 44.dp)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(thumbShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isSelectionMode) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAddToPlaylistClick) {
                Icon(
                    imageVector = Icons.Filled.PlaylistAdd,
                    contentDescription = "재생목록에 추가",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── 정렬 옵션 ─────────────────────────────────────────────────────────────────
enum class SortOption(val label: String) {
    TITLE_ASC("제목 ↑"),
    TITLE_DESC("제목 ↓"),
    ARTIST_ASC("아티스트 ↑"),
    RECENTLY_ADDED("최근 추가순"),
}

fun List<Track>.applySortOption(option: SortOption): List<Track> = when (option) {
    SortOption.TITLE_ASC       -> sortedBy { it.title.lowercase() }
    SortOption.TITLE_DESC      -> sortedByDescending { it.title.lowercase() }
    SortOption.ARTIST_ASC      -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SortOption.RECENTLY_ADDED  -> this // Drive가 반환하는 기본 순서를 그대로 사용
}

// ── 네트워크 뱃지 ─────────────────────────────────────────────────────────────

@Composable
private fun NetworkBadge(
    networkType: com.example.cloudmusic.data.local.NetworkType,
    isDataSaverActive: Boolean,
    onToggle: () -> Unit,
) {
    val (label, containerColor, contentColor) = when {
        isDataSaverActive && networkType == com.example.cloudmusic.data.local.NetworkType.CELLULAR ->
            Triple(
                "데이터 절약",
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        isDataSaverActive ->
            Triple(
                "절약 ON",
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
        networkType == com.example.cloudmusic.data.local.NetworkType.CELLULAR ->
            Triple(
                "모바일 데이터",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        networkType == com.example.cloudmusic.data.local.NetworkType.WIFI ->
            Triple(
                "Wi-Fi",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        else -> return
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(24.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
