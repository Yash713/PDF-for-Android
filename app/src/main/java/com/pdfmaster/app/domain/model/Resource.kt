package com.pdfmaster.app.domain.model

/**
 * Wraps UI state for any async operation (per the Code Quality Rules: every
 * screen renders one of these instead of juggling separate loading/error flags).
 */
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
    data class Loading(val progress: Float = 0f) : Resource<Nothing>()
    data object Idle : Resource<Nothing>()
}
