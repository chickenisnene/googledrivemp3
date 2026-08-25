package com.example.cloudmusic.data.local

import android.content.Context
import com.example.cloudmusic.data.model.Playlist
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

    fun sanitizeAccountKey(rawKey: String?): String =
        rawKey?.takeIf { it.isNotBlank() } ?: "unknown_account"

    private fun favoritesKey(accountKey: String) = "favorite_track_ids:$accountKey"
    private fun playlistsKey(accountKey: String) = "playlists:$accountKey"
    private fun recentlyPlayedKey(accountKey: String) = "recently_played:$accountKey"
    private fun lastPositionKey(accountKey: String) = "last_position:$accountKey"
}
