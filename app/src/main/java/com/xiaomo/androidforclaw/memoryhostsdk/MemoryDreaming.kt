package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/dreaming.ts
 *
 * Memory dreaming configuration: light, deep, and REM dreaming phases.
 * Consolidation system that processes memories during idle periods.
 */

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- Default Constants ----------

const val DEFAULT_MEMORY_DREAMING_ENABLED = false
const val DEFAULT_MEMORY_DREAMING_VERBOSE_LOGGING = false
const val DEFAULT_MEMORY_DREAMING_STORAGE_MODE = "inline"
const val DEFAULT_MEMORY_DREAMING_SEPARATE_REPORTS = false
const val DEFAULT_MEMORY_DREAMING_FREQUENCY = "0 3 * * *"

const val DEFAULT_MEMORY_LIGHT_DREAMING_CRON_EXPR = "0 */6 * * *"
const val DEFAULT_MEMORY_LIGHT_DREAMING_LOOKBACK_DAYS = 2
const val DEFAULT_MEMORY_LIGHT_DREAMING_LIMIT = 100
const val DEFAULT_MEMORY_LIGHT_DREAMING_DEDUPE_SIMILARITY = 0.9

const val DEFAULT_MEMORY_DEEP_DREAMING_CRON_EXPR = "0 3 * * *"
const val DEFAULT_MEMORY_DEEP_DREAMING_LIMIT = 10
const val DEFAULT_MEMORY_DEEP_DREAMING_MIN_SCORE = 0.8
const val DEFAULT_MEMORY_DEEP_DREAMING_MIN_RECALL_COUNT = 3
const val DEFAULT_MEMORY_DEEP_DREAMING_MIN_UNIQUE_QUERIES = 3
const val DEFAULT_MEMORY_DEEP_DREAMING_RECENCY_HALF_LIFE_DAYS = 14
const val DEFAULT_MEMORY_DEEP_DREAMING_MAX_AGE_DAYS = 30

const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_ENABLED = true
const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_TRIGGER_BELOW_HEALTH = 0.35
const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_LOOKBACK_DAYS = 30
const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_MAX_CANDIDATES = 20
const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_MIN_CONFIDENCE = 0.9
const val DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_AUTO_WRITE_MIN_CONFIDENCE = 0.97

const val DEFAULT_MEMORY_REM_DREAMING_CRON_EXPR = "0 5 * * 0"
const val DEFAULT_MEMORY_REM_DREAMING_LOOKBACK_DAYS = 7
const val DEFAULT_MEMORY_REM_DREAMING_LIMIT = 10
const val DEFAULT_MEMORY_REM_DREAMING_MIN_PATTERN_STRENGTH = 0.75

const val DEFAULT_MEMORY_DREAMING_SPEED = "balanced"
const val DEFAULT_MEMORY_DREAMING_THINKING = "medium"
const val DEFAULT_MEMORY_DREAMING_BUDGET = "medium"

// ---------- Enum Types ----------

enum class MemoryDreamingSpeed(val value: String) {
    FAST("fast"), BALANCED("balanced"), SLOW("slow");
    companion object {
        fun fromString(s: String?): MemoryDreamingSpeed? =
            entries.find { it.value == s?.trim()?.lowercase() }
    }
}

enum class MemoryDreamingThinking(val value: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");
    companion object {
        fun fromString(s: String?): MemoryDreamingThinking? =
            entries.find { it.value == s?.trim()?.lowercase() }
    }
}

enum class MemoryDreamingBudget(val value: String) {
    CHEAP("cheap"), MEDIUM("medium"), EXPENSIVE("expensive");
    companion object {
        fun fromString(s: String?): MemoryDreamingBudget? =
            entries.find { it.value == s?.trim()?.lowercase() }
    }
}

enum class MemoryDreamingStorageMode(val value: String) {
    INLINE("inline"), SEPARATE("separate"), BOTH("both");
    companion object {
        fun fromString(s: String?): MemoryDreamingStorageMode =
            entries.find { it.value == s?.trim()?.lowercase() } ?: INLINE
    }
}

// ---------- Config Data Classes ----------

/**
 * Execution config for dreaming phases.
 * Aligned with TS MemoryDreamingExecutionConfig.
 */
