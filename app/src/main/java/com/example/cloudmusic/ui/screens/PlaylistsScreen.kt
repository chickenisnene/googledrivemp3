package com.example.cloudmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    allTracks: List<Track>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist, Track?) -> Unit,
    onRemoveTrack: (Playlist, Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }

    BackHandler(enabled = selectedPlaylist != null) { selectedPlaylistId = null }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedPlaylist == null) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("재생목록", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "재생목록 만들기")
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("아직 재생목록이 없어요.\n오른쪽 위 + 버튼으로 만들어보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedPlaylistId = playlist.id }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${playlist.trackIds.size}곡",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDeletePlaylist(playlist) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "재생목록 삭제")
                                }
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        } else {
            PlaylistDetail(
                playlist = selectedPlaylist,
                tracks = remember(selectedPlaylist, allTracks) {
                    val byId = allTracks.associateBy { it.id }  // 곡마다 전체 목록을 훑지 않도록
                    selectedPlaylist.trackIds.mapNotNull { byId[it] }
                },
                onBack = { selectedPlaylistId = null },
                onPlayAll = { onPlayPlaylist(selectedPlaylist, null) },
                onPlayTrack = { onPlayPlaylist(selectedPlaylist, it) },
                onRemoveTrack = { onRemoveTrack(selectedPlaylist, it) },
            )
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("새 재생목록") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(name)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("만들기") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Spacer(Modifier.width(4.dp))
            Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))

        if (tracks.isNotEmpty()) {
            Button(onClick = onPlayAll, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("전체 재생")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("이 재생목록에는 아직 곡이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(tracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.cloudmusic.ui.components.TrackThumbnail(track = track, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveTrack(track) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "재생목록에서 제거")
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}
