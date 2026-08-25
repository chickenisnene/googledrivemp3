package com.example.cloudmusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.cloudmusic.playback.TokenProvider
import okhttp3.OkHttpClient

/**
 * Coil(이미지 로딩 라이브러리)이 앱 전역에서 쓸 OkHttpClient를 지정한다.
 *
 * 주의: Drive 썸네일(thumbnailLink)은 googleapis.com이 아니라 별도의 호스트
 * (예: lh3.googleusercontent.com, drive-thirdparty.googleusercontent.com)에서 내려오고,
 * 보통 인증 헤더 없이도 바로 열린다. 오히려 모든 요청에 무조건 Authorization 헤더를
 * 붙이면 썸네일 서버가 이를 잘못된 인증으로 보고 요청을 거부할 수 있다.
 * 그래서 Drive API 본체(googleapis.com)로 가는 요청에만 토큰을 붙인다.
 */
class CloudMusicApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = TokenProvider.accessToken
                val request = if (token != null && original.url.host.endsWith("googleapis.com")) {
                    original.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else original
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }
}

