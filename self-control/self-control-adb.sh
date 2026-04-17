#!/bin/bash
# Self-Control ADB Helper Script
#
# 提供便捷的命令行接口来调用 PhoneForClaw 的 Self-Control Skills

set -e

PACKAGE="com.xiaomo.hermes"
PROVIDER_AUTHORITY="${PACKAGE}.selfcontrol"
BROADCAST_ACTION="${PACKAGE}.SELF_CONTROL"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印帮助信息
print_help() {
    cat << 'EOF'
Self-Control ADB Helper Script

使用方式:
  ./self-control-adb.sh SKILL_NAME [ARGS...]

可用方法:
  1. ContentProvider (推荐)
     ./self-control-adb.sh --method=provider navigate_app page=config

  2. Broadcast
     ./self-control-adb.sh --method=broadcast navigate_app page=config

  3. 列出所有 Skills
     ./self-control-adb.sh list

  4. 健康检查
     ./self-control-adb.sh health

示例:

  # 页面导航
  ./self-control-adb.sh navigate_app page=config

  # 配置管理 - 读取
  ./self-control-adb.sh manage_config operation=get key=exploration_mode

  # 配置管理 - 设置
  ./self-control-adb.sh manage_config operation=set key=exploration_mode value=true

  # 服务控制 - 隐藏悬浮窗
  ./self-control-adb.sh control_service operation=hide_float

  # 服务控制 - 显示悬浮窗（延迟）
  ./self-control-adb.sh control_service operation=show_float delay_ms=500

  # 日志查询
  ./self-control-adb.sh query_logs level=E lines=50

  # 列出所有 Skills
  ./self-control-adb.sh list

  # 健康检查
  ./self-control-adb.sh health

参数类型:
  - string: key=value
  - integer: key:i=123
  - long: key:l=123456789
  - boolean: key:b=true
  - float: key:f=3.14
  - double: key:d=3.14159

EOF
}

# 检查 ADB
check_adb() {
    if ! command -v adb &> /dev/null; then
        echo -e "${RED}错误: 未找到 adb 命令${NC}"
        exit 1
    fi

    # 检查设备连接
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}错误: 未检测到 Android 设备${NC}"
        echo "请通过 USB 连接设备并启用调试模式"
        exit 1
    fi
}

# ContentProvider 方法
call_provider() {
    local skill="$1"
    shift

    echo -e "${BLUE}使用 ContentProvider 调用: ${skill}${NC}"

    # 构建参数
    local extras=""
    for arg in "$@"; do
        local key="${arg%%=*}"
        local value="${arg#*=}"

        # 检测类型
        if [[ "$key" == *":i" ]]; then
            key="${key%:i}"
            extras="${extras} --extra ${key}:i:${value}"
        elif [[ "$key" == *":l" ]]; then
            key="${key%:l}"
            extras="${extras} --extra ${key}:l:${value}"
        elif [[ "$key" == *":b" ]]; then
            key="${key%:b}"
            extras="${extras} --extra ${key}:b:${value}"
        elif [[ "$key" == *":f" ]]; then
            key="${key%:f}"
            extras="${extras} --extra ${key}:f:${value}"
        elif [[ "$key" == *":d" ]]; then
            key="${key%:d}"
            extras="${extras} --extra ${key}:d:${value}"
        else
            extras="${extras} --extra ${key}:s:${value}"
        fi
    done

    # 执行调用
    echo -e "${YELLOW}执行中...${NC}"
    local result=$(adb shell content call --uri "content://${PROVIDER_AUTHORITY}/execute" \
        --method "${skill}" ${extras} 2>&1)

    # 解析结果
    if echo "$result" | grep -q "success=true"; then
        echo -e "${GREEN}✅ 成功${NC}"
        echo "$result" | grep "content=" | sed 's/.*content=//'
    else
        echo -e "${RED}❌ 失败${NC}"
        echo "$result"
    fi
}

# Broadcast 方法
call_broadcast() {
    local skill="$1"
    shift

    echo -e "${BLUE}使用 Broadcast 调用: ${skill}${NC}"

    # 构建参数
    local extras="--es skill ${skill}"
    for arg in "$@"; do
        local key="${arg%%=*}"
        local value="${arg#*=}"

        # 检测类型
        if [[ "$key" == *":i" ]]; then
            key="${key%:i}"
            extras="${extras} --ei ${key} ${value}"
        elif [[ "$key" == *":l" ]]; then
            key="${key%:l}"
            extras="${extras} --el ${key} ${value}"
        elif [[ "$key" == *":b" ]]; then
            key="${key%:b}"
            extras="${extras} --ez ${key} ${value}"
        elif [[ "$key" == *":f" ]]; then
            key="${key%:f}"
            extras="${extras} --ef ${key} ${value}"
        elif [[ "$key" == *":d" ]]; then
            key="${key%:d}"
            extras="${extras} --ed ${key} ${value}"
        else
            extras="${extras} --es ${key} ${value}"
        fi
    done

    # 执行调用
    echo -e "${YELLOW}执行中...${NC}"
    adb shell am broadcast -a "${BROADCAST_ACTION}" ${extras}

    # 等待并查看日志
    echo -e "${YELLOW}查看结果（从 logcat）...${NC}"
    sleep 1
    adb logcat -d -t 20 | grep "SelfControlReceiver" | tail -10
}

# 列出 Skills
list_skills() {
    echo -e "${BLUE}列出所有可用的 Skills${NC}"

    adb shell content call --uri "content://${PROVIDER_AUTHORITY}/execute" \
        --method list_skills 2>&1 | grep -E "(skills=|name=)" || {
        echo -e "${YELLOW}使用 Broadcast 方式...${NC}"
        adb shell am broadcast -a "${BROADCAST_ACTION}" --es action list_skills
        sleep 1
        adb logcat -d -t 50 | grep "SelfControlReceiver" | tail -30
    }
}

# 健康检查
health_check() {
    echo -e "${BLUE}Self-Control 健康检查${NC}"

    adb shell content call --uri "content://${PROVIDER_AUTHORITY}/execute" \
        --method health 2>&1 | grep -E "(success=|status=|skill_count=)" || {
        echo -e "${YELLOW}使用 Broadcast 方式...${NC}"
        adb shell am broadcast -a "${BROADCAST_ACTION}" --es action health
        sleep 1
        adb logcat -d -t 50 | grep "SelfControlReceiver" | tail -10
    }
}

# 主函数
main() {
    check_adb

    # 解析参数
    local method="provider"  # 默认使用 ContentProvider

    # 检查是否指定方法
    if [[ "$1" == --method=* ]]; then
        method="${1#*=}"
        shift
    fi

    # 特殊命令
    case "$1" in
        "" | "-h" | "--help" | "help")
            print_help
            exit 0
            ;;
        "list")
            list_skills
            exit 0
            ;;
        "health")
            health_check
            exit 0
            ;;
    esac

    # 执行 Skill
    local skill="$1"
    shift

    if [ -z "$skill" ]; then
        echo -e "${RED}错误: 缺少 Skill 名称${NC}"
        echo "使用 --help 查看帮助"
        exit 1
    fi

    case "$method" in
        "provider")
            call_provider "$skill" "$@"
            ;;
        "broadcast")
            call_broadcast "$skill" "$@"
            ;;
        *)
            echo -e "${RED}错误: 未知方法: ${method}${NC}"
            echo "可用方法: provider, broadcast"
            exit 1
            ;;
    esac
}

main "$@"
