/** 1:1 对齐 hermes/gateway/platforms/qqbot/onboard.py */
package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * QQBot scan-to-configure (QR code onboard) module.
 *
 * Calls the q.qq.com create_bind_task / poll_bind_result APIs to
 * generate a QR-code URL and poll for scan completion. On success the caller
 * receives the bot's app_id, client_secret (decrypted locally), and the
 * scanner's user_openid — enough to fully configure the QQBot gateway.
 */
object Onboard {

    private const val _TAG = "Onboard"

    // -----------------------------------------------------------------------
    // Bind status
    // -----------------------------------------------------------------------

    /**
     * Status codes returned by poll_bind_result.
     */
    enum class BindStatus(val code: Int) {
        NONE(0),
        PENDING(1),
        COMPLETED(2),
        EXPIRED(3);

        companion object {
            fun fromCode(code: Int): BindStatus =
                entries.find { it.code == code } ?: NONE
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Result from [createBindTask].
     */
    data class BindTaskResult(
        val taskId: String,
        val aesKeyBase64: String,
    )

    /**
     * Create a bind task and return (taskId, aesKeyBase64).
     *
     * The AES key is generated locally and sent to the server so it can
     * encrypt the bot credentials before returning them.
     *
     * @throws RuntimeException If the API returns a non-zero retcode.
     */
    fun createBindTask(
        timeout: Int = (Constants.ONBOARD_API_TIMEOUT * 1000).toInt(),
    ): BindTaskResult {
        val url = URL("https://${Constants.PORTAL_HOST}${Constants.ONBOARD_CREATE_PATH}")
        val key = Crypto.generateBindKey()

        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().put("key", key).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(responseText)

            if (data.optInt("retcode", -1) != 0) {
                throw RuntimeException(data.optString("msg", "create_bind_task failed"))
            }

            val taskId = data.optJSONObject("data")?.optString("task_id", "")
                ?: ""
            if (taskId.isEmpty()) {
                throw RuntimeException("create_bind_task: missing task_id in response")
            }

            Log.d(_TAG, "create_bind_task ok: task_id=$taskId")
            return BindTaskResult(taskId, key)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Result from [pollBindResult].
     */
    data class PollResult(
        val status: BindStatus,
        val botAppid: String,
        val botEncryptSecret: String,
        val userOpenid: String,
    )

    /**
     * Poll the bind result for [taskId].
     *
     * @return A [PollResult] containing status, bot_appid, bot_encrypt_secret,
     *         and user_openid.
     *
     *         bot_encrypt_secret is AES-256-GCM encrypted — decrypt it with
     *         [Crypto.decryptSecret] using the key from [createBindTask].
     *
     *         user_openid is the OpenID of the person who scanned the code
     *         (available when status == COMPLETED).
     *
     * @throws RuntimeException If the API returns a non-zero retcode.
     */
    fun pollBindResult(
        taskId: String,
        timeout: Int = (Constants.ONBOARD_API_TIMEOUT * 1000).toInt(),
    ): PollResult {
        val url = URL("https://${Constants.PORTAL_HOST}${Constants.ONBOARD_POLL_PATH}")

        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().put("task_id", taskId).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(responseText)

            if (data.optInt("retcode", -1) != 0) {
                throw RuntimeException(data.optString("msg", "poll_bind_result failed"))
            }

            val d = data.optJSONObject("data") ?: JSONObject()
            return PollResult(
                status = BindStatus.fromCode(d.optInt("status", 0)),
                botAppid = d.optString("bot_appid", ""),
                botEncryptSecret = d.optString("bot_encrypt_secret", ""),
                userOpenid = d.optString("user_openid", ""),
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Build the QR-code target URL for a given taskId.
     */
    fun buildConnectUrl(taskId: String): String {
        return Constants.QR_URL_TEMPLATE.replace(
            "{task_id}",
            URLEncoder.encode(taskId, "UTF-8")
        )
    }
}
