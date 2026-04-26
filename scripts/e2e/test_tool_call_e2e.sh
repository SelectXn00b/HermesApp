#!/usr/bin/env bash
# End-to-end 验证（带 tool 调用路径）：
#   1. 通过 ApiConfigReceiver 广播写入 API key
#   2. 启动 app
#   3. 发一条**必须**触发工具调用的 chat 广播，并要求 agent 在最终回复中包含一个 TOKEN
#   4. 观察 logcat，断言同时出现：
#        - HermesBridge/Tool dispatch IN/OUT （工具真的被派发了）
#        - ExternalChatReceiver Result broadcast success=true （回合完成 + 结果广播）
#        - aiResponsePreview 中含 TOKEN （★ agent 读到 tool_result 后给出了正确回复）
#   5. 任何 401/4xx/NonRetriableException 直接 FAIL
#
# 这是 agent-level 验收：工具派发成功但 agent 没把 tool_result 合入回复 —— FAIL。
# 覆盖的"盲点"：
#   OperitChatCompletionServer 正则抽 <tool> XML → 合成 OpenAI toolCalls
#   → HermesAgentLoop.dispatch → OperitToolDispatcher → 返回结果 → 下一轮 → 含 TOKEN 的 final reply
#
# 使用：
#   HERMES_E2E_KEY=... HERMES_E2E_PROVIDER=MIMO ./scripts/e2e/test_tool_call_e2e.sh
#   # 或不设 HERMES_E2E_KEY，脚本会尝试使用 local.properties 里的 MIMO_API_KEY
#
# 退出码：0=PASS，非 0=FAIL

set -euo pipefail

PKG="com.xiaomo.androidforclaw"
MAIN_ACTIVITY="${PKG}/com.ai.assistance.operit.ui.main.MainActivity"
API_RECEIVER="${PKG}/com.ai.assistance.operit.integrations.intent.ApiConfigReceiver"
CHAT_RECEIVER="${PKG}/com.ai.assistance.operit.integrations.intent.ExternalChatReceiver"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

WAIT_AFTER_LAUNCH_S="${WAIT_AFTER_LAUNCH_S:-6}"
MAX_WAIT_S="${MAX_WAIT_S:-180}"

cd "$(dirname "$0")/../.."