data class MemoryDreamingExecutionConfig(
    val speed: MemoryDreamingSpeed = MemoryDreamingSpeed.BALANCED,
    val thinking: MemoryDreamingThinking = MemoryDreamingThinking.MEDIUM,
    val budget: MemoryDreamingBudget = MemoryDreamingBudget.MEDIUM,
    val model: String? = null,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val timeoutMs: Long? = null
)

/**
 * Storage config for dreaming output.
 * Aligned with TS MemoryDreamingStorageConfig.
 */
data class MemoryDreamingStorageConfig(
    val mode: MemoryDreamingStorageMode = MemoryDreamingStorageMode.INLINE,
    val separateReports: Boolean = DEFAULT_MEMORY_DREAMING_SEPARATE_REPORTS
)

/**
 * Deep dreaming recovery config.
 * Aligned with TS MemoryDeepDreamingRecoveryConfig.
 */
data class MemoryDeepDreamingRecoveryConfig(
    val enabled: Boolean = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_ENABLED,
    val triggerBelowHealth: Double = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_TRIGGER_BELOW_HEALTH,
    val lookbackDays: Int = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_LOOKBACK_DAYS,
    val maxRecoveredCandidates: Int = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_MAX_CANDIDATES,
    val minRecoveryConfidence: Double = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_MIN_CONFIDENCE,
    val autoWriteMinConfidence: Double = DEFAULT_MEMORY_DEEP_DREAMING_RECOVERY_AUTO_WRITE_MIN_CONFIDENCE
)

/**
 * Light dreaming phase config.
 * Aligned with TS MemoryLightDreamingConfig.
 */
data class MemoryLightDreamingConfig(
    val enabled: Boolean = true,
    val cron: String = DEFAULT_MEMORY_LIGHT_DREAMING_CRON_EXPR,
    val lookbackDays: Int = DEFAULT_MEMORY_LIGHT_DREAMING_LOOKBACK_DAYS,
    val limit: Int = DEFAULT_MEMORY_LIGHT_DREAMING_LIMIT,
    val dedupeSimilarity: Double = DEFAULT_MEMORY_LIGHT_DREAMING_DEDUPE_SIMILARITY,
    val sources: List<String> = listOf("daily", "sessions", "recall"),
    val execution: MemoryDreamingExecutionConfig = MemoryDreamingExecutionConfig(
        speed = MemoryDreamingSpeed.FAST,
        thinking = MemoryDreamingThinking.LOW,
        budget = MemoryDreamingBudget.CHEAP
    )
)

/**
 * Deep dreaming phase config.
 * Aligned with TS MemoryDeepDreamingConfig.
 */
data class MemoryDeepDreamingConfig(
    val enabled: Boolean = true,
    val cron: String = DEFAULT_MEMORY_DEEP_DREAMING_CRON_EXPR,
    val limit: Int = DEFAULT_MEMORY_DEEP_DREAMING_LIMIT,
    val minScore: Double = DEFAULT_MEMORY_DEEP_DREAMING_MIN_SCORE,
    val minRecallCount: Int = DEFAULT_MEMORY_DEEP_DREAMING_MIN_RECALL_COUNT,
    val minUniqueQueries: Int = DEFAULT_MEMORY_DEEP_DREAMING_MIN_UNIQUE_QUERIES,
    val recencyHalfLifeDays: Int = DEFAULT_MEMORY_DEEP_DREAMING_RECENCY_HALF_LIFE_DAYS,
    val maxAgeDays: Int? = DEFAULT_MEMORY_DEEP_DREAMING_MAX_AGE_DAYS,
    val sources: List<String> = listOf("daily", "memory", "sessions", "logs", "recall"),
    val recovery: MemoryDeepDreamingRecoveryConfig = MemoryDeepDreamingRecoveryConfig(),
    val execution: MemoryDreamingExecutionConfig = MemoryDreamingExecutionConfig(
        speed = MemoryDreamingSpeed.BALANCED,
        thinking = MemoryDreamingThinking.HIGH,
        budget = MemoryDreamingBudget.MEDIUM
    )
)

/**
 * REM dreaming phase config.
 * Aligned with TS MemoryRemDreamingConfig.
 */
data class MemoryRemDreamingConfig(
    val enabled: Boolean = true,
    val cron: String = DEFAULT_MEMORY_REM_DREAMING_CRON_EXPR,
    val lookbackDays: Int = DEFAULT_MEMORY_REM_DREAMING_LOOKBACK_DAYS,
    val limit: Int = DEFAULT_MEMORY_REM_DREAMING_LIMIT,
    val minPatternStrength: Double = DEFAULT_MEMORY_REM_DREAMING_MIN_PATTERN_STRENGTH,
    val sources: List<String> = listOf("memory", "daily", "deep"),
    val execution: MemoryDreamingExecutionConfig = MemoryDreamingExecutionConfig(
        speed = MemoryDreamingSpeed.SLOW,
        thinking = MemoryDreamingThinking.HIGH,
        budget = MemoryDreamingBudget.EXPENSIVE
    )
)

/**
 * Complete dreaming configuration.
 * Aligned with TS MemoryDreamingConfig.
 */
data class MemoryDreamingConfig(
    val enabled: Boolean = DEFAULT_MEMORY_DREAMING_ENABLED,
    val frequency: String = DEFAULT_MEMORY_DREAMING_FREQUENCY,
    val timezone: String? = null,
    val verboseLogging: Boolean = DEFAULT_MEMORY_DREAMING_VERBOSE_LOGGING,
    val storage: MemoryDreamingStorageConfig = MemoryDreamingStorageConfig(),
    val execution: MemoryDreamingExecutionConfig = MemoryDreamingExecutionConfig(),
    val phases: MemoryDreamingPhases = MemoryDreamingPhases()
)

data class MemoryDreamingPhases(
    val light: MemoryLightDreamingConfig = MemoryLightDreamingConfig(),
    val deep: MemoryDeepDreamingConfig = MemoryDeepDreamingConfig(),
    val rem: MemoryRemDreamingConfig = MemoryRemDreamingConfig()
)

/**
 * Dreaming workspace reference.
 * Aligned with TS MemoryDreamingWorkspace.
 */
data class MemoryDreamingWorkspace(
    val workspaceDir: String,
    val agentIds: List<String>
)

// ---------- Config Resolution ----------

/**
 * Resolve the memory core plugin config from a raw config map.
 * Aligned with TS resolveMemoryCorePluginConfig.
 */
@Suppress("UNCHECKED_CAST")
fun resolveMemoryCorePluginConfig(cfg: Map<String, Any?>?): Map<String, Any?>? {
    val plugins = (cfg?.get("plugins") as? Map<String, Any?>) ?: return null
    val entries = (plugins["entries"] as? Map<String, Any?>) ?: return null
    val memoryCore = (entries["memory-core"] as? Map<String, Any?>) ?: return null
    return memoryCore["config"] as? Map<String, Any?>
}

/**
 * Resolve memory dreaming configuration from plugin config.
 * Aligned with TS resolveMemoryDreamingConfig.
 */
@Suppress("UNCHECKED_CAST")
fun resolveMemoryDreamingConfig(
    pluginConfig: Map<String, Any?>? = null
): MemoryDreamingConfig {
    val dreaming = pluginConfig?.get("dreaming") as? Map<String, Any?> ?: return MemoryDreamingConfig()

    val frequency = (dreaming["frequency"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_MEMORY_DREAMING_FREQUENCY

    val storage = dreaming["storage"] as? Map<String, Any?>
    val phases = dreaming["phases"] as? Map<String, Any?>
    val light = phases?.get("light") as? Map<String, Any?>
    val deep = phases?.get("deep") as? Map<String, Any?>
    val rem = phases?.get("rem") as? Map<String, Any?>

    return MemoryDreamingConfig(
        enabled = normalizeBoolean(dreaming["enabled"], DEFAULT_MEMORY_DREAMING_ENABLED),
        frequency = frequency,
        timezone = (dreaming["timezone"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
        verboseLogging = normalizeBoolean(dreaming["verboseLogging"], DEFAULT_MEMORY_DREAMING_VERBOSE_LOGGING),
        storage = MemoryDreamingStorageConfig(
            mode = MemoryDreamingStorageMode.fromString(storage?.get("mode") as? String),
            separateReports = normalizeBoolean(storage?.get("separateReports"), DEFAULT_MEMORY_DREAMING_SEPARATE_REPORTS)
        ),
        execution = MemoryDreamingExecutionConfig(),
        phases = MemoryDreamingPhases(
            light = MemoryLightDreamingConfig(
                enabled = normalizeBoolean(light?.get("enabled"), true),
                cron = frequency,
                lookbackDays = normalizeNonNegativeInt(light?.get("lookbackDays"), DEFAULT_MEMORY_LIGHT_DREAMING_LOOKBACK_DAYS),
                limit = normalizeNonNegativeInt(light?.get("limit"), DEFAULT_MEMORY_LIGHT_DREAMING_LIMIT),
                dedupeSimilarity = normalizeScore(light?.get("dedupeSimilarity"), DEFAULT_MEMORY_LIGHT_DREAMING_DEDUPE_SIMILARITY)
            ),
            deep = MemoryDeepDreamingConfig(
                enabled = normalizeBoolean(deep?.get("enabled"), true),
                cron = frequency,
                limit = normalizeNonNegativeInt(deep?.get("limit"), DEFAULT_MEMORY_DEEP_DREAMING_LIMIT),
                minScore = normalizeScore(deep?.get("minScore"), DEFAULT_MEMORY_DEEP_DREAMING_MIN_SCORE),
                minRecallCount = normalizeNonNegativeInt(deep?.get("minRecallCount"), DEFAULT_MEMORY_DEEP_DREAMING_MIN_RECALL_COUNT),
                minUniqueQueries = normalizeNonNegativeInt(deep?.get("minUniqueQueries"), DEFAULT_MEMORY_DEEP_DREAMING_MIN_UNIQUE_QUERIES),
                recencyHalfLifeDays = normalizeNonNegativeInt(deep?.get("recencyHalfLifeDays"), DEFAULT_MEMORY_DEEP_DREAMING_RECENCY_HALF_LIFE_DAYS),
                maxAgeDays = normalizeOptionalPositiveInt(deep?.get("maxAgeDays")) ?: DEFAULT_MEMORY_DEEP_DREAMING_MAX_AGE_DAYS
            ),
            rem = MemoryRemDreamingConfig(
                enabled = normalizeBoolean(rem?.get("enabled"), true),
                cron = frequency,
                lookbackDays = normalizeNonNegativeInt(rem?.get("lookbackDays"), DEFAULT_MEMORY_REM_DREAMING_LOOKBACK_DAYS),
                limit = normalizeNonNegativeInt(rem?.get("limit"), DEFAULT_MEMORY_REM_DREAMING_LIMIT),
                minPatternStrength = normalizeScore(rem?.get("minPatternStrength"), DEFAULT_MEMORY_REM_DREAMING_MIN_PATTERN_STRENGTH)
            )
        )
    )
}

// ---------- Day formatting ----------

/**
 * Format an epoch timestamp as a local ISO day string.
 * Aligned with TS formatMemoryDreamingDay.
 */
fun formatMemoryDreamingDay(epochMs: Long, timezone: String? = null): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    if (timezone != null) {
        try {
            sdf.timeZone = TimeZone.getTimeZone(timezone)
        } catch (_: Exception) {
            sdf.timeZone = TimeZone.getDefault()
        }
    }
    return sdf.format(Date(epochMs))
}

/**
 * Check if two epochs fall on the same dreaming day.
 * Aligned with TS isSameMemoryDreamingDay.
 */
fun isSameMemoryDreamingDay(firstEpochMs: Long, secondEpochMs: Long, timezone: String? = null): Boolean =
    formatMemoryDreamingDay(firstEpochMs, timezone) == formatMemoryDreamingDay(secondEpochMs, timezone)

// ---------- Private normalize helpers (mirror TS dreaming.ts) ----------

private fun normalizeBoolean(value: Any?, fallback: Boolean): Boolean {
    if (value is Boolean) return value
    if (value is String) {
        return when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
    }
    return fallback
}

private fun normalizeNonNegativeInt(value: Any?, fallback: Int): Int {
    val num = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull() ?: return fallback
        else -> return fallback
    }
    if (!num.isFinite()) return fallback
    val floored = num.toInt()
    return if (floored < 0) fallback else floored
}

private fun normalizeOptionalPositiveInt(value: Any?): Int? {
    if (value == null) return null
    val num = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull() ?: return null
        else -> return null
    }
    if (!num.isFinite()) return null
    val floored = num.toInt()
    return if (floored <= 0) null else floored
}

private fun normalizeScore(value: Any?, fallback: Double): Double {
    val num = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull() ?: return fallback
        else -> return fallback
    }
    if (!num.isFinite() || num < 0 || num > 1) return fallback
    return num
}
