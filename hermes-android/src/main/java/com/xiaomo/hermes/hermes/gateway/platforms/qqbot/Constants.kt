/** 1:1 对齐 hermes/gateway/platforms/qqbot/constants.py */
package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

/**
 * QQBot package-level constants shared across adapter, onboard, and other modules.
 */
object Constants {

    // -----------------------------------------------------------------------
    // QQBot adapter version — bump on functional changes to the adapter package.
    // -----------------------------------------------------------------------

    const val QQBOT_VERSION = "1.1.0"

    // -----------------------------------------------------------------------
    // API endpoints
    // -----------------------------------------------------------------------

    // The portal domain is configurable via QQ_API_HOST for corporate proxies
    // or test environments. Default: q.qq.com (production).
    val PORTAL_HOST: String = System.getProperty("QQ_PORTAL_HOST", "q.qq.com")

    const val API_BASE = "https://api.sgroup.qq.com"
    const val TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken"
    const val GATEWAY_URL_PATH = "/gateway"

    // QR-code onboard endpoints (on the portal host)
    const val ONBOARD_CREATE_PATH = "/lite/create_bind_task"
    const val ONBOARD_POLL_PATH = "/lite/poll_bind_result"
    const val QR_URL_TEMPLATE =
        "https://q.qq.com/qqbot/openclaw/connect.html" +
        "?task_id={task_id}&_wv=2&source=hermes"

    // -----------------------------------------------------------------------
    // Timeouts & retry
    // -----------------------------------------------------------------------

    const val DEFAULT_API_TIMEOUT = 30.0
    const val FILE_UPLOAD_TIMEOUT = 120.0
    const val CONNECT_TIMEOUT_SECONDS = 20.0

    val RECONNECT_BACKOFF = listOf(2, 5, 10, 30, 60)
    const val MAX_RECONNECT_ATTEMPTS = 100
    const val RATE_LIMIT_DELAY = 60 // seconds
    const val QUICK_DISCONNECT_THRESHOLD = 5.0 // seconds
    const val MAX_QUICK_DISCONNECT_COUNT = 3

    const val ONBOARD_POLL_INTERVAL = 2.0 // seconds between poll_bind_result calls
    const val ONBOARD_API_TIMEOUT = 10.0

    // -----------------------------------------------------------------------
    // Message limits
    // -----------------------------------------------------------------------

    const val MAX_MESSAGE_LENGTH = 4000
    const val DEDUP_WINDOW_SECONDS = 300
    const val DEDUP_MAX_SIZE = 1000

    // -----------------------------------------------------------------------
    // QQ Bot message types
    // -----------------------------------------------------------------------

    const val MSG_TYPE_TEXT = 0
    const val MSG_TYPE_MARKDOWN = 2
    const val MSG_TYPE_MEDIA = 7
    const val MSG_TYPE_INPUT_NOTIFY = 6

    // -----------------------------------------------------------------------
    // QQ Bot file media types
    // -----------------------------------------------------------------------

    const val MEDIA_TYPE_IMAGE = 1
    const val MEDIA_TYPE_VIDEO = 2
    const val MEDIA_TYPE_VOICE = 3
    const val MEDIA_TYPE_FILE = 4
}
