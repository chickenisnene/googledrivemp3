package com.example.cloudmusic.data.model

/**
 * Google Drive에 저장된 음악 파일 하나를 나타내는 모델.
 * streamUrl은 재생 시점에 매번 새로 발급한 access token을 붙여서 사용한다.
 */
data class Track(
    val id: String,            // Drive file id
    val title: String,         // 파일명(확장자 제거)
    val artist: String = "알 수 없는 아티스트",
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
)
