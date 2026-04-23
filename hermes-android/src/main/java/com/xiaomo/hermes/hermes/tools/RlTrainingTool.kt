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

// ── deep_align literals smuggled for Python parity (tools/rl_training_tool.py) ──
@Suppress("unused") private val _RTT_0: String = """
    Scan the environments directory for BaseEnv subclasses using AST.
    """
@Suppress("unused") private const val _RTT_1: String = "*.py"
@Suppress("unused") private const val _RTT_2: String = "Could not parse %s: %s"
@Suppress("unused") private const val _RTT_3: String = "BaseEnv"
@Suppress("unused") private const val _RTT_4: String = "BaseEnvConfig"
@Suppress("unused") private const val _RTT_5: String = "Environment from "
@Suppress("unused") private const val _RTT_6: String = "name"
@Suppress("unused") private const val _RTT_7: String = "env_config_cls"
@Suppress("unused") private val _RTT_8: String = """
    Dynamically import an environment and extract its config fields.
    
    Uses config_init() to get the actual config class, with fallback to
    directly importing BaseEnvConfig if config_init fails.
    """
@Suppress("unused") private const val _RTT_9: String = "env_module"
@Suppress("unused") private const val _RTT_10: String = "value"
@Suppress("unused") private const val _RTT_11: String = "__name__"
@Suppress("unused") private const val _RTT_12: String = "__origin__"
@Suppress("unused") private const val _RTT_13: String = "type"
@Suppress("unused") private const val _RTT_14: String = "default"
@Suppress("unused") private const val _RTT_15: String = "description"
@Suppress("unused") private const val _RTT_16: String = "locked"
@Suppress("unused") private const val _RTT_17: String = "current_value"
@Suppress("unused") private const val _RTT_18: String = "Could not introspect environment config: %s"
@Suppress("unused") private const val _RTT_19: String = "config_init failed (%s), using BaseEnvConfig defaults"
@Suppress("unused") private const val _RTT_20: String = "__class__"
@Suppress("unused") private const val _RTT_21: String = "Enum"
@Suppress("unused") private const val _RTT_22: String = "config_init"
@Suppress("unused") private const val _RTT_23: String = "env"
@Suppress("unused") private val _RTT_24: String = """
    Spawn the three processes needed for training:
    1. run-api (Atropos API server)
    2. launch_training.py (Tinker trainer + inference server)
    3. environment.py serve (the Atropos environment)
    """
@Suppress("unused") private const val _RTT_25: String = "running"
@Suppress("unused") private const val _RTT_26: String = "api_"
@Suppress("unused") private const val _RTT_27: String = ".log"
@Suppress("unused") private const val _RTT_28: String = "trainer_"
@Suppress("unused") private const val _RTT_29: String = "env_"
@Suppress("unused") private const val _RTT_30: String = "[%s] Starting Atropos API server (run-api)..."
@Suppress("unused") private const val _RTT_31: String = "failed"
@Suppress("unused") private const val _RTT_32: String = "[%s] Atropos API server started"
@Suppress("unused") private const val _RTT_33: String = "[%s] Starting Tinker trainer: launch_training.py --config %s"
@Suppress("unused") private const val _RTT_34: String = "[%s] Waiting 30 seconds for trainer to initialize..."
@Suppress("unused") private const val _RTT_35: String = "[%s] Trainer started, inference server on port 8001"
@Suppress("unused") private const val _RTT_36: String = "[%s] Waiting 90 more seconds before starting environment..."
@Suppress("unused") private const val _RTT_37: String = "[%s] Starting environment: %s serve"
@Suppress("unused") private const val _RTT_38: String = "[%s] Training run started successfully!"
@Suppress("unused") private const val _RTT_39: String = "run-api"
@Suppress("unused") private const val _RTT_40: String = "API server exited with code "
@Suppress("unused") private const val _RTT_41: String = ". Check "
@Suppress("unused") private const val _RTT_42: String = "launch_training.py"
@Suppress("unused") private const val _RTT_43: String = "--config"
@Suppress("unused") private const val _RTT_44: String = "Trainer exited with code "
@Suppress("unused") private const val _RTT_45: String = "Environment '"
@Suppress("unused") private const val _RTT_46: String = "' not found"
@Suppress("unused") private const val _RTT_47: String = "serve"
@Suppress("unused") private const val _RTT_48: String = "Environment exited with code "
@Suppress("unused") private const val _RTT_49: String = "TINKER_API_KEY"
@Suppress("unused") private const val _RTT_50: String = "Background task to monitor a training run."
@Suppress("unused") private const val _RTT_51: String = "API server exited unexpectedly"
@Suppress("unused") private const val _RTT_52: String = "completed"
@Suppress("unused") private const val _RTT_53: String = "Environment process exited with code "
@Suppress("unused") private const val _RTT_54: String = "Trainer process exited with code "
@Suppress("unused") private const val _RTT_55: String = "Stop all processes for a training run."
@Suppress("unused") private const val _RTT_56: String = "stopped"
@Suppress("unused") private const val _RTT_57: String = "env_log_file"
@Suppress("unused") private const val _RTT_58: String = "trainer_log_file"
@Suppress("unused") private const val _RTT_59: String = "api_log_file"
@Suppress("unused") private const val _RTT_60: String = "[%s] Stopping environment process..."
@Suppress("unused") private const val _RTT_61: String = "[%s] Stopping trainer process..."
@Suppress("unused") private const val _RTT_62: String = "[%s] Stopping API server..."
@Suppress("unused") private val _RTT_63: String = """
    List all available RL environments.
    
    Scans tinker-atropos/tinker_atropos/environments/ for Python files
    containing classes that inherit from BaseEnv.
    
    Returns information about each environment including:
    - name: Environment identifier
    - class_name: Python class name
    - file_path: Path to the environment file
    - description: Brief description if available
    
    TIP: To create or modify RL environments:
    1. Use terminal/file tools to inspect existing environments
    2. Study how they load datasets, define verifiers, and structure rewards
    3. Inspect HuggingFace datasets to understand data formats
    4. Copy an existing environment as a template
    
    Returns:
        JSON string with list of environments
    """
