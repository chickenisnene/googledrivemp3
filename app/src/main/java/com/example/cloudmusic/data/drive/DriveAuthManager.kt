package com.example.cloudmusic.data.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google 로그인(Drive 읽기 전용 권한)과 access token 발급을 담당.
 *
 * 주의: 실제 동작을 위해서는 Google Cloud Console에서
 *  1) OAuth 2.0 클라이언트 ID(Android 유형, 패키지명 + SHA-1 등록)
 *  2) Drive API 활성화
 * 가 선행되어야 한다. README.md 참고.
 */
class DriveAuthManager(private val context: Context) {

    companion object {
        private const val SCOPE_DRIVE_READONLY =
            "oauth2:https://www.googleapis.com/auth/drive.readonly"
    }

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.readonly"))
            .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener { onComplete() }
    }

    /**
     * 현재 로그인된 계정으로 Drive API access token을 받아온다.
     *
     * 반환 타입: [TokenResult]
     *  - [TokenResult.Success]  : 토큰 발급 성공
     *  - [TokenResult.NeedsConsent] : 사용자가 권한 재동의를 해야 함 (UserRecoverableAuthException).
     *    [TokenResult.NeedsConsent.recoveryIntent]로 재로그인 UI를 띄우면 된다.
     *  - [TokenResult.Error]    : 복구 불가능한 에러 (계정 없음, 네트워크 등)
     *
     * 이전 버전은 예외를 그냥 throw해서 앱이 크래시되었다.
     */
    suspend fun fetchAccessToken(account: GoogleSignInAccount): TokenResult =
        withContext(Dispatchers.IO) {
            val androidAccount: Account = account.account
                ?: return@withContext TokenResult.Error("로그인된 계정 정보를 찾을 수 없습니다.")
            runCatching {
                val token = GoogleAuthUtil.getToken(context, androidAccount, SCOPE_DRIVE_READONLY)
                TokenResult.Success(token)
            }.getOrElse { e ->
                when (e) {
                    is UserRecoverableAuthException -> {
                        val intent = e.intent
                        if (intent != null) TokenResult.NeedsConsent(intent)
                        else TokenResult.Error("권한 동의가 필요하지만 실행할 수 없습니다.")
                    }
                    else ->
                        TokenResult.Error(e.message ?: "알 수 없는 인증 오류")
                }
            }
        }

    sealed class TokenResult {
        data class Success(val token: String) : TokenResult()
        data class NeedsConsent(val recoveryIntent: Intent) : TokenResult()
        data class Error(val message: String) : TokenResult()
    }
}
