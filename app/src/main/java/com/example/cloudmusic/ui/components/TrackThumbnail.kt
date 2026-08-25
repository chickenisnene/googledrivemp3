package com.example.cloudmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cloudmusic.data.model.Track

/**
 * 트랙 한 곡의 작은 표지 썸네일.
 *
 * ★ 이전 버전은 Drive 썸네일이 없으면 ArtworkExtractor를 통해
 *   ID3 태그를 읽으려고 HTTP 요청(파일 앞부분 다운로드)을 보냈다.
 *   라이브러리를 스크롤하는 것만으로도 수백 개 파일의 앞부분을 내려받게 되어
 *   데이터를 심하게 낭비했다.
 *
 *   해결: 목록에서는 Drive thumbnailLink만 사용한다.
 *   ID3 임베디드 아트워크는 현재 재생 중인 트랙에만 표시한다.
 *   (ExoPlayer가 재생하면서 이미 읽어오는 것이므로 추가 데이터 비용 없음)
 *
 * 우선순위:
 * 1) Drive thumbnailLink → AsyncImage로 표시
 * 2) 없으면 → 음표 아이콘 (추가 네트워크 요청 없음)
 */
@Composable
fun TrackThumbnail(
    track: Track,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = remember(size) { RoundedCornerShape((size.value / 4).dp) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (track.thumbnailUrl != null) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
        }
    }
}
