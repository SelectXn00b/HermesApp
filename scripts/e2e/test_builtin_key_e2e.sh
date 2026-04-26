#!/usr/bin/env bash
# End-to-end 验证：新用户 → 选择预置 OpenRouter Key 路径
#
# 模拟"全新安装 + 点击 使用内置 OpenRouter Key 按钮"的场景：
#   1. pm clear 清掉 app 所有 DataStore
#   2. 安装 APK
#   3. 解密 BuiltInKeyProvider 里的内置 key，广播写入 default 配置
#      (provider=OPENROUTER, model=openrouter/free — 与 ConfigurationScreen 里
#       点 onUseDefault 的运行时状态等价)
#   4. 启动 app
#   5. 发 external chat
#   6. logcat 等 "回合完成"
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

log() { printf '\033[1;36m[e2e-builtin]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
pass() { printf '\033[1;32m[PASS]\033[0m %s\n' "$*"; }

### 0. 解密内置 key
KEY_SRC="app/src/main/java/com/ai/assistance/operit/data/preferences/BuiltInKeyProvider.kt"
[[ -f "$KEY_SRC" ]] || fail "$KEY_SRC not found"

ENC=$(grep 'ENCRYPTED_KEY = "' "$KEY_SRC" | head -1 | sed -E 's/.*"(.*)".*/\1/')
MODEL=$(grep 'OPENROUTER_DEFAULT_MODEL = "' "$KEY_SRC" | head -1 | sed -E 's/.*"(.*)".*/\1/')
ENDPOINT=$(grep 'OPENROUTER_BASE_URL = "' "$KEY_SRC" | head -1 | sed -E 's/.*"(.*)".*/\1/')
[[ -n "$ENC" && -n "$MODEL" && -n "$ENDPOINT" ]] || fail "failed to parse BuiltInKeyProvider constants"

KEY=$(ENC="$ENC" python3 - <<'PY'
import base64, os, sys
try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.stderr.write("cryptography lib missing — pip3 install cryptography\n"); sys.exit(2)
K = bytes([
    0x41, 0x6E, 0x64, 0x72, 0x6F, 0x69, 0x64, 0x46,
    0x6F, 0x72, 0x43, 0x6C, 0x61, 0x77, 0x4B, 0x65,
    0x79, 0x50, 0x72, 0x6F, 0x76, 0x69, 0x64, 0x65,
    0x72, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x21])
data = base64.b64decode(os.environ["ENC"])
print(AESGCM(K).decrypt(data[:12], data[12:], None).decode())
PY
) || fail "failed to decrypt built-in key"
[[ -n "$KEY" ]] || fail "decrypted key is empty"
log "decrypted built-in key keyLen=${#KEY} model=$MODEL"

### 1. 设备
DEVICE="${ADB_DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/{print $1; exit}')"
  [[ -z "$DEVICE" ]] && DEVICE="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[[ -n "$DEVICE" ]] || fail "no adb device"
log "device=$DEVICE"
ADB="adb -s $DEVICE"

### 2. 安装 + 清数据（先装再清，避免 install-after-clear 的 bind-application NPE race）
### 备份 gateway 凭证（pm clear 会冲掉 Feishu/Weixin 绑定），clear 完再 restore 回去
[[ -f "$APK_PATH" ]] || fail "$APK_PATH not found, build first: ./gradlew :app:assembleDebug"
log "force-stop any running $PKG"
$ADB shell am force-stop "$PKG" >/dev/null || true
log "installing $APK_PATH"
$ADB install -r -t "$APK_PATH" >/dev/null

# gateway backup (only if already installed & has prefs — first-run case 是空的，跳过)
GATEWAY_BACKUP_DIR="$(mktemp -d -t hermes-gwbk.XXXXXX)"
trap 'rm -rf "$GATEWAY_BACKUP_DIR"' EXIT
GATEWAY_HAS_BACKUP=0

# exec-out 是 binary-safe 的 adb 通道（shell 会 mangle \r\n）
$ADB exec-out "run-as $PKG sh -c 'cat shared_prefs/hermes_gateway_secrets.xml 2>/dev/null || true'" \
    > "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" 2>/dev/null || true
$ADB exec-out "run-as $PKG sh -c 'cat files/datastore/hermes_gateway_preferences.preferences_pb 2>/dev/null || true'" \
    > "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" 2>/dev/null || true
[[ -s "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" ]] && GATEWAY_HAS_BACKUP=1
[[ -s "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" ]] && GATEWAY_HAS_BACKUP=1
if [[ "$GATEWAY_HAS_BACKUP" == "1" ]]; then
    log "backed up gateway prefs (secrets=$(stat -f%z "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" 2>/dev/null || stat -c%s "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" 2>/dev/null || echo 0)B policy=$(stat -f%z "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" 2>/dev/null || stat -c%s "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" 2>/dev/null || echo 0)B)"
fi

log "pm clear $PKG (wipe DataStore → 新用户状态)"
$ADB shell pm clear "$PKG" >/dev/null
sleep 1

# gateway restore BEFORE launch — cold start 会直接读到恢复后的文件
if [[ "$GATEWAY_HAS_BACKUP" == "1" ]]; then
    log "restoring gateway prefs"
    # 用 on-device pipe 绕开 /sdcard 权限问题：shell UID 能读 /sdcard，run-as 切到 app UID 写入
    if [[ -s "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" ]]; then
        $ADB push "$GATEWAY_BACKUP_DIR/hermes_gateway_secrets.xml" /sdcard/_hermes_gwbk_secrets.xml >/dev/null
        $ADB shell "cat /sdcard/_hermes_gwbk_secrets.xml | run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/hermes_gateway_secrets.xml && chmod 600 shared_prefs/hermes_gateway_secrets.xml'" >/dev/null || true
        $ADB shell rm -f /sdcard/_hermes_gwbk_secrets.xml >/dev/null || true
    fi
    if [[ -s "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" ]]; then
        $ADB push "$GATEWAY_BACKUP_DIR/hermes_gateway_preferences.preferences_pb" /sdcard/_hermes_gwbk_policy.pb >/dev/null
        $ADB shell "cat /sdcard/_hermes_gwbk_policy.pb | run-as $PKG sh -c 'mkdir -p files/datastore && cat > files/datastore/hermes_gateway_preferences.preferences_pb && chmod 600 files/datastore/hermes_gateway_preferences.preferences_pb'" >/dev/null || true
        $ADB shell rm -f /sdcard/_hermes_gwbk_policy.pb >/dev/null || true
    fi
    # verify restore landed
    RESTORED_SECRETS=$($ADB shell "run-as $PKG sh -c 'stat -c%s shared_prefs/hermes_gateway_secrets.xml 2>/dev/null || echo 0'" | tr -d '\r\n')
    RESTORED_POLICY=$($ADB shell "run-as $PKG sh -c 'stat -c%s files/datastore/hermes_gateway_preferences.preferences_pb 2>/dev/null || echo 0'" | tr -d '\r\n')
    log "restore verified (secrets=${RESTORED_SECRETS}B policy=${RESTORED_POLICY}B)"
fi

### 3. 启动 app（模拟新用户首次打开 app，等 DataStore 初始化）
log "launching app (cold start after pm clear)"
$ADB logcat -c
$ADB shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep "$WAIT_AFTER_LAUNCH_S"

### 4. 广播内置 key 配置（等价于新用户点"使用内置 OpenRouter Key"按钮）
log "broadcasting built-in key config"
$ADB shell am broadcast \
  -n "$API_RECEIVER" \
  -a com.ai.assistance.operit.SET_API_KEY \
  --es key "$KEY" \
  --es provider OPENROUTER \
  --es endpoint "$ENDPOINT" \
  --es model "$MODEL" >/dev/null

# 延长到 40*0.5=20s，cold start 后 DataStore/ModelConfigManager 初始化可能耗时
for i in $(seq 1 40); do
  if $ADB logcat -d -v time -s ApiConfigReceiver:I 2>/dev/null | grep -q "Updated config"; then
    break
  fi
  sleep 0.5
done
$ADB logcat -d -v time -s ApiConfigReceiver:I | grep "Updated config" | tail -1 \
  >/dev/null || fail "config receiver did not log Updated config"
log "config applied"

### 5. 发送真实 chat 广播
REQ_ID="e2e-builtin-$(date +%s)"
MSG="Say OK in one word"
log "sending chat message requestId=$REQ_ID"
$ADB logcat -c
$ADB shell "am broadcast \
  -n '$CHAT_RECEIVER' \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id '$REQ_ID' \
  --es message '$MSG' \
  --ez return_tool_status false \
  --ez create_new_chat true \
  --ez show_floating true" >/dev/null

### 6. 监听 logcat
START_TS=$(date +%s)
PASS_PAT='MessageProcessingDelegate.*: 回合完成|handleTaskCompletion|EXTERNAL_CHAT_RESULT.*success=true'
FAIL_PAT='User not found|status code: 40[0-9]|NonRetriableException|error.*code.*40[0-9]'

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TS))
  if (( ELAPSED > MAX_WAIT_S )); then
    log "--- last 40 log lines ---"
    $ADB logcat -d -v time 2>/dev/null | grep -E "AIService|Hermes|OpenRouter|ExternalChat|MessageProcessing|ApiConfig" | tail -40 || true
    fail "timeout ${MAX_WAIT_S}s waiting for completion"
  fi

  LOG="$($ADB logcat -d -v time 2>/dev/null || true)"
  if echo "$LOG" | grep -Eq "$FAIL_PAT"; then
    log "--- failing log ---"
    echo "$LOG" | grep -E "$FAIL_PAT|AIService|HermesAgentLoop" | tail -20
    fail "saw auth/4xx error after ${ELAPSED}s"
  fi
  if echo "$LOG" | grep -Eq "$PASS_PAT"; then
    pass "builtin-key chat turn completed after ${ELAPSED}s"
    $ADB logcat -d -v time 2>/dev/null | grep -E "回合完成|AIService.*连接成功|AIService.*输入token" | tail -5
    # write last-green marker so Stop hook accepts this SHA
    HEAD=$(git rev-parse HEAD 2>/dev/null || echo unknown)
    MARKER_DIR="scripts/e2e"
    : > "$MARKER_DIR/.green-builtin-key"
    echo -n "$HEAD" > "$MARKER_DIR/.green-builtin-key"
    exit 0
  fi
  sleep 2
done
