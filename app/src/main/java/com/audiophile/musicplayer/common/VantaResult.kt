package com.audiophile.musicplayer.common

/**
 * Unified result wrapper for all VANTA async operations.
 *
 * Usage:
 *   Success  → VantaResult.Success(data)
 *   Error    → VantaResult.Error("Human-readable message", cause)
 *   Loading  → VantaResult.Loading
 *
 * Flows should be typed as Flow<VantaResult<T>> so the UI can render
 * loading skeletons, error states, and content in a single collect.
 */
sealed class VantaResult<out T> {

    /** The operation completed and produced [data]. */
    data class Success<T>(val data: T) : VantaResult<T>()

    /** The operation failed with a human-readable [message] and optional [cause]. */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : VantaResult<Nothing>()

    /** The operation is in progress. */
    data object Loading : VantaResult<Nothing>()

    // ── Convenience helpers ──────────────────────────────────────────────────

    val isSuccess get() = this is Success
    val isError   get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorMessageOrNull(): String? = (this as? Error)?.message

    inline fun onSuccess(block: (T) -> Unit): VantaResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onError(block: (Error) -> Unit): VantaResult<T> {
        if (this is Error) block(this)
        return this
    }

    inline fun <R> map(transform: (T) -> R): VantaResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error   -> this
        is Loading -> Loading
    }
}

/** Wraps a suspending [block] in a try/catch, returning Success or Error. */
suspend fun <T> vantaRunCatching(
    errorMessage: String = "An unexpected error occurred",
    block: suspend () -> T
): VantaResult<T> = try {
    VantaResult.Success(block())
} catch (e: Exception) {
    VantaResult.Error(errorMessage, e)
}
