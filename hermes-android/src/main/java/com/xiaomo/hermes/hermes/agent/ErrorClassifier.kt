package com.xiaomo.hermes.hermes.agent

/**
 * Error Classifier - API 错误分类 + failover 决策
 * 1:1 对齐 hermes/agent/error_classifier.py
 *
 * 分类 API 错误类型，决定是否需要 failover
 */
class ErrorClassifier {

    /**
     * 错误类型枚举
     */
    enum class ErrorType {
        RATE_LIMIT,        // 429 - 需要 failover
        QUOTA_EXCEEDED,    // 配额耗尽 - 需要 failover
        AUTH_ERROR,        // 401/403 - key 无效
        SERVER_ERROR,      // 5xx - 可重试
        TIMEOUT,           // 超时 - 可重试
        INVALID_REQUEST,   // 400 - 不重试
        CONTENT_FILTER,    // 内容过滤 - 不重试
        UNKNOWN            // 未知
    }

    /**
     * 分类错误
     *
     * @param statusCode HTTP 状态码
     * @param errorMessage 错误消息
     * @return 错误类型
     */
    fun classify(statusCode: Int, errorMessage: String?): ErrorType {
        val msg = errorMessage?.lowercase() ?: ""

        return when {
            statusCode == 429 -> ErrorType.RATE_LIMIT
            statusCode == 401 || statusCode == 403 -> ErrorType.AUTH_ERROR
            statusCode in 500..599 -> ErrorType.SERVER_ERROR
            statusCode == 408 -> ErrorType.TIMEOUT
            statusCode == 400 -> ErrorType.INVALID_REQUEST
            msg.contains("quota") -> ErrorType.QUOTA_EXCEEDED
            msg.contains("timeout") -> ErrorType.TIMEOUT
            msg.contains("content filter") -> ErrorType.CONTENT_FILTER
            else -> ErrorType.UNKNOWN
        }
    }

    /**
     * 判断是否需要 failover
     */
    fun shouldFailover(errorType: ErrorType): Boolean {
        return when (errorType) {
            ErrorType.RATE_LIMIT,
            ErrorType.QUOTA_EXCEEDED,
            ErrorType.AUTH_ERROR,
            ErrorType.SERVER_ERROR,
            ErrorType.TIMEOUT -> true
            else -> false
        }
    }

    /**
     * 判断是否可重试（同一 provider 内重试）
     */
    fun isRetryable(errorType: ErrorType): Boolean {
        return when (errorType) {
            ErrorType.SERVER_ERROR,
            ErrorType.TIMEOUT -> true
            else -> false
        }
    }

    /**
     * 获取建议的重试延迟（毫秒）
     */
    fun getRetryDelayMs(errorType: ErrorType, attempt: Int): Long {
        return when (errorType) {
            ErrorType.RATE_LIMIT -> 1000L * attempt  // 线性退避
            ErrorType.SERVER_ERROR -> 2000L * attempt // 指数退避的基础
            ErrorType.TIMEOUT -> 5000L * attempt
            else -> 0L
        }
    
    /** Check if an error message is an auth error. */
    fun isAuth(error: String): Boolean {
        val lower = error.lowercase()
        return "unauthorized" in lower || "forbidden" in lower || "401" in lower || "403" in lower ||
               "authentication" in lower || "invalid api key" in lower || "invalid_api_key" in lower
    }
}

}

enum class FailoverReason {
    AUTH,
    AUTH_PERMANENT,
    BILLING,
    RATE_LIMIT,
    OVERLOADED,
    SERVER_ERROR,
    TIMEOUT,
    CONTEXT_OVERFLOW,
    PAYLOAD_TOO_LARGE,
    MODEL_NOT_FOUND,
    FORMAT_ERROR,
    THINKING_SIGNATURE,
    LONG_CONTEXT_TIER,
    UNKNOWN
}

data class ClassifiedError(
    val reason: FailoverReason,
    val statusCode: Int? = null,
    val provider: String? = null,
    val model: String? = null,
    val message: String = "",
    val errorContext: Map<String, Any> = emptyMap(),
    val retryable: Boolean = true,
    val shouldCompress: Boolean = false,
    val shouldRotateCredential: Boolean = false,
    val shouldFallback: Boolean = false
) {
    val isAuth: Boolean get() = reason == FailoverReason.AUTH || reason == FailoverReason.AUTH_PERMANENT
}
