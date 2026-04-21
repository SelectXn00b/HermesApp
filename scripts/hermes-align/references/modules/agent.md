# agent/ 模块移植 Checklist

## 文件清单（25 个 .py 文件，排除 __init__.py）

### 核心模块（14）
- [x] `credential_pool.py` → `CredentialPool.kt` ✅ 已完成（完整实现）
- [x] `auxiliary_client.py` → `AuxiliaryClient.kt` ⚠️ 骨架（需填充 provider 链细节）
- [x] `context_compressor.py` → `ContextCompressor.kt` ⚠️ 骨架
- [x] `prompt_builder.py` → `PromptBuilder.kt` ⚠️ 骨架
- [x] `error_classifier.py` → `ErrorClassifier.kt` ✅ 已完成
- [x] `model_metadata.py` → `ModelMetadata.kt` ✅ 已完成
- [x] `smart_model_routing.py` → `SmartModelRouting.kt` ✅ 已完成
- [ ] `anthropic_adapter.py` → `AnthropicAdapter.kt`
- [ ] `prompt_caching.py` → `PromptCaching.kt`
- [ ] `memory_manager.py` → `MemoryManager.kt`
- [ ] `memory_provider.py` → `MemoryProvider.kt`（移到 plugins/memory/）
- [ ] `models_dev.py` → `ModelsDev.kt`
- [ ] `rate_limit_tracker.py` → `RateLimitTracker.kt`
- [ ] `usage_pricing.py` → `UsagePricing.kt`

### 辅助模块（11）
- [ ] `context_references.py` → `ContextReferences.kt`
- [ ] `copilot_acp_client.py` → `CopilotACPClient.kt`（Copilot 特有，评估是否需要）
- [ ] `display.py` → `Display.kt`
- [ ] `insights.py` → `Insights.kt`
- [ ] `redact.py` → `Redact.kt`
- [ ] `retry_utils.py` → `RetryUtils.kt`
- [ ] `skill_commands.py` → `SkillCommands.kt`
- [ ] `skill_utils.py` → `SkillUtils.kt`
- [ ] `title_generator.py` → `TitleGenerator.kt`
- [ ] `trajectory.py` → `Trajectory.kt`

### 跳过
- `__init__.py` → Python 包标识，Kotlin 无对应

## 关键类对齐

### CredentialPool（核心亮点）
```python
class CredentialPool:
    def add_key(self, provider: str, key: str, weight: int = 1): ...
    def get_key(self, provider: str) -> str: ...
    def mark_exhausted(self, provider: str, key: str, ttl: int): ...
    def rotate(self, provider: str): ...
```

### AuxiliaryClient（provider 链 failover）
```python
class AuxiliaryClient:
    def get_provider_chain(self) -> List[str]: ...
    def call_with_fallback(self, messages: List[dict], **kwargs) -> str: ...
```

## 注意事项
- `anthropic_adapter.py` 是独立的 Anthropic SDK 封装，支持 OAuth token 刷新
- `prompt_builder.py` 加载 SOUL.md / AGENTS.md / CLAUDE.md / skills
- `credential_pool.py` 支持多 key 轮转 + exhausted TTL + 自动 failover
- `copilot_acp_client.py` Copilot 特有，Android 上可能不需要
