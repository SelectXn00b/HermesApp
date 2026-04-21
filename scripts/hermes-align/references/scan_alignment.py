#!/usr/bin/env python3
"""
hermes-android 对齐度扫描工具
精确统计 Python → Kotlin 方法对齐度：
- 只统计 Python 类的顶层公开方法
- 排除嵌套函数、闭包、__dunder__ 方法
- snake_case → camelCase 自动转换匹配
- 可选排除平台适配器（Android 不需要）
"""

import ast
import os
import re
import sys
import json

SKIP_FILES = {"cli.py", "mcp_serve.py", "mini_swe_runner.py", "rl_cli.py", "run_agent.py", "__init__.py"}
SKIP_DIRS = {"tests", "scripts", "skills", "optional-skills", "hermes_cli", "website", "cron"}
SKIP_DUNDERS = {
    "__init__", "__enter__", "__exit__", "__repr__", "__str__", "__hash__",
    "__eq__", "__ne__", "__lt__", "__le__", "__gt__", "__ge__",
    "__del__", "__new__", "__getattr__", "__setattr__", "__delattr__",
    "__iter__", "__next__", "__len__", "__bool__", "__contains__",
    "__getitem__", "__setitem__", "__call__", "__post_init__", "__class_getitem__",
}

# Platform adapters - skip for Android alignment
PLATFORM_ADAPTERS = {
    "gateway/platforms/feishu.py", "gateway/platforms/telegram.py", "gateway/platforms/discord.py",
    "gateway/platforms/qqbot.py", "gateway/platforms/slack.py", "gateway/platforms/wecom.py",
    "gateway/platforms/matrix.py", "gateway/platforms/whatsapp.py", "gateway/platforms/signal.py",
    "gateway/platforms/sms.py", "gateway/platforms/weixin.py", "gateway/platforms/api_server.py",
    "gateway/platforms/webhook.py", "gateway/platforms/email.py", "gateway/platforms/dingtalk.py",
    "gateway/platforms/mattermost.py", "gateway/platforms/bluebubbles.py", "gateway/platforms/homeassistant.py",
    "gateway/platforms/wecom_callback.py", "gateway/platforms/wecom_crypto.py", "gateway/platforms/helpers.py",
    "acp_adapter/server.py", "acp_adapter/session.py",
}


def snake_to_camel(name: str) -> str:
    prefix = ""
    clean = name
    while clean.startswith("_"):
        prefix += "_"
        clean = clean[1:]
    parts = clean.split("_")
    return prefix + parts[0] + "".join(p.capitalize() for p in parts[1:])


def get_python_class_methods(py_path: str, by_class: bool = False):
    """扫描 Python 文件中的类方法。
    by_class=False (默认): 返回 set[str]（所有方法名）
    by_class=True: 返回 dict[str, set[str]]（{类名: 方法名集合}）
    """
    with open(py_path) as f:
        source = f.read()
    tree = ast.parse(source)
    if by_class:
        result = {}
        for node in ast.iter_child_nodes(tree):
            if isinstance(node, ast.ClassDef):
                cls_methods = set()
                for item in node.body:
                    if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        if item.name not in SKIP_DUNDERS:
                            cls_methods.add(item.name)
                if cls_methods:
                    result[node.name] = cls_methods
        return result
    else:
        methods = set()
        for node in ast.iter_child_nodes(tree):
            if isinstance(node, ast.ClassDef):
                for item in node.body:
                    if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        if item.name not in SKIP_DUNDERS:
                            methods.add(item.name)
        return methods


def get_kotlin_methods(kt_path: str) -> set[str]:
    with open(kt_path) as f:
        return set(re.findall(r"(?:suspend\s+)?fun\s+(\w+)", f.read()))


def find_kotlin_file(py_rel: str, kbase: str) -> str | None:
    base = os.path.splitext(py_rel)[0]
    parts = base.split("/")
    kt_parts = [p.replace("_", "").title() for p in parts]
    kt_rel = "/".join(kt_parts) + ".kt"
    kt_full = os.path.join(kbase, kt_rel)
    if os.path.exists(kt_full):
        return kt_full
    for kroot, kdirs, kfiles in os.walk(kbase):
        for kf in kfiles:
            if kf.lower() == os.path.basename(kt_rel).lower():
                return os.path.join(kroot, kf)
    return None