log() { printf '\033[1;36m[e2e-tool]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
pass() { printf '\033[1;32m[PASS]\033[0m %s\n' "$*"; }

### 0. Key / provider resolve
KEY="${HERMES_E2E_KEY:-}"
PROVIDER="${HERMES_E2E_PROVIDER:-}"
if [[ -z "$KEY" ]]; then
  KEY="$(grep -E '^MIMO_API_KEY=' local.properties 2>/dev/null | sed 's/^MIMO_API_KEY=//' | tr -d '\r' || true)"
  [[ -n "$KEY" ]] && PROVIDER="${PROVIDER:-MIMO}"
fi
PROVIDER="${PROVIDER:-OPENROUTER}"
[[ -n "$KEY" ]] || fail "no key: set HERMES_E2E_KEY env var or MIMO_API_KEY in local.properties"
log "provider=$PROVIDER keyLen=${#KEY}"

### 1. 设备
DEVICE="${ADB_DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
  # 首选 emulator，否则拿第一个 device
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/{print $1; exit}')"
  [[ -z "$DEVICE" ]] && DEVICE="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[[ -n "$DEVICE" ]] || fail "no adb device"
log "device=$DEVICE"
ADB="adb -s $DEVICE"

### 2. 装包
[[ -f "$APK_PATH" ]] || fail "$APK_PATH not found, build first: ./gradlew :app:assembleDebug"
log "installing $APK_PATH"
$ADB install -r -t "$APK_PATH" >/dev/null

### 3. 强停
$ADB shell am force-stop "$PKG" >/dev/null || true

### 4. 广播写 key
log "broadcasting SET_API_KEY"
$ADB shell am broadcast \
  -n "$API_RECEIVER" \
  -a com.ai.assistance.operit.SET_API_KEY \
  --es key "$KEY" \
  --es provider "$PROVIDER" >/dev/null

for i in $(seq 1 20); do
  if $ADB logcat -d -v time -s ApiConfigReceiver:I 2>/dev/null | grep -q "Updated config"; then
    break
  fi
  sleep 0.2
done
$ADB logcat -d -v time -s ApiConfigReceiver:I | grep "Updated config" | tail -1 \
  | grep -v 'TESTKEY' >/dev/null || fail "config receiver did not log Updated config"

### 5. 启动 app
log "launching app"
$ADB shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep "$WAIT_AFTER_LAUNCH_S"

### 6. 发真实、**必须触发 tool call** 的 chat 广播
#    用 sleep 工具：模型无法"假装睡眠"，必须走 tool 才能完成这个请求。
#    同时要求回包含一个 token，方便我们确认 LLM 真的看见了 tool_result 再回。
REQ_ID="e2e-tool-$(date +%s)"
TOKEN="HERMES_E2E_TOOL_OK_$((RANDOM))"
MSG="请用 sleep 工具休眠 1 秒钟再回复，回复内容必须以 $TOKEN 开头。"

log "sending tool-forcing chat message requestId=$REQ_ID token=$TOKEN"
$ADB logcat -c
$ADB shell "am broadcast \
  -n '$CHAT_RECEIVER' \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id '$REQ_ID' \
  --es message '$MSG' \
  --ez return_tool_status true \
  --ez create_new_chat true \
  --ez show_floating true" >/dev/null

### 7. 三阶段断言：tool dispatch → 回合完成 → agent 回复含 TOKEN
START_TS=$(date +%s)
DISPATCH_IN_PAT='HermesBridge/Tool.*dispatch IN'
DISPATCH_OUT_PAT='HermesBridge/Tool.*dispatch OUT'
TOOL_EVENT_PAT='HermesBridge/Adapter.*ToolCall(Start|End)'
COMPLETE_PAT='MessageProcessingDelegate.*回合完成|handleTaskCompletion|EXTERNAL_CHAT_RESULT.*success=true'
RESULT_BCAST_PAT="ExternalChatReceiver.*Result broadcast: requestId=$REQ_ID success=true"
FAIL_PAT='User not found|status code: 40[0-9]|NonRetriableException|error.*code.*40[0-9]'

SAW_DISPATCH_IN=0
SAW_DISPATCH_OUT=0
SAW_TOOL_EVENT=0

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TS))
  if (( ELAPSED > MAX_WAIT_S )); then
    log "--- last 60 log lines ---"
    $ADB logcat -d -v time 2>/dev/null \
      | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|ApiConfig|dispatch" \
      | tail -60 || true
    fail "timeout ${MAX_WAIT_S}s — sawDispatchIn=$SAW_DISPATCH_IN sawDispatchOut=$SAW_DISPATCH_OUT sawToolEvent=$SAW_TOOL_EVENT"
  fi

  LOG="$($ADB logcat -d -v time 2>/dev/null || true)"

  if echo "$LOG" | grep -Eq "$FAIL_PAT"; then
    log "--- failing log ---"
    echo "$LOG" | grep -E "$FAIL_PAT|AIService|HermesAgentLoop|HermesBridge" | tail -25
    fail "saw auth/4xx error after ${ELAPSED}s"
  fi

  if (( SAW_DISPATCH_IN == 0 )) && echo "$LOG" | grep -Eq "$DISPATCH_IN_PAT"; then
    SAW_DISPATCH_IN=1
    log "saw dispatch IN after ${ELAPSED}s"
  fi
  if (( SAW_DISPATCH_OUT == 0 )) && echo "$LOG" | grep -Eq "$DISPATCH_OUT_PAT"; then
    SAW_DISPATCH_OUT=1
    log "saw dispatch OUT after ${ELAPSED}s"
  fi
  if (( SAW_TOOL_EVENT == 0 )) && echo "$LOG" | grep -Eq "$TOOL_EVENT_PAT"; then
    SAW_TOOL_EVENT=1
    log "saw ToolCall event after ${ELAPSED}s"
  fi

  # Find the Result broadcast line for *our* requestId (agent-level).
  RESULT_LINE="$(echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1 || true)"
  if [[ -n "$RESULT_LINE" ]]; then
    if (( SAW_DISPATCH_IN == 0 || SAW_DISPATCH_OUT == 0 )); then
      log "--- completion without dispatch; last 40 lines ---"
      $ADB logcat -d -v time 2>/dev/null \
        | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|dispatch" \
        | tail -40 || true
      fail "turn completed without HermesBridge/Tool dispatch IN+OUT — model skipped the tool"
    fi

    # Agent-level assertion: the TOKEN we seeded must appear in aiResponsePreview.
    # Preview is logged as: aiResponsePreview=<<<...>>> — scan just within the markers.
    PREVIEW="$(printf '%s' "$RESULT_LINE" | sed -n 's/.*aiResponsePreview=<<<\(.*\)>>>.*/\1/p')"
    if [[ -z "$PREVIEW" ]]; then
      log "--- empty aiResponsePreview; raw result line ---"
      printf '%s\n' "$RESULT_LINE"
      fail "aiResponsePreview empty — agent produced no final reply text"
    fi
    if ! printf '%s' "$PREVIEW" | grep -Fq "$TOKEN"; then
      log "--- TOKEN missing from aiResponsePreview ---"
      log "expected TOKEN=$TOKEN"
      log "preview (first 800 chars): ${PREVIEW:0:800}"
      fail "agent reply missing TOKEN — model dispatched tool but did not consume tool_result correctly"
    fi

    if ! echo "$LOG" | grep -Eq "$COMPLETE_PAT"; then
      # Result broadcast preceded the completion log in a race — it's still PASS
      # because the broadcast is emitted only after the turn finishes.
      :
    fi

    pass "agent-level turn completed after ${ELAPSED}s (dispatchIn/out=$SAW_DISPATCH_IN/$SAW_DISPATCH_OUT toolEvent=$SAW_TOOL_EVENT tokenOK=yes)"
    printf '  TOKEN=%s found in aiResponsePreview\n' "$TOKEN"
    echo "$LOG" | grep -E "HermesBridge/Tool|HermesBridge/Adapter.*ToolCall|$COMPLETE_PAT|ExternalChatReceiver.*Result broadcast" | tail -8
    # Write last-green marker for Stop hook (CLAUDE.md §0.2)
    HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
    printf '%s\n' "$HEAD_SHA" > scripts/e2e/.green-tool-call
    exit 0
  fi

  sleep 2
done
