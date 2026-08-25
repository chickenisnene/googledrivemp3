package com.example.cloudmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track
import com.example.cloudmusic.ui.components.TrackThumbnail

@Composable
fun ArtistsScreen(
    artistMap: Map<String, List<Track>>,
    nowPlayingTrackId: String?,
    isPlaying: Boolean,
    selectedTrackIds: Set<String>,
    playlists: List<Playlist>,
    onPlayArtist: (String, Track?) -> Unit,
    onTrackClick: (Track) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAllArtist: (String) -> Unit,
    onClearSelection: () -> Unit,
    onAddSelectionToPlaylist: (String) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var showBulkPlaylistDialog by remember { mutableStateOf(false) }

    val isSelectionMode = selectedTrackIds.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedArtist == null) {
            // ── 아티스트 목록 ────────────────────────────────────────────────
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("아티스트", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))

                if (artistMap.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("라이브러리에서 아티스트 정보를 찾지 못했어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(artistMap.entries.toList()) { (artist, tracks) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedArtist = artist }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 아티스트 첫 글자를 표시
                                    Text(
                                        text = artist.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(artist, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(
                                        "${tracks.size}곡",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        } else {
            // ── 아티스트 상세: 곡 목록 + 다중 선택 ─────────────────────────
            val artist = selectedArtist!!
            val tracks = artistMap[artist] ?: emptyList()

            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))

                // 상단 헤더
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        selectedArtist = null
                        onClearSelection()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(artist, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                }

                Spacer(Modifier.height(8.dp))

                // 전체 재생 / 전체 선택 버튼
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPlayArtist(artist, null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("전체 재생")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isSelectionMode && tracks.all { it.id in selectedTrackIds }) {
                                onClearSelection()
                            } else {
                                onSelectAllArtist(artist)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.SelectAll, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (tracks.all { it.id in selectedTrackIds }) "선택 해제" else "전체 선택")
                    }
                }

                // 다중 선택 중일 때 배너
                if (isSelectionMode) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${selectedTrackIds.size}곡 선택됨",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { showBulkPlaylistDialog = true }) {
                                Text("재생목록에 추가")
                            }
                            TextButton(onClick = onClearSelection) {
                                Text("취소")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn {
                    items(tracks) { track ->
                        val isCurrent  = track.id == nowPlayingTrackId
                        val isSelected = track.id in selectedTrackIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        isCurrent  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else       -> androidx.compose.ui.graphics.Color.Transparent
                                    }
                                )
                                .clickable {
                                    if (isSelectionMode) onToggleSelect(track.id)
                                    else onTrackClick(track)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 선택 체크박스 or 썸네일
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelect(track.id) },
                                    modifier = Modifier.size(44.dp),
                                )
                            } else {
                                Box(modifier = Modifier.size(44.dp)) {
                                    TrackThumbnail(track = track, size = 44.dp)
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(10.dp))
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
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            // 길게 누르지 않아도 선택모드 진입 가능하도록 체크 아이콘 제공
                            if (!isSelectionMode) {
                                IconButton(onClick = { onToggleSelect(track.id) }) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "선택",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    // ── 일괄 플레이리스트 추가 다이얼로그 ───────────────────────────────────
    if (showBulkPlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBulkPlaylistDialog = false },
            title = { Text("${selectedTrackIds.size}곡을 재생목록에 추가") },
            text = {
                Column {
                    if (playlists.isNotEmpty()) {
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddSelectionToPlaylist(playlist.id)
                                        showBulkPlaylistDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.QueueMusic, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${playlist.trackIds.size}곡", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
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
                    onClick = {
                        onCreatePlaylistAndAdd(newName)
                        showBulkPlaylistDialog = false
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("새로 만들고 추가") }
            },
            dismissButton = { TextButton(onClick = { showBulkPlaylistDialog = false }) { Text("취소") } },
        )
    }
}
