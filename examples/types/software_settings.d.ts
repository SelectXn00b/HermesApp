/**
 * Software settings type definitions for Assistance Package Tools
 */

import {
    EnvironmentVariableReadResultData,
    EnvironmentVariableWriteResultData,
    SandboxPackageUpdateResultData,
    SandboxPackagesResultData,
    SandboxScriptExecutionResultData,
    McpRestartWithLogsResultData,
    SpeechServicesConfigResultData,
    SpeechServicesTtsPlaybackTestResultData,
    SpeechServicesUpdateResultData,
    ModelConfigsResultData,
    ModelConfigCreateResultData,
    ModelConfigUpdateResultData,
    ModelConfigDeleteResultData,
    FunctionModelConfigsResultData,
    FunctionModelConfigResultData,
    FunctionModelBindingResultData,
    ModelConfigConnectionTestResultData
} from './results';

/**
 * Software settings operations namespace
 */
export namespace SoftwareSettings {
    interface SpeechServicesUpdateOptions {
        tts_service_type?: 'SIMPLE_TTS' | 'HTTP_TTS' | 'OPENAI_WS_TTS' | 'SILICONFLOW_TTS' | 'MINIMAX_TTS' | 'OPENAI_TTS';
        tts_url_template?: string;
        tts_api_key?: string;
        tts_headers?: string | Record<string, string>;
        tts_http_method?: 'GET' | 'POST' | string;
        tts_request_body?: string;
        tts_content_type?: string;
        tts_locale?: string;
        tts_voice_id?: string;
        tts_model_name?: string;
        tts_response_pipeline?: string | Array<{
            type: string;
            path?: string;
            headers?: Record<string, string>;
        }>;
        tts_cleaner_regexs?: string | string[];
        tts_speech_rate?: number;
        tts_pitch?: number;
        stt_service_type?: 'SHERPA_NCNN' | 'OPENAI_STT' | 'DEEPGRAM_STT' | string;
        stt_endpoint_url?: string;
        stt_api_key?: string;
        stt_model_name?: string;
    }

    interface TtsPlaybackTestOptions {
        interrupt?: boolean;
        speech_rate?: number;
        pitch?: number;
    }

    interface SandboxScriptDirectOptions {
        source_path?: string;
        source_code?: string;
        params_json?: string;
        env_file_path?: string;
        script_label?: string;
        wait_ms?: number | string;
    }

    interface ModelConfigUpdateOptions {
        name?: string;
        api_provider_type?: string;
        api_endpoint?: string;
        api_key?: string;
        model_name?: string;
        max_tokens_enabled?: boolean;
        max_tokens?: number;
        temperature_enabled?: boolean;
        temperature?: number;
        top_p_enabled?: boolean;
        top_p?: number;
        top_k_enabled?: boolean;
        top_k?: number;
        presence_penalty_enabled?: boolean;
        presence_penalty?: number;
        frequency_penalty_enabled?: boolean;
        frequency_penalty?: number;
        repetition_penalty_enabled?: boolean;
        repetition_penalty?: number;
        context_length?: number;
        max_context_length?: number;
        enable_max_context_mode?: boolean;
        summary_token_threshold?: number;
        enable_summary?: boolean;
        enable_summary_by_message_count?: boolean;
        summary_message_count_threshold?: number;
        custom_parameters?: string | Record<string, unknown>;
        custom_headers?: string | Record<string, string>;
        enable_direct_image_processing?: boolean;
        enable_direct_audio_processing?: boolean;
        enable_direct_video_processing?: boolean;
        enable_google_search?: boolean;
        enable_tool_call?: boolean;
        mnn_forward_type?: number;
        mnn_thread_count?: number;
        llama_thread_count?: number;
        llama_context_size?: number;
        llama_gpu_layers?: number;
        request_limit_per_minute?: number;
        max_concurrent_requests?: number;
    }

