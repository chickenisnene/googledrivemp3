package com.example.cloudmusic.data.local

import android.content.Context
import com.example.cloudmusic.data.model.Playlist
import com.example.cloudmusic.data.model.Track
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 즐겨찾기, 재생목록, 최근재생, 이어듣기 위치를 기기 내 SharedPreferences에 JSON으로 저장한다.
 * Room처럼 정식 DB는 아니지만, 앱을 완전히 종료해도 데이터가 남도록 해주는 가벼운 영속화 계층.
 *
 * 모든 키는 accountKey(로그인 계정의 이메일)로 네임스페이스를 나눈다.
 * 그래야 A 계정에서 만든 데이터가 B 계정으로 전환했을 때 보이지 않는다.
 */
class PreferencesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("cloud_music_prefs", Context.MODE_PRIVATE)

    // ── 즐겨찾기 ──────────────────────────────────────────────────────────

    fun loadFavoriteIds(accountKey: String): Set<String> {
        val raw = prefs.getString(favoritesKey(accountKey), null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveFavoriteIds(accountKey: String, ids: Set<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString(favoritesKey(accountKey), arr.toString()).apply()
    }

    // ── 재생목록 ──────────────────────────────────────────────────────────

    fun loadPlaylists(accountKey: String): List<Playlist> {
        val raw = prefs.getString(playlistsKey(accountKey), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val trackIdsArr = obj.getJSONArray("trackIds")
                val trackIds = (0 until trackIdsArr.length()).map { trackIdsArr.getString(it) }
                Playlist(id = obj.getString("id"), name = obj.getString("name"), trackIds = trackIds)
            }
        }.getOrDefault(emptyList())
    }

    fun savePlaylists(accountKey: String, playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val trackIdsArr = JSONArray()
            playlist.trackIds.forEach { trackIdsArr.put(it) }
            obj.put("trackIds", trackIdsArr)
            arr.put(obj)
        }
        prefs.edit().putString(playlistsKey(accountKey), arr.toString()).apply()
    }

    // ── 최근 재생 목록 ────────────────────────────────────────────────────

    fun loadRecentlyPlayedIds(accountKey: String): List<String> {
        val raw = prefs.getString(recentlyPlayedKey(accountKey), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun saveRecentlyPlayedIds(accountKey: String, ids: List<String>) {
        val arr = JSONArray()
        ids.take(30).forEach { arr.put(it) }
        prefs.edit().putString(recentlyPlayedKey(accountKey), arr.toString()).apply()
    }

    // ── 마지막 재생 위치 (앱을 완전히 껐다 켜도 이어듣기 가능하도록) ─────────────

    data class LastPosition(val trackId: String, val positionMs: Long)

    fun loadLastPosition(accountKey: String): LastPosition? {
        val raw = prefs.getString(lastPositionKey(accountKey), null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            LastPosition(obj.getString("trackId"), obj.getLong("positionMs"))
        }.getOrNull()
    }

    fun saveLastPosition(accountKey: String, trackId: String, positionMs: Long) {
        val obj = JSONObject()
        obj.put("trackId", trackId)
        obj.put("positionMs", positionMs)
        prefs.edit().putString(lastPositionKey(accountKey), obj.toString()).apply()
    }

    fun clearLastPosition(accountKey: String) {
        prefs.edit().remove(lastPositionKey(accountKey)).apply()
    }

    // ── 설정 / 사용량 (계정과 무관한 전역 값) ───────────────────────────────

    fun loadCacheMaxMb(): Int = prefs.getInt("cache_max_mb", DEFAULT_CACHE_MB)

    fun saveCacheMaxMb(mb: Int) { prefs.edit().putInt("cache_max_mb", mb).apply() }

    /** 이번 달에 모바일 데이터로 받은 오디오 바이트. 달이 바뀌면 새로 시작한다. */
    fun loadCellularBytesThisMonth(): Long = prefs.getLong(cellularKey(), 0L)

    fun addCellularBytes(bytes: Long) {
        prefs.edit().putLong(cellularKey(), loadCellularBytesThisMonth() + bytes).apply()
    }

    private fun cellularKey() =
        "cellular_bytes:" + SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    // ── 라이브러리 목록 로컬 저장 ───────────────────────────────────────────
    // 수천 곡이라 SharedPreferences(앱 시작 때 통째로 메모리에 올라감) 대신 파일로 저장한다.
    // thumbnailUrl은 만료되는 링크라 저장하지 않는다. 새로고침 때 다시 채워진다.

    fun loadTracks(accountKey: String): List<Track> = runCatching {
        val arr = JSONArray(tracksFile(accountKey).readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Track(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                mimeType = o.getString("mimeType"),
                sizeBytes = o.optLong("sizeBytes"),
            )
        }
    }.getOrDefault(emptyList())

    fun saveTracks(accountKey: String, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("title", it.title).put("artist", it.artist)
                .put("mimeType", it.mimeType).put("sizeBytes", it.sizeBytes))
        }
        val file = tracksFile(accountKey)
        val tmp = File(file.parentFile, file.name + ".tmp")
        // 임시 파일에 쓴 뒤 교체해서, 쓰다 죽어도 이전 목록이 깨지지 않게 한다.
        runCatching { tmp.writeText(arr.toString()); tmp.renameTo(file) }
    }

    fun clearTracks(accountKey: String) { tracksFile(accountKey).delete() }

    private fun tracksFile(accountKey: String) =
        File(appContext.filesDir, "library_" + accountKey.replace(Regex("[^A-Za-z0-9._@-]"), "_") + ".json")

    companion object { const val DEFAULT_CACHE_MB = 200 }

    fun sanitizeAccountKey(rawKey: String?): String =
        rawKey?.takeIf { it.isNotBlank() } ?: "unknown_account"

    private fun favoritesKey(accountKey: String) = "favorite_track_ids:$accountKey"
    private fun playlistsKey(accountKey: String) = "playlists:$accountKey"
    private fun recentlyPlayedKey(accountKey: String) = "recently_played:$accountKey"
    private fun lastPositionKey(accountKey: String) = "last_position:$accountKey"
}
