#!/usr/bin/env bash
# End-to-end 验证（纯聊天路径，agent-level）：
#   1. 通过 ApiConfigReceiver 用 adb 广播写入 API key
#   2. 启动 app
#   3. 通过 ExternalChatReceiver 让 app 真实调用 LLM，并要求 agent 回复中含 TOKEN
#   4. 观察 logcat，断言：
#        - ExternalChatReceiver Result broadcast success=true
#        - aiResponsePreview 中含 TOKEN （★ agent 真实产生了正确回复）
#   5. 任何 401/4xx/NonRetriableException 直接 FAIL
#
# 这是 agent-level 验收：provider 通了但 agent 回复为空 / 只有 <think> —— FAIL。
#
# 使用：
#   HERMES_E2E_KEY=... HERMES_E2E_PROVIDER=OPENROUTER ./scripts/e2e/test_api_config_e2e.sh
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

log() { printf '\033[1;36m[e2e]\033[0m %s\n' "$*"; }
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
[[ -n "$KEY" ]] || fail "no key: set HERMES_E2E_KEY env var or MIMO_APIKey in local.properties"
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

### 3. 强停 + 清旧数据（覆盖 DataStore 里的脏 key）
$ADB shell am force-stop "$PKG" >/dev/null || true

### 4. 广播写 key
log "broadcasting SET_API_KEY"
$ADB shell am broadcast \
  -n "$API_RECEIVER" \
  -a com.ai.assistance.operit.SET_API_KEY \
  --es key "$KEY" \
  --es provider "$PROVIDER" >/dev/null

# 等 receiver 把 key 落盘
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

### 6. 清日志后发真实 chat 广播（带 TOKEN 要求 agent 原文回显）
#    注意：adb shell 会做两次 shell 解析，message 里有空格必须用内单引号包裹。
REQ_ID="e2e-$(date +%s)"
TOKEN="HERMES_E2E_API_OK_$((RANDOM))"
MSG="请严格只用一行回复，回复内容必须以 $TOKEN 开头，不要加任何其他前缀或 XML。"
log "sending chat message requestId=$REQ_ID token=$TOKEN"
$ADB logcat -c
$ADB shell "am broadcast \
  -n '$CHAT_RECEIVER' \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id '$REQ_ID' \
  --es message '$MSG' \
  --ez return_tool_status false \
  --ez create_new_chat true \
  --ez show_floating true" >/dev/null

### 7. 监听 logcat，直到拿到 PASS/FAIL 信号（agent-level）
START_TS=$(date +%s)
RESULT_BCAST_PAT="ExternalChatReceiver.*Result broadcast: requestId=$REQ_ID success=true"
FAIL_PAT='User not found|status code: 40[0-9]|NonRetriableException|error.*code.*40[0-9]'

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TS))
  if (( ELAPSED > MAX_WAIT_S )); then
    log "--- last 40 log lines ---"
    $ADB logcat -d -v time 2>/dev/null | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|ApiConfig" | tail -40 || true
    fail "timeout ${MAX_WAIT_S}s waiting for agent-level completion"
  fi

  LOG="$($ADB logcat -d -v time 2>/dev/null || true)"
  if echo "$LOG" | grep -Eq "$FAIL_PAT"; then
    log "--- failing log ---"
    echo "$LOG" | grep -E "$FAIL_PAT|AIService|HermesAgentLoop" | tail -20
    fail "saw auth/4xx error after ${ELAPSED}s"
  fi

  RESULT_LINE="$(echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1 || true)"
  if [[ -n "$RESULT_LINE" ]]; then
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
      fail "agent reply missing TOKEN — chat turn completed but text is wrong"
    fi
    pass "agent-level chat turn completed after ${ELAPSED}s (tokenOK=yes)"
    printf '  TOKEN=%s found in aiResponsePreview\n' "$TOKEN"
    echo "$LOG" | grep -E "$RESULT_BCAST_PAT" | tail -1
    # Write last-green marker for Stop hook (CLAUDE.md §0.2)
    HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
    printf '%s\n' "$HEAD_SHA" > scripts/e2e/.green-api-config
    exit 0
  fi
  sleep 2
done
