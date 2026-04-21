# hermes-android 未对齐清单

> 自动生成于 2026-04-20 19:07 | 加权对齐度: 80.8%
> 文件 166/166 (100.0%) | 类方法 1129/1129 (100.0%) | 模块函数 358/1499 (23.9%) | 类 190/190 (100.0%)

## 🔴 核心 (13 文件, 0 类方法, 67 模块函数)

### `gateway/status.py`

**模块函数** (29 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_pid_path` | `_getPidPath` |
| `_get_runtime_status_path` | `_getRuntimeStatusPath` |
| `_utc_now_iso` | `_utcNowIso` |
| `terminate_pid` | `terminatePid` |
| `_scope_hash` | `_scopeHash` |
| `_get_scope_lock_path` | `_getScopeLockPath` |
| `_get_process_start_time` | `_getProcessStartTime` |
| `_read_process_cmdline` | `_readProcessCmdline` |
| `_looks_like_gateway_process` | `_looksLikeGatewayProcess` |
| `_record_looks_like_gateway` | `_recordLooksLikeGateway` |
| `_build_pid_record` | `_buildPidRecord` |
| `_build_runtime_status_record` | `_buildRuntimeStatusRecord` |
| `_read_json_file` | `_readJsonFile` |
| `_write_json_file` | `_writeJsonFile` |
| `_read_pid_record` | `_readPidRecord` |
| `_cleanup_invalid_pid_path` | `_cleanupInvalidPidPath` |
| `write_pid_file` | `writePidFile` |
| `write_runtime_status` | `writeRuntimeStatus` |
| `read_runtime_status` | `readRuntimeStatus` |
| `remove_pid_file` | `removePidFile` |
| `acquire_scoped_lock` | `acquireScopedLock` |
| `release_scoped_lock` | `releaseScopedLock` |
| `release_all_scoped_locks` | `releaseAllScopedLocks` |
| `_get_takeover_marker_path` | `_getTakeoverMarkerPath` |
| `write_takeover_marker` | `writeTakeoverMarker` |
| `consume_takeover_marker_for_self` | `consumeTakeoverMarkerForSelf` |
| `clear_takeover_marker` | `clearTakeoverMarker` |
| `get_running_pid` | `getRunningPid` |
| `is_gateway_running` | `isGatewayRunning` |

### `gateway/run.py`

**模块函数** (13 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_ensure_ssl_certs` | `_ensureSslCerts` |
| `_normalize_whatsapp_identifier` | `_normalizeWhatsappIdentifier` |
| `_expand_whatsapp_auth_aliases` | `_expandWhatsappAuthAliases` |
| `_resolve_runtime_agent_kwargs` | `_resolveRuntimeAgentKwargs` |
| `_build_media_placeholder` | `_buildMediaPlaceholder` |
| `_dequeue_pending_event` | `_dequeuePendingEvent` |
| `_check_unavailable_skill` | `_checkUnavailableSkill` |
| `_platform_config_key` | `_platformConfigKey` |
| `_load_gateway_config` | `_loadGatewayConfig` |
| `_parse_session_key` | `_parseSessionKey` |
| `_start_cron_ticker` | `_startCronTicker` |
| `start_gateway` | `startGateway` |
| `main` | `main` |

### `gateway/channel_directory.py`

**模块函数** (12 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_normalize_channel_query` | `_normalizeChannelQuery` |
| `_channel_target_name` | `_channelTargetName` |
| `_session_entry_id` | `_sessionEntryId` |
| `_session_entry_name` | `_sessionEntryName` |
| `build_channel_directory` | `buildChannelDirectory` |
| `_build_discord` | `_buildDiscord` |
| `_build_slack` | `_buildSlack` |
| `_build_from_sessions` | `_buildFromSessions` |
| `load_directory` | `loadDirectory` |
| `lookup_channel_type` | `lookupChannelType` |
| `resolve_channel_name` | `resolveChannelName` |
| `format_directory_for_display` | `formatDirectoryForDisplay` |

### `gateway/sticker_cache.py`

**模块函数** (6 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_load_cache` | `_loadCache` |
| `_save_cache` | `_saveCache` |
| `get_cached_description` | `getCachedDescription` |
| `cache_sticker_description` | `cacheStickerDescription` |
| `build_sticker_injection` | `buildStickerInjection` |
| `build_animated_sticker_injection` | `buildAnimatedStickerInjection` |

### `gateway/builtin_hooks/boot_md.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_boot_prompt` | `_buildBootPrompt` |
| `_run_boot_agent` | `_runBootAgent` |
| `handle` | `handle` |

### `gateway/display_config.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `resolve_display_setting` | `resolveDisplaySetting` |
| `_normalise` | `_normalise` |

### `gateway/pairing.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_secure_write` | `_secureWrite` |

### `gateway/mirror.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `mirror_to_session` | `mirrorToSession` |

## 🟡 Agent (29 文件, 0 类方法, 308 模块函数)

### `agent/auxiliary_client.py`

**模块函数** (56 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_fixed_temperature_for_model` | `_fixedTemperatureForModel` |
| `_to_openai_base_url` | `_toOpenaiBaseUrl` |
| `_select_pool_entry` | `_selectPoolEntry` |
| `_pool_runtime_api_key` | `_poolRuntimeApiKey` |
| `_pool_runtime_base_url` | `_poolRuntimeBaseUrl` |
| `_convert_content_for_responses` | `_convertContentForResponses` |
| `_read_nous_auth` | `_readNousAuth` |
| `_nous_api_key` | `_nousApiKey` |
| `_nous_base_url` | `_nousBaseUrl` |
| `_read_codex_access_token` | `_readCodexAccessToken` |
| `_resolve_api_key_provider` | `_resolveApiKeyProvider` |
| `_try_openrouter` | `_tryOpenrouter` |
| `_try_nous` | `_tryNous` |
| `_read_main_model` | `_readMainModel` |
| `_read_main_provider` | `_readMainProvider` |
| `_resolve_custom_runtime` | `_resolveCustomRuntime` |
| `_current_custom_base_url` | `_currentCustomBaseUrl` |
| `_validate_proxy_env_urls` | `_validateProxyEnvUrls` |
| `_validate_base_url` | `_validateBaseUrl` |
| `_try_custom_endpoint` | `_tryCustomEndpoint` |
| `_try_codex` | `_tryCodex` |
| `_try_anthropic` | `_tryAnthropic` |
| `_normalize_main_runtime` | `_normalizeMainRuntime` |
| `_get_provider_chain` | `_getProviderChain` |
| `_is_payment_error` | `_isPaymentError` |
| `_is_connection_error` | `_isConnectionError` |
| `_try_payment_fallback` | `_tryPaymentFallback` |
| `_resolve_auto` | `_resolveAuto` |
| `_to_async_client` | `_toAsyncClient` |
| `_normalize_resolved_model` | `_normalizeResolvedModel` |
| `resolve_provider_client` | `resolveProviderClient` |
| `get_text_auxiliary_client` | `getTextAuxiliaryClient` |
| `get_async_text_auxiliary_client` | `getAsyncTextAuxiliaryClient` |
| `_normalize_vision_provider` | `_normalizeVisionProvider` |
| `_resolve_strict_vision_backend` | `_resolveStrictVisionBackend` |
| `_strict_vision_backend_available` | `_strictVisionBackendAvailable` |
| `get_available_vision_backends` | `getAvailableVisionBackends` |
| `resolve_vision_provider_client` | `resolveVisionProviderClient` |
| `get_auxiliary_extra_body` | `getAuxiliaryExtraBody` |
| `auxiliary_max_tokens_param` | `auxiliaryMaxTokensParam` |
| `neuter_async_httpx_del` | `neuterAsyncHttpxDel` |
| `_force_close_async_httpx` | `_forceCloseAsyncHttpx` |
| `shutdown_cached_clients` | `shutdownCachedClients` |
| `cleanup_stale_async_clients` | `cleanupStaleAsyncClients` |
| `_is_openrouter_client` | `_isOpenrouterClient` |
| `_compat_model` | `_compatModel` |
| `_get_cached_client` | `_getCachedClient` |
| `_resolve_task_provider_model` | `_resolveTaskProviderModel` |
| `_get_task_timeout` | `_getTaskTimeout` |
| `_is_anthropic_compat_endpoint` | `_isAnthropicCompatEndpoint` |
| `_convert_openai_images_to_anthropic` | `_convertOpenaiImagesToAnthropic` |
| `_build_call_kwargs` | `_buildCallKwargs` |
| `_validate_llm_response` | `_validateLlmResponse` |
| `call_llm` | `callLlm` |
| `extract_content_or_reasoning` | `extractContentOrReasoning` |
| `async_call_llm` | `asyncCallLlm` |

### `agent/anthropic_adapter.py`

**模块函数** (37 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_anthropic_max_output` | `_getAnthropicMaxOutput` |
| `_supports_adaptive_thinking` | `_supportsAdaptiveThinking` |
| `_supports_xhigh_effort` | `_supportsXhighEffort` |
| `_forbids_sampling_params` | `_forbidsSamplingParams` |
| `_detect_claude_code_version` | `_detectClaudeCodeVersion` |
| `_get_claude_code_version` | `_getClaudeCodeVersion` |
| `_is_oauth_token` | `_isOauthToken` |
| `_normalize_base_url_text` | `_normalizeBaseUrlText` |
| `_is_third_party_anthropic_endpoint` | `_isThirdPartyAnthropicEndpoint` |
| `_requires_bearer_auth` | `_requiresBearerAuth` |
| `_common_betas_for_base_url` | `_commonBetasForBaseUrl` |
| `build_anthropic_client` | `buildAnthropicClient` |
| `build_anthropic_bedrock_client` | `buildAnthropicBedrockClient` |
| `read_claude_code_credentials` | `readClaudeCodeCredentials` |
| `read_claude_managed_key` | `readClaudeManagedKey` |
| `is_claude_code_token_valid` | `isClaudeCodeTokenValid` |
| `refresh_anthropic_oauth_pure` | `refreshAnthropicOauthPure` |
| `_refresh_oauth_token` | `_refreshOauthToken` |
| `_write_claude_code_credentials` | `_writeClaudeCodeCredentials` |
| `_resolve_claude_code_token_from_credentials` | `_resolveClaudeCodeTokenFromCredentials` |
| `_prefer_refreshable_claude_code_token` | `_preferRefreshableClaudeCodeToken` |
| `resolve_anthropic_token` | `resolveAnthropicToken` |
| `run_oauth_setup_token` | `runOauthSetupToken` |
| `_generate_pkce` | `_generatePkce` |
| `run_hermes_oauth_login_pure` | `runHermesOauthLoginPure` |
| `read_hermes_oauth_credentials` | `readHermesOauthCredentials` |
| `normalize_model_name` | `normalizeModelName` |
| `_sanitize_tool_id` | `_sanitizeToolId` |
| `convert_tools_to_anthropic` | `convertToolsToAnthropic` |
| `_image_source_from_openai_url` | `_imageSourceFromOpenaiUrl` |
| `_convert_content_part_to_anthropic` | `_convertContentPartToAnthropic` |
| `_to_plain_data` | `_toPlainData` |
| `_extract_preserved_thinking_blocks` | `_extractPreservedThinkingBlocks` |
| `_convert_content_to_anthropic` | `_convertContentToAnthropic` |
| `convert_messages_to_anthropic` | `convertMessagesToAnthropic` |
| `build_anthropic_kwargs` | `buildAnthropicKwargs` |
| `normalize_anthropic_response` | `normalizeAnthropicResponse` |

### `agent/model_metadata.py`

**模块函数** (33 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_normalize_base_url` | `_normalizeBaseUrl` |
| `_is_openrouter_base_url` | `_isOpenrouterBaseUrl` |
| `_is_custom_endpoint` | `_isCustomEndpoint` |
| `_infer_provider_from_url` | `_inferProviderFromUrl` |
| `_is_known_provider_base_url` | `_isKnownProviderBaseUrl` |
| `is_local_endpoint` | `isLocalEndpoint` |
| `detect_local_server_type` | `detectLocalServerType` |
| `_iter_nested_dicts` | `_iterNestedDicts` |
| `_coerce_reasonable_int` | `_coerceReasonableInt` |
| `_extract_first_int` | `_extractFirstInt` |
| `_extract_context_length` | `_extractContextLength` |
| `_extract_max_completion_tokens` | `_extractMaxCompletionTokens` |
| `_extract_pricing` | `_extractPricing` |
| `_add_model_aliases` | `_addModelAliases` |
| `fetch_model_metadata` | `fetchModelMetadata` |
| `fetch_endpoint_model_metadata` | `fetchEndpointModelMetadata` |
| `_get_context_cache_path` | `_getContextCachePath` |
| `_load_context_cache` | `_loadContextCache` |
| `save_context_length` | `saveContextLength` |
| `get_cached_context_length` | `getCachedContextLength` |
| `get_next_probe_tier` | `getNextProbeTier` |
| `parse_context_limit_from_error` | `parseContextLimitFromError` |
| `parse_available_output_tokens_from_error` | `parseAvailableOutputTokensFromError` |
| `_model_id_matches` | `_modelIdMatches` |
| `query_ollama_num_ctx` | `queryOllamaNumCtx` |
| `_query_local_context_length` | `_queryLocalContextLength` |
| `_normalize_model_version` | `_normalizeModelVersion` |
| `_query_anthropic_context_length` | `_queryAnthropicContextLength` |
| `_resolve_nous_context_length` | `_resolveNousContextLength` |
| `get_model_context_length` | `getModelContextLength` |
| `estimate_tokens_rough` | `estimateTokensRough` |
| `estimate_messages_tokens_rough` | `estimateMessagesTokensRough` |
| `estimate_request_tokens_rough` | `estimateRequestTokensRough` |

### `agent/display.py`

**模块函数** (23 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_diff_ansi` | `_diffAnsi` |
| `_diff_dim` | `_diffDim` |
| `_diff_file` | `_diffFile` |
| `_diff_hunk` | `_diffHunk` |
| `_diff_minus` | `_diffMinus` |
| `_diff_plus` | `_diffPlus` |
| `set_tool_preview_max_len` | `setToolPreviewMaxLen` |
| `get_tool_preview_max_len` | `getToolPreviewMaxLen` |
| `_get_skin` | `_getSkin` |
| `get_skin_tool_prefix` | `getSkinToolPrefix` |
| `_resolved_path` | `_resolvedPath` |
| `_snapshot_text` | `_snapshotText` |
| `_display_diff_path` | `_displayDiffPath` |
| `_resolve_skill_manage_paths` | `_resolveSkillManagePaths` |
| `_resolve_local_edit_paths` | `_resolveLocalEditPaths` |
| `capture_local_edit_snapshot` | `captureLocalEditSnapshot` |
| `_diff_from_snapshot` | `_diffFromSnapshot` |
| `extract_edit_diff` | `extractEditDiff` |
| `_emit_inline_diff` | `_emitInlineDiff` |
| `_render_inline_unified_diff` | `_renderInlineUnifiedDiff` |
| `_split_unified_diff_sections` | `_splitUnifiedDiffSections` |
| `_summarize_rendered_diff_sections` | `_summarizeRenderedDiffSections` |
| `render_edit_diff_with_delta` | `renderEditDiffWithDelta` |

### `agent/prompt_builder.py`

**模块函数** (22 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_scan_context_content` | `_scanContextContent` |
| `_find_git_root` | `_findGitRoot` |
| `_find_hermes_md` | `_findHermesMd` |
| `_strip_yaml_frontmatter` | `_stripYamlFrontmatter` |
| `build_environment_hints` | `buildEnvironmentHints` |
| `_skills_prompt_snapshot_path` | `_skillsPromptSnapshotPath` |
| `clear_skills_system_prompt_cache` | `clearSkillsSystemPromptCache` |
| `_build_skills_manifest` | `_buildSkillsManifest` |
| `_load_skills_snapshot` | `_loadSkillsSnapshot` |
| `_write_skills_snapshot` | `_writeSkillsSnapshot` |
| `_build_snapshot_entry` | `_buildSnapshotEntry` |
| `_parse_skill_file` | `_parseSkillFile` |
| `_skill_should_show` | `_skillShouldShow` |
| `build_skills_system_prompt` | `buildSkillsSystemPrompt` |
| `build_nous_subscription_prompt` | `buildNousSubscriptionPrompt` |
| `_truncate_content` | `_truncateContent` |
| `load_soul_md` | `loadSoulMd` |
| `_load_hermes_md` | `_loadHermesMd` |
| `_load_agents_md` | `_loadAgentsMd` |
| `_load_claude_md` | `_loadClaudeMd` |
| `_load_cursorrules` | `_loadCursorrules` |
| `build_context_files_prompt` | `buildContextFilesPrompt` |

### `agent/context_references.py`

**模块函数** (21 个):

| Python | 期望 Kotlin |
|--------|------------|
| `parse_context_references` | `parseContextReferences` |
| `preprocess_context_references` | `preprocessContextReferences` |
| `preprocess_context_references_async` | `preprocessContextReferencesAsync` |
| `_expand_reference` | `_expandReference` |
| `_expand_file_reference` | `_expandFileReference` |
| `_expand_folder_reference` | `_expandFolderReference` |
| `_expand_git_reference` | `_expandGitReference` |
| `_fetch_url_content` | `_fetchUrlContent` |
| `_default_url_fetcher` | `_defaultUrlFetcher` |
| `_resolve_path` | `_resolvePath` |
| `_ensure_reference_path_allowed` | `_ensureReferencePathAllowed` |
| `_strip_trailing_punctuation` | `_stripTrailingPunctuation` |
| `_strip_reference_wrappers` | `_stripReferenceWrappers` |
| `_parse_file_reference_value` | `_parseFileReferenceValue` |
| `_remove_reference_tokens` | `_removeReferenceTokens` |
| `_is_binary_file` | `_isBinaryFile` |
| `_build_folder_listing` | `_buildFolderListing` |
| `_iter_visible_entries` | `_iterVisibleEntries` |
| `_rg_files` | `_rgFiles` |
| `_file_metadata` | `_fileMetadata` |
| `_code_fence_language` | `_codeFenceLanguage` |

### `agent/skill_utils.py`

**模块函数** (16 个):

| Python | 期望 Kotlin |
|--------|------------|
| `yaml_load` | `yamlLoad` |
| `parse_frontmatter` | `parseFrontmatter` |
| `skill_matches_platform` | `skillMatchesPlatform` |
| `get_disabled_skill_names` | `getDisabledSkillNames` |
| `_normalize_string_set` | `_normalizeStringSet` |
| `get_external_skills_dirs` | `getExternalSkillsDirs` |
| `get_all_skills_dirs` | `getAllSkillsDirs` |
| `extract_skill_conditions` | `extractSkillConditions` |
| `extract_skill_config_vars` | `extractSkillConfigVars` |
| `discover_all_skill_config_vars` | `discoverAllSkillConfigVars` |
| `_resolve_dotpath` | `_resolveDotpath` |
| `resolve_skill_config_values` | `resolveSkillConfigValues` |
| `extract_skill_description` | `extractSkillDescription` |
| `iter_skill_index_files` | `iterSkillIndexFiles` |
| `parse_qualified_name` | `parseQualifiedName` |
| `is_valid_namespace` | `isValidNamespace` |

### `agent/credential_pool.py`

**模块函数** (14 个):

| Python | 期望 Kotlin |
|--------|------------|
| `label_from_token` | `labelFromToken` |
| `_next_priority` | `_nextPriority` |
| `_is_manual_source` | `_isManualSource` |
| `_normalize_custom_pool_name` | `_normalizeCustomPoolName` |
| `_iter_custom_providers` | `_iterCustomProviders` |
| `get_custom_provider_pool_key` | `getCustomProviderPoolKey` |
| `list_custom_pool_providers` | `listCustomPoolProviders` |
| `_get_custom_provider_config` | `_getCustomProviderConfig` |
| `_upsert_entry` | `_upsertEntry` |
| `_normalize_pool_priorities` | `_normalizePoolPriorities` |
| `_seed_from_singletons` | `_seedFromSingletons` |
| `_seed_from_env` | `_seedFromEnv` |
| `_prune_stale_seeded_entries` | `_pruneStaleSeededEntries` |
| `_seed_custom_pool` | `_seedCustomPool` |

### `agent/usage_pricing.py`

**模块函数** (12 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_to_decimal` | `_toDecimal` |
| `_to_int` | `_toInt` |
| `resolve_billing_route` | `resolveBillingRoute` |
| `_lookup_official_docs_pricing` | `_lookupOfficialDocsPricing` |
| `_openrouter_pricing_entry` | `_openrouterPricingEntry` |
| `_pricing_entry_from_metadata` | `_pricingEntryFromMetadata` |
| `get_pricing_entry` | `getPricingEntry` |
| `normalize_usage` | `normalizeUsage` |
| `estimate_usage_cost` | `estimateUsageCost` |
| `has_known_pricing` | `hasKnownPricing` |
| `format_duration_compact` | `formatDurationCompact` |
| `format_token_count_compact` | `formatTokenCountCompact` |

### `agent/google_oauth.py`

**模块函数** (10 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_lock_path` | `_lockPath` |
| `_credentials_lock` | `_credentialsLock` |
| `_locate_gemini_cli_oauth_js` | `_locateGeminiCliOauthJs` |
| `_scrape_client_credentials` | `_scrapeClientCredentials` |
| `_bind_callback_server` | `_bindCallbackServer` |
| `_is_headless` | `_isHeadless` |
| `start_oauth_flow` | `startOauthFlow` |
| `_paste_mode_login` | `_pasteModeLogin` |
| `_prompt_paste_fallback` | `_promptPasteFallback` |
| `run_gemini_oauth_login_pure` | `runGeminiOauthLoginPure` |

### `agent/error_classifier.py`

**模块函数** (10 个):

| Python | 期望 Kotlin |
|--------|------------|
| `classify_api_error` | `classifyApiError` |
| `_classify_by_status` | `_classifyByStatus` |
| `_classify_402` | `_classify402` |
| `_classify_400` | `_classify400` |
| `_classify_by_error_code` | `_classifyByErrorCode` |
| `_classify_by_message` | `_classifyByMessage` |
| `_extract_status_code` | `_extractStatusCode` |
| `_extract_error_body` | `_extractErrorBody` |
| `_extract_error_code` | `_extractErrorCode` |
| `_extract_message` | `_extractMessage` |

### `agent/skill_commands.py`

**模块函数** (9 个):

| Python | 期望 Kotlin |
|--------|------------|
| `build_plan_path` | `buildPlanPath` |
| `_load_skill_payload` | `_loadSkillPayload` |
| `_inject_skill_config` | `_injectSkillConfig` |
| `_build_skill_message` | `_buildSkillMessage` |
| `scan_skill_commands` | `scanSkillCommands` |
| `get_skill_commands` | `getSkillCommands` |
| `resolve_skill_command_key` | `resolveSkillCommandKey` |
| `build_skill_invocation_message` | `buildSkillInvocationMessage` |
| `build_preloaded_skills_prompt` | `buildPreloadedSkillsPrompt` |

### `agent/rate_limit_tracker.py`

**模块函数** (9 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_safe_int` | `_safeInt` |
| `_safe_float` | `_safeFloat` |
| `parse_rate_limit_headers` | `parseRateLimitHeaders` |
| `_fmt_count` | `_fmtCount` |
| `_fmt_seconds` | `_fmtSeconds` |
| `_bar` | `_bar` |
| `_bucket_line` | `_bucketLine` |
| `format_rate_limit_display` | `formatRateLimitDisplay` |
| `format_rate_limit_compact` | `formatRateLimitCompact` |

### `agent/copilot_acp_client.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_command` | `_resolveCommand` |
| `_resolve_args` | `_resolveArgs` |
| `_jsonrpc_error` | `_jsonrpcError` |
| `_format_messages_as_prompt` | `_formatMessagesAsPrompt` |
| `_render_message_content` | `_renderMessageContent` |
| `_extract_tool_calls_from_text` | `_extractToolCallsFromText` |
| `_ensure_path_within_cwd` | `_ensurePathWithinCwd` |

### `agent/models_dev.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_cache_path` | `_getCachePath` |
| `_save_disk_cache` | `_saveDiskCache` |
| `fetch_models_dev` | `fetchModelsDev` |
| `lookup_models_dev_context` | `lookupModelsDevContext` |
| `_parse_model_info` | `_parseModelInfo` |
| `_parse_provider_info` | `_parseProviderInfo` |
| `get_model_info` | `getModelInfo` |

### `agent/bedrock_adapter.py`

**模块函数** (5 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_require_boto3` | `_requireBoto3` |
| `_get_bedrock_runtime_client` | `_getBedrockRuntimeClient` |
| `_get_bedrock_control_client` | `_getBedrockControlClient` |
| `call_converse` | `callConverse` |
| `call_converse_stream` | `callConverseStream` |

### `agent/smart_model_routing.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_coerce_bool` | `_coerceBool` |
| `_coerce_int` | `_coerceInt` |
| `choose_cheap_model_route` | `chooseCheapModelRoute` |
| `resolve_turn_route` | `resolveTurnRoute` |

### `agent/title_generator.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `generate_title` | `generateTitle` |
| `auto_title_session` | `autoTitleSession` |
| `maybe_auto_title` | `maybeAutoTitle` |

### `agent/redact.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_mask_token` | `_maskToken` |
| `redact_sensitive_text` | `redactSensitiveText` |

### `agent/memory_manager.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `sanitize_context` | `sanitizeContext` |
| `build_memory_context_block` | `buildMemoryContextBlock` |

### `agent/prompt_caching.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_apply_cache_marker` | `_applyCacheMarker` |
| `apply_anthropic_cache_control` | `applyAnthropicCacheControl` |

### `agent/context_compressor.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_summarize_tool_result` | `_summarizeToolResult` |

### `agent/gemini_cloudcode_adapter.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_iter_sse_events` | `_iterSseEvents` |

### `agent/manual_compression_feedback.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `summarize_manual_compression` | `summarizeManualCompression` |

### `agent/retry_utils.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `jittered_backoff` | `jitteredBackoff` |

## 🟠 工具 (65 文件, 0 类方法, 649 模块函数)

### `tools/browser_tool.py`

**模块函数** (50 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_browser_candidate_path_dirs` | `_browserCandidatePathDirs` |
| `_merge_browser_path` | `_mergeBrowserPath` |
| `_get_command_timeout` | `_getCommandTimeout` |
| `_get_vision_model` | `_getVisionModel` |
| `_get_extraction_model` | `_getExtractionModel` |
| `_resolve_cdp_override` | `_resolveCdpOverride` |
| `_get_cdp_override` | `_getCdpOverride` |
| `_get_cloud_provider` | `_getCloudProvider` |
| `_browser_install_hint` | `_browserInstallHint` |
| `_requires_real_termux_browser_install` | `_requiresRealTermuxBrowserInstall` |
| `_termux_browser_install_error` | `_termuxBrowserInstallError` |
| `_is_local_mode` | `_isLocalMode` |
| `_is_local_backend` | `_isLocalBackend` |
| `_allow_private_urls` | `_allowPrivateUrls` |
| `_socket_safe_tmpdir` | `_socketSafeTmpdir` |
| `_emergency_cleanup_all_sessions` | `_emergencyCleanupAllSessions` |
| `_cleanup_inactive_browser_sessions` | `_cleanupInactiveBrowserSessions` |
| `_write_owner_pid` | `_writeOwnerPid` |
| `_reap_orphaned_browser_sessions` | `_reapOrphanedBrowserSessions` |
| `_browser_cleanup_thread_worker` | `_browserCleanupThreadWorker` |
| `_start_browser_cleanup_thread` | `_startBrowserCleanupThread` |
| `_stop_browser_cleanup_thread` | `_stopBrowserCleanupThread` |
| `_update_session_activity` | `_updateSessionActivity` |
| `_create_local_session` | `_createLocalSession` |
| `_create_cdp_session` | `_createCdpSession` |
| `_get_session_info` | `_getSessionInfo` |
| `_find_agent_browser` | `_findAgentBrowser` |
| `_extract_screenshot_path_from_text` | `_extractScreenshotPathFromText` |
| `_run_browser_command` | `_runBrowserCommand` |
| `_extract_relevant_content` | `_extractRelevantContent` |
| `_truncate_snapshot` | `_truncateSnapshot` |
| `browser_navigate` | `browserNavigate` |
| `browser_snapshot` | `browserSnapshot` |
| `browser_click` | `browserClick` |
| `browser_type` | `browserType` |
| `browser_scroll` | `browserScroll` |
| `browser_back` | `browserBack` |
| `browser_press` | `browserPress` |
| `browser_console` | `browserConsole` |
| `_browser_eval` | `_browserEval` |
| `_camofox_eval` | `_camofoxEval` |
| `_maybe_start_recording` | `_maybeStartRecording` |
| `_maybe_stop_recording` | `_maybeStopRecording` |
| `browser_get_images` | `browserGetImages` |
| `browser_vision` | `browserVision` |
| `_cleanup_old_screenshots` | `_cleanupOldScreenshots` |
| `_cleanup_old_recordings` | `_cleanupOldRecordings` |
| `cleanup_browser` | `cleanupBrowser` |
| `cleanup_all_browsers` | `cleanupAllBrowsers` |
| `check_browser_requirements` | `checkBrowserRequirements` |

### `tools/mcp_tool.py`

**模块函数** (41 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_safe_env` | `_buildSafeEnv` |
| `_sanitize_error` | `_sanitizeError` |
| `_scan_mcp_description` | `_scanMcpDescription` |
| `_prepend_path` | `_prependPath` |
| `_resolve_stdio_command` | `_resolveStdioCommand` |
| `_format_connect_error` | `_formatConnectError` |
| `_safe_numeric` | `_safeNumeric` |
| `_get_auth_error_types` | `_getAuthErrorTypes` |
| `_is_auth_error` | `_isAuthError` |
| `_handle_auth_error_and_retry` | `_handleAuthErrorAndRetry` |
| `_snapshot_child_pids` | `_snapshotChildPids` |
| `_mcp_loop_exception_handler` | `_mcpLoopExceptionHandler` |
| `_ensure_mcp_loop` | `_ensureMcpLoop` |
| `_run_on_mcp_loop` | `_runOnMcpLoop` |
| `_interrupted_call_result` | `_interruptedCallResult` |
| `_interpolate_env_vars` | `_interpolateEnvVars` |
| `_load_mcp_config` | `_loadMcpConfig` |
| `_connect_server` | `_connectServer` |
| `_make_tool_handler` | `_makeToolHandler` |
| `_make_list_resources_handler` | `_makeListResourcesHandler` |
| `_make_read_resource_handler` | `_makeReadResourceHandler` |
| `_make_list_prompts_handler` | `_makeListPromptsHandler` |
| `_make_get_prompt_handler` | `_makeGetPromptHandler` |
| `_make_check_fn` | `_makeCheckFn` |
| `_normalize_mcp_input_schema` | `_normalizeMcpInputSchema` |
| `sanitize_mcp_name_component` | `sanitizeMcpNameComponent` |
| `_convert_mcp_schema` | `_convertMcpSchema` |
| `_build_utility_schemas` | `_buildUtilitySchemas` |
| `_normalize_name_filter` | `_normalizeNameFilter` |
| `_parse_boolish` | `_parseBoolish` |
| `_select_utility_schemas` | `_selectUtilitySchemas` |
| `_existing_tool_names` | `_existingToolNames` |
| `_register_server_tools` | `_registerServerTools` |
| `_discover_and_register_server` | `_discoverAndRegisterServer` |
| `register_mcp_servers` | `registerMcpServers` |
| `discover_mcp_tools` | `discoverMcpTools` |
| `get_mcp_status` | `getMcpStatus` |
| `probe_mcp_server_tools` | `probeMcpServerTools` |
| `shutdown_mcp_servers` | `shutdownMcpServers` |
| `_kill_orphaned_mcp_children` | `_killOrphanedMcpChildren` |
| `_stop_mcp_loop` | `_stopMcpLoop` |

### `tools/web_tools.py`

**模块函数** (38 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_load_web_config` | `_loadWebConfig` |
| `_get_backend` | `_getBackend` |
| `_is_backend_available` | `_isBackendAvailable` |
| `_get_direct_firecrawl_config` | `_getDirectFirecrawlConfig` |
| `_get_firecrawl_gateway_url` | `_getFirecrawlGatewayUrl` |
| `_is_tool_gateway_ready` | `_isToolGatewayReady` |
| `_has_direct_firecrawl_config` | `_hasDirectFirecrawlConfig` |
| `_raise_web_backend_configuration_error` | `_raiseWebBackendConfigurationError` |
| `_firecrawl_backend_help_suffix` | `_firecrawlBackendHelpSuffix` |
| `_web_requires_env` | `_webRequiresEnv` |
| `_get_firecrawl_client` | `_getFirecrawlClient` |
| `_get_parallel_client` | `_getParallelClient` |
| `_get_async_parallel_client` | `_getAsyncParallelClient` |
| `_tavily_request` | `_tavilyRequest` |
| `_normalize_tavily_search_results` | `_normalizeTavilySearchResults` |
| `_normalize_tavily_documents` | `_normalizeTavilyDocuments` |
| `_to_plain_object` | `_toPlainObject` |
| `_normalize_result_list` | `_normalizeResultList` |
| `_extract_web_search_results` | `_extractWebSearchResults` |
| `_extract_scrape_payload` | `_extractScrapePayload` |
| `_is_nous_auxiliary_client` | `_isNousAuxiliaryClient` |
| `_resolve_web_extract_auxiliary` | `_resolveWebExtractAuxiliary` |
| `_get_default_summarizer_model` | `_getDefaultSummarizerModel` |
| `process_content_with_llm` | `processContentWithLlm` |
| `_call_summarizer_llm` | `_callSummarizerLlm` |
| `_process_large_content_chunked` | `_processLargeContentChunked` |
| `clean_base64_images` | `cleanBase64Images` |
| `_get_exa_client` | `_getExaClient` |
| `_exa_search` | `_exaSearch` |
| `_exa_extract` | `_exaExtract` |
| `_parallel_search` | `_parallelSearch` |
| `_parallel_extract` | `_parallelExtract` |
| `web_search_tool` | `webSearchTool` |
| `web_extract_tool` | `webExtractTool` |
| `web_crawl_tool` | `webCrawlTool` |
| `check_firecrawl_api_key` | `checkFirecrawlApiKey` |
| `check_web_api_key` | `checkWebApiKey` |
| `check_auxiliary_model` | `checkAuxiliaryModel` |

### `tools/send_message_tool.py`

**模块函数** (33 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_sanitize_error_text` | `_sanitizeErrorText` |
| `_error` | `_error` |
| `_telegram_retry_delay` | `_telegramRetryDelay` |
| `_send_telegram_message_with_retry` | `_sendTelegramMessageWithRetry` |
| `send_message_tool` | `sendMessageTool` |
| `_handle_list` | `_handleList` |
| `_handle_send` | `_handleSend` |
| `_parse_target_ref` | `_parseTargetRef` |
| `_describe_media_for_mirror` | `_describeMediaForMirror` |
| `_get_cron_auto_delivery_target` | `_getCronAutoDeliveryTarget` |
| `_maybe_skip_cron_duplicate_send` | `_maybeSkipCronDuplicateSend` |
| `_send_to_platform` | `_sendToPlatform` |
| `_send_telegram` | `_sendTelegram` |
| `_derive_forum_thread_name` | `_deriveForumThreadName` |
| `_remember_channel_is_forum` | `_rememberChannelIsForum` |
| `_probe_is_forum_cached` | `_probeIsForumCached` |
| `_send_discord` | `_sendDiscord` |
| `_send_slack` | `_sendSlack` |
| `_send_whatsapp` | `_sendWhatsapp` |
| `_send_signal` | `_sendSignal` |
| `_send_email` | `_sendEmail` |
| `_send_sms` | `_sendSms` |
| `_send_mattermost` | `_sendMattermost` |
| `_send_matrix` | `_sendMatrix` |
| `_send_matrix_via_adapter` | `_sendMatrixViaAdapter` |
| `_send_homeassistant` | `_sendHomeassistant` |
| `_send_dingtalk` | `_sendDingtalk` |
| `_send_wecom` | `_sendWecom` |
| `_send_weixin` | `_sendWeixin` |
| `_send_bluebubbles` | `_sendBluebubbles` |
| `_send_feishu` | `_sendFeishu` |
| `_check_send_message` | `_checkSendMessage` |
| `_send_qqbot` | `_sendQqbot` |

### `tools/approval.py`

**模块函数** (31 个):

| Python | 期望 Kotlin |
|--------|------------|
| `reset_current_session_key` | `resetCurrentSessionKey` |
| `get_current_session_key` | `getCurrentSessionKey` |
| `_legacy_pattern_key` | `_legacyPatternKey` |
| `_approval_key_aliases` | `_approvalKeyAliases` |
| `_normalize_command_for_detection` | `_normalizeCommandForDetection` |
| `detect_dangerous_command` | `detectDangerousCommand` |
| `register_gateway_notify` | `registerGatewayNotify` |
| `unregister_gateway_notify` | `unregisterGatewayNotify` |
| `resolve_gateway_approval` | `resolveGatewayApproval` |
| `has_blocking_approval` | `hasBlockingApproval` |
| `submit_pending` | `submitPending` |
| `approve_session` | `approveSession` |
| `enable_session_yolo` | `enableSessionYolo` |
| `disable_session_yolo` | `disableSessionYolo` |
| `clear_session` | `clearSession` |
| `is_session_yolo_enabled` | `isSessionYoloEnabled` |
| `is_current_session_yolo_enabled` | `isCurrentSessionYoloEnabled` |
| `is_approved` | `isApproved` |
| `approve_permanent` | `approvePermanent` |
| `load_permanent` | `loadPermanent` |
| `load_permanent_allowlist` | `loadPermanentAllowlist` |
| `save_permanent_allowlist` | `savePermanentAllowlist` |
| `prompt_dangerous_approval` | `promptDangerousApproval` |
| `_normalize_approval_mode` | `_normalizeApprovalMode` |
| `_get_approval_config` | `_getApprovalConfig` |
| `_get_approval_mode` | `_getApprovalMode` |
| `_get_approval_timeout` | `_getApprovalTimeout` |
| `_smart_approve` | `_smartApprove` |
| `check_dangerous_command` | `checkDangerousCommand` |
| `_format_tirith_description` | `_formatTirithDescription` |
| `check_all_command_guards` | `checkAllCommandGuards` |

### `tools/terminal_tool.py`

**模块函数** (31 个):

| Python | 期望 Kotlin |
|--------|------------|
| `set_sudo_password_callback` | `setSudoPasswordCallback` |
| `set_approval_callback` | `setApprovalCallback` |
| `_check_all_guards` | `_checkAllGuards` |
| `_validate_workdir` | `_validateWorkdir` |
| `_handle_sudo_failure` | `_handleSudoFailure` |
| `_prompt_for_sudo_password` | `_promptForSudoPassword` |
| `_safe_command_preview` | `_safeCommandPreview` |
| `_looks_like_env_assignment` | `_looksLikeEnvAssignment` |
| `_read_shell_token` | `_readShellToken` |
| `_rewrite_real_sudo_invocations` | `_rewriteRealSudoInvocations` |
| `_transform_sudo_command` | `_transformSudoCommand` |
| `register_task_env_overrides` | `registerTaskEnvOverrides` |
| `clear_task_env_overrides` | `clearTaskEnvOverrides` |
| `_parse_env_var` | `_parseEnvVar` |
| `_get_env_config` | `_getEnvConfig` |
| `_get_modal_backend_state` | `_getModalBackendState` |
| `_create_environment` | `_createEnvironment` |
| `_cleanup_inactive_envs` | `_cleanupInactiveEnvs` |
| `_cleanup_thread_worker` | `_cleanupThreadWorker` |
| `_start_cleanup_thread` | `_startCleanupThread` |
| `_stop_cleanup_thread` | `_stopCleanupThread` |
| `get_active_env` | `getActiveEnv` |
| `is_persistent_env` | `isPersistentEnv` |
| `cleanup_all_environments` | `cleanupAllEnvironments` |
| `cleanup_vm` | `cleanupVm` |
| `_atexit_cleanup` | `_atexitCleanup` |
| `_interpret_exit_code` | `_interpretExitCode` |
| `_command_requires_pipe_stdin` | `_commandRequiresPipeStdin` |
| `terminal_tool` | `terminalTool` |
| `check_terminal_requirements` | `checkTerminalRequirements` |
| `_handle_terminal` | `_handleTerminal` |

### `tools/tts_tool.py`

**模块函数** (27 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_import_elevenlabs` | `_importElevenlabs` |
| `_import_openai_client` | `_importOpenaiClient` |
| `_import_mistral_client` | `_importMistralClient` |
| `_import_sounddevice` | `_importSounddevice` |
| `_get_default_output_dir` | `_getDefaultOutputDir` |
| `_load_tts_config` | `_loadTtsConfig` |
| `_get_provider` | `_getProvider` |
| `_has_ffmpeg` | `_hasFfmpeg` |
| `_convert_to_opus` | `_convertToOpus` |
| `_generate_edge_tts` | `_generateEdgeTts` |
| `_generate_elevenlabs` | `_generateElevenlabs` |
| `_generate_openai_tts` | `_generateOpenaiTts` |
| `_generate_xai_tts` | `_generateXaiTts` |
| `_generate_minimax_tts` | `_generateMinimaxTts` |
| `_generate_mistral_tts` | `_generateMistralTts` |
| `_wrap_pcm_as_wav` | `_wrapPcmAsWav` |
| `_generate_gemini_tts` | `_generateGeminiTts` |
| `_check_neutts_available` | `_checkNeuttsAvailable` |
| `_default_neutts_ref_audio` | `_defaultNeuttsRefAudio` |
| `_default_neutts_ref_text` | `_defaultNeuttsRefText` |
| `_generate_neutts` | `_generateNeutts` |
| `text_to_speech_tool` | `textToSpeechTool` |
| `check_tts_requirements` | `checkTtsRequirements` |
| `_resolve_openai_audio_client_config` | `_resolveOpenaiAudioClientConfig` |
| `_has_openai_audio_backend` | `_hasOpenaiAudioBackend` |
| `_strip_markdown_for_tts` | `_stripMarkdownForTts` |
| `stream_tts_to_speaker` | `streamTtsToSpeaker` |

### `tools/skills_tool.py`

**模块函数** (25 个):

| Python | 期望 Kotlin |
|--------|------------|
| `load_env` | `loadEnv` |
| `set_secret_capture_callback` | `setSecretCaptureCallback` |
| `skill_matches_platform` | `skillMatchesPlatform` |
| `_normalize_prerequisite_values` | `_normalizePrerequisiteValues` |
| `_collect_prerequisite_values` | `_collectPrerequisiteValues` |
| `_normalize_setup_metadata` | `_normalizeSetupMetadata` |
| `_get_required_environment_variables` | `_getRequiredEnvironmentVariables` |
| `_capture_required_environment_variables` | `_captureRequiredEnvironmentVariables` |
| `_is_gateway_surface` | `_isGatewaySurface` |
| `_get_terminal_backend_name` | `_getTerminalBackendName` |
| `_is_env_var_persisted` | `_isEnvVarPersisted` |
| `_remaining_required_environment_names` | `_remainingRequiredEnvironmentNames` |
| `_gateway_setup_hint` | `_gatewaySetupHint` |
| `_build_setup_note` | `_buildSetupNote` |
| `check_skills_requirements` | `checkSkillsRequirements` |
| `_parse_frontmatter` | `_parseFrontmatter` |
| `_get_category_from_path` | `_getCategoryFromPath` |
| `_parse_tags` | `_parseTags` |
| `_get_disabled_skill_names` | `_getDisabledSkillNames` |
| `_is_skill_disabled` | `_isSkillDisabled` |
| `_find_all_skills` | `_findAllSkills` |
| `_load_category_description` | `_loadCategoryDescription` |
| `skills_list` | `skillsList` |
| `_serve_plugin_skill` | `_servePluginSkill` |
| `skill_view` | `skillView` |

### `tools/browser_camofox.py`

**模块函数** (22 个):

| Python | 期望 Kotlin |
|--------|------------|
| `check_camofox_available` | `checkCamofoxAvailable` |
| `get_vnc_url` | `getVncUrl` |
| `_managed_persistence_enabled` | `_managedPersistenceEnabled` |
| `_get_session` | `_getSession` |
| `_ensure_tab` | `_ensureTab` |
| `_drop_session` | `_dropSession` |
| `camofox_soft_cleanup` | `camofoxSoftCleanup` |
| `_post` | `_post` |
| `_get` | `_get` |
| `_get_raw` | `_getRaw` |
| `_delete` | `_delete` |
| `camofox_navigate` | `camofoxNavigate` |
| `camofox_snapshot` | `camofoxSnapshot` |
| `camofox_click` | `camofoxClick` |
| `camofox_type` | `camofoxType` |
| `camofox_scroll` | `camofoxScroll` |
| `camofox_back` | `camofoxBack` |
| `camofox_press` | `camofoxPress` |
| `camofox_close` | `camofoxClose` |
| `camofox_get_images` | `camofoxGetImages` |
| `camofox_vision` | `camofoxVision` |
| `camofox_console` | `camofoxConsole` |

### `tools/transcription_tools.py`

**模块函数** (21 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_safe_find_spec` | `_safeFindSpec` |
| `_load_stt_config` | `_loadSttConfig` |
| `is_stt_enabled` | `isSttEnabled` |
| `_has_openai_audio_backend` | `_hasOpenaiAudioBackend` |
| `_find_binary` | `_findBinary` |
| `_find_ffmpeg_binary` | `_findFfmpegBinary` |
| `_find_whisper_binary` | `_findWhisperBinary` |
| `_get_local_command_template` | `_getLocalCommandTemplate` |
| `_has_local_command` | `_hasLocalCommand` |
| `_normalize_local_command_model` | `_normalizeLocalCommandModel` |
| `_get_provider` | `_getProvider` |
| `_validate_audio_file` | `_validateAudioFile` |
| `_transcribe_local` | `_transcribeLocal` |
| `_prepare_local_audio` | `_prepareLocalAudio` |
| `_transcribe_local_command` | `_transcribeLocalCommand` |
| `_transcribe_groq` | `_transcribeGroq` |
| `_transcribe_openai` | `_transcribeOpenai` |
| `_transcribe_mistral` | `_transcribeMistral` |
| `transcribe_audio` | `transcribeAudio` |
| `_resolve_openai_audio_client_config` | `_resolveOpenaiAudioClientConfig` |
| `_extract_transcript_text` | `_extractTranscriptText` |

### `tools/rl_training_tool.py`

**模块函数** (20 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_ensure_logs_dir` | `_ensureLogsDir` |
| `_scan_environments` | `_scanEnvironments` |
| `_get_env_config_fields` | `_getEnvConfigFields` |
| `_initialize_environments` | `_initializeEnvironments` |
| `_spawn_training_run` | `_spawnTrainingRun` |
| `_monitor_training_run` | `_monitorTrainingRun` |
| `_stop_training_run` | `_stopTrainingRun` |
| `rl_list_environments` | `rlListEnvironments` |
| `rl_select_environment` | `rlSelectEnvironment` |
| `rl_get_current_config` | `rlGetCurrentConfig` |
| `rl_edit_config` | `rlEditConfig` |
| `rl_start_training` | `rlStartTraining` |
| `rl_check_status` | `rlCheckStatus` |
| `rl_stop_training` | `rlStopTraining` |
| `rl_get_results` | `rlGetResults` |
| `rl_list_runs` | `rlListRuns` |
| `rl_test_inference` | `rlTestInference` |
| `check_rl_python_version` | `checkRlPythonVersion` |
| `check_rl_api_keys` | `checkRlApiKeys` |
| `get_missing_keys` | `getMissingKeys` |

### `tools/file_tools.py`

**模块函数** (20 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_max_read_chars` | `_getMaxReadChars` |
| `_is_blocked_device` | `_isBlockedDevice` |
| `_check_sensitive_path` | `_checkSensitivePath` |
| `_is_expected_write_exception` | `_isExpectedWriteException` |
| `_cap_read_tracker_data` | `_capReadTrackerData` |
| `_get_file_ops` | `_getFileOps` |
| `clear_file_ops_cache` | `clearFileOpsCache` |
| `read_file_tool` | `readFileTool` |
| `reset_file_dedup` | `resetFileDedup` |
| `notify_other_tool_call` | `notifyOtherToolCall` |
| `_update_read_timestamp` | `_updateReadTimestamp` |
| `_check_file_staleness` | `_checkFileStaleness` |
| `write_file_tool` | `writeFileTool` |
| `patch_tool` | `patchTool` |
| `search_tool` | `searchTool` |
| `_check_file_reqs` | `_checkFileReqs` |
| `_handle_read_file` | `_handleReadFile` |
| `_handle_write_file` | `_handleWriteFile` |
| `_handle_patch` | `_handlePatch` |
| `_handle_search_files` | `_handleSearchFiles` |

### `tools/tirith_security.py`

**模块函数** (20 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_env_bool` | `_envBool` |
| `_env_int` | `_envInt` |
| `_load_security_config` | `_loadSecurityConfig` |
| `_get_hermes_home` | `_getHermesHome` |
| `_failure_marker_path` | `_failureMarkerPath` |
| `_read_failure_reason` | `_readFailureReason` |
| `_is_install_failed_on_disk` | `_isInstallFailedOnDisk` |
| `_mark_install_failed` | `_markInstallFailed` |
| `_clear_install_failed` | `_clearInstallFailed` |
| `_hermes_bin_dir` | `_hermesBinDir` |
| `_detect_target` | `_detectTarget` |
| `_download_file` | `_downloadFile` |
| `_verify_cosign` | `_verifyCosign` |
| `_verify_checksum` | `_verifyChecksum` |
| `_install_tirith` | `_installTirith` |
| `_is_explicit_path` | `_isExplicitPath` |
| `_resolve_tirith_path` | `_resolveTirithPath` |
| `_background_install` | `_backgroundInstall` |
| `ensure_installed` | `ensureInstalled` |
| `check_command_security` | `checkCommandSecurity` |

### `tools/skill_manager_tool.py`

**模块函数** (17 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_security_scan_skill` | `_securityScanSkill` |
| `_is_local_skill` | `_isLocalSkill` |
| `_validate_name` | `_validateName` |
| `_validate_category` | `_validateCategory` |
| `_validate_frontmatter` | `_validateFrontmatter` |
| `_validate_content_size` | `_validateContentSize` |
| `_resolve_skill_dir` | `_resolveSkillDir` |
| `_find_skill` | `_findSkill` |
| `_validate_file_path` | `_validateFilePath` |
| `_resolve_skill_target` | `_resolveSkillTarget` |
| `_atomic_write_text` | `_atomicWriteText` |
| `_create_skill` | `_createSkill` |
| `_edit_skill` | `_editSkill` |
| `_patch_skill` | `_patchSkill` |
| `_delete_skill` | `_deleteSkill` |
| `_remove_file` | `_removeFile` |
| `skill_manage` | `skillManage` |

### `tools/skills_hub.py`

**模块函数** (17 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_read_index_cache` | `_readIndexCache` |
| `_write_index_cache` | `_writeIndexCache` |
| `_skill_meta_to_dict` | `_skillMetaToDict` |
| `append_audit_log` | `appendAuditLog` |
| `ensure_hub_dirs` | `ensureHubDirs` |
| `quarantine_bundle` | `quarantineBundle` |
| `install_from_quarantine` | `installFromQuarantine` |
| `uninstall_skill` | `uninstallSkill` |
| `bundle_content_hash` | `bundleContentHash` |
| `_source_matches` | `_sourceMatches` |
| `check_for_skill_updates` | `checkForSkillUpdates` |
| `_load_hermes_index` | `_loadHermesIndex` |
| `_load_stale_index_cache` | `_loadStaleIndexCache` |
| `create_source_router` | `createSourceRouter` |
| `_search_one_source` | `_searchOneSource` |
| `parallel_search_sources` | `parallelSearchSources` |
| `unified_search` | `unifiedSearch` |

### `tools/code_execution_tool.py`

**模块函数** (16 个):

| Python | 期望 Kotlin |
|--------|------------|
| `check_sandbox_requirements` | `checkSandboxRequirements` |
| `generate_hermes_tools_module` | `generateHermesToolsModule` |
| `_rpc_server_loop` | `_rpcServerLoop` |
| `_get_or_create_env` | `_getOrCreateEnv` |
| `_ship_file_to_remote` | `_shipFileToRemote` |
| `_env_temp_dir` | `_envTempDir` |
| `_rpc_poll_loop` | `_rpcPollLoop` |
| `_execute_remote` | `_executeRemote` |
| `execute_code` | `executeCode` |
| `_kill_process_group` | `_killProcessGroup` |
| `_load_config` | `_loadConfig` |
| `_get_execution_mode` | `_getExecutionMode` |
| `_is_usable_python` | `_isUsablePython` |
| `_resolve_child_python` | `_resolveChildPython` |
| `_resolve_child_cwd` | `_resolveChildCwd` |
| `build_execute_code_schema` | `buildExecuteCodeSchema` |

### `tools/homeassistant_tool.py`

**模块函数** (15 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_config` | `_getConfig` |
| `_get_headers` | `_getHeaders` |
| `_filter_and_summarize` | `_filterAndSummarize` |
| `_async_list_entities` | `_asyncListEntities` |
| `_async_get_state` | `_asyncGetState` |
| `_build_service_payload` | `_buildServicePayload` |
| `_parse_service_response` | `_parseServiceResponse` |
| `_async_call_service` | `_asyncCallService` |
| `_run_async` | `_runAsync` |
| `_handle_list_entities` | `_handleListEntities` |
| `_handle_get_state` | `_handleGetState` |
| `_handle_call_service` | `_handleCallService` |
| `_async_list_services` | `_asyncListServices` |
| `_handle_list_services` | `_handleListServices` |
| `_check_ha_available` | `_checkHaAvailable` |

### `tools/mcp_oauth.py`

**模块函数** (15 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_safe_filename` | `_safeFilename` |
| `_find_free_port` | `_findFreePort` |
| `_is_interactive` | `_isInteractive` |
| `_can_open_browser` | `_canOpenBrowser` |
| `_read_json` | `_readJson` |
| `_write_json` | `_writeJson` |
| `_make_callback_handler` | `_makeCallbackHandler` |
| `_redirect_handler` | `_redirectHandler` |
| `_wait_for_callback` | `_waitForCallback` |
| `remove_oauth_tokens` | `removeOauthTokens` |
| `_configure_callback_port` | `_configureCallbackPort` |
| `_build_client_metadata` | `_buildClientMetadata` |
| `_maybe_preregister_client` | `_maybePreregisterClient` |
| `_parse_base_url` | `_parseBaseUrl` |
| `build_oauth_auth` | `buildOauthAuth` |

### `tools/voice_mode.py`

**模块函数** (13 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_import_audio` | `_importAudio` |
| `_audio_available` | `_audioAvailable` |
| `_voice_capture_install_hint` | `_voiceCaptureInstallHint` |
| `_termux_api_app_installed` | `_termuxApiAppInstalled` |
| `_termux_voice_capture_available` | `_termuxVoiceCaptureAvailable` |
| `play_beep` | `playBeep` |
| `create_audio_recorder` | `createAudioRecorder` |
| `is_whisper_hallucination` | `isWhisperHallucination` |
| `transcribe_recording` | `transcribeRecording` |
| `stop_playback` | `stopPlayback` |
| `play_audio_file` | `playAudioFile` |
| `check_voice_requirements` | `checkVoiceRequirements` |
| `cleanup_temp_recordings` | `cleanupTempRecordings` |

### `tools/image_generation_tool.py`

**模块函数** (12 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_managed_fal_gateway` | `_resolveManagedFalGateway` |
| `_normalize_fal_queue_url_format` | `_normalizeFalQueueUrlFormat` |
| `_get_managed_fal_client` | `_getManagedFalClient` |
| `_submit_fal_request` | `_submitFalRequest` |
| `_extract_http_status` | `_extractHttpStatus` |
| `_resolve_fal_model` | `_resolveFalModel` |
| `_build_fal_payload` | `_buildFalPayload` |
| `_upscale_image` | `_upscaleImage` |
| `image_generate_tool` | `imageGenerateTool` |
| `check_fal_api_key` | `checkFalApiKey` |
| `check_image_generation_requirements` | `checkImageGenerationRequirements` |
| `_handle_image_generate` | `_handleImageGenerate` |

### `tools/delegate_tool.py`

**模块函数** (12 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_max_concurrent_children` | `_getMaxConcurrentChildren` |
| `check_delegate_requirements` | `checkDelegateRequirements` |
| `_build_child_system_prompt` | `_buildChildSystemPrompt` |
| `_resolve_workspace_hint` | `_resolveWorkspaceHint` |
| `_strip_blocked_tools` | `_stripBlockedTools` |
| `_build_child_progress_callback` | `_buildChildProgressCallback` |
| `_build_child_agent` | `_buildChildAgent` |
| `_run_single_child` | `_runSingleChild` |
| `delegate_task` | `delegateTask` |
| `_resolve_child_credential_pool` | `_resolveChildCredentialPool` |
| `_resolve_delegation_credentials` | `_resolveDelegationCredentials` |
| `_load_config` | `_loadConfig` |

### `tools/credential_files.py`

**模块函数** (12 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_registered` | `_getRegistered` |
| `_resolve_hermes_home` | `_resolveHermesHome` |
| `register_credential_file` | `registerCredentialFile` |
| `register_credential_files` | `registerCredentialFiles` |
| `_load_config_files` | `_loadConfigFiles` |
| `get_credential_file_mounts` | `getCredentialFileMounts` |
| `get_skills_directory_mount` | `getSkillsDirectoryMount` |
| `_safe_skills_path` | `_safeSkillsPath` |
| `iter_skills_files` | `iterSkillsFiles` |
| `get_cache_directory_mounts` | `getCacheDirectoryMounts` |
| `iter_cache_files` | `iterCacheFiles` |
| `clear_credential_files` | `clearCredentialFiles` |

### `tools/vision_tools.py`

**模块函数** (11 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_download_timeout` | `_resolveDownloadTimeout` |
| `_validate_image_url` | `_validateImageUrl` |
| `_detect_image_mime_type` | `_detectImageMimeType` |
| `_download_image` | `_downloadImage` |
| `_determine_mime_type` | `_determineMimeType` |
| `_image_to_base64_data_url` | `_imageToBase64DataUrl` |
| `_is_image_size_error` | `_isImageSizeError` |
| `_resize_image_for_vision` | `_resizeImageForVision` |
| `vision_analyze_tool` | `visionAnalyzeTool` |
| `check_vision_requirements` | `checkVisionRequirements` |
| `_handle_vision_analyze` | `_handleVisionAnalyze` |

### `tools/skills_guard.py`

**模块函数** (10 个):

| Python | 期望 Kotlin |
|--------|------------|
| `scan_file` | `scanFile` |
| `scan_skill` | `scanSkill` |
| `should_allow_install` | `shouldAllowInstall` |
| `format_scan_report` | `formatScanReport` |
| `content_hash` | `contentHash` |
| `_check_structure` | `_checkStructure` |
| `_unicode_char_name` | `_unicodeCharName` |
| `_resolve_trust_level` | `_resolveTrustLevel` |
| `_determine_verdict` | `_determineVerdict` |
| `_build_summary` | `_buildSummary` |

### `tools/cronjob_tools.py`

**模块函数** (10 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_scan_cron_prompt` | `_scanCronPrompt` |
| `_origin_from_env` | `_originFromEnv` |
| `_repeat_display` | `_repeatDisplay` |
| `_canonical_skills` | `_canonicalSkills` |
| `_resolve_model_override` | `_resolveModelOverride` |
| `_normalize_optional_job_value` | `_normalizeOptionalJobValue` |
| `_validate_cron_script_path` | `_validateCronScriptPath` |
| `_format_job` | `_formatJob` |
| `cronjob` | `cronjob` |
| `check_cronjob_requirements` | `checkCronjobRequirements` |

### `tools/environments/base.py`

**模块函数** (10 个):

| Python | 期望 Kotlin |
|--------|------------|
| `set_activity_callback` | `setActivityCallback` |
| `_get_activity_callback` | `_getActivityCallback` |
| `touch_activity_if_due` | `touchActivityIfDue` |
| `get_sandbox_dir` | `getSandboxDir` |
| `_pipe_stdin` | `_pipeStdin` |
| `_popen_bash` | `_popenBash` |
| `_load_json_store` | `_loadJsonStore` |
| `_save_json_store` | `_saveJsonStore` |
| `_file_mtime_key` | `_fileMtimeKey` |
| `_cwd_marker` | `_cwdMarker` |

### `tools/checkpoint_manager.py`

**模块函数** (8 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_validate_commit_hash` | `_validateCommitHash` |
| `_validate_file_path` | `_validateFilePath` |
| `_normalize_path` | `_normalizePath` |
| `_shadow_repo_path` | `_shadowRepoPath` |
| `_git_env` | `_gitEnv` |
| `_init_shadow_repo` | `_initShadowRepo` |
| `_dir_file_count` | `_dirFileCount` |
| `format_checkpoint_list` | `formatCheckpointList` |

### `tools/session_search_tool.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_format_timestamp` | `_formatTimestamp` |
| `_format_conversation` | `_formatConversation` |
| `_truncate_around_matches` | `_truncateAroundMatches` |
| `_summarize_session` | `_summarizeSession` |
| `_list_recent_sessions` | `_listRecentSessions` |
| `session_search` | `sessionSearch` |
| `check_session_search_requirements` | `checkSessionSearchRequirements` |

### `tools/environments/modal.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_load_snapshots` | `_loadSnapshots` |
| `_save_snapshots` | `_saveSnapshots` |
| `_direct_snapshot_key` | `_directSnapshotKey` |
| `_get_snapshot_restore_candidate` | `_getSnapshotRestoreCandidate` |
| `_store_direct_snapshot` | `_storeDirectSnapshot` |
| `_delete_direct_snapshot` | `_deleteDirectSnapshot` |
| `_resolve_modal_image` | `_resolveModalImage` |

### `tools/environments/singularity.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_find_singularity_executable` | `_findSingularityExecutable` |
| `_ensure_singularity_available` | `_ensureSingularityAvailable` |
| `_load_snapshots` | `_loadSnapshots` |
| `_save_snapshots` | `_saveSnapshots` |
| `_get_scratch_dir` | `_getScratchDir` |
| `_get_apptainer_cache_dir` | `_getApptainerCacheDir` |
| `_get_or_build_sif` | `_getOrBuildSif` |

### `tools/mixture_of_agents_tool.py`

**模块函数** (6 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_construct_aggregator_prompt` | `_constructAggregatorPrompt` |
| `_run_reference_model_safe` | `_runReferenceModelSafe` |
| `_run_aggregator_model` | `_runAggregatorModel` |
| `mixture_of_agents_tool` | `mixtureOfAgentsTool` |
| `check_moa_requirements` | `checkMoaRequirements` |
| `get_moa_configuration` | `getMoaConfiguration` |

### `tools/website_policy.py`

**模块函数** (6 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_default_config_path` | `_getDefaultConfigPath` |
| `_iter_blocklist_file_rules` | `_iterBlocklistFileRules` |
| `_load_policy_config` | `_loadPolicyConfig` |
| `load_website_blocklist` | `loadWebsiteBlocklist` |
| `_match_host_against_rule` | `_matchHostAgainstRule` |
| `_extract_host_from_urlish` | `_extractHostFromUrlish` |

### `tools/environments/file_sync.py`

**模块函数** (5 个):

| Python | 期望 Kotlin |
|--------|------------|
| `iter_sync_files` | `iterSyncFiles` |
| `quoted_rm_command` | `quotedRmCommand` |
| `quoted_mkdir_command` | `quotedMkdirCommand` |
| `unique_parent_dirs` | `uniqueParentDirs` |
| `_sha256_file` | `_sha256File` |

### `tools/environments/docker.py`

**模块函数** (5 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_normalize_forward_env_names` | `_normalizeForwardEnvNames` |
| `_normalize_env_dict` | `_normalizeEnvDict` |
| `_load_hermes_env_vars` | `_loadHermesEnvVars` |
| `find_docker` | `findDocker` |
| `_ensure_docker_available` | `_ensureDockerAvailable` |

### `tools/environments/local.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_provider_env_blocklist` | `_buildProviderEnvBlocklist` |
| `_sanitize_subprocess_env` | `_sanitizeSubprocessEnv` |
| `_find_bash` | `_findBash` |
| `_make_run_env` | `_makeRunEnv` |

### `tools/managed_tool_gateway.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `auth_json_path` | `authJsonPath` |
| `_read_nous_provider_state` | `_readNousProviderState` |
| `_parse_timestamp` | `_parseTimestamp` |
| `_access_token_is_expiring` | `_accessTokenIsExpiring` |

### `tools/tool_result_storage.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_storage_dir` | `_resolveStorageDir` |
| `_heredoc_marker` | `_heredocMarker` |
| `_write_to_sandbox` | `_writeToSandbox` |
| `maybe_persist_tool_result` | `maybePersistToolResult` |

### `tools/browser_providers/browser_use.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_or_create_pending_create_key` | `_getOrCreatePendingCreateKey` |
| `_clear_pending_create_key` | `_clearPendingCreateKey` |
| `_should_preserve_pending_create_key` | `_shouldPreservePendingCreateKey` |

### `tools/memory_tool.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `get_memory_dir` | `getMemoryDir` |
| `memory_tool` | `memoryTool` |
| `check_memory_requirements` | `checkMemoryRequirements` |

### `tools/skills_sync.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_bundled_dir` | `_getBundledDir` |
| `_compute_relative_dest` | `_computeRelativeDest` |
| `reset_bundled_skill` | `resetBundledSkill` |

### `tools/fuzzy_match.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_orig_to_norm_map` | `_buildOrigToNormMap` |
| `_map_positions_norm_to_orig` | `_mapPositionsNormToOrig` |
| `_map_normalized_positions` | `_mapNormalizedPositions` |

### `tools/registry.py`

**模块函数** (3 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_is_registry_register_call` | `_isRegistryRegisterCall` |
| `_module_registers_tools` | `_moduleRegistersTools` |
| `discover_builtin_tools` | `discoverBuiltinTools` |

### `tools/process_registry.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `format_uptime_short` | `formatUptimeShort` |
| `_handle_process` | `_handleProcess` |

### `tools/tool_backend_helpers.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `normalize_modal_mode` | `normalizeModalMode` |
| `prefers_gateway` | `prefersGateway` |

### `tools/browser_camofox_state.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `get_camofox_state_dir` | `getCamofoxStateDir` |
| `get_camofox_identity` | `getCamofoxIdentity` |

### `tools/environments/modal_utils.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `wrap_modal_stdin_heredoc` | `wrapModalStdinHeredoc` |
| `wrap_modal_sudo_pipe` | `wrapModalSudoPipe` |

### `tools/openrouter_client.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `get_async_client` | `getAsyncClient` |
| `check_api_key` | `checkApiKey` |

### `tools/patch_parser.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_count_occurrences` | `_countOccurrences` |
| `_validate_operations` | `_validateOperations` |

### `tools/file_operations.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_safe_write_root` | `_getSafeWriteRoot` |

### `tools/url_safety.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_allows_private_ip_resolution` | `_allowsPrivateIpResolution` |

### `tools/clarify_tool.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `check_clarify_requirements` | `checkClarifyRequirements` |

### `tools/mcp_oauth_manager.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_make_hermes_provider_class` | `_makeHermesProviderClass` |

### `tools/todo_tool.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `check_todo_requirements` | `checkTodoRequirements` |

### `tools/binary_extensions.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `has_binary_extension` | `hasBinaryExtension` |

### `tools/env_passthrough.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_allowed` | `_getAllowed` |

### `tools/environments/managed_modal.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_request_timeout_env` | `_requestTimeoutEnv` |

### `tools/environments/ssh.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_ensure_ssh_available` | `_ensureSshAvailable` |

### `tools/neutts_synth.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `main` | `main` |

## 🟠 插件 (4 文件, 0 类方法, 11 模块函数)

### `plugins/memory/holographic/holographic.py`

**模块函数** (11 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_require_numpy` | `_requireNumpy` |
| `encode_atom` | `encodeAtom` |
| `bind` | `bind` |
| `unbind` | `unbind` |
| `bundle` | `bundle` |
| `similarity` | `similarity` |
| `encode_text` | `encodeText` |
| `encode_fact` | `encodeFact` |
| `phases_to_bytes` | `phasesToBytes` |
| `bytes_to_phases` | `bytesToPhases` |
| `snr_estimate` | `snrEstimate` |

## 🟠 环境 (4 文件, 0 类方法, 7 模块函数)

### `environments/agentic_opd_env.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_hint_judge_messages` | `_buildHintJudgeMessages` |
| `_parse_hint_result` | `_parseHintResult` |
| `_select_best_hint` | `_selectBestHint` |
| `_append_hint_to_messages` | `_appendHintToMessages` |

### `environments/agent_loop.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `resize_tool_pool` | `resizeToolPool` |
| `_extract_reasoning_from_message` | `_extractReasoningFromMessage` |

### `environments/tool_context.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_run_tool_in_thread` | `_runToolInThread` |

## 🟠 定时 (1 文件, 0 类方法, 1 模块函数)

### `cron/scheduler.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_send_media_via_adapter` | `_sendMediaViaAdapter` |

## 🔵 平台 (4 文件, 0 类方法, 46 模块函数)

### `gateway/platforms/feishu_comment.py`

**模块函数** (32 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_build_request` | `_buildRequest` |
| `_exec_request` | `_execRequest` |
| `parse_drive_comment_event` | `parseDriveCommentEvent` |
| `add_comment_reaction` | `addCommentReaction` |
| `delete_comment_reaction` | `deleteCommentReaction` |
| `query_document_meta` | `queryDocumentMeta` |
| `batch_query_comment` | `batchQueryComment` |
| `list_whole_comments` | `listWholeComments` |
| `list_comment_replies` | `listCommentReplies` |
| `_sanitize_comment_text` | `_sanitizeCommentText` |
| `reply_to_comment` | `replyToComment` |
| `add_whole_comment` | `addWholeComment` |
| `_chunk_text` | `_chunkText` |
| `deliver_comment_reply` | `deliverCommentReply` |
| `_extract_reply_text` | `_extractReplyText` |
| `_get_reply_user_id` | `_getReplyUserId` |
| `_extract_semantic_text` | `_extractSemanticText` |
| `_extract_docs_links` | `_extractDocsLinks` |
| `_reverse_lookup_wiki_token` | `_reverseLookupWikiToken` |
| `_resolve_wiki_nodes` | `_resolveWikiNodes` |
| `_format_referenced_docs` | `_formatReferencedDocs` |
| `_truncate` | `_truncate` |
| `_select_local_timeline` | `_selectLocalTimeline` |
| `_select_whole_timeline` | `_selectWholeTimeline` |
| `build_local_comment_prompt` | `buildLocalCommentPrompt` |
| `build_whole_comment_prompt` | `buildWholeCommentPrompt` |
| `_resolve_model_and_runtime` | `_resolveModelAndRuntime` |
| `_session_key` | `_sessionKey` |
| `_load_session_history` | `_loadSessionHistory` |
| `_save_session_history` | `_saveSessionHistory` |
| `_run_comment_agent` | `_runCommentAgent` |
| `handle_drive_comment_event` | `handleDriveCommentEvent` |

### `gateway/platforms/telegram_network.py`

**模块函数** (8 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_proxy_url` | `_resolveProxyUrl` |
| `_normalize_fallback_ips` | `_normalizeFallbackIps` |
| `parse_fallback_ip_env` | `parseFallbackIpEnv` |
| `_resolve_system_dns` | `_resolveSystemDns` |
| `_query_doh_provider` | `_queryDohProvider` |
| `discover_fallback_ips` | `discoverFallbackIps` |
| `_rewrite_request_for_ip` | `_rewriteRequestForIp` |
| `_is_retryable_connect_error` | `_isRetryableConnectError` |

### `gateway/platforms/feishu_comment_rules.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_parse_frozenset` | `_parseFrozenset` |
| `_print_status` | `_printStatus` |
| `_do_check` | `_doCheck` |
| `_main` | `_main` |

### `gateway/platforms/qqbot/adapter.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `check_qq_requirements` | `checkQqRequirements` |
| `_coerce_list` | `_coerceList` |

## 🔵 平台基类 (1 文件, 0 类方法, 7 模块函数)

### `gateway/platforms/base.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_custom_unit_to_cp` | `_customUnitToCp` |
| `_detect_macos_system_proxy` | `_detectMacosSystemProxy` |
| `proxy_kwargs_for_bot` | `proxyKwargsForBot` |
| `proxy_kwargs_for_aiohttp` | `proxyKwargsForAiohttp` |
| `_ssrf_redirect_guard` | `_ssrfRedirectGuard` |
| `merge_pending_message_event` | `mergePendingMessageEvent` |
| `resolve_channel_prompt` | `resolveChannelPrompt` |

## 🔵 ACP (2 文件, 0 类方法, 2 模块函数)

### `acp_adapter/auth.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `detect_provider` | `detectProvider` |
| `has_provider` | `hasProvider` |

## ⚪ 其他 (10 文件, 0 类方法, 43 模块函数)

### `hermes_constants.py`

**模块函数** (11 个):

| Python | 期望 Kotlin |
|--------|------------|
| `get_default_hermes_root` | `getDefaultHermesRoot` |
| `get_optional_skills_dir` | `getOptionalSkillsDir` |
| `display_hermes_home` | `displayHermesHome` |
| `get_subprocess_home` | `getSubprocessHome` |
| `parse_reasoning_effort` | `parseReasoningEffort` |
| `is_termux` | `isTermux` |
| `is_wsl` | `isWsl` |
| `is_container` | `isContainer` |
| `get_skills_dir` | `getSkillsDir` |
| `get_env_path` | `getEnvPath` |
| `apply_ipv4_preference` | `applyIpv4Preference` |

### `model_tools.py`

**模块函数** (9 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_tool_loop` | `_getToolLoop` |
| `_get_worker_loop` | `_getWorkerLoop` |
| `_run_async` | `_runAsync` |
| `coerce_tool_args` | `coerceToolArgs` |
| `_coerce_value` | `_coerceValue` |
| `_coerce_number` | `_coerceNumber` |
| `_coerce_boolean` | `_coerceBoolean` |
| `get_toolset_for_tool` | `getToolsetForTool` |
| `check_tool_availability` | `checkToolAvailability` |

### `hermes_logging.py`

**模块函数** (7 个):

| Python | 期望 Kotlin |
|--------|------------|
| `set_session_context` | `setSessionContext` |
| `clear_session_context` | `clearSessionContext` |
| `_install_session_record_factory` | `_installSessionRecordFactory` |
| `setup_logging` | `setupLogging` |
| `setup_verbose_logging` | `setupVerboseLogging` |
| `_add_rotating_handler` | `_addRotatingHandler` |
| `_read_logging_config` | `_readLoggingConfig` |

### `batch_runner.py`

**模块函数** (5 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_normalize_tool_stats` | `_normalizeToolStats` |
| `_normalize_tool_error_counts` | `_normalizeToolErrorCounts` |
| `_extract_tool_stats` | `_extractToolStats` |
| `_process_batch_worker` | `_processBatchWorker` |
| `main` | `main` |

### `hermes_time.py`

**模块函数** (4 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_resolve_timezone_name` | `_resolveTimezoneName` |
| `_get_zoneinfo` | `_getZoneinfo` |
| `get_timezone` | `getTimezone` |
| `now` | `now` |

### `toolsets.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_get_plugin_toolset_names` | `_getPluginToolsetNames` |
| `_get_registry_toolset_aliases` | `_getRegistryToolsetAliases` |

### `trajectory_compressor.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_effective_temperature_for_model` | `_effectiveTemperatureForModel` |
| `main` | `main` |

### `utils.py`

**模块函数** (2 个):

| Python | 期望 Kotlin |
|--------|------------|
| `_preserve_file_mode` | `_preserveFileMode` |
| `_restore_file_mode` | `_restoreFileMode` |

### `toolset_distributions.py`

**模块函数** (1 个):

| Python | 期望 Kotlin |
|--------|------------|
| `print_distribution_info` | `printDistributionInfo` |
