# environments/ 模块移植 Checklist

## 核心模块（6）
- [ ] `agent_loop.py` → `AgentLoop.kt`
- [ ] `hermes_base_env.py` → `HermesBaseEnv.kt`
- [ ] `hermes_swe_env.py` → `HermesSWEEnv.kt`
- [ ] `web_research_env.py` → `WebResearchEnv.kt`
- [ ] `terminal_test_env.py` → `TerminalTestEnv.kt`
- [ ] `scoring_utils.py` → `ScoringUtils.kt`

## Tool Call Parsers（12 种模型）
- [x] `ToolCallParser.kt` — 基类 + Registry ✅
- [ ] `DeepSeekV3Parser.kt`
- [ ] `DeepSeekV31Parser.kt`
- [ ] `GLM45Parser.kt`
- [ ] `GLM47Parser.kt`
- [ ] `QwenParser.kt`
- [ ] `Qwen3CoderParser.kt`
- [ ] `KimiK2Parser.kt`
- [ ] `LongcatParser.kt`
- [ ] `LlamaParser.kt`
- [ ] `MistralParser.kt`
- [ ] `HermesParser.kt`
