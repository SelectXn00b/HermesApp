#!/usr/bin/env python3
"""统一的 Hermes Agent → hermes-android 对齐度检查工具。

合并 verify_align.py 和 scan_alignment.py 的优点：
- 用 AST 精确解析 Python 端（类、方法、模块函数、常量）
- 用改进正则精确解析 Kotlin 端（类、方法、顶层函数、companion 方法、常量）
- snake_case → camelCase 名称归一化匹配
- 支持 exceptions.json 排除不需要移植的目录/文件
- 模块级函数单独统计，不与类方法混为一谈
- 平台适配器可选排除（默认排除）
- 输出清晰的分层报告 + 可选 JSON / TODO.md

用法：
    python3 check_align.py --hermes hermes-agent/ --android AndroidForClaw/.../hermes/
    python3 check_align.py --hermes ... --android ... --include-platforms
    python3 check_align.py --hermes ... --android ... --json
    python3 check_align.py --hermes ... --android ... --todo TODO.md
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


# ─── 平台适配器（Android 用 extensions 模块实现，默认排除）─────────
PLATFORM_ADAPTERS = {
    "gateway/platforms/feishu.py", "gateway/platforms/telegram.py",
    "gateway/platforms/discord.py", "gateway/platforms/qqbot.py",
    "gateway/platforms/slack.py", "gateway/platforms/wecom.py",
    "gateway/platforms/matrix.py", "gateway/platforms/whatsapp.py",
    "gateway/platforms/signal.py", "gateway/platforms/sms.py",
    "gateway/platforms/weixin.py", "gateway/platforms/api_server.py",
    "gateway/platforms/webhook.py", "gateway/platforms/email.py",
    "gateway/platforms/dingtalk.py", "gateway/platforms/mattermost.py",
    "gateway/platforms/bluebubbles.py", "gateway/platforms/homeassistant.py",
    "gateway/platforms/wecom_callback.py", "gateway/platforms/wecom_crypto.py",
    "gateway/platforms/helpers.py",
    "acp_adapter/server.py", "acp_adapter/session.py",
}

# dunder 方法不统计
SKIP_DUNDERS = {
    "__init__", "__enter__", "__exit__", "__repr__", "__str__", "__hash__",
    "__eq__", "__ne__", "__lt__", "__le__", "__gt__", "__ge__",
    "__del__", "__new__", "__getattr__", "__setattr__", "__delattr__",
    "__iter__", "__next__", "__len__", "__bool__", "__contains__",
    "__getitem__", "__setitem__", "__call__", "__post_init__", "__class_getitem__",
}


# ─── 名称转换 ───────────────────────────────────────────────

def snake_to_camel(name: str) -> str:
    """snake_case → camelCase，保留前导下划线。"""
    prefix = ""
    stripped = name
    while stripped.startswith("_"):
        prefix += "_"
        stripped = stripped[1:]
    if not stripped:
        return name
    parts = stripped.split("_")
    return prefix + parts[0] + "".join(p.capitalize() for p in parts[1:])


def snake_to_pascal(name: str) -> str:
    """snake_case → PascalCase"""
    return "".join(p.capitalize() for p in name.lstrip("_").split("_"))


def normalize_name(name: str) -> str:
    """统一为纯小写无分隔符，用于文件名匹配。"""
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    return s.lower().replace("_", "")


# ─── Python 端 AST 解析 ────────────────────────────────────

def parse_python_file(py_path: Path) -> dict | None:
    """解析单个 Python 文件，提取类、方法、模块函数、常量。"""
    try:
        source = py_path.read_text(encoding="utf-8")
        tree = ast.parse(source, filename=str(py_path))
    except (SyntaxError, UnicodeDecodeError):
        return None

    classes = []
    class_methods = []
    module_functions = []
    constants = []

    for node in ast.iter_child_nodes(tree):
        if isinstance(node, ast.ClassDef):
            classes.append(node.name)
            for item in node.body:
                if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    if item.name not in SKIP_DUNDERS:
                        class_methods.append({
                            "name": item.name,
                            "class": node.name,
                            "kt_name": snake_to_camel(item.name),
                            "is_async": isinstance(item, ast.AsyncFunctionDef),
                        })
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            if not (node.name.startswith("__") and node.name.endswith("__")):
                module_functions.append({
                    "name": node.name,
                    "kt_name": snake_to_camel(node.name),
                    "is_async": isinstance(node, ast.AsyncFunctionDef),
                })
        elif isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id.isupper() and len(target.id) >= 2:
                    # 排除 TypeVar 等类型别名（T = TypeVar("T")）
                    if isinstance(node.value, ast.Call):
                        func = node.value.func
                        func_name = ""
                        if isinstance(func, ast.Name):
                            func_name = func.id
                        elif isinstance(func, ast.Attribute):
                            func_name = func.attr
                        if func_name in ("TypeVar", "NewType", "TypeAlias"):
                            continue
                    constants.append(target.id)

    return {
        "classes": classes,
        "class_methods": class_methods,
        "module_functions": module_functions,
        "constants": constants,
    }


def scan_python(hermes_root: Path, exceptions: dict, skip_platforms: bool) -> dict[str, dict]:
    """扫描 Python 端。返回 {relative_path: parse_result}"""
    skip_dirs = set(exceptions.get("skip_dirs", []))
    skip_files = set(exceptions.get("skip_files", []))
    results = {}

    for py_file in sorted(hermes_root.rglob("*.py")):
        if py_file.name.startswith("__"):
            continue
        if py_file.name in skip_files:
            continue
        rel = py_file.relative_to(hermes_root)
        parts = rel.parts
        if any(p.startswith(".") or p == "__pycache__" or p == "node_modules" for p in parts):
            continue
        rel_str = str(rel)
        rel_dir = str(rel.parent) if rel.parent != Path(".") else ""
        if any(rel_dir == sd or rel_dir.startswith(sd + "/") for sd in skip_dirs):
            continue
        if skip_platforms and rel_str in PLATFORM_ADAPTERS:
            continue

        parsed = parse_python_file(py_file)
        if parsed is not None:
            results[rel_str] = parsed
    return results


# ─── Kotlin 端正则解析 ──────────────────────────────────────

_KT_FUN_RE = re.compile(
    r"(?:(?:suspend|override|private|internal|protected|public|actual|expect|"
    r"inline|operator|infix|tailrec)\s+)*"
    r"fun\s+"
    r"(?:<[^>]+>\s+)?"        # 泛型 <T>
    r"(?:\w+\.\s*)?"          # 扩展接收者
    r"(\w+)"                  # 方法名
    r"\s*\(",
)

_KT_CLASS_RE = re.compile(
    r"(?:(?:abstract|data|sealed|enum|inner|value|open|private|internal|actual|expect)\s+)*"
    r"(?:class|interface|object)\s+"
    r"(\w+)"
)

_KT_CONST_RE = re.compile(r"(?:const\s+)?val\s+([A-Z][A-Z0-9_]+)\s*[=:]")


def parse_kotlin_file(kt_path: Path) -> dict:
    """解析单个 Kotlin 文件，提取方法名、类名、常量名。"""
    content = kt_path.read_text(encoding="utf-8", errors="ignore")
    methods = set()
    classes = set()
    constants = set()

    for m in _KT_FUN_RE.finditer(content):
        methods.add(m.group(1))
    for m in _KT_CLASS_RE.finditer(content):
        classes.add(m.group(1))
    for m in _KT_CONST_RE.finditer(content):
        constants.add(m.group(1))

    return {"methods": methods, "classes": classes, "constants": constants}


def scan_kotlin(android_root: Path) -> dict[str, list[dict]]:
    """扫描 Kotlin 端。返回 {normalized_stem: [{path, rel_dir, methods, classes, constants}, ...]}
    同名文件保存为列表，由 find_kt_match 根据目录路径选择最佳匹配。"""
    results: dict[str, list[dict]] = {}
    for kt_file in sorted(android_root.rglob("*.kt")):
        if kt_file.stem.endswith("Test"):
            continue
        norm = normalize_name(kt_file.stem)
        parsed = parse_kotlin_file(kt_file)
        rel = kt_file.relative_to(android_root)
        parsed["path"] = rel
        parsed["rel_dir"] = str(rel.parent) if rel.parent != Path(".") else ""
        results.setdefault(norm, []).append(parsed)
    return results


# ─── 匹配逻辑 ──────────────────────────────────────────────

def find_kt_match(py_rel: str, kt_index: dict[str, list[dict]], py_stem_conflicts: set[str]) -> dict | None:
    """根据 Python 文件路径找对应的 Kotlin 文件。
    优先匹配目录路径一致的 kt 文件。
    对于 Python 端有同名冲突的 stem，要求严格目录匹配。
    对于无冲突的 stem，允许合并多个 kt 文件。"""
    py_path = Path(py_rel)
    py_stem = py_path.stem
    norm = normalize_name(py_stem)
    candidates = kt_index.get(norm)
    if not candidates:
        return None

    if len(candidates) == 1:
        return candidates[0]

    # 多个同名 kt 文件
    py_dir = str(py_path.parent) if py_path.parent != Path(".") else ""
    py_dir_norm = normalize_name(py_dir.replace("/", "_")) if py_dir else ""

    # 精确目录匹配
    for kt in candidates:
        kt_dir_norm = normalize_name(kt["rel_dir"].replace("/", "_")) if kt["rel_dir"] else ""
        if kt_dir_norm == py_dir_norm:
            return kt

    # 目录包含匹配
    if py_dir:
        py_dir_parts = [normalize_name(p) for p in py_dir.split("/")]
        best_score = -1
        best_kt = None
        for kt in candidates:
            kt_dir_parts = [normalize_name(p) for p in kt["rel_dir"].split("/") if p]
            score = sum(1 for p in py_dir_parts if p in kt_dir_parts)
            if score > best_score:
                best_score = score
                best_kt = kt
        if best_score > 0:
            return best_kt

    # Python 端没有同名冲突 → 合并所有候选 kt 文件
    if norm not in py_stem_conflicts:
        merged = {
            "path": candidates[0]["path"],
            "rel_dir": candidates[0]["rel_dir"],
            "methods": set(),
            "classes": set(),
            "constants": set(),
        }
        for kt in candidates:
            merged["methods"] |= kt["methods"]
            merged["classes"] |= kt["classes"]
            merged["constants"] |= kt["constants"]
        return merged

    # Python 端有同名冲突，只对根目录 Python 文件 fallback 到根目录 kt 文件
    if not py_dir:
        for kt in candidates:
            if not kt["rel_dir"]:
                return kt

    return None


def check_method_exists(kt_name: str, kt_methods: set[str]) -> bool:
    """检查 Kotlin 方法是否存在（尝试原名和去/加前缀 _ 变体）。"""
    if kt_name in kt_methods:
        return True
    if kt_name.startswith("_"):
        if kt_name.lstrip("_") in kt_methods:
            return True
    else:
        if "_" + kt_name in kt_methods:
            return True
    return False


# ─── 核心：对齐检查 ─────────────────────────────────────────

def check_alignment(py_files: dict, kt_index: dict) -> dict:
    """执行完整对齐检查，返回报告。"""

    # 找出 Python 端 normalized stem 有冲突的文件名
    py_stem_counts: dict[str, int] = {}
    for py_rel in py_files:
        norm = normalize_name(Path(py_rel).stem)
        py_stem_counts[norm] = py_stem_counts.get(norm, 0) + 1
    py_stem_conflicts = {norm for norm, count in py_stem_counts.items() if count > 1}

    report = {
        "files": {
            "py_total": len(py_files),
            "matched": 0,
            "missing": [],
        },
        "class_methods": {"total": 0, "matched": 0, "missing": []},
        "module_functions": {"total": 0, "matched": 0, "missing": []},
        "classes": {"total": 0, "matched": 0, "missing": []},
        "constants": {"total": 0, "matched": 0, "missing": []},
        "per_file": [],
    }

    for py_rel, py_data in sorted(py_files.items()):
        kt_data = find_kt_match(py_rel, kt_index, py_stem_conflicts)

        if kt_data is None:
            report["files"]["missing"].append(py_rel)
            # 所有内容都算缺失
            for m in py_data["class_methods"]:
                report["class_methods"]["total"] += 1
                report["class_methods"]["missing"].append({
                    "method": m["name"], "kt_name": m["kt_name"],
                    "class": m["class"], "file": py_rel,
                })
            for m in py_data["module_functions"]:
                report["module_functions"]["total"] += 1
                report["module_functions"]["missing"].append({
                    "method": m["name"], "kt_name": m["kt_name"],
                    "file": py_rel,
                })
            for c in py_data["classes"]:
                report["classes"]["total"] += 1
                report["classes"]["missing"].append({"class": c, "file": py_rel})
            for c in py_data["constants"]:
                report["constants"]["total"] += 1
                report["constants"]["missing"].append({"constant": c, "file": py_rel})
            continue

        report["files"]["matched"] += 1
        kt_methods = kt_data["methods"]
        kt_classes = kt_data["classes"]
        kt_constants = kt_data["constants"]

        file_detail = {
            "file": py_rel,
            "class_methods_missing": [],
            "module_functions_missing": [],
            "classes_missing": [],
            "constants_missing": [],
        }

        # 类方法
        for m in py_data["class_methods"]:
            report["class_methods"]["total"] += 1
            if check_method_exists(m["kt_name"], kt_methods):
                report["class_methods"]["matched"] += 1
            else:
                report["class_methods"]["missing"].append({
                    "method": m["name"], "kt_name": m["kt_name"],
                    "class": m["class"], "file": py_rel,
                })
                file_detail["class_methods_missing"].append(m)

        # 模块函数 — 在 Kotlin 端可能是顶层函数、companion 方法、或类成员
        for m in py_data["module_functions"]:
            report["module_functions"]["total"] += 1
            if check_method_exists(m["kt_name"], kt_methods):
                report["module_functions"]["matched"] += 1
            else:
                report["module_functions"]["missing"].append({
                    "method": m["name"], "kt_name": m["kt_name"],
                    "file": py_rel,
                })
                file_detail["module_functions_missing"].append(m)

        # 类
        for c in py_data["classes"]:
            report["classes"]["total"] += 1
            if c in kt_classes:
                report["classes"]["matched"] += 1
            else:
                report["classes"]["missing"].append({"class": c, "file": py_rel})
                file_detail["classes_missing"].append(c)

        # 常量
        for c in py_data["constants"]:
            report["constants"]["total"] += 1
            if c in kt_constants:
                report["constants"]["matched"] += 1
            else:
                report["constants"]["missing"].append({"constant": c, "file": py_rel})
                file_detail["constants_missing"].append(c)

        total_missing = (len(file_detail["class_methods_missing"])
                         + len(file_detail["module_functions_missing"])
                         + len(file_detail["classes_missing"])
                         + len(file_detail["constants_missing"]))
        if total_missing > 0:
            report["per_file"].append(file_detail)

    # ── 综合评分 ──
    # 文件 20%，类方法 40%，模块函数 15%，类 15%，常量 10%
    def pct(d):
        return d["matched"] / d["total"] * 100 if d["total"] else 100

    report["score"] = {
        "files": round(report["files"]["matched"] / report["files"]["py_total"] * 100, 1)
            if report["files"]["py_total"] else 100,
        "class_methods": round(pct(report["class_methods"]), 1),
        "module_functions": round(pct(report["module_functions"]), 1),
        "classes": round(pct(report["classes"]), 1),
        "constants": round(pct(report["constants"]), 1),
    }
    report["score"]["weighted"] = round(
        report["score"]["files"] * 0.20
        + report["score"]["class_methods"] * 0.40
        + report["score"]["module_functions"] * 0.15
        + report["score"]["classes"] * 0.15
        + report["score"]["constants"] * 0.10,
        1,
    )

    return report


# ─── 输出：终端报告 ──────────────────────────────────────────

def print_report(report: dict):
    s = report["score"]
    cm = report["class_methods"]
    mf = report["module_functions"]
    cl = report["classes"]
    co = report["constants"]
    fi = report["files"]

    def icon(pct):
        return "✅" if pct >= 90 else "⚠️ " if pct >= 70 else "❌"

    print("=" * 65)
    print("  Hermes Agent → hermes-android 对齐度报告")
    print("=" * 65)
    print()
    print(f"  {icon(s['files'])} 文件         {fi['matched']}/{fi['py_total']}  ({s['files']}%)")
    print(f"  {icon(s['class_methods'])} 类方法       {cm['matched']}/{cm['total']}  ({s['class_methods']}%)")
    print(f"  {icon(s['module_functions'])} 模块函数     {mf['matched']}/{mf['total']}  ({s['module_functions']}%)")
    print(f"  {icon(s['classes'])} 类           {cl['matched']}/{cl['total']}  ({s['classes']}%)")
    print(f"  {icon(s['constants'])} 常量         {co['matched']}/{co['total']}  ({s['constants']}%)")
    print()
    print(f"  {'-' * 55}")
    w = s["weighted"]
    print(f"  {icon(w)} 加权综合: {w}%")
    print(f"    (文件20% + 类方法40% + 模块函数15% + 类15% + 常量10%)")
    print()

    # 缺失文件
    missing_files = fi["missing"]
    if missing_files:
        by_dir = defaultdict(list)
        for f in missing_files:
            d = str(Path(f).parent) if str(Path(f).parent) != "." else "(root)"
            by_dir[d].append(Path(f).name)
        print(f"  ❌ 缺失文件 ({len(missing_files)} 个):")
        for d, files in sorted(by_dir.items()):
            print(f"     [{d}] ({len(files)})")
            for fn in files[:5]:
                print(f"        {fn}")
            if len(files) > 5:
                print(f"        ... +{len(files) - 5}")
        print()

    # 按文件展示缺失（前 20 个）
    per_file = report["per_file"]
    if per_file:
        # 按缺失数排序
        per_file.sort(key=lambda x: -(
            len(x["class_methods_missing"]) + len(x["module_functions_missing"])
            + len(x["classes_missing"]) + len(x["constants_missing"])
        ))
        print(f"  缺失方法 Top 文件:")
        print(f"  {'Python File':<42} {'类方法':>6} {'模块函数':>8} {'类':>4} {'常量':>4}")
        print(f"  {'-' * 65}")
        for fd in per_file[:20]:
            cm_miss = len(fd["class_methods_missing"])
            mf_miss = len(fd["module_functions_missing"])
            cl_miss = len(fd["classes_missing"])
            co_miss = len(fd["constants_missing"])
            print(f"  {fd['file']:<42} {cm_miss:>6} {mf_miss:>8} {cl_miss:>4} {co_miss:>4}")
        if len(per_file) > 20:
            print(f"  ... +{len(per_file) - 20} 个文件")
        print()


# ─── 输出：TODO.md ───────────────────────────────────────────

def generate_todo(report: dict, output: Path):
    """生成按优先级排序的 TODO.md。"""
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    s = report["score"]
    cm = report["class_methods"]
    mf = report["module_functions"]
    cl = report["classes"]

    TAG_RULES = {
        "gateway/run.py": "🔴 核心",
        "gateway/stream_consumer.py": "🔴 核心",
        "gateway/session.py": "🔴 核心",
        "gateway/delivery.py": "🔴 核心",
        "gateway/platforms/base.py": "🔵 平台基类",
    }
    PATTERN_RULES = [
        ("agent/", "🟡 Agent"),
        ("tools/", "🟠 工具"),
        ("plugins/", "🟠 插件"),
        ("environments/", "🟠 环境"),
        ("gateway/platforms/", "🔵 平台"),
        ("gateway/", "🔴 核心"),
        ("acp_adapter/", "🔵 ACP"),
        ("cron/", "🟠 定时"),
    ]

    def assign_tag(rel):
        if rel in TAG_RULES:
            return TAG_RULES[rel]
        for prefix, tag in PATTERN_RULES:
            if rel.startswith(prefix):
                return tag
        return "⚪ 其他"

    lines = [
        f"# hermes-android 未对齐清单",
        f"",
        f"> 自动生成于 {now} | 加权对齐度: {s['weighted']}%",
        f"> 文件 {report['files']['matched']}/{report['files']['py_total']} ({s['files']}%)"
        f" | 类方法 {cm['matched']}/{cm['total']} ({s['class_methods']}%)"
        f" | 模块函数 {mf['matched']}/{mf['total']} ({s['module_functions']}%)"
        f" | 类 {cl['matched']}/{cl['total']} ({s['classes']}%)",
        f"",
    ]

    # 按文件分组 + 打标签
    per_file = report["per_file"]
    if not per_file:
        lines.append("所有方法已对齐！")
        output.write_text("\n".join(lines), encoding="utf-8")
        return

    tagged = []
    for fd in per_file:
        tag = assign_tag(fd["file"])
        total_miss = (len(fd["class_methods_missing"])
                      + len(fd["module_functions_missing"]))
        tagged.append((tag, total_miss, fd))

    tag_priority = {"🔴": 0, "🟡": 1, "🟠": 2, "🔵": 3, "⚪": 4}
    tagged.sort(key=lambda x: (tag_priority.get(x[0][0], 9), -x[1]))

    # 按 tag 分组
    by_tag = defaultdict(list)
    for tag, _, fd in tagged:
        by_tag[tag].append(fd)

    for tag, items in sorted(by_tag.items(), key=lambda x: tag_priority.get(x[0][0], 9)):
        tag_cm = sum(len(i["class_methods_missing"]) for i in items)
        tag_mf = sum(len(i["module_functions_missing"]) for i in items)
        lines.append(f"## {tag} ({len(items)} 文件, {tag_cm} 类方法, {tag_mf} 模块函数)")
        lines.append(f"")

        for fd in items:
            cm_miss = fd["class_methods_missing"]
            mf_miss = fd["module_functions_missing"]
            if not cm_miss and not mf_miss:
                continue
            lines.append(f"### `{fd['file']}`")
            if cm_miss:
                lines.append(f"")
                lines.append(f"**类方法** ({len(cm_miss)} 个):")
                lines.append(f"")
                lines.append(f"| Python | 期望 Kotlin | 类 |")
                lines.append(f"|--------|------------|-----|")
                for m in cm_miss:
                    lines.append(f"| `{m['name']}` | `{m['kt_name']}` | {m['class']} |")
            if mf_miss:
                lines.append(f"")
                lines.append(f"**模块函数** ({len(mf_miss)} 个):")
                lines.append(f"")
                lines.append(f"| Python | 期望 Kotlin |")
                lines.append(f"|--------|------------|")
                for m in mf_miss:
                    lines.append(f"| `{m['name']}` | `{m['kt_name']}` |")
            lines.append(f"")

    output.write_text("\n".join(lines), encoding="utf-8")
    total_cm = sum(len(fd["class_methods_missing"]) for fd in per_file)
    total_mf = sum(len(fd["module_functions_missing"]) for fd in per_file)
    print(f"📝 TODO 已写入: {output} ({total_cm} 类方法 + {total_mf} 模块函数)")


# ─── main ───────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Hermes Agent → hermes-android 对齐度检查",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--hermes", required=True, help="Hermes 项目根目录")
    parser.add_argument("--android", required=True, help="hermes-android Kotlin 源码根目录")
    parser.add_argument("--exceptions", help="例外配置 JSON（默认自动查找 references/exceptions.json）")
    parser.add_argument("--include-platforms", action="store_true",
                        help="包含平台适配器文件（默认排除）")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    parser.add_argument("--todo", help="输出 TODO.md 文件路径")
    args = parser.parse_args()

    hermes_root = Path(args.hermes).resolve()
    android_root = Path(args.android).resolve()

    if not hermes_root.exists():
        print(f"Error: {hermes_root} 不存在", file=sys.stderr)
        sys.exit(1)
    if not android_root.exists():
        print(f"Error: {android_root} 不存在", file=sys.stderr)
        sys.exit(1)

    # 加载 exceptions
    exceptions = {}
    exc_path = args.exceptions
    if not exc_path:
        default_exc = Path(__file__).parent.parent / "references" / "exceptions.json"
        if default_exc.exists():
            exc_path = str(default_exc)
    if exc_path:
        with open(exc_path, encoding="utf-8") as f:
            exceptions = json.load(f)

    # 扫描
    py_files = scan_python(hermes_root, exceptions, skip_platforms=not args.include_platforms)
    kt_index = scan_kotlin(android_root)

    # 对齐检查
    report = check_alignment(py_files, kt_index)

    # 输出
    if args.json:
        # JSON 模式精简 missing 列表
        out = json.loads(json.dumps(report, default=str))
        for key in ["class_methods", "module_functions", "classes", "constants"]:
            if "missing" in out[key]:
                out[key]["missing"] = out[key]["missing"][:50]
        out.pop("per_file", None)
        print(json.dumps(out, indent=2, ensure_ascii=False))
    else:
        print_report(report)

    if args.todo:
        generate_todo(report, Path(args.todo))

    sys.exit(0 if report["score"]["weighted"] >= 90 else 1)


if __name__ == "__main__":
    main()