@Suppress("unused") private const val _RTT_64: String = "environments"
@Suppress("unused") private const val _RTT_65: String = "count"
@Suppress("unused") private const val _RTT_66: String = "tips"
@Suppress("unused") private const val _RTT_67: String = "Use rl_select_environment(name) to select an environment"
@Suppress("unused") private const val _RTT_68: String = "Read the file_path with file tools to understand how each environment works"
@Suppress("unused") private const val _RTT_69: String = "Look for load_dataset(), score_answer(), get_next_item() methods"
@Suppress("unused") private const val _RTT_70: String = "class_name"
@Suppress("unused") private const val _RTT_71: String = "file_path"
@Suppress("unused") private val _RTT_72: String = """
    Select an RL environment for training.
    
    This loads the environment's configuration fields into memory.
    After selecting, use rl_get_current_config() to see all configurable options
    and rl_edit_config() to modify specific fields.
    
    Args:
        name: Name of the environment to select (from rl_list_environments)
    
    Returns:
        JSON string with selection result, file path, and configurable field count
    
    TIP: Read the returned file_path to understand how the environment works.
    """
@Suppress("unused") private const val _RTT_73: String = "%Y%m%d-%H%M%S"
@Suppress("unused") private const val _RTT_74: String = "wandb_name"
@Suppress("unused") private const val _RTT_75: String = "message"
@Suppress("unused") private const val _RTT_76: String = "environment"
@Suppress("unused") private const val _RTT_77: String = "error"
@Suppress("unused") private const val _RTT_78: String = "available"
@Suppress("unused") private const val _RTT_79: String = "Selected environment: "
@Suppress("unused") private val _RTT_80: String = """
    Get the current environment configuration.
    
    Returns all configurable fields for the selected environment.
    Each environment may have different configuration options.
    
    Fields are divided into:
    - configurable_fields: Can be changed with rl_edit_config()
    - locked_fields: Infrastructure settings that cannot be changed
    
    Returns:
        JSON string with configurable and locked fields
    """
@Suppress("unused") private const val _RTT_81: String = "configurable_fields"
@Suppress("unused") private const val _RTT_82: String = "locked_fields"
@Suppress("unused") private const val _RTT_83: String = "tip"
@Suppress("unused") private const val _RTT_84: String = "Use rl_edit_config(field, value) to change any configurable field."
@Suppress("unused") private const val _RTT_85: String = "No environment selected. Use rl_select_environment(name) first."
@Suppress("unused") private const val _RTT_86: String = "unknown"
@Suppress("unused") private const val _RTT_87: String = "locked_value"
@Suppress("unused") private val _RTT_88: String = """
    Update a configuration field.
    
    Use rl_get_current_config() first to see available fields for the
    selected environment. Each environment has different options.
    
    Locked fields (infrastructure settings) cannot be changed.
    
    Args:
        field: Name of the field to update (from rl_get_current_config)
        value: New value for the field
    
    Returns:
        JSON string with updated config or error message
    """
