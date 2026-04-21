#!/usr/bin/env python3
"""自动为 hermes-android 补齐缺失的类方法、模块函数、类、常量 stub。

基于 check_align.py 的对齐报告，自动：
1. 创建缺失的 .kt 文件（含包声明 + 类/方法/常量 stub）
2. 在已有 .kt 文件中追加缺失的类方法 stub
3. 在已有 .kt 文件中追加缺失的模块函数 stub（作为顶层函数）
4. 在已有 .kt 文件中追加缺失的类声明 stub
5. 在已有 .kt 文件中追加缺失的常量 stub

所有 stub 标记 // TODO: align — 方便后续 AI 填充内容。

用法：
    python3 inject_stubs.py --hermes hermes-agent/ --android .../hermes/
    python3 inject_stubs.py ... --dry-run       # 只预览，不写入
    python3 inject_stubs.py ... --skip-module-functions  # 跳过模块函数
    python3 inject_stubs.py ... --skip-constants         # 跳过常量
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

# 复用 check_align 的核心逻辑
sys.path.insert(0, str(Path(__file__).parent))
from check_align import (
    scan_python, scan_kotlin, check_alignment, find_kt_match,
    snake_to_camel, snake_to_pascal, normalize_name,
    PLATFORM_ADAPTERS, SKIP_DUNDERS,
)


BASE_PKG = "com.xiaomo.hermes.hermes"
STUB_MARKER = "// TODO: align"


# ─── Python 类型注解 → Kotlin 类型 ───────────────────────────

TYPE_MAP = {
    "str": "String", "int": "Int", "float": "Double", "bool": "Boolean",
    "bytes": "ByteArray", "None": "Unit", "Any": "Any?", "NoneType": "Unit",
    "Path": "String", "datetime": "String", "date": "String",
    "dict": "Map<String, Any?>", "Dict": "Map<String, Any?>",
    "list": "List<Any?>", "List": "List<Any?>",
    "set": "Set<Any?>", "Set": "Set<Any?>",
    "tuple": "List<Any?>", "Tuple": "Pair<Any?, Any?>",
}


def map_type(ann: str) -> str:
    """简单 Python 类型注解 → Kotlin 类型。"""
    if not ann:
        return "Any?"
    ann = ann.strip()
    if ann in TYPE_MAP:
        return TYPE_MAP[ann]
    if ann.startswith("Optional["):
        inner = ann[len("Optional["):-1]
        return f"{map_type(inner)}?"
    if ann.startswith(("List[", "list[")):
        inner = ann.split("[", 1)[1].rstrip("]")
        return f"List<{map_type(inner)}>"
    if ann.startswith(("Dict[", "dict[")):
        return "Map<String, Any?>"
    if ann.startswith(("Set[", "set[")):
        inner = ann.split("[", 1)[1].rstrip("]")
        return f"Set<{map_type(inner)}>"
    if ann[0].isupper():
        return ann
    return "Any?"


# ─── Python AST → Kotlin 方法签名 ────────────────────────────

def extract_method_signature(py_path: Path, method_name: str, parent_class: str | None) -> dict:
    """从 Python 源码提取方法签名细节。"""
    try:
        source = py_path.read_text(encoding="utf-8")
        tree = ast.parse(source)
    except Exception:
        return {"args": [], "return_type": "", "is_async": False, "docstring": ""}

    for node in ast.walk(tree):
        if parent_class:
            if isinstance(node, ast.ClassDef) and node.name == parent_class:
                for item in node.body:
                    if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)) and item.name == method_name:
                        return _extract_func_info(item)
        else:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == method_name:
                # 确保是顶层函数
                return _extract_func_info(node)
    return {"args": [], "return_type": "", "is_async": False, "docstring": ""}


def _extract_func_info(node) -> dict:
    args = []
    defaults = node.args.defaults
    defaults_offset = len(node.args.args) - len(defaults)
    for i, arg in enumerate(node.args.args):
        if arg.arg in ("self", "cls"):
            continue
        ann_str = ""
        if arg.annotation:
            try:
                ann_str = ast.unparse(arg.annotation)
            except Exception:
                pass
        def_val = None
        def_idx = i - defaults_offset
        if 0 <= def_idx < len(defaults):
            try:
                def_val = ast.unparse(defaults[def_idx])
            except Exception:
                pass
        args.append({"name": arg.arg, "type": ann_str, "default": def_val})

    ret_str = ""
    if node.returns:
        try:
            ret_str = ast.unparse(node.returns)
        except Exception:
            pass

    docstring = ast.get_docstring(node) or ""
    return {
        "args": args,
        "return_type": ret_str,
        "is_async": isinstance(node, ast.AsyncFunctionDef),
        "docstring": docstring,
    }


def format_kt_method(method_name: str, info: dict, indent: str = "    ") -> str:
    """生成 Kotlin 方法 stub 代码。"""
    camel = snake_to_camel(method_name)
    kt_args = []
    for arg in info["args"]:
        kt_name = snake_to_camel(arg["name"])
        kt_type = map_type(arg["type"])
        default = ""
        if arg.get("default"):
            d = arg["default"]
            if d == "None":
                if "?" not in kt_type:
                    kt_type = f"{kt_type}?"
                default = " = null"
            elif d in ("True", "False"):
                default = f" = {d.lower()}"
            elif d == "[]":
                default = " = emptyList()"
            elif d == "{}":
                default = " = emptyMap()"
        kt_args.append(f"{kt_name}: {kt_type}{default}")

    kt_ret = map_type(info["return_type"])
    prefix = "suspend " if info["is_async"] else ""
    args_str = ", ".join(kt_args)
    ret_suffix = f": {kt_ret}" if kt_ret != "Unit" else ""

    # 方法体
    if kt_ret == "Unit" or not kt_ret:
        body = f"{indent}    {STUB_MARKER}"
    elif kt_ret.endswith("?") or kt_ret == "Any?":
        body = f"{indent}    return null {STUB_MARKER}"
    elif kt_ret == "Boolean":
        body = f"{indent}    return false {STUB_MARKER}"
    elif kt_ret == "Int":
        body = f"{indent}    return 0 {STUB_MARKER}"
    elif kt_ret == "Double":
        body = f"{indent}    return 0.0 {STUB_MARKER}"
    elif kt_ret == "String":
        body = f'{indent}    return "" {STUB_MARKER}'
    elif kt_ret.startswith("List"):
        body = f"{indent}    return emptyList() {STUB_MARKER}"
    elif kt_ret.startswith("Map"):
        body = f"{indent}    return emptyMap() {STUB_MARKER}"
    elif kt_ret.startswith("Set"):
        body = f"{indent}    return emptySet() {STUB_MARKER}"
    else:
        body = f'{indent}    throw NotImplementedError("{camel}") {STUB_MARKER}'

    return f"{indent}{prefix}fun {camel}({args_str}){ret_suffix} {{\n{body}\n{indent}}}"


# ─── Kotlin 目录/包名映射 ────────────────────────────────────

DIR_REMAP = {
    "acp_adapter": "acp",
    "environments/tool_call_parsers": "environments",
    "gateway/platforms/qqbot": "gateway/platforms",
}

def py_dir_to_kt_subdir(py_dir: str) -> str:
    if not py_dir or py_dir == ".":
        return ""
    for src, dst in DIR_REMAP.items():
        if py_dir == src or py_dir.startswith(src + "/"):
            py_dir = dst + py_dir[len(src):]
    # browser_providers → 保留原名
    return py_dir


def kt_subdir_to_pkg(subdir: str) -> str:
    if not subdir:
        return BASE_PKG
    return BASE_PKG + "." + subdir.replace("/", ".")


# ─── 找到现有 kt 文件路径 ────────────────────────────────────

def resolve_kt_path(py_rel: str, kt_index: dict, android_root: Path, py_stem_conflicts: set) -> Path | None:
    """找到 Python 文件对应的现有 Kotlin 文件路径。"""
    kt_data = find_kt_match(py_rel, kt_index, py_stem_conflicts)
    if kt_data is None:
        return None
    return android_root / kt_data["path"]


# ─── 注入 stub 到现有文件 ────────────────────────────────────

def inject_into_existing(kt_path: Path, class_methods: list, module_functions: list,
                         classes: list, constants: list, hermes_root: Path,
                         py_rel: str, dry_run: bool) -> dict:
    """向现有 .kt 文件注入缺失的 stub。返回注入统计。"""
    content = kt_path.read_text(encoding="utf-8")
    additions = []
    stats = {"methods": 0, "functions": 0, "classes": 0, "constants": 0}

    py_path = hermes_root / py_rel

    # ── 常量（追加到文件头部，package 行之后）──
    if constants:
        const_lines = []
        for c in constants:
            const_lines.append(f"val {c} = null {STUB_MARKER}")
        stats["constants"] = len(const_lines)
        additions.append(("constants", "\n".join(const_lines)))

    # ── 缺失类声明（追加到文件末尾）──
    if classes:
        class_stubs = []
        for cls_name in classes:
            # 找这个类在 Python 中有哪些方法
            cls_methods_for_class = [m for m in class_methods if m.get("class") == cls_name]
            method_stubs = []
            for m in cls_methods_for_class:
                info = extract_method_signature(py_path, m["name"], m.get("class"))
                method_stubs.append(format_kt_method(m["name"], info, indent="    "))

            body = "\n\n".join(method_stubs) if method_stubs else f"    {STUB_MARKER}"
            class_stubs.append(f"class {cls_name} {{\n{body}\n}}")
            stats["classes"] += 1
        additions.append(("classes", "\n\n".join(class_stubs)))

    # ── 类方法（注入到已有类的末尾）──
    # 只处理不属于缺失类的方法（缺失类的方法已在上面处理）
    missing_class_names = set(classes)
    existing_class_methods = [m for m in class_methods if m.get("class") not in missing_class_names]

    if existing_class_methods:
        # 按类分组
        by_class = defaultdict(list)
        for m in existing_class_methods:
            by_class[m["class"]].append(m)

        for cls_name, methods in by_class.items():
            method_stubs = []
            for m in methods:
                info = extract_method_signature(py_path, m["name"], m.get("class"))
                method_stubs.append(format_kt_method(m["name"], info, indent="    "))
                stats["methods"] += 1

            stub_block = "\n\n".join(method_stubs)

            # 找到类在文件中的位置，在其最后一个 } 前插入
            # 使用正则找 class ClassName
            class_pattern = re.compile(
                r"(?:(?:abstract|data|sealed|enum|inner|value|open|private|internal)\s+)*"
                rf"class\s+{re.escape(cls_name)}\b"
            )
            match = class_pattern.search(content)
            if match:
                # 从 match 位置找到 { 然后 brace balance 找 }
                start = match.start()
                brace_start = content.find("{", start)
                if brace_start >= 0:
                    depth = 1
                    pos = brace_start + 1
                    while pos < len(content) and depth > 0:
                        if content[pos] == "{":
                            depth += 1
                        elif content[pos] == "}":
                            depth -= 1
                        pos += 1
                    # pos 现在指向类的 } 之后
                    insert_pos = pos - 1  # } 的位置
                    content = content[:insert_pos] + "\n\n" + stub_block + "\n" + content[insert_pos:]

    # ── 模块函数（追加为文件末尾的顶层函数）──
    if module_functions:
        func_stubs = []
        for m in module_functions:
            info = extract_method_signature(py_path, m["name"], None)
            func_stubs.append(format_kt_method(m["name"], info, indent=""))
            stats["functions"] += 1
        additions.append(("functions", "\n\n".join(func_stubs)))

    # ── 组装最终内容 ──
    if not any(stats.values()):
        return stats

    # 常量加到 package/import 块之后
    for kind, block in additions:
        if kind == "constants":
            # 找到最后一个 import 行或 package 行之后
            lines = content.split("\n")
            insert_after = 0
            for i, line in enumerate(lines):
                if line.startswith("import ") or line.startswith("package "):
                    insert_after = i + 1
            # 找到 import 块后的第一个空行
            while insert_after < len(lines) and lines[insert_after].strip() == "":
                insert_after += 1
            lines.insert(insert_after, "\n" + block + "\n")
            content = "\n".join(lines)
        elif kind in ("classes", "functions"):
            # 追加到文件末尾
            if not content.endswith("\n"):
                content += "\n"
            content += "\n" + block + "\n"

    if dry_run:
        print(f"  [DRY-RUN] {kt_path.name}: +{stats['methods']} methods, +{stats['functions']} functions, +{stats['classes']} classes, +{stats['constants']} constants")
    else:
        kt_path.write_text(content, encoding="utf-8")
        print(f"  ✅ {kt_path.name}: +{stats['methods']} methods, +{stats['functions']} functions, +{stats['classes']} classes, +{stats['constants']} constants")

    return stats


# ─── 创建新 .kt 文件 ─────────────────────────────────────────

def create_new_kt_file(py_rel: str, py_data: dict, hermes_root: Path,
                       android_root: Path, dry_run: bool) -> dict:
    """创建缺失的 .kt 文件。"""
    py_path_obj = Path(py_rel)
    py_dir = str(py_path_obj.parent) if py_path_obj.parent != Path(".") else ""
    kt_filename = snake_to_pascal(py_path_obj.stem) + ".kt"
    kt_subdir = py_dir_to_kt_subdir(py_dir)
    kt_dir = android_root / kt_subdir if kt_subdir else android_root
    kt_path = kt_dir / kt_filename
    pkg = kt_subdir_to_pkg(kt_subdir)

    py_source_path = hermes_root / py_rel

    lines = [f"package {pkg}", "", f"/**", f" * Ported from {py_rel}", f" */", ""]

    stats = {"methods": 0, "functions": 0, "classes": 0, "constants": 0}

    # 常量
    for c in py_data.get("constants", []):
        lines.append(f"val {c} = null {STUB_MARKER}")
        stats["constants"] += 1
    if py_data.get("constants"):
        lines.append("")

    # 类
    for cls_name in py_data.get("classes", []):
        cls_methods = [m for m in py_data.get("class_methods", []) if m["class"] == cls_name]
        method_stubs = []
        for m in cls_methods:
            info = extract_method_signature(py_source_path, m["name"], cls_name)
            method_stubs.append(format_kt_method(m["name"], info, indent="    "))
            stats["methods"] += 1

        body = "\n\n".join(method_stubs) if method_stubs else f"    {STUB_MARKER}"
        lines.append(f"class {cls_name} {{")
        lines.append(body)
        lines.append("}")
        lines.append("")
        stats["classes"] += 1

    # 无类归属的类方法（不应存在，但防御性处理）
    orphan_methods = [m for m in py_data.get("class_methods", [])
                      if m["class"] not in py_data.get("classes", [])]
    for m in orphan_methods:
        info = extract_method_signature(py_source_path, m["name"], m.get("class"))
        lines.append(format_kt_method(m["name"], info, indent=""))
        lines.append("")
        stats["methods"] += 1

    # 模块函数
    for m in py_data.get("module_functions", []):
        info = extract_method_signature(py_source_path, m["name"], None)
        lines.append(format_kt_method(m["name"], info, indent=""))
        lines.append("")
        stats["functions"] += 1

    content = "\n".join(lines)

    if dry_run:
        print(f"  [DRY-RUN] CREATE {kt_path.relative_to(android_root)}: {stats}")
    else:
        kt_dir.mkdir(parents=True, exist_ok=True)
        kt_path.write_text(content, encoding="utf-8")
        print(f"  ✅ CREATE {kt_path.relative_to(android_root)}: {stats}")

    return stats


# ─── main ───────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="自动注入对齐 stub")
    parser.add_argument("--hermes", required=True, help="Hermes 项目根目录")
    parser.add_argument("--android", required=True, help="hermes-android Kotlin 源码根目录")
    parser.add_argument("--exceptions", help="例外配置 JSON")
    parser.add_argument("--dry-run", action="store_true", help="只预览，不写入")
    parser.add_argument("--skip-module-functions", action="store_true", help="跳过模块函数 stub")
    parser.add_argument("--skip-constants", action="store_true", help="跳过常量 stub")
    args = parser.parse_args()

    hermes_root = Path(args.hermes).resolve()
    android_root = Path(args.android).resolve()

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

    py_files = scan_python(hermes_root, exceptions, skip_platforms=True)
    kt_index = scan_kotlin(android_root)

    # 找出同名冲突
    py_stem_counts: dict[str, int] = {}
    for py_rel in py_files:
        norm = normalize_name(Path(py_rel).stem)
        py_stem_counts[norm] = py_stem_counts.get(norm, 0) + 1
    py_stem_conflicts = {norm for norm, count in py_stem_counts.items() if count > 1}

    report = check_alignment(py_files, kt_index)

    totals = {"methods": 0, "functions": 0, "classes": 0, "constants": 0, "new_files": 0}

    # ===== 1. 创建缺失文件 =====
    print("=" * 60)
    print("1. 创建缺失的 .kt 文件")
    print("=" * 60)
    for py_rel in report["files"]["missing"]:
        py_data = py_files.get(py_rel, {})
        if not any([py_data.get("classes"), py_data.get("class_methods"),
                     py_data.get("module_functions"), py_data.get("constants")]):
            continue
        if args.skip_module_functions:
            py_data = {**py_data, "module_functions": []}
        if args.skip_constants:
            py_data = {**py_data, "constants": []}
        stats = create_new_kt_file(py_rel, py_data, hermes_root, android_root, args.dry_run)
        for k in stats:
            totals[k] = totals.get(k, 0) + stats[k]
        totals["new_files"] += 1

    # ===== 2. 注入 stub 到现有文件 =====
    print()
    print("=" * 60)
    print("2. 注入 stub 到现有 .kt 文件")
    print("=" * 60)
    for fd in report["per_file"]:
        py_rel = fd["file"]
        if py_rel in report["files"]["missing"]:
            continue  # 已在上面处理

        kt_path = resolve_kt_path(py_rel, kt_index, android_root, py_stem_conflicts)
        if kt_path is None or not kt_path.exists():
            print(f"  ⚠️  找不到 {py_rel} 对应的 kt 文件，跳过")
            continue

        cm = fd["class_methods_missing"]
        mf = fd["module_functions_missing"] if not args.skip_module_functions else []
        cl = fd["classes_missing"]
        co = fd["constants_missing"] if not args.skip_constants else []

        if not any([cm, mf, cl, co]):
            continue

        stats = inject_into_existing(
            kt_path, cm, mf, cl, co, hermes_root, py_rel, args.dry_run
        )
        for k in stats:
            totals[k] = totals.get(k, 0) + stats[k]

    # ===== 汇总 =====
    print()
    print("=" * 60)
    action = "Would inject" if args.dry_run else "Injected"
    print(f"{action}:")
    print(f"  新文件:     {totals['new_files']}")
    print(f"  类方法:     {totals['methods']}")
    print(f"  模块函数:   {totals['functions']}")
    print(f"  类:         {totals['classes']}")
    print(f"  常量:       {totals['constants']}")
    total_items = totals["methods"] + totals["functions"] + totals["classes"] + totals["constants"]
    print(f"  总计:       {total_items} 项")
    if args.dry_run:
        print(f"\n  (dry-run 模式，未实际写入。去掉 --dry-run 执行写入)")


if __name__ == "__main__":
    main()
