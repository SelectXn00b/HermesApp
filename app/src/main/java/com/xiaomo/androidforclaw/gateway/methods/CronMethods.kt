/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway.methods

import android.util.Log
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
        return JSONObject().apply {
            put("error", JSONObject(mapOf("code" to "NOT_IMPLEMENTED", "message" to "cron.runs not implemented")))
        }
    }

    private fun jobToJson(job: CronJob) = JSONObject().apply {
        put("id", job.id)
        put("name", job.name)
        put("enabled", job.enabled)
        put("schedule", scheduleToJson(job.schedule))
        put("payload", payloadToJson(job.payload))
        put("createdAtMs", job.createdAtMs)
        put("state", stateToJson(job.state))
    }

    private fun jsonToJob(json: JSONObject) = CronJob(
        id = "",
        name = json.getString("name"),
        schedule = jsonToSchedule(json.getJSONObject("schedule")),
        sessionTarget = SessionTarget.ISOLATED,
        wakeMode = WakeMode.NEXT_HEARTBEAT,
        payload = jsonToPayload(json.getJSONObject("payload")),
        enabled = json.optBoolean("enabled", true),
        createdAtMs = System.currentTimeMillis(),
        updatedAtMs = System.currentTimeMillis()
    )

    private fun scheduleToJson(s: CronSchedule) = JSONObject().apply {
        when (s) {
            is CronSchedule.At -> {
                put("kind", "at")
                put("at", s.at)
            }
            is CronSchedule.Every -> {
                put("kind", "every")
                put("everyMs", s.everyMs)
            }
            is CronSchedule.Cron -> {
                put("kind", "cron")
                put("expr", s.expr)
            }
        }
    }

    private fun jsonToSchedule(json: JSONObject): CronSchedule = when (json.getString("kind")) {
        "at" -> CronSchedule.At(json.getString("at"))
        "every" -> CronSchedule.Every(json.getLong("everyMs"))
        "cron" -> CronSchedule.Cron(json.getString("expr"))
        else -> throw IllegalArgumentException("Unknown schedule")
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
            }
        }
    }

    private fun jsonToPayload(json: JSONObject): CronPayload = when (json.getString("kind")) {
        "systemEvent" -> CronPayload.SystemEvent(json.getString("text"))
        "agentTurn" -> CronPayload.AgentTurn(json.getString("message"))
        else -> throw IllegalArgumentException("Unknown payload")
    }

    private fun stateToJson(s: CronJobState) = JSONObject().apply {
        s.nextRunAtMs?.let { put("nextRunAtMs", it) }
        s.lastRunAtMs?.let { put("lastRunAtMs", it) }
        s.lastRunStatus?.let { put("lastRunStatus", it.name.lowercase()) }
        put("consecutiveErrors", s.consecutiveErrors)
    }

    private fun applyPatch(job: CronJob, patch: JSONObject) = job.copy(
        name = patch.optString("name", job.name),
        enabled = patch.optBoolean("enabled", job.enabled)
    )
}
