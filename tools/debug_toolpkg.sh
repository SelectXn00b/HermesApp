#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
else
    echo "Error: Python not found. Install python3 or python and ensure it is in PATH." >&2
    exit 1
fi

exec "$PYTHON_BIN" "$SCRIPT_DIR/debug_toolpkg.py" "$@"