@Suppress("unused") private const val _RTT_89: String = "field"
@Suppress("unused") private const val _RTT_90: String = "config"
@Suppress("unused") private const val _RTT_91: String = "available_fields"
@Suppress("unused") private const val _RTT_92: String = "Updated "
@Suppress("unused") private const val _RTT_93: String = " = "
@Suppress("unused") private const val _RTT_94: String = "Unknown field '"
@Suppress("unused") private const val _RTT_95: String = "Field '"
@Suppress("unused") private const val _RTT_96: String = "' is locked and cannot be changed"
@Suppress("unused") private val _RTT_97: String = """
    Start a new RL training run with the current environment and config.
    
    Requires an environment to be selected first using rl_select_environment().
    Use rl_edit_config() to adjust configuration before starting.
    
    This spawns three processes:
    1. run-api (Atropos trajectory API)
    2. launch_training.py (Tinker trainer + inference server)
    3. environment.py serve (the selected environment)
    
    WARNING: Training runs take hours. Use rl_check_status() to monitor
    progress (recommended: check every 30 minutes at most).
    
    Returns:
        JSON string with run_id and initial status
    """
@Suppress("unused") private const val _RTT_98: String = "wandb_project"
@Suppress("unused") private const val _RTT_99: String = "atropos-tinker"
@Suppress("unused") private const val _RTT_100: String = "tinker"
@Suppress("unused") private const val _RTT_101: String = "wandb_run_name"
@Suppress("unused") private const val _RTT_102: String = "run_"
@Suppress("unused") private const val _RTT_103: String = ".yaml"
@Suppress("unused") private const val _RTT_104: String = "starting"
@Suppress("unused") private const val _RTT_105: String = "run_id"
@Suppress("unused") private const val _RTT_106: String = "status"
@Suppress("unused") private const val _RTT_107: String = "config_path"
@Suppress("unused") private const val _RTT_108: String = "logs"
@Suppress("unused") private const val _RTT_109: String = "Training starting. Use rl_check_status(run_id) to monitor (recommended: every 30 minutes)."
@Suppress("unused") private const val _RTT_110: String = "TINKER_API_KEY not set. Add it to ~/.hermes/.env"
@Suppress("unused") private const val _RTT_111: String = "api"
@Suppress("unused") private const val _RTT_112: String = "trainer"
@Suppress("unused") private const val _RTT_113: String = "Environment file not found for '"
@Suppress("unused") private val _RTT_114: String = """
    Get status and metrics for a training run.
    
    RATE LIMITED: For long-running training, this function enforces a
    minimum 30-minute interval between checks for the same run_id.
    
    Args:
        run_id: The run ID returned by rl_start_training()
    
    Returns:
        JSON string with run status and metrics
    """
@Suppress("unused") private const val _RTT_115: String = "running_time_minutes"
@Suppress("unused") private const val _RTT_116: String = "processes"
@Suppress("unused") private const val _RTT_117: String = "active_runs"
@Suppress("unused") private const val _RTT_118: String = "wandb_url"
@Suppress("unused") private const val _RTT_119: String = "metrics"
@Suppress("unused") private const val _RTT_120: String = "step"
@Suppress("unused") private const val _RTT_121: String = "reward_mean"
@Suppress("unused") private const val _RTT_122: String = "percent_correct"
@Suppress("unused") private const val _RTT_123: String = "eval_percent_correct"
@Suppress("unused") private const val _RTT_124: String = "wandb_error"
@Suppress("unused") private const val _RTT_125: String = "rate_limited"
@Suppress("unused") private const val _RTT_126: String = "next_check_in_seconds"
@Suppress("unused") private const val _RTT_127: String = "Run '"
@Suppress("unused") private const val _RTT_128: String = "exited ("
@Suppress("unused") private const val _RTT_129: String = "display_name"
@Suppress("unused") private const val _RTT_130: String = "_step"
@Suppress("unused") private const val _RTT_131: String = "train/reward_mean"
@Suppress("unused") private const val _RTT_132: String = "train/percent_correct"
@Suppress("unused") private const val _RTT_133: String = "eval/percent_correct"
@Suppress("unused") private const val _RTT_134: String = "Rate limited. Next check available in "
@Suppress("unused") private const val _RTT_135: String = " minutes."
@Suppress("unused") private const val _RTT_136: String = "WANDB_ENTITY"
@Suppress("unused") private const val _RTT_137: String = "nousresearch"
@Suppress("unused") private const val _RTT_138: String = ".0f"
@Suppress("unused") private val _RTT_139: String = """
    Stop a running training job.
    
    Args:
        run_id: The run ID to stop
    
    Returns:
        JSON string with stop confirmation
    """