    /**
     * Read current value of an environment variable.
     * @param key - Environment variable key
     */
    function readEnvironmentVariable(key: string): Promise<EnvironmentVariableReadResultData>;

    /**
     * Write an environment variable; empty value clears the variable.
     * @param key - Environment variable key
     * @param value - Variable value (empty string clears)
     */
    function writeEnvironmentVariable(key: string, value?: string): Promise<EnvironmentVariableWriteResultData>;

    /**
     * List sandbox packages (built-in and external) with enabled states and management paths.
     */
    function listSandboxPackages(): Promise<SandboxPackagesResultData>;

    /**
     * Enable or disable a sandbox package.
     * @param packageName - Sandbox package name
     * @param enabled - true to enable, false to disable
     */
    function setSandboxPackageEnabled(
        packageName: string,
        enabled: boolean | string | number
    ): Promise<SandboxPackageUpdateResultData>;

    /**
     * Execute a sandbox script directly and return the structured raw execution result.
     * @param options - Script source, params, optional env file, and wait timeout
     */
    function executeSandboxScriptDirect(
        options?: Partial<SandboxScriptDirectOptions>
    ): Promise<SandboxScriptExecutionResultData>;

    /**
     * Restart MCP startup flow and collect per-plugin startup logs.
     * @param timeoutMs - Optional max wait time in milliseconds
     */
    function restartMcpWithLogs(timeoutMs?: number | string): Promise<McpRestartWithLogsResultData>;

    /**
     * Get current TTS/STT speech services configuration.
     */
    function getSpeechServicesConfig(): Promise<SpeechServicesConfigResultData>;

    /**
     * Update TTS/STT speech services configuration.
     * @param updates - Fields to update
     */
    function setSpeechServicesConfig(
        updates?: Partial<SpeechServicesUpdateOptions>
    ): Promise<SpeechServicesUpdateResultData>;

    /**
     * Play one TTS test utterance using the current speech-service configuration.
     * @param text - Text to play once
     * @param options - Optional one-off playback overrides
     */
    function testTtsPlayback(
        text: string,
        options?: Partial<TtsPlaybackTestOptions>
    ): Promise<SpeechServicesTtsPlaybackTestResultData>;

    /**
     * List all model configs and current function bindings.
     */
    function listModelConfigs(): Promise<ModelConfigsResultData>;

    /**
     * Create a model config.
     * @param options - Optional initial fields
     */
    function createModelConfig(options?: Partial<ModelConfigUpdateOptions> & { name?: string }): Promise<ModelConfigCreateResultData>;

    /**
     * Update an existing model config by id.
     * @param configId - Model config id
     * @param updates - Fields to update
     */
    function updateModelConfig(
        configId: string,
        updates?: Partial<ModelConfigUpdateOptions>
    ): Promise<ModelConfigUpdateResultData>;

    /**
     * Delete a model config by id.
     * @param configId - Model config id
     */
    function deleteModelConfig(configId: string): Promise<ModelConfigDeleteResultData>;

    /**
     * List function model config bindings.
     */
    function listFunctionModelConfigs(): Promise<FunctionModelConfigsResultData>;

    /**
     * Get the model config bound to a specific function type.
     * @param functionType - Function type enum name
     */
    function getFunctionModelConfig(functionType: string): Promise<FunctionModelConfigResultData>;

    /**
     * Bind one function type to a model config.
     * @param functionType - Function type enum name
     * @param configId - Model config id
     * @param modelIndex - Optional model index
     */
    function setFunctionModelConfig(
        functionType: string,
        configId: string,
        modelIndex?: number | string
    ): Promise<FunctionModelBindingResultData>;

    /**
     * Test one model config with the same checks as model settings UI.
     * @param configId - Model config id
     * @param modelIndex - Optional model index
     */
    function testModelConfigConnection(
        configId: string,
        modelIndex?: number | string
    ): Promise<ModelConfigConnectionTestResultData>;
}
