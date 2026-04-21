package androidx.compose.foundation.internal

internal inline fun checkPrecondition(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        throw IllegalStateException(lazyMessage())
    }
}

internal inline fun requirePrecondition(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        throw IllegalArgumentException(lazyMessage())
    }
}

internal inline fun <T : Any> requirePreconditionNotNull(
    value: T?,
    lazyMessage: () -> String,
): T {
    return value ?: throw IllegalArgumentException(lazyMessage())
}

internal fun throwIndexOutOfBoundsException(message: String): Nothing {
    throw IndexOutOfBoundsException(message)
}
