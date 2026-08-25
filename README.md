# CloudMusicPlayer

Kotlin + Jetpack Compose + Media3(ExoPlayer)로 만든, Google Drive 음악을
바로 스트리밍하는 안드로이드 앱입니다.

## 포함된 기능
- Google 계정 로그인 (Drive 읽기 전용 권한만 사용)
- Drive 안의 오디오 파일 자동 검색 → 라이브러리 목록
- 탭하면 바로 스트리밍 재생 (다운로드 후 재생 아님, Range 스트리밍)
- **백그라운드 재생**: 앱을 나가거나 화면을 꺼도 계속 재생되고,
  알림/잠금화면에서 재생·일시정지·다음·이전 컨트롤 가능 (Media3 MediaSessionService)
- 이어폰 분리 시 자동 일시정지, 미디어 버튼(블루투스 등) 연동
- 검색, 셔플, 반복(끄기/전체/한곡), 시크바가 있는 풀스크린 플레이어
- **풀스크린 플레이어를 아래로 스와이프해서 닫기** (뒤로가기 버튼으로도 닫힘)
- **앨범 커버 표시**: Drive 썸네일이 있으면 미니플레이어·풀스크린 플레이어에 그대로 노출
- **즐겨찾기**: 곡마다 ♡ 아이콘으로 즐겨찾기 추가/해제, 즐겨찾기 탭에서 모아보고 바로 재생
- **재생목록**: 새 재생목록 생성, 곡을 원하는 재생목록에 추가, 재생목록 상세에서 전체재생/개별재생/곡 삭제
- **최근 재생**: 라이브러리 상단에 최근 들은 곡을 가로 스크롤로 보여주고 탭하면 바로 재생
- **오프라인 캐싱**: 한 번 스트리밍한 구간은 디스크에 캐싱(최대 200MB)해서, 다시 들을 때 데이터를 아끼고 버퍼링 없이 즉시 재생
- **대용량 라이브러리 지원**: Drive 파일이 몇천 개여도 nextPageToken을 따라가며 전부 불러옴
- 하단 탭 네비게이션 (라이브러리 / 재생목록 / 즐겨찾기)
- 라이브러리 목록에서 현재 재생 중인 곡 하이라이트 표시
- 단정한 무채색+인디고 포인트의 Material3 테마 (라이트/다크 자동 대응)

## 프로젝트 구조
```
app/src/main/java/com/example/cloudmusic/
 ├─ MainActivity.kt              # 화면 호스팅, 로그인 결과 처리
 ├─ CloudMusicApp.kt
 ├─ data/
 │   ├─ model/Track.kt
 │   └─ drive/
 │        ├─ DriveAuthManager.kt # Google 로그인 + access token 발급
 │        └─ DriveRepository.kt  # Drive 파일 목록 조회, 스트리밍 URL 생성
 ├─ playback/
 │   ├─ MusicService.kt          # MediaSessionService (백그라운드 재생 핵심)
 │   ├─ PlaybackController.kt    # UI <-> 서비스 간 MediaController 래퍼
 │   └─ TokenProvider.kt         # 서비스가 읽는 access token 홀더
 └─ ui/
     ├─ MainViewModel.kt
     ├─ theme/ (Color, Type, Theme)
     ├─ screens/ (LibraryScreen, PlayerScreen)
     └─ components/MiniPlayer.kt
```

## 실행 전 필수 설정 (가장 중요)

이 앱은 Google Drive API를 쓰기 때문에, **Google Cloud Console에서 OAuth 클라이언트를
직접 등록**해야 합니다. 이걸 안 하면 로그인 단계에서 실패합니다.

1. https://console.cloud.google.com/ 에서 새 프로젝트 생성
2. "API 및 서비스 > 라이브러리"에서 **Google Drive API** 활성화
3. "API 및 서비스 > OAuth 동의 화면" 설정 (외부/테스트 사용자로 본인 계정 추가)
4. "사용자 인증 정보 > 사용자 인증 정보 만들기 > OAuth 클라이언트 ID"
   - 애플리케이션 유형: **Android**
   - 패키지 이름: `com.example.cloudmusic` (build.gradle.kts의 applicationId와 동일해야 함)
   - SHA-1 인증서 지문: 디버그 키스토어 기준으로 아래 명령으로 확인
     ```
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
5. 패키지 이름을 바꾸고 싶다면 `app/build.gradle.kts`의 `namespace`/`applicationId`와
   모든 코틀린 파일의 `package com.example.cloudmusic` 선언을 함께 바꿔주세요.

> Google Sign-In은 별도의 `client_id` 값을 코드에 적지 않아도, 위 단계에서
> 패키지명 + SHA-1로 등록만 해두면 `GoogleSignInOptions.DEFAULT_SIGN_IN` 으로 동작합니다.

## 빌드 방법
Android Studio(Iguana 이상)에서 이 폴더를 **Open**으로 열면 Gradle 동기화가 자동으로
진행됩니다. 이후 실제 기기 또는 Drive 로그인이 가능한 에뮬레이터(Google Play 포함 이미지)에서
실행하세요. (`gradlew` 래퍼 jar 바이너리는 포함되어 있지 않으니, Android Studio가 자동 생성하는
것을 사용하거나 `gradle wrapper` 명령으로 한 번 생성해주세요.)

## 알려진 한계 / 다음에 보완하면 좋은 부분
- **즐겨찾기·재생목록은 SharedPreferences에 저장됩니다.** Room 같은 정식 DB는 아니라서
  데이터가 아주 많아지면(수천 곡) 비효율적일 수 있습니다.
- **토큰 만료**: Drive access token은 보통 1시간 정도 유효합니다. 50분마다 자동
  재발급하도록 되어 있지만(`startTokenRefreshLoop`), 더 안정적으로 하려면
  refresh token을 서버에서 관리하는 방식을 고려하세요.
- **오디오 길이/메타데이터**: 현재는 Drive API가 주는 파일명/썸네일만 사용합니다.
  정확한 재생시간, 아티스트 태그를 보여주려면 MediaMetadataRetriever나
  ID3 태그 파싱을 추가하면 좋습니다.
- **Dropbox 등 다른 클라우드**: 현재는 Google Drive만 연동되어 있습니다. 구조상
  `DriveRepository`와 동일한 인터페이스로 `DropboxRepository`를 추가하면 확장 가능합니다.
- **캐시 용량은 고정 200MB**: `MusicService.CACHE_MAX_BYTES`에서 조절할 수 있습니다.
  Wi-Fi에서만 캐싱하게 하는 등의 정책은 아직 없습니다.
