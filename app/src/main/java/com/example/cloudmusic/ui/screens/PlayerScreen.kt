package com.example.cloudmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track
import com.example.cloudmusic.playback.PlaybackUiState
import com.example.cloudmusic.ui.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * 풀스크린 플레이어.
 * - 위쪽 화살표 버튼 탭 / 아래로 스와이프 / 뒤로가기 → 닫힘
 * - 앨범아트 왼쪽 더블탭 → -10초, 오른쪽 더블탭 → +10초
 * - 속도 버튼 → 0.75x~2.0x 순환
 * - 슬립 타이머 버튼 → 15/30/45/60분 또는 취소
 * - 큐 버튼 → 업 넥스트 바텀 시트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    playlists: List<Playlist>,
    queue: List<Track>,
    sleepTimerRemainingMs: Long?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onJumpToQueueItem: (Int) -> Unit,
    currentPositionProvider: () -> Long,
    durationProvider: () -> Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(1f) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        while (true) {
            if (!isUserSeeking) {
                sliderPosition = currentPositionProvider().toFloat()
                duration = durationProvider().toFloat().coerceAtLeast(1f)
            }
            delay(500)
        }
    }
    LaunchedEffect(state.currentTrackId) { sliderPosition = 0f }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        val newValue = (offsetY.value + dragAmount).coerceAtLeast(0f)
                        scope.launch { offsetY.snapTo(newValue) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > dismissThresholdPx) onClose()
                            offsetY.animateTo(0f)
                        }
                    },
                )
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // ── 상단 핸들 + 닫기 버튼 ─────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.weight(1f))

            // ── 앨범아트 (더블탭 좌우 = ±10초) ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val delta = if (offset.x < size.width / 2) -10_000L else 10_000L
                                onSeekRelative(delta)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val artworkModel: Any? = state.currentArtworkData ?: state.currentArtworkUri
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // 왼쪽/오른쪽 반투명 힌트 레이어 (처음 보는 사람이 눈치채도록)
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {}
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {}
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 곡 제목 + 아티스트 + 즐겨찾기 + 재생목록 ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrackTitle ?: "재생 중인 곡 없음",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!state.currentArtist.isNullOrBlank()) {
                        Text(
                            text = state.currentArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showPlaylistDialog = true }) {
                    Icon(
                        Icons.Filled.PlaylistAdd,
                        contentDescription = "재생목록에 추가",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 시크바 ────────────────────────────────────────────────────────
            Slider(
                value = sliderPosition.coerceIn(0f, duration),
                valueRange = 0f..duration,
                onValueChange = { isUserSeeking = true; sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()); isUserSeeking = false },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatMs(sliderPosition.toLong()), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(duration.toLong()), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            // ── 셔플 / 이전 / 재생-일시정지 / 다음 / 반복 ────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "셔플",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "이전", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "일시정지" else "재생",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "다음", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "반복",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 속도 / 슬립 타이머 / 업 넥스트 ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 속도 버튼: 탭할 때마다 다음 속도로 순환
                val currentSpeedIdx = SPEED_OPTIONS.indexOfFirst { it == state.playbackSpeed }
                    .coerceAtLeast(0)
                TextButton(
                    onClick = {
                        val next = SPEED_OPTIONS[(currentSpeedIdx + 1) % SPEED_OPTIONS.size]
                        onSetSpeed(next)
                    }
                ) {
                    val label = if (state.playbackSpeed == 1.0f) "배속"
                                else "${state.playbackSpeed}x"
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }

                // 슬립 타이머: 남은 시간 표시 또는 아이콘
                IconButton(onClick = { showSleepTimerDialog = true }) {
                    if (sleepTimerRemainingMs != null) {
                        Text(
                            text = formatMs(sleepTimerRemainingMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "슬립 타이머",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 업 넥스트 (현재 큐)
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "업 넥스트",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    // ── 재생목록 선택 다이얼로그 ─────────────────────────────────────────────
    if (showPlaylistDialog) {
        PlayerAddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onPickPlaylist = { onAddToPlaylist(it); showPlaylistDialog = false },
            onCreatePlaylist = { onCreatePlaylistAndAdd(it); showPlaylistDialog = false },
        )
    }

    // ── 슬립 타이머 다이얼로그 ───────────────────────────────────────────────
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = sleepTimerRemainingMs != null,
            remainingMs = sleepTimerRemainingMs,
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { minutes -> onStartSleepTimer(minutes); showSleepTimerDialog = false },
            onCancel = { onCancelSleepTimer(); showSleepTimerDialog = false },
        )
    }

    // ── 업 넥스트 바텀 시트 ──────────────────────────────────────────────────
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            QueueBottomSheet(
                queue = queue,
                currentIndex = state.currentQueueIndex,
                onJumpTo = { idx -> onJumpToQueueItem(idx); showQueueSheet = false },
            )
        }
    }
}

// ── 슬립 타이머 다이얼로그 ───────────────────────────────────────────────────

@Composable
private fun SleepTimerDialog(
    isActive: Boolean,
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isActive) "슬립 타이머 실행 중" else "슬립 타이머")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive && remainingMs != null) {
                    Text(
                        "${formatMs(remainingMs)} 후 정지",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                listOf(15, 30, 45, 60).forEach { minutes ->
                    OutlinedButton(
                        onClick = { onSetTimer(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${minutes}분 후 정지")
                    }
                }
            }
        },
        confirmButton = {
            if (isActive) {
                TextButton(onClick = onCancel) { Text("타이머 취소") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

// ── 업 넥스트 바텀 시트 ──────────────────────────────────────────────────────

@Composable
private fun QueueBottomSheet(
    queue: List<Track>,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) listState.animateScrollToItem(currentIndex)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            "업 넥스트",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("재생 목록이 비어있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                itemsIndexed(queue) { idx, track ->
                    val isCurrent = idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable { onJumpTo(idx) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ── 재생목록 선택 다이얼로그 ─────────────────────────────────────────────────

@Composable
private fun PlayerAddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPickPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("재생목록에 추가") },
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

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