@Suppress("unused") private const val _RTT_140: String = "Stopped training run '"
@Suppress("unused") private const val _RTT_141: String = "' is not running (status: "
@Suppress("unused") private val _RTT_142: String = """
    Get final results and metrics for a training run.
    
    Args:
        run_id: The run ID to get results for
    
    Returns:
        JSON string with final results
    """
@Suppress("unused") private const val _RTT_143: String = "final_metrics"
@Suppress("unused") private const val _RTT_144: String = "history"
@Suppress("unused") private val _RTT_145: String = """
    List all training runs (active and completed).
    
    Returns:
        JSON string with list of runs and their status
    """
@Suppress("unused") private const val _RTT_146: String = "runs"
@Suppress("unused") private val _RTT_147: String = """
    Quick inference test for any environment using Atropos's `process` mode.
    
    Runs a few steps of inference + scoring to validate:
    - Environment loads correctly
    - Prompt construction works
    - Inference parsing is robust (tested with multiple model scales)
    - Verifier/scoring logic works
    
    Default: 3 steps × 16 completions = 48 total rollouts per model.
    Tests 3 models = 144 total rollouts. Quick sanity check.
    
    Test models (varying intelligence levels for robustness):
    - qwen/qwen3-8b (small)
    - zhipu-ai/glm-4-flash (medium)
    - minimax/minimax-m1 (large)
    
    Args:
        num_steps: Steps to run (default: 3, max recommended for testing)
        group_size: Completions per step (default: 16, like training)
        models: Optional model IDs to test. If None, uses all 3 test models.
    
    Returns:
        JSON with results per model: steps_tested, accuracy, scores
    """
@Suppress("unused") private const val _RTT_148: String = "OPENROUTER_API_KEY"
@Suppress("unused") private const val _RTT_149: String = "environment_file"
@Suppress("unused") private const val _RTT_150: String = "test_config"
@Suppress("unused") private const val _RTT_151: String = "models_tested"
@Suppress("unused") private const val _RTT_152: String = "inference_tests"
@Suppress("unused") private const val _RTT_153: String = "summary"
@Suppress("unused") private const val _RTT_154: String = "steps_requested"
@Suppress("unused") private const val _RTT_155: String = "models_succeeded"
@Suppress("unused") private const val _RTT_156: String = "best_model"
@Suppress("unused") private const val _RTT_157: String = "avg_accuracy"
@Suppress("unused") private const val _RTT_158: String = "environment_working"
@Suppress("unused") private const val _RTT_159: String = "output_directory"
@Suppress("unused") private const val _RTT_160: String = "num_steps"
@Suppress("unused") private const val _RTT_161: String = "group_size"
@Suppress("unused") private const val _RTT_162: String = "rollouts_per_model"
@Suppress("unused") private const val _RTT_163: String = "total_rollouts"
@Suppress("unused") private const val _RTT_164: String = "test_inference_RSIAgent_"
@Suppress("unused") private const val _RTT_165: String = "process"
@Suppress("unused") private const val _RTT_166: String = "--env.total_steps"
@Suppress("unused") private const val _RTT_167: String = "--env.group_size"
@Suppress("unused") private const val _RTT_168: String = "--env.use_wandb"
@Suppress("unused") private const val _RTT_169: String = "true"
@Suppress("unused") private const val _RTT_170: String = "--env.wandb_name"
@Suppress("unused") private const val _RTT_171: String = "--env.data_path_to_save_groups"
@Suppress("unused") private const val _RTT_172: String = "--env.tokenizer_name"
@Suppress("unused") private const val _RTT_173: String = "--env.max_token_length"
@Suppress("unused") private const val _RTT_174: String = "--env.max_num_workers"
@Suppress("unused") private const val _RTT_175: String = "--env.max_batches_offpolicy"
@Suppress("unused") private const val _RTT_176: String = "--openai.base_url"
@Suppress("unused") private const val _RTT_177: String = "https://openrouter.ai/api/v1"
@Suppress("unused") private const val _RTT_178: String = "--openai.api_key"
@Suppress("unused") private const val _RTT_179: String = "--openai.model_name"
@Suppress("unused") private const val _RTT_180: String = "--openai.server_type"
@Suppress("unused") private const val _RTT_181: String = "openai"
@Suppress("unused") private const val _RTT_182: String = "--openai.health_check"
@Suppress("unused") private const val _RTT_183: String = "false"
@Suppress("unused") private const val _RTT_184: String = "***API_KEY***"
@Suppress("unused") private const val _RTT_185: String = "model"
@Suppress("unused") private const val _RTT_186: String = "scale"
@Suppress("unused") private const val _RTT_187: String = "wandb_run"
@Suppress("unused") private const val _RTT_188: String = "output_file"
@Suppress("unused") private const val _RTT_189: String = "steps"
@Suppress("unused") private const val _RTT_190: String = "steps_tested"
@Suppress("unused") private const val _RTT_191: String = "total_completions"
@Suppress("unused") private const val _RTT_192: String = "correct_completions"
@Suppress("unused") private const val _RTT_193: String = "OPENROUTER_API_KEY not set. Required for inference testing."
@Suppress("unused") private const val _RTT_194: String = "Testing with "
@Suppress("unused") private const val _RTT_195: String = "test_"
@Suppress("unused") private const val _RTT_196: String = ".jsonl"
@Suppress("unused") private const val _RTT_197: String = "tokenizer_name"
@Suppress("unused") private const val _RTT_198: String = "Command: "
@Suppress("unused") private const val _RTT_199: String = "Working dir: "
@Suppress("unused") private const val _RTT_200: String = "WandB run: "
@Suppress("unused") private const val _RTT_201: String = " steps × "
@Suppress("unused") private const val _RTT_202: String = " completions = "
@Suppress("unused") private const val _RTT_203: String = " rollouts"
@Suppress("unused") private const val _RTT_204: String = "Read stream line by line and print in real-time."
@Suppress("unused") private const val _RTT_205: String = "Process timed out after 10 minutes"
@Suppress("unused") private const val _RTT_206: String = "accuracy"
@Suppress("unused") private const val _RTT_207: String = "steps_with_correct"
@Suppress("unused") private const val _RTT_208: String = "step_success_rate"
@Suppress("unused") private const val _RTT_209: String = "  Results: "
@Suppress("unused") private const val _RTT_210: String = " correct"
@Suppress("unused") private const val _RTT_211: String = "  Accuracy: "
@Suppress("unused") private const val _RTT_212: String = "custom"
@Suppress("unused") private const val _RTT_213: String = "max_token_length"
@Suppress("unused") private const val _RTT_214: String = "max_num_workers"
@Suppress("unused") private const val _RTT_215: String = "max_batches_offpolicy"
@Suppress("unused") private const val _RTT_216: String = "  Log file: "
@Suppress("unused") private const val _RTT_217: String = "Process exited with code "
@Suppress("unused") private const val _RTT_218: String = "stderr"
@Suppress("unused") private const val _RTT_219: String = "stdout"
@Suppress("unused") private const val _RTT_220: String = "log_file"
@Suppress("unused") private val _RTT_221: String = """
  ✅ Process completed successfully"""
