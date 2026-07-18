package com.audiophile.musicplayer.account

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Adds the current Firebase ID token and retries once with a refreshed token on 401. */
class FirebaseIdTokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val initialToken = currentToken(forceRefresh = false)
        val initialRequest = chain.request().withFirebaseToken(initialToken)
        val initialResponse = chain.proceed(initialRequest)
        if (initialResponse.code != 401 || initialToken.isNullOrBlank()) return initialResponse

        initialResponse.close()
        return chain.proceed(chain.request().withFirebaseToken(currentToken(forceRefresh = true)))
    }

    private fun currentToken(forceRefresh: Boolean): String? = runCatching {
        val user = FirebaseAuth.getInstance().currentUser ?: return@runCatching null
        Tasks.await(user.getIdToken(forceRefresh), 10, TimeUnit.SECONDS).token
    }.getOrNull()

    private fun Request.withFirebaseToken(token: String?): Request = if (token.isNullOrBlank()) {
        this
    } else {
        newBuilder().header("Authorization", "Bearer $token").build()
    }
}
