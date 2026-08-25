package com.example.cloudmusic.data.local

/**
 * 데이터 절약 모드 여부를 MusicService와 ViewModel이 공유하는 단순한 홀더.
 *
 * ViewModel이 설정하고, MusicService가 읽는다.
 * TokenProvider와 같은 패턴이지만, 목적은 버퍼 크기 조정에 한정된다.
 *
 * isDataSaverMode가 true이면:
 *  - ExoPlayer 최대 버퍼: 20초 (기본 60초 대비 1/3)
 *  - TrackThumbnail: ID3 추출 안 함 (이미 적용됨)
 */
object DataSaverProvider {
    @Volatile var isDataSaverMode: Boolean = false
}
