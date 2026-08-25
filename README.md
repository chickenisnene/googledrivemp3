# CloudMusicPlayer

Kotlin + Jetpack Compose + Media3(ExoPlayer)로 만든, Google Drive 음악을
바로 스트리밍하는 안드로이드 앱

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
