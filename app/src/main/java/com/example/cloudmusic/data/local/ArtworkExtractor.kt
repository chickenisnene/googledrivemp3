package com.example.cloudmusic.data.local

import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.example.cloudmusic.playback.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * mp3/m4a 파일 안에 박혀있는 ID3 앨범아트를 한 번씩 추출해서 캐싱한다.
 *
 * 개선점:
 * - LruCache(200): 가장 오래 안 쓴 항목을 자동으로 제거. 이전의 ConcurrentHashMap은 상한이 없어서
 *   수천 곡의 라이브러리에서 메모리가 계속 늘어났다.
 * - sentinel 패턴 유지: LruCache는 null 값을 허용하지 않으므로 EMPTY_SENTINEL로 "표지 없음"을 표시.
 * - Semaphore(3): 동시에 최대 3개의 추출만 진행해 네트워크/CPU 과부하 방지.
 */
object ArtworkExtractor {

    private val EMPTY_SENTINEL = ByteArray(0)
    // 200개 LRU: 아트워크 평균 ~50KB × 200 = ~10MB (넉넉하지만 무한 증가하지 않음)
    private val cache = LruCache<String, ByteArray>(200)
    private val semaphore = Semaphore(3)

    suspend fun extract(trackId: String, streamUrl: String): ByteArray? {
        cache.get(trackId)?.let { cached ->
            return if (cached === EMPTY_SENTINEL) null else cached
        }

        return withContext(Dispatchers.IO) {
            semaphore.withPermit {
                // 세마포어 대기 중 다른 코루틴이 완료했을 수 있으니 재확인
                cache.get(trackId)?.let { cached ->
                    return@withPermit if (cached === EMPTY_SENTINEL) null else cached
                }

                val retriever = MediaMetadataRetriever()
                val artwork = runCatching {
                    val token = TokenProvider.accessToken
                    val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
                    retriever.setDataSource(streamUrl, headers)
                    retriever.embeddedPicture
                }.getOrNull()
                runCatching { retriever.release() }

                val toCache = artwork ?: EMPTY_SENTINEL
                cache.put(trackId, toCache)
                artwork
            }
        }
    }

    /** 계정 전환/로그아웃 시 캐시를 비운다 (다른 계정의 아트워크가 잔류하지 않도록). */
    fun evictAll() {
        cache.evictAll()
    }
}
