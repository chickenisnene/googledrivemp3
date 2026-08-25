package com.example.cloudmusic.playback

/**
 * 현재 유효한 Google Drive access token을 보관하는 단순한 싱글톤 홀더.
 * MainViewModel이 로그인/토큰 갱신 시점마다 갱신하고,
 * MusicService의 OkHttp Interceptor가 매 요청마다 이 값을 읽어 Authorization 헤더에 붙인다.
 */
object TokenProvider {
    @Volatile
    var accessToken: String? = null
}
