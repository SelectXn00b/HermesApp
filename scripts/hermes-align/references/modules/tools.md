# tools/ 模块移植 Checklist

## 文件清单（70 个 .py 文件，排除 __init__.py）

### 核心工具（9）
- [ ] `terminal_tool.py` → `TerminalTool.kt`
- [ ] `file_operations.py` → `FileOperations.kt`
- [ ] `file_tools.py` → `FileTools.kt`
- [ ] `browser_tool.py` → `BrowserTool.kt`
- [ ] `browser_camofox.py` → `BrowserCamofox.kt`
- [ ] `browser_camofox_state.py` → `BrowserCamofoxState.kt`
- [ ] `web_tools.py` → `WebTools.kt`
- [ ] `code_execution_tool.py` → `CodeExecutionTool.kt`
- [ ] `mcp_tool.py` → `MCPTool.kt`

### AI/ML 工具（7）
- [ ] `image_generation_tool.py` → `ImageGenerationTool.kt`
- [ ] `tts_tool.py` → `TTSTool.kt`
- [ ] `voice_mode.py` → `VoiceMode.kt`
- [ ] `vision_tools.py` → `VisionTools.kt`
- [ ] `transcription_tools.py` → `TranscriptionTools.kt`
- [ ] `neutts_synth.py` → `NeuTTSSynth.kt`
- [ ] `rl_training_tool.py` → `RLTrainingTool.kt`

### 基础设施工具（12）
- [ ] `memory_tool.py` → `MemoryTool.kt`
- [ ] `todo_tool.py` → `TodoTool.kt`
- [ ] `cronjob_tools.py` → `CronjobTools.kt`
- [ ] `skills_tool.py` → `SkillsTool.kt`
- [x] `skill_manager_tool.py` → `SkillManagerTool.kt` ⚠️ 骨架
- [ ] `skills_hub.py` → `SkillsHub.kt`
- [ ] `skills_sync.py` → `SkillsSync.kt`
- [ ] `skills_guard.py` → `SkillsGuard.kt`
- [ ] `send_message_tool.py` → `SendMessageTool.kt`
- [ ] `delegate_tool.py` → `DelegateTool.kt`
- [ ] `mixture_of_agents_tool.py` → `MixtureOfAgentsTool.kt`
- [ ] `clarify_tool.py` → `ClarifyTool.kt`
- [ ] `session_search_tool.py` → `SessionSearchTool.kt`

### 安全/策略（6）
- [ ] `path_security.py` → `PathSecurity.kt`
- [ ] `tirith_security.py` → `TirithSecurity.kt`
- [ ] `url_safety.py` → `URLSafety.kt`
- [ ] `approval.py` → `Approval.kt`
- [ ] `website_policy.py` → `WebsitePolicy.kt`
- [ ] `osv_check.py` → `OSVCheck.kt`

### 工具系统（14）
- [ ] `registry.py` → `Registry.kt`
- [ ] `managed_tool_gateway.py` → `ManagedToolGateway.kt`
- [ ] `tool_backend_helpers.py` → `ToolBackendHelpers.kt`
- [ ] `tool_result_storage.py` → `ToolResultStorage.kt`
- [ ] `process_registry.py` → `ProcessRegistry.kt`
- [ ] `interrupt.py` → `Interrupt.kt`
- [ ] `ansi_strip.py` → `ANSIStrip.kt`
- [ ] `binary_extensions.py` → `BinaryExtensions.kt`
- [ ] `fuzzy_match.py` → `FuzzyMatch.kt`
- [ ] `patch_parser.py` → `PatchParser.kt`
- [ ] `credential_files.py` → `CredentialFiles.kt`
- [ ] `debug_helpers.py` → `DebugHelpers.kt`
- [ ] `env_passthrough.py` → `EnvPassthrough.kt`
- [ ] `budget_config.py` → `BudgetConfig.kt`

### 独立工具（2）
- [ ] `mcp_oauth.py` → `MCPOAuth.kt`
- [ ] `openrouter_client.py` → `OpenRouterClient.kt`

### 已移入其他模块
- `homeassistant_tool.py` → 在 gateway/ 下实现
- `environments/` → 移到顶层 hermes/environments/
- `browser_providers/` → 移到顶层 hermes/environments/browserProviders/
