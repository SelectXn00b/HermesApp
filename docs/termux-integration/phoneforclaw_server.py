#!/data/data/com.termux/files/usr/bin/python3
"""
PhoneForClaw Bridge Server for Termux
监听来自 AndroidForClaw 的请求并执行代码
"""

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from datetime import datetime

# 配置
SHARED_DIR = Path("/sdcard/.androidforclaw/.ipc")
REQUEST_FILE = SHARED_DIR / "request.json"
RESPONSE_FILE = SHARED_DIR / "response.json"
LOCK_FILE = SHARED_DIR / "server.lock"
LOG_FILE = SHARED_DIR / "server.log"

def log(message: str):
    """记录日志"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    log_entry = f"[{timestamp}] {message}\n"

    try:
        with open(LOG_FILE, "a") as f:
            f.write(log_entry)
    except Exception:
        pass

    print(log_entry.strip())

def execute_code(runtime: str, code: str, args: dict = None) -> dict:
    """执行代码并返回结果"""
    args = args or {}
    cwd = args.get("cwd", str(Path.home()))
    timeout = args.get("timeout", 60)

    try:
        if runtime == "python":
            result = subprocess.run(
                ["python3", "-c", code],
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd
            )
        elif runtime == "nodejs":
            result = subprocess.run(
                ["node", "-e", code],
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd
            )
        elif runtime == "shell":
            result = subprocess.run(
                code,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd
            )
        else:
            return {
                "success": False,
                "error": f"Unknown runtime: {runtime}. Use python/nodejs/shell"
            }

        return {
            "success": result.returncode == 0,
            "stdout": result.stdout,
            "stderr": result.stderr,
            "returncode": result.returncode
        }

    except subprocess.TimeoutExpired:
        return {
            "success": False,
            "error": f"Execution timeout after {timeout}s"
        }
    except Exception as e:
        return {
            "success": False,
            "error": f"Execution failed: {str(e)}"
        }

def install_package(pm: str, package: str) -> dict:
    """安装包"""
    try:
        if pm == "pip":
            cmd = f"pip3 install {package}"
        elif pm == "npm":
            cmd = f"npm install -g {package}"
        elif pm == "pkg":
            cmd = f"pkg install -y {package}"
        else:
            return {"success": False, "error": f"Unknown package manager: {pm}"}

        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            timeout=300
        )

        return {
            "success": result.returncode == 0,
            "output": result.stdout + result.stderr
        }

    except Exception as e:
        return {
            "success": False,
            "error": f"Installation failed: {str(e)}"
        }

def handle_request(request: dict) -> dict:
    """处理请求"""
    action = request.get("action")

    if action == "setup_storage":
        # 设置存储访问权限
        try:
            result = subprocess.run(
                ["termux-setup-storage"],
                capture_output=True,
                text=True,
                timeout=30
            )

            return {
                "success": True,
                "message": "Storage access configured. Please grant permission in the popup.",
                "output": result.stdout + result.stderr
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"Failed to setup storage: {str(e)}"
            }

    elif action == "exec":
        runtime = request.get("runtime")
        code = request.get("code")
        args = request.get("args", {})

        if not runtime or not code:
            return {"success": False, "error": "Missing runtime or code"}

        log(f"Executing {runtime} code ({len(code)} chars)")
        return execute_code(runtime, code, args)

    elif action == "install":
        pm = request.get("pm")
        package = request.get("package")

        if not pm or not package:
            return {"success": False, "error": "Missing pm or package"}

        log(f"Installing {package} via {pm}")
        return install_package(pm, package)

    elif action == "ping":
        return {
            "success": True,
            "message": "pong",
            "version": "1.0.0",
            "python_version": sys.version,
            "cwd": str(Path.cwd())
        }

    elif action == "check_env":
        # 检查环境
        env_info = {
            "success": True,
            "python": subprocess.run(["which", "python3"], capture_output=True, text=True).stdout.strip(),
            "node": subprocess.run(["which", "node"], capture_output=True, text=True).stdout.strip(),
            "pip": subprocess.run(["which", "pip3"], capture_output=True, text=True).stdout.strip(),
            "npm": subprocess.run(["which", "npm"], capture_output=True, text=True).stdout.strip(),
        }
        return env_info

    else:
        return {"success": False, "error": f"Unknown action: {action}"}

def notify_completion(success: bool, message: str = None):
    """使用 termux-toast 通知完成"""
    try:
        msg = message or ("Task completed" if success else "Task failed")
        subprocess.run(
            ["termux-toast", "-s", msg],
            timeout=5
        )
    except Exception:
        pass

def process_request_file():
    """处理请求文件"""
    try:
        # 读取请求
        with open(REQUEST_FILE, "r") as f:
            request = json.load(f)

        log(f"Processing request: {request.get('action', 'unknown')}")

        # 处理请求
        response = handle_request(request)

        # 写入响应
        with open(RESPONSE_FILE, "w") as f:
            json.dump(response, f, indent=2)

        # 通知完成
        notify_completion(response.get("success", False))

        # 删除请求文件
        REQUEST_FILE.unlink()

        log(f"Request processed: {response.get('success', False)}")

    except Exception as e:
        log(f"Error processing request: {str(e)}")

        # 写入错误响应
        try:
            error_response = {
                "success": False,
                "error": f"Server error: {str(e)}"
            }
            with open(RESPONSE_FILE, "w") as f:
                json.dump(error_response, f)

            notify_completion(False, str(e))
        except Exception:
            pass

def start_server():
    """启动服务器（轮询模式）"""
    # 确保目录存在
    SHARED_DIR.mkdir(parents=True, exist_ok=True)

    # 创建锁文件
    try:
        if LOCK_FILE.exists():
            log("Server already running (lock file exists)")
            sys.exit(1)

        LOCK_FILE.write_text(str(os.getpid()))
    except Exception as e:
        log(f"Failed to create lock file: {e}")
        sys.exit(1)

    log("PhoneForClaw Bridge Server started")
    log(f"Watching: {REQUEST_FILE}")
    log(f"Python: {sys.version}")
    log(f"CWD: {Path.cwd()}")

    try:
        while True:
            # 检查是否有新请求
            if REQUEST_FILE.exists():
                process_request_file()

            # 等待 1 秒
            time.sleep(1)

    except KeyboardInterrupt:
        log("Server stopped by user")
    except Exception as e:
        log(f"Server error: {e}")
    finally:
        # 清理锁文件
        if LOCK_FILE.exists():
            LOCK_FILE.unlink()
        log("Server shutdown")

if __name__ == "__main__":
    start_server()
