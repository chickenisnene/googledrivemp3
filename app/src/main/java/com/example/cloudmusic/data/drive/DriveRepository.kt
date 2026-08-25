package com.example.cloudmusic.data.drive

import com.example.cloudmusic.data.model.Track
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Drive 안의 오디오 파일을 조회하고, 직접 스트리밍할 수 있는 다운로드 URL을 만들어준다.
 *
 * 실제 오디오 바이트는 ExoPlayer가 Authorization 헤더(access token)를 붙여
 * `https://www.googleapis.com/drive/v3/files/{id}?alt=media` 로 직접 요청해서 스트리밍한다.
 * 즉, 파일을 통째로 미리 내려받지 않고 Range 요청으로 부분부분 스트리밍된다.
 */
class DriveRepository(private val context: android.content.Context) {

    // 오디오로 취급할 mimeType
    private val audioMimePrefixes = listOf("audio/")

    // ── Drive 서비스 캐싱 ─────────────────────────────────────────────────────
    // 매 API 호출마다 Drive 인스턴스를 새로 만들면 NetHttpTransport 초기화 비용이 반복된다.
    // 같은 계정에 대한 Drive 인스턴스를 캐시해서 재사용한다.
    // 계정이 바뀌면 (id가 달라지면) 새 인스턴스를 만든다.
    @Volatile private var cachedDrive: Drive? = null
    @Volatile private var cachedAccountId: String? = null

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val accountId = account.id
        if (accountId != null && accountId == cachedAccountId) {
            cachedDrive?.let { return it }
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account
        val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("CloudMusicPlayer")
            .build()
        cachedDrive = drive
        cachedAccountId = accountId
        return drive
    }

    /** 계정 전환/로그아웃 시 캐시를 비운다. */
    fun clearCache() {
        cachedDrive = null
        cachedAccountId = null
    }

    /**
     * 로그인된 Drive 계정에서 오디오 파일 목록을 가져온다.
     * Drive API는 한 번에 최대 1000개까지만 주기 때문에, nextPageToken을 따라가며
     * 라이브러리에 곡이 몇천 개가 있어도 전부 가져온다.
     * (무한 루프 방지를 위해 안전 상한선으로 5000개에서 멈춘다.)
     */
    suspend fun listAudioTracks(account: GoogleSignInAccount): List<Track> = withContext(Dispatchers.IO) {
        val drive = getDriveService(account)
        val query = audioMimePrefixes.joinToString(" or ") { "mimeType contains '$it'" }

        val allTracks = mutableListOf<Track>()
        var pageToken: String? = null

        do {
            val result = drive.files().list()
                .setQ("($query) and trashed = false")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, mimeType, size, thumbnailLink)")
                .setPageSize(1000)
                .setPageToken(pageToken)
                .execute()

            result.files.orEmpty().forEach { file ->
                val rawName = file.name?.substringBeforeLast('.') ?: "제목 없음"
                val (artist, title) = parseArtistTitle(rawName)
                allTracks += Track(
                    id = file.id,
                    title = title,
                    artist = artist,
                    mimeType = file.mimeType ?: "audio/*",
                    sizeBytes = file.getSize() ?: 0L,
                    thumbnailUrl = file.thumbnailLink,
                )
            }
            pageToken = result.nextPageToken
        } while (pageToken != null && allTracks.size < MAX_TRACKS_SAFETY_LIMIT)

        allTracks
    }

    /** ExoPlayer가 직접 요청할 스트리밍 URL */
    fun streamUrlFor(trackId: String): String = Companion.streamUrlFor(trackId)

    companion object {
        private const val MAX_TRACKS_SAFETY_LIMIT = 5000

        /** UI 레이어 등에서 DriveRepository 인스턴스 없이도 스트리밍 URL을 만들 수 있게 한다. */
        fun streamUrlFor(trackId: String): String =
            "https://www.googleapis.com/drive/v3/files/$trackId?alt=media"

        /**
         * 파일명에서 아티스트와 제목을 분리한다.
         * 지원 형식:
         *   "Yorushika - Martian"         → artist="Yorushika",           title="Martian"
         *   "Vaundy - Todome no Waltz"    → artist="Vaundy",              title="Todome no Waltz"
         *   "SomeTrack"                   → artist=UNKNOWN_ARTIST_MARKER, title="SomeTrack"
         *
         * 구분자는 " - " (앞뒤 공백 포함)를 기준으로 하고, 첫 번째 " - "만 분리점으로 쓴다.
         * 그래야 "Artist - Title - Remix" 같은 이름에서 title이 "Title - Remix"로 보존된다.
         *
         * UI 표시용 문자열("알 수 없는 아티스트")은 data layer에 두지 않고
         * UNKNOWN_ARTIST_MARKER 상수를 sentinel로 쓴다.
         */
        const val UNKNOWN_ARTIST_MARKER = "__unknown__"

        fun parseArtistTitle(rawName: String): Pair<String, String> {
            val separatorIdx = rawName.indexOf(" - ")
            return if (separatorIdx > 0) {
                val artist = rawName.substring(0, separatorIdx).trim()
                val title  = rawName.substring(separatorIdx + 3).trim()
                artist to title
            } else {
                UNKNOWN_ARTIST_MARKER to rawName.trim()
            }
        }
    }
}