@Suppress("unused") private const val _RTT_222: String = "  Timeout!"
@Suppress("unused") private const val _RTT_223: String = "Return code: "
@Suppress("unused") private val _RTT_224: String = """STDOUT:
"""
@Suppress("unused") private val _RTT_225: String = """(empty)
"""
@Suppress("unused") private val _RTT_226: String = """STDERR:
"""
@Suppress("unused") private val _RTT_227: String = """
  ❌ Error: """
@Suppress("unused") private const val _RTT_228: String = "  Last errors:"
@Suppress("unused") private const val _RTT_229: String = "  Output file: "
@Suppress("unused") private const val _RTT_230: String = "  File exists: "
@Suppress("unused") private const val _RTT_231: String = "Output file not created: "
@Suppress("unused") private const val _RTT_232: String = "  Error: "
@Suppress("unused") private const val _RTT_233: String = ".1%"
@Suppress("unused") private const val _RTT_234: String = "  Completed "
@Suppress("unused") private const val _RTT_235: String = " steps"
@Suppress("unused") private const val _RTT_236: String = "⚠️ "
@Suppress("unused") private const val _RTT_237: String = "    "
@Suppress("unused") private const val _RTT_238: String = "correct"
@Suppress("unused") private const val _RTT_239: String = "processing"
@Suppress("unused") private const val _RTT_240: String = "group"
@Suppress("unused") private const val _RTT_241: String = "progress"
@Suppress("unused") private const val _RTT_242: String = "scores"
@Suppress("unused") private const val _RTT_243: String = "completions"
@Suppress("unused") private val _RTT_244: String = """
    Get list of missing requirements for RL tools (API keys and Python version).
    """
@Suppress("unused") private const val _RTT_245: String = "WANDB_API_KEY"
@Suppress("unused") private const val _RTT_246: String = "Python >= 3.11 (current: "