def scan(pybase: str, kbase: str, skip_platform: bool = True, json_output: bool = False):
    results = []
    total_py = total_kt = total_missing = 0

    for root, dirs, files in os.walk(pybase):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fname in sorted(files):
            if not fname.endswith(".py") or fname in SKIP_FILES:
                continue
            py_path = os.path.join(root, fname)
            rel = os.path.relpath(py_path, pybase)
            if skip_platform and rel in PLATFORM_ADAPTERS:
                continue

            kt_path = find_kotlin_file(rel, kbase)
            if not kt_path:
                continue

            py_methods = get_python_class_methods(py_path)
            if not py_methods:
                continue
            kt_methods = get_kotlin_methods(kt_path)

            missing = sorted(m for m in py_methods if snake_to_camel(m) not in kt_methods and m not in kt_methods)

            total_py += len(py_methods)
            total_kt += len(kt_methods)
            total_missing += len(missing)

            if missing or not json_output:
                cov = (len(py_methods) - len(missing)) / max(len(py_methods), 1) * 100
                results.append({
                    "file": rel, "py": len(py_methods), "kt": len(kt_methods),
                    "missing": len(missing), "coverage": cov,
                    "missing_methods": [f"{m} → {snake_to_camel(m)}" for m in missing],
                })

    covered = total_py - total_missing
    overall = covered / max(total_py, 1) * 100

    if json_output:
        print(json.dumps({
            "total_py_methods": total_py,
            "total_kt_methods": total_kt,
            "total_missing": total_missing,
            "overall_coverage": overall,
            "files": results,
        }, indent=2, ensure_ascii=False))
    else:
        print(f"{'Python File':<50} {'Py':>4} {'Kt':>4} {'Miss':>4} {'Cov%':>6}")
        print("-" * 75)
        for r in sorted(results, key=lambda x: x["missing"], reverse=True):
            if r["missing"] > 0:
                print(f"{r['file']:<50} {r['py']:>4} {r['kt']:>4} {r['missing']:>4} {r['coverage']:>5.1f}%")
        print("-" * 75)
        print(f"{'TOTAL':<50} {total_py:>4} {total_kt:>4} {total_missing:>4} {overall:>5.1f}%")

    return overall



