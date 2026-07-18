package com.audiophile.musicplayer.account

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthSessionManager(
    context: Context,
    private val accountManager: AccountManager
) {
    sealed interface State {
        data object Unavailable : State
        data object SignedOut : State
        data class SignedIn(val uid: String, val email: String?, val displayName: String?) : State
        data class Failed(val message: String) : State
    }

    private val auth: FirebaseAuth? = FirebaseApp.initializeApp(context.applicationContext)
        ?.let { FirebaseAuth.getInstance(it) }
    private val _state = MutableStateFlow<State>(auth?.currentUser?.toState() ?: if (auth == null) State.Unavailable else State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _state.value = user?.toState() ?: State.SignedOut
        user?.syncAccountProfile()
    }

    init {
        auth?.addAuthStateListener(authStateListener)
    }

    suspend fun signIn(email: String, password: String): State = authenticate {
        auth?.signInWithEmailAndPassword(email.trim(), password)?.await()?.user
    }

    suspend fun register(email: String, password: String): State = authenticate {
        auth?.createUserWithEmailAndPassword(email.trim(), password)?.await()?.user
    }

    suspend fun idToken(): String? = auth?.currentUser?.getIdToken(false)?.await()?.token

    fun signOut() {
        auth?.signOut()
        accountManager.signOut()
        _state.value = if (auth == null) State.Unavailable else State.SignedOut
    }

    fun close() {
        auth?.removeAuthStateListener(authStateListener)
    }

    private suspend fun authenticate(action: suspend () -> FirebaseUser?): State {
        if (auth == null) return State.Unavailable
        return try {
            val user = action() ?: return State.Failed("Sign-in did not return an account.")
            user.syncAccountProfile()
            user.toState().also { _state.value = it }
        } catch (_: Exception) {
            State.Failed("Sign-in failed. Check your email, password, and Firebase setup.").also { _state.value = it }
        }
    }

    private fun FirebaseUser.syncAccountProfile() {
        accountManager.linkCloudIdentity(uid, email, displayName, "firebase")
    }

    private fun FirebaseUser.toState(): State.SignedIn = State.SignedIn(uid, email, displayName)
}
