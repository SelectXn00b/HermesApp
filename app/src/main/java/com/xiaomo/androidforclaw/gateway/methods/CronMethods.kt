/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-cron.ts
 */
package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.cron.*
import com.xiaomo.androidforclaw.gateway.protocol.ErrorShape
import org.json.JSONArray
import org.json.JSONObject

object CronMethods {
    private const val TAG = "CronMethods"
    private var cronService: CronService? = null

    fun initialize(service: CronService) {
        cronService = service
        Log.d(TAG, "CronMethods initialized")
    }

    fun list(params: JSONObject): JSONObject {
        val service = cronService
        if (service == null) {
            return JSONObject().apply {
                put("error", JSONObject(mapOf(
                    "code" to "SERVICE_NOT_INITIALIZED",
                    "message" to "Cron not initialized"
                )))
            }
        }
        
        return try {
            val jobs = service.list(
                includeDisabled = params.optBoolean("includeDisabled", true),
                enabled = if (params.has("enabled")) params.getBoolean("enabled") else null
            )
            
            JSONObject().apply {
                put("jobs", JSONArray(jobs.map { jobToJson(it) }))
                put("total", jobs.size)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf(
                    "code" to "LIST_FAILED",
                    "message" to (e.message ?: "Failed")
                )))
            }
        }
    }

    fun status(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }
        
        return try {
            JSONObject(service.status())
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "STATUS_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    fun add(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }
        
        return try {
            val job = jsonToJob(params)
            val created = service.add(job)
            jobToJson(created)
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "ADD_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    fun update(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }
        
        return try {
            val jobId = params.optString("id") ?: params.optString("jobId")
            if (jobId.isEmpty()) {
                return JSONObject().apply {
                    put("error", JSONObject(mapOf("code" to "MISSING_JOB_ID", "message" to "Missing job id")))
                }
            }
            
            val patch = params.getJSONObject("patch")
            val updated = service.update(jobId) { applyPatch(it, patch) }
            if (updated == null) {
                return JSONObject().apply {
                    put("error", JSONObject(mapOf("code" to "JOB_NOT_FOUND", "message" to "Job not found")))
                }
            }
            
            jobToJson(updated)
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "UPDATE_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    fun remove(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }
        
        return try {
            val jobId = params.optString("id") ?: params.optString("jobId")
            if (jobId.isEmpty()) {
                return JSONObject().apply {
                    put("error", JSONObject(mapOf("code" to "MISSING_JOB_ID", "message" to "Missing job id")))
                }
            }
            
            val removed = service.remove(jobId)
            JSONObject().apply {
                put("ok", removed)
                put("removed", removed)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "REMOVE_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    fun run(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }
        
        return try {
            val jobId = params.optString("id") ?: params.optString("jobId")
            if (jobId.isEmpty()) {
                return JSONObject().apply {
                    put("error", JSONObject(mapOf("code" to "MISSING_JOB_ID", "message" to "Missing job id")))
                }
            }
            
            val force = params.optString("mode", "due") == "force"
            val ran = service.run(jobId, force)
            
            JSONObject().apply {
                put("ok", ran)
                put("ran", ran)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "RUN_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    fun runs(params: JSONObject): JSONObject {
        val service = cronService ?: return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "SERVICE_NOT_INITIALIZED", "message" to "Cron not initialized")))
        }

        return try {
            val jobId = params.optString("id").ifEmpty { null }
                ?: params.optString("jobId").ifEmpty { null }
            if (jobId == null) {
                return JSONObject().apply {
                    put("error", JSONObject(mapOf("code" to "MISSING_JOB_ID", "message" to "Missing job id")))
                }
            }

            val limit = params.optInt("limit", 100)
            val statusFilter = params.optString("status", "").ifEmpty { null }?.let { s ->
                try { RunStatus.valueOf(s.uppercase()) } catch (_: Exception) { null }
            }

            val entries = service.queryRuns(jobId, limit, statusFilter)

            JSONObject().apply {
                put("runs", JSONArray(entries.map { entry ->
                    JSONObject().apply {
                        put("ts", entry.ts)
                        put("jobId", entry.jobId)
                        put("action", entry.action)
                        entry.status?.let { put("status", it.name.lowercase()) }
                        entry.error?.let { put("error", it) }
                        entry.summary?.let { put("summary", it) }
                        entry.runAtMs?.let { put("runAtMs", it) }
                        entry.durationMs?.let { put("durationMs", it) }
                        entry.nextRunAtMs?.let { put("nextRunAtMs", it) }
                    }
                }))
                put("total", entries.size)
                put("jobId", jobId)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", JSONObject(mapOf("code" to "RUNS_FAILED", "message" to (e.message ?: "Failed"))))
            }
        }
    }

    private fun jobToJson(job: CronJob) = JSONObject().apply {
        put("id", job.id)
        put("name", job.name)
        job.description?.let { put("description", it) }
        put("enabled", job.enabled)
        job.deleteAfterRun?.let { put("deleteAfterRun", it) }
        put("schedule", scheduleToJson(job.schedule))
        put("sessionTarget", when (job.sessionTarget) {
            SessionTarget.MAIN -> "main"
            SessionTarget.ISOLATED -> "isolated"
        })
        put("wakeMode", when (job.wakeMode) {
            WakeMode.NOW -> "now"
            WakeMode.NEXT_HEARTBEAT -> "next-heartbeat"
        })
        put("payload", payloadToJson(job.payload))
        job.delivery?.let { d ->
            put("delivery", JSONObject().apply {
                put("mode", when (d.mode) {
                    DeliveryMode.NONE -> "none"
                    DeliveryMode.ANNOUNCE -> "announce"
                    DeliveryMode.WEBHOOK -> "webhook"
                })
                d.channel?.let { put("channel", it) }
                d.to?.let { put("to", it) }
            })
        }
        job.failureAlert?.let { fa ->
            put("failureAlert", JSONObject().apply {
                put("after", fa.after)
                put("cooldownMs", fa.cooldownMs)
                fa.channel?.let { put("channel", it) }
            })
        }
        put("createdAtMs", job.createdAtMs)
        put("updatedAtMs", job.updatedAtMs)
        put("state", stateToJson(job.state))
    }

    private fun jsonToJob(json: JSONObject): CronJob {
        val now = System.currentTimeMillis()
        return CronJob(
            id = "",
            name = json.getString("name"),
            description = json.optString("description", "").ifEmpty { null },
            schedule = jsonToSchedule(json.getJSONObject("schedule")),
            sessionTarget = when (json.optString("sessionTarget", "isolated")) {
                "main" -> SessionTarget.MAIN
                else -> SessionTarget.ISOLATED
            },
            wakeMode = when (json.optString("wakeMode", "next-heartbeat")) {
                "now" -> WakeMode.NOW
                else -> WakeMode.NEXT_HEARTBEAT
            },
            payload = jsonToPayload(json.getJSONObject("payload")),
            delivery = json.optJSONObject("delivery")?.let { d ->
                CronDelivery(
                    mode = when (d.optString("mode", "announce")) {
                        "none" -> DeliveryMode.NONE
                        "webhook" -> DeliveryMode.WEBHOOK
                        else -> DeliveryMode.ANNOUNCE
                    },
                    channel = d.optString("channel", "").ifEmpty { null },
                    to = d.optString("to", "").ifEmpty { null }
                )
            },
            failureAlert = json.optJSONObject("failureAlert")?.let { fa ->
                CronFailureAlert(
                    after = fa.optInt("after", 2),
                    cooldownMs = if (fa.has("cooldownMs")) fa.getLong("cooldownMs") else 3_600_000L,
                    channel = fa.optString("channel", "").ifEmpty { null }
                )
            },
            enabled = json.optBoolean("enabled", true),
            deleteAfterRun = if (json.has("deleteAfterRun")) json.getBoolean("deleteAfterRun") else null,
            createdAtMs = now,
            updatedAtMs = now
        )
    }

    private fun scheduleToJson(s: CronSchedule) = JSONObject().apply {
        when (s) {
            is CronSchedule.At -> {
                put("kind", "at")
                put("at", s.at)
            }
            is CronSchedule.Every -> {
                put("kind", "every")
                put("everyMs", s.everyMs)
                s.anchorMs?.let { put("anchorMs", it) }
            }
            is CronSchedule.Cron -> {
                put("kind", "cron")
                put("expr", s.expr)
                s.tz?.let { put("tz", it) }
                s.staggerMs?.let { put("staggerMs", it) }
            }
        }
    }

    private fun jsonToSchedule(json: JSONObject): CronSchedule = when (json.getString("kind")) {
        "at" -> CronSchedule.At(json.getString("at"))
        "every" -> CronSchedule.Every(
            everyMs = json.getLong("everyMs"),
            anchorMs = if (json.has("anchorMs")) json.getLong("anchorMs") else null
        )
        "cron" -> CronSchedule.Cron(
            expr = json.getString("expr"),
            tz = json.optString("tz", "").ifEmpty { null },
            staggerMs = if (json.has("staggerMs")) json.getLong("staggerMs") else null
        )
        else -> throw IllegalArgumentException("Unknown schedule kind: ${json.optString("kind")}")
    }

    private fun payloadToJson(p: CronPayload) = JSONObject().apply {
        when (p) {
            is CronPayload.SystemEvent -> {
                put("kind", "systemEvent")
                put("text", p.text)
            }
            is CronPayload.AgentTurn -> {
                put("kind", "agentTurn")
                put("message", p.message)
                p.model?.let { put("model", it) }
                p.fallbacks?.let { put("fallbacks", JSONArray(it)) }
                p.thinking?.let { put("thinking", it) }
                p.timeoutSeconds?.let { put("timeoutSeconds", it) }
                p.deliver?.let { put("deliver", it) }
                p.channel?.let { put("channel", it) }
                p.to?.let { put("to", it) }
                p.bestEffortDeliver?.let { put("bestEffortDeliver", it) }
                p.lightContext?.let { put("lightContext", it) }
                p.allowUnsafeExternalContent?.let { put("allowUnsafeExternalContent", it) }
            }
        }
    }

    private fun jsonToPayload(json: JSONObject): CronPayload = when (json.getString("kind")) {
        "systemEvent" -> CronPayload.SystemEvent(json.getString("text"))
        "agentTurn" -> CronPayload.AgentTurn(
            message = json.getString("message"),
            model = json.optString("model", "").ifEmpty { null },
            fallbacks = json.optJSONArray("fallbacks")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            thinking = json.optString("thinking", "").ifEmpty { null },
            timeoutSeconds = if (json.has("timeoutSeconds")) json.getInt("timeoutSeconds") else null,
            deliver = if (json.has("deliver")) json.getBoolean("deliver") else null,
            channel = json.optString("channel", "").ifEmpty { null },
            to = json.optString("to", "").ifEmpty { null },
            bestEffortDeliver = if (json.has("bestEffortDeliver")) json.getBoolean("bestEffortDeliver") else null,
            lightContext = if (json.has("lightContext")) json.getBoolean("lightContext") else null,
            allowUnsafeExternalContent = if (json.has("allowUnsafeExternalContent")) json.getBoolean("allowUnsafeExternalContent") else null
        )
        else -> throw IllegalArgumentException("Unknown payload kind: ${json.optString("kind")}")
    }

    private fun stateToJson(s: CronJobState) = JSONObject().apply {
        s.nextRunAtMs?.let { put("nextRunAtMs", it) }
        s.runningAtMs?.let { put("runningAtMs", it) }
        s.lastRunAtMs?.let { put("lastRunAtMs", it) }
        s.lastRunStatus?.let { put("lastRunStatus", it.name.lowercase()) }
        s.lastError?.let { put("lastError", it) }
        s.lastDurationMs?.let { put("lastDurationMs", it) }
        put("consecutiveErrors", s.consecutiveErrors)
        s.lastDelivered?.let { put("lastDelivered", it) }
        s.lastDeliveryStatus?.let { put("lastDeliveryStatus", it.name.lowercase().replace('_', '-')) }
        s.lastFailureAlertAtMs?.let { put("lastFailureAlertAtMs", it) }
    }

    /**
     * Apply a patch to a cron job.
     * Aligned with OpenClaw CronJobPatchSchema: supports patching all job fields.
     */
    private fun applyPatch(job: CronJob, patch: JSONObject): CronJob {
        var patched = job.copy(updatedAtMs = System.currentTimeMillis())

        if (patch.has("name")) {
            patched = patched.copy(name = patch.getString("name"))
        }
        if (patch.has("enabled")) {
            patched = patched.copy(enabled = patch.getBoolean("enabled"))
        }
        if (patch.has("schedule")) {
            patched = patched.copy(schedule = jsonToSchedule(patch.getJSONObject("schedule")))
        }
        if (patch.has("payload")) {
            patched = patched.copy(payload = jsonToPayload(patch.getJSONObject("payload")))
        }
        if (patch.has("sessionTarget")) {
            patched = patched.copy(sessionTarget = when (patch.getString("sessionTarget")) {
                "main" -> SessionTarget.MAIN
                "isolated" -> SessionTarget.ISOLATED
                else -> patched.sessionTarget
            })
        }
        if (patch.has("wakeMode")) {
            patched = patched.copy(wakeMode = when (patch.getString("wakeMode")) {
                "now" -> WakeMode.NOW
                "next-heartbeat" -> WakeMode.NEXT_HEARTBEAT
                else -> patched.wakeMode
            })
        }
        if (patch.has("delivery")) {
            val deliveryJson = patch.optJSONObject("delivery")
            patched = if (deliveryJson != null) {
                patched.copy(delivery = CronDelivery(
                    mode = when (deliveryJson.optString("mode", "announce")) {
                        "none" -> DeliveryMode.NONE
                        "webhook" -> DeliveryMode.WEBHOOK
                        else -> DeliveryMode.ANNOUNCE
                    },
                    channel = deliveryJson.optString("channel", "").ifEmpty { null },
                    to = deliveryJson.optString("to", "").ifEmpty { null }
                ))
            } else {
                patched.copy(delivery = null)
            }
        }
        if (patch.has("failureAlert")) {
            // failureAlert can be false (disable) or an object
            val raw = patch.get("failureAlert")
            patched = if (raw == false || raw == java.lang.Boolean.FALSE) {
                patched.copy(failureAlert = null)
            } else if (raw is JSONObject) {
                patched.copy(failureAlert = CronFailureAlert(
                    after = raw.optInt("after", patched.failureAlert?.after ?: 2),
                    cooldownMs = if (raw.has("cooldownMs")) raw.getLong("cooldownMs")
                        else (patched.failureAlert?.cooldownMs ?: 3_600_000L),
                    channel = raw.optString("channel", "").ifEmpty { null }
                ))
            } else {
                patched
            }
        }

        return patched
    }
}
