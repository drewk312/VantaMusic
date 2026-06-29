package com.audiophile.musicplayer.common

/**
 * Sealed result type for repository and use-case boundaries.
 * Replaces stringly-typed errors and unchecked exceptions.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val userMessage: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

fun <T> T.toSuccess(): Result.Success<T> = Result.Success(this)

fun <T> Throwable.toResult(userMessage: String? = null): Result<T> = Result.Error(this, userMessage)
