package com.example.cloudmusic.data.model

/**
 * 사용자가 만든 재생목록. trackIds는 Drive 파일 id를 그대로 참조한다.
 * (지금은 메모리에만 보관되며, 앱을 완전히 종료하면 사라진다 — README 참고)
 */
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
)