def generate_todo(pybase: str, kbase: str, output: str):
    """Generate TODO_METHODS.md with priority tags."""
    from collections import defaultdict

    TAG_RULES = {
        "gateway/run.py": "🔴 核心（gateway 主逻辑）",
        "gateway/stream_consumer.py": "🔴 核心（流式输出）",
        "gateway/session.py": "🔴 核心（会话管理）",
        "gateway/delivery.py": "🔴 核心（消息投递）",
        "agent/display.py": "🟡 终端动画（KawaiiSpinner/ANSI，Android 用 Toast/通知代替）",
        "agent/credential_pool.py": "🟡 Agent 逻辑",
        "agent/memory_manager.py": "🟡 Agent 逻辑",
        "agent/memory_provider.py": "🟡 Agent 逻辑",
        "agent/context_compressor.py": "🟡 Agent 逻辑",
        "agent/context_engine.py": "🟡 Agent 逻辑",
        "agent/insights.py": "🟡 Agent 逻辑",
        "agent/subdirectory_hints.py": "🟡 Agent 逻辑",
        "agent/copilot_acp_client.py": "🟡 Agent 逻辑",
        "agent/auxiliary_client.py": "🟡 Agent 逻辑",
        "agent/rate_limit_tracker.py": "🟡 Agent 逻辑",
        "agent/usage_pricing.py": "🟡 Agent 逻辑",
        "agent/redact.py": "🟡 Agent 逻辑",
        "gateway/pairing.py": "🟡 配对管理",
        "gateway/hooks.py": "🟡 Hook 管道",
        "gateway/platforms/base.py": "🔵 平台适配器（Android 用 OpenClaw 渠道，后续适配）",
    }
    # Pattern-based defaults
    PATTERN_RULES = [
        ("tools/", "🟠 工具集"),
        ("plugins/", "🟠 插件"),
        ("environments/", "🟠 运行环境"),
        ("gateway/platforms/", "🔵 平台适配器（Android 用 OpenClaw 渠道，后续适配）"),
        ("acp_adapter/", "🔵 平台适配器（ACP 协议，后续适配）"),
    ]

    def assign_tag(rel):
        if rel in TAG_RULES:
            return TAG_RULES[rel]
        for pattern, tag in PATTERN_RULES:
            if rel.startswith(pattern):
                return tag
        return "⚪ 未分类"

    todos = []
    for root, dirs, files in os.walk(pybase):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fname in sorted(files):
            if not fname.endswith(".py") or fname in SKIP_FILES:
                continue
            py_path = os.path.join(root, fname)
            rel = os.path.relpath(py_path, pybase)
            if rel in PLATFORM_ADAPTERS:
                continue
            kt_path = find_kotlin_file(rel, kbase)
            if not kt_path:
                continue
            py_methods_by_class = get_python_class_methods(py_path, by_class=True)
            if not py_methods_by_class:
                continue
            kt_methods = get_kotlin_methods(kt_path)
            all_py = set()
            for cls, ms in py_methods_by_class.items():
                all_py.update(ms)
            missing = sorted(m for m in all_py if snake_to_camel(m) not in kt_methods and m not in kt_methods)
            if not missing:
                continue
            todos.append({
                "file": rel,
                "kt_file": os.path.relpath(kt_path, kbase),
                "classes": list(py_methods_by_class.keys()),
                "py_methods": len(all_py),
                "kt_methods": len(kt_methods),
                "missing": len(missing),
                "tag": assign_tag(rel),
                "missing_methods": [f"{m} → {snake_to_camel(m)}" for m in missing],
            })

    tag_priority = {"🔴": 0, "🟠": 1, "🟡": 2, "🔵": 3, "⚪": 4}
    todos.sort(key=lambda x: (tag_priority.get(x["tag"][0], 9), -x["missing"]))

    total_missing = sum(t["missing"] for t in todos)
    total_files = len(todos)

    by_tag = defaultdict(list)
    for t in todos:
        by_tag[t["tag"]].append(t)

    md = []
    md.append("# hermes-android 方法补全 TODO")
    md.append("")
    md.append(f"> 自动扫描于 {__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M')}")
    md.append(f"> Python 基准: {pybase}/ | Kotlin: {kbase}/")
    md.append(f"> **总计: {total_files} 个文件, {total_missing} 个缺失方法**")
    md.append("")
    md.append("| 标签 | 含义 |")
    md.append("|------|------|")
    md.append("| 🔴 核心 | gateway 主逻辑，直接影响对话/流式/会话 |")
    md.append("| 🟡 Agent | agent 逻辑，影响记忆/上下文/配对 |")
    md.append("| 🟠 工具 | tools 集合，影响工具调用/环境/浏览器 |")
    md.append("| 🟠 插件 | memory/honcho 等插件 |")
    md.append("| 🟠 环境 | 运行环境（sandbox/docker/ssh 等） |")
    md.append("| 🔵 平台 | 平台适配器，Android 用 OpenClaw 渠道，后续适配 |")
    md.append("| 🟡 终端 | 终端动画，Android 用 Toast/通知代替 |")
    md.append("| ⚪ 未分类 | 需要人工判断 |")
    md.append("")

    for tag, items in sorted(by_tag.items(), key=lambda x: tag_priority.get(x[0][0], 9)):
        tag_total = sum(i["missing"] for i in items)
        md.append(f"---")
        md.append(f"## {tag} ({len(items)} 文件, {tag_total} 方法)")
        md.append("")
        for i in items:
            md.append(f"### `{i['file']}` → `{i['kt_file']}`")
            md.append(f"- Python: {i['py_methods']} 方法 | Kotlin: {i['kt_methods']} 方法 | **缺失: {i['missing']}**")
            md.append(f"- Python 类: {', '.join(i['classes'])}")
            md.append(f"- 缺失方法:")
            md.append(f"```")
            for m in i["missing_methods"]:
                md.append(f"  {m}")
            md.append(f"```")
            md.append("")

    with open(output, "w") as f:
        f.write("\n".join(md))
    print(f"Written {total_missing} missing methods across {total_files} files to {output}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="hermes-android alignment scanner")
    parser.add_argument("--pybase", default="hermes-agent", help="Python source base dir")
    parser.add_argument("--kbase", default="AndroidForClaw/hermes-android/src/main/java/com/xiaomo/hermes/hermes", help="Kotlin source base dir")
    parser.add_argument("--include-platforms", action="store_true", help="Include platform adapters in scan")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--todo", help="Generate TODO_METHODS.md at given path")
    args = parser.parse_args()

    if args.todo:
        generate_todo(args.pybase, args.kbase, args.todo)
    else:
        scan(args.pybase, args.kbase, skip_platform=not args.include_platforms, json_output=args.json)
