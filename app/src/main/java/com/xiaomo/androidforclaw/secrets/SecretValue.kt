package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/secret-value.ts
 *
 * Secret value type checking and assertion utilities.
 */

/**
 * Expected resolved value shape for a secret.
 * Aligned with TS SecretExpectedResolvedValue.
 */
enum class SecretExpectedResolvedValue(val value: String) {
    STRING("string"),
    STRING_OR_OBJECT("string-or-object");

    companion object {
        fun fromString(s: String): SecretExpectedResolvedValue = when (s) {
            "string" -> STRING
            "string-or-object" -> STRING_OR_OBJECT
            else -> STRING
        }
    }
}

/**
 * Check if a value matches the expected resolved secret shape.
 * Aligned with TS isExpectedResolvedSecretValue.
 */
fun isExpectedResolvedSecretValue(
    value: Any?,
    expected: SecretExpectedResolvedValue
): Boolean = when (expected) {
    SecretExpectedResolvedValue.STRING -> isNonEmptyString(value)
    SecretExpectedResolvedValue.STRING_OR_OBJECT -> isNonEmptyString(value) || isRecord(value)
}

/**
 * Check if a configured plaintext value is present and non-empty.
 * Aligned with TS hasConfiguredPlaintextSecretValue.
 */
fun hasConfiguredPlaintextSecretValue(
    value: Any?,
    expected: SecretExpectedResolvedValue
): Boolean = when (expected) {
    SecretExpectedResolvedValue.STRING -> isNonEmptyString(value)
    SecretExpectedResolvedValue.STRING_OR_OBJECT ->
        isNonEmptyString(value) ||
                (value is Map<*, *> && value.isNotEmpty())
}

/**
 * Assert that a value matches the expected resolved secret shape.
 * Aligned with TS assertExpectedResolvedSecretValue.
 */
fun assertExpectedResolvedSecretValue(
    value: Any?,
    expected: SecretExpectedResolvedValue,
    errorMessage: String
) {
    if (!isExpectedResolvedSecretValue(value, expected)) {
        throw IllegalStateException(errorMessage)
    }
}
