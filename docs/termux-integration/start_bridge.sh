#!/data/data/com.termux/files/usr/bin/bash
#
# PhoneForClaw Bridge 启动脚本
# 安装依赖并启动 RPC 服务器
#

set -e

echo "==================================="
echo "PhoneForClaw Bridge Installer"
echo "==================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Termux:API
check_termux_api() {
    if ! command -v termux-toast &> /dev/null; then
        echo -e "${YELLOW}Termux:API not found${NC}"
        echo "Installing Termux:API..."
        pkg install -y termux-api

        if ! command -v termux-toast &> /dev/null; then
            echo -e "${RED}Failed to install Termux:API${NC}"
            echo "Please install Termux:API app from F-Droid or GitHub:"
            echo "https://github.com/termux/termux-api/releases"
            exit 1
        fi
    fi
    echo -e "${GREEN}✓ Termux:API installed${NC}"
}

# 检查 Python
check_python() {
    if ! command -v python3 &> /dev/null; then
        echo -e "${YELLOW}Python not found${NC}"
        echo "Installing Python..."
        pkg install -y python
    fi
    echo -e "${GREEN}✓ Python $(python3 --version)${NC}"
}

# 检查 Node.js
check_nodejs() {
    if ! command -v node &> /dev/null; then
        echo -e "${YELLOW}Node.js not found${NC}"
        read -p "Install Node.js? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            pkg install -y nodejs
        fi
    fi

    if command -v node &> /dev/null; then
        echo -e "${GREEN}✓ Node.js $(node --version)${NC}"
    else
        echo -e "${YELLOW}⊘ Node.js not installed (optional)${NC}"
    fi
}

# 安装服务器脚本
install_server() {
    SCRIPT_DIR="$HOME/.termux"
    SERVER_SCRIPT="$SCRIPT_DIR/phoneforclaw_server.py"

    mkdir -p "$SCRIPT_DIR"

    # 检查脚本是否存在
    if [ -f "$SERVER_SCRIPT" ]; then
        echo -e "${GREEN}✓ Server script already exists${NC}"
    else
        echo -e "${YELLOW}Server script not found${NC}"
        echo "Please copy phoneforclaw_server.py to:"
        echo "  $SERVER_SCRIPT"
        echo ""
        echo "Or download from:"
        echo "  https://github.com/SelectXn00b/AndroidForClaw/raw/main/docs/termux-integration/phoneforclaw_server.py"
        exit 1
    fi

    # 设置执行权限
    chmod +x "$SERVER_SCRIPT"

    echo -e "${GREEN}✓ Server script ready${NC}"
}

# 创建共享目录
setup_shared_dir() {
    SHARED_DIR="/sdcard/.androidforclaw/.ipc"
    mkdir -p "$SHARED_DIR"
    echo -e "${GREEN}✓ Shared directory created: $SHARED_DIR${NC}"
}

# 启动服务器
start_server() {
    SERVER_SCRIPT="$HOME/.termux/phoneforclaw_server.py"

    echo ""
    echo "==================================="
    echo "Starting PhoneForClaw Bridge Server"
    echo "==================================="
    echo ""
    echo "Press Ctrl+C to stop"
    echo ""

    # 使用 nohup 在后台运行（可选）
    read -p "Run in background? (y/n) " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        nohup python3 "$SERVER_SCRIPT" > /sdcard/.androidforclaw/.ipc/server.log 2>&1 &
        PID=$!
        echo -e "${GREEN}✓ Server started in background (PID: $PID)${NC}"
        echo "Log file: /sdcard/.androidforclaw/.ipc/server.log"
        echo ""
        echo "To stop the server:"
        echo "  kill $PID"
    else
        python3 "$SERVER_SCRIPT"
    fi
}

# 主流程
main() {
    echo "Checking dependencies..."
    echo ""

    check_termux_api
    check_python
    check_nodejs
    install_server
    setup_shared_dir

    echo ""
    echo -e "${GREEN}✓ All dependencies installed${NC}"
    echo ""

    start_server
}

# 运行
main
