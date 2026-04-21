package com.ai.assistance.operit.util.exceptions

import kotlinx.coroutines.CancellationException

/**
 * An exception to indicate that an operation was explicitly cancelled by the user.
 * This helps distinguish user cancellation from other network or I/O errors,
 * and allows for clean and silent termination of coroutines without being
 * treated as a general error.
 */
class UserCancellationException(message: String, cause: Throwable? = null) : CancellationException(message) {
    init {
        initCause(cause)
    }
} 