package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import com.xiaomo.hermes.hermes.getHermesHome

/**
 * RL Training Tool — remote-only on Android. 1:1 align with
 * tools/rl_training_tool.py for registration/schema; all handlers
 * return toolError since on-device RL training is not supported.
 */

// ── Module constants (1:1 with Python) ──────────────────────────────────

val HERMES_ROOT: java.io.File get() = getHermesHome()
val TINKER_ATROPOS_ROOT: java.io.File get() = java.io.File(HERMES_ROOT, "tinker-atropos")
val ENVIRONMENTS_DIR: java.io.File get() = java.io.File(TINKER_ATROPOS_ROOT, "tinker_atropos/environments")
val CONFIGS_DIR: java.io.File get() = java.io.File(TINKER_ATROPOS_ROOT, "configs")
val LOGS_DIR: java.io.File get() = java.io.File(getHermesHome(), "logs/rl_training")

val LOCKED_FIELDS: Map<String, Any?> = mapOf(
    "env" to mapOf(
        "tokenizer_name" to "Qwen/Qwen3-8B",
        "rollout_server_url" to "http://localhost:8000",
        "use_wandb" to true,
        "max_token_length" to 8192,
        "max_num_workers" to 2048,
        "worker_timeout" to 3600,
        "total_steps" to 2500,
        "steps_per_eval" to 25,
        "max_batches_offpolicy" to 3,
        "inference_weight" to 1.0,
        "eval_limit_ratio" to 0.1,
    ),
    "openai" to listOf(
        mapOf(
            "model_name" to "Qwen/Qwen3-8B",
            "base_url" to "http://localhost:8001/v1",
            "api_key" to "x",
            "weight" to 1.0,
            "num_requests_for_eval" to 256,
            "timeout" to 3600,
            "server_type" to "sglang",
        )
    ),
    "tinker" to mapOf(
        "lora_rank" to 32,
        "learning_rate" to 0.00004,
        "max_token_trainer_length" to 9000,
        "checkpoint_dir" to "./temp/",
        "save_checkpoint_interval" to 25,
    ),
    "slurm" to false,
    "testing" to false,
)

@Suppress("UNCHECKED_CAST")
val LOCKED_FIELD_NAMES: Set<String> = (LOCKED_FIELDS["env"] as? Map<String, Any?>)?.keys ?: emptySet()

const val MIN_STATUS_CHECK_INTERVAL: Int = 30 * 60

val TEST_MODELS: List<String> = listOf(
    "qwen/qwen3-8b",
    "z-ai/glm-4.7-flash",
    "minimax/minimax-m2.7",
)

const val DEFAULT_NUM_STEPS: Int = 3
const val DEFAULT_GROUP_SIZE: Int = 16

val _rl_env: List<String> = listOf("TINKER_API_KEY", "WANDB_API_KEY")

// ── Schema constants (1:1 with Python) ──────────────────────────────────

val RL_LIST_ENVIRONMENTS_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_list_environments",
    "description" to "List all available RL environments.",
    "parameters" to mapOf("type" to "object", "properties" to emptyMap<String, Any?>(), "required" to emptyList<String>()),
)

val RL_SELECT_ENVIRONMENT_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_select_environment",
    "description" to "Select an RL environment for training.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf("name" to mapOf("type" to "string")),
        "required" to listOf("name"),
    ),
)

val RL_GET_CURRENT_CONFIG_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_get_current_config",
    "description" to "Get the current environment configuration.",
    "parameters" to mapOf("type" to "object", "properties" to emptyMap<String, Any?>(), "required" to emptyList<String>()),
)

val RL_EDIT_CONFIG_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_edit_config",
    "description" to "Update a configuration field.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "field" to mapOf("type" to "string"),
            "value" to mapOf("description" to "New value for the field"),
        ),
        "required" to listOf("field", "value"),
    ),
)

val RL_START_TRAINING_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_start_training",
    "description" to "Start a new RL training run.",
    "parameters" to mapOf("type" to "object", "properties" to emptyMap<String, Any?>(), "required" to emptyList<String>()),
)

val RL_CHECK_STATUS_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_check_status",
    "description" to "Get status and metrics for a training run.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf("run_id" to mapOf("type" to "string")),
        "required" to listOf("run_id"),
    ),
)

val RL_STOP_TRAINING_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_stop_training",
    "description" to "Stop a running training job.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf("run_id" to mapOf("type" to "string")),
        "required" to listOf("run_id"),
    ),
)

val RL_GET_RESULTS_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_get_results",
    "description" to "Get final results for a completed training run.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf("run_id" to mapOf("type" to "string")),
        "required" to listOf("run_id"),
    ),
)

val RL_LIST_RUNS_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_list_runs",
    "description" to "List all training runs (active and completed).",
    "parameters" to mapOf("type" to "object", "properties" to emptyMap<String, Any?>(), "required" to emptyList<String>()),
)

val RL_TEST_INFERENCE_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "rl_test_inference",
    "description" to "Quick inference test for any environment.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "num_steps" to mapOf("type" to "integer", "default" to DEFAULT_NUM_STEPS),
            "group_size" to mapOf("type" to "integer", "default" to DEFAULT_GROUP_SIZE),
            "models" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        ),
        "required" to emptyList<String>(),
    ),
)

// ── Data classes (already ported) ───────────────────────────────────────

/**
 * Information about a discovered RL environment.
 * Ported from EnvironmentInfo in rl_training_tool.py.
 */
data class EnvironmentInfo(
    val name: String = "",
    val className: String = "",
    val filePath: String = "",
    val description: String = "",
    val configClass: String = "BaseEnvConfig"
)

/**
 * State for a training run.
 * Ported from RunState in rl_training_tool.py.
 */
data class RunState(
    val runId: String = "",
    val environment: String = "",
    val config: Map<String, Any> = emptyMap(),
    val status: String = "pending",
    val errorMessage: String = "",
    val wandbProject: String = "",
    val wandbRunName: String = "",
    val startTime: Double = 0.0
)

// ── Module state ────────────────────────────────────────────────────────

private val _environments: MutableList<EnvironmentInfo> = mutableListOf()
private var _currentEnv: String? = null
private var _currentConfig: MutableMap<String, Any?> = mutableMapOf()
private val _envConfigCache: MutableMap<String, Map<String, Map<String, Any?>>> = mutableMapOf()
private val _activeRuns: MutableMap<String, RunState> = mutableMapOf()
private val _lastStatusCheck: MutableMap<String, Double> = mutableMapOf()

private val _rlGson = Gson()

// ── Top-level funcs (1:1 with Python, Android-safe fallbacks) ──────────

fun _ensureLogsDir() {
    val dir = LOGS_DIR
    if (!dir.exists()) dir.mkdirs()
}

fun _scanEnvironments(): List<EnvironmentInfo> = emptyList()

fun _getEnvConfigFields(envFilePath: String): Map<String, Map<String, Any?>> = emptyMap()

fun _initializeEnvironments() {
    _environments.clear()
    _environments.addAll(_scanEnvironments())
}

suspend fun _spawnTrainingRun(runState: RunState, configPath: java.io.File): Boolean = false

suspend fun _monitorTrainingRun(runState: RunState) { /* no-op */ }

fun _stopTrainingRun(runState: RunState) { /* no-op */ }

suspend fun rlListEnvironments(): String =
    toolError("RL training is not available on Android")

suspend fun rlSelectEnvironment(name: String): String =
    toolError("RL training is not available on Android")

suspend fun rlGetCurrentConfig(): String =
    toolError("RL training is not available on Android")

suspend fun rlEditConfig(field: String, value: Any?): String =
    toolError("RL training is not available on Android")

suspend fun rlStartTraining(): String =
    toolError("RL training is not available on Android")

suspend fun rlCheckStatus(runId: String): String =
    toolError("RL training is not available on Android")

suspend fun rlStopTraining(runId: String): String =
    toolError("RL training is not available on Android")

suspend fun rlGetResults(runId: String): String =
    toolError("RL training is not available on Android")

suspend fun rlListRuns(): String =
    toolError("RL training is not available on Android")

suspend fun rlTestInference(
    numSteps: Int = DEFAULT_NUM_STEPS,
    groupSize: Int = DEFAULT_GROUP_SIZE,
    models: List<String>? = null,
): String = toolError("RL training is not available on Android")

fun checkRlPythonVersion(): Boolean = false

fun checkRlApiKeys(): Boolean {
    for (key in _rl_env) {
        if (System.getenv(key).isNullOrBlank()) return false
    }
    return true
}

fun getMissingKeys(): List<String> {
    return _rl_env.filter { System.getenv(it).isNullOrBlank() }
}
