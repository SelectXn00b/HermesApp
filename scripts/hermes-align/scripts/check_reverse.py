#!/usr/bin/env python3
"""反向对齐检查：找出 hermes-android 中多出来的内容（Python 端不存在的）。

与 check_align.py 互补：
  check_align.py   : Python 有，Kotlin 缺 → 需要补齐
  check_reverse.py : Kotlin 有，Python 无 → 多余/Android 自有

用法：
    python3 check_reverse.py \
        --hermes  hermes-agent/ \
        --android AndroidForClaw/.../hermes/ \
        [--include-platforms] [--json] [--verbose]
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys
from collections import defaultdict
from pathlib import Path


# ─── 常量 ──────────────────────────────────────────────────────

PLATFORM_ADAPTERS_KT = {
    "gateway/platforms/Feishu.kt", "gateway/platforms/Telegram.kt",
    "gateway/platforms/Discord.kt", "gateway/platforms/Qqbot.kt",
    "gateway/platforms/Slack.kt", "gateway/platforms/WeCom.kt",
    "gateway/platforms/Matrix.kt", "gateway/platforms/WhatsApp.kt",
    "gateway/platforms/Signal.kt", "gateway/platforms/Sms.kt",
    "gateway/platforms/Weixin.kt", "gateway/platforms/ApiServer.kt",
    "gateway/platforms/Webhook.kt", "gateway/platforms/Email.kt",
    "gateway/platforms/Dingtalk.kt", "gateway/platforms/Mattermost.kt",
    "gateway/platforms/Bluebubbles.kt", "gateway/platforms/Homeassistant.kt",
    "gateway/platforms/WeComCallback.kt", "gateway/platforms/AppChat.kt",
}

SKIP_DUNDERS = {
    "__init__", "__enter__", "__exit__", "__repr__", "__str__", "__hash__",
    "__eq__", "__ne__", "__lt__", "__le__", "__gt__", "__ge__",
    "__del__", "__new__", "__getattr__", "__setattr__", "__delattr__",
    "__iter__", "__next__", "__len__", "__bool__", "__contains__",
    "__getitem__", "__setitem__", "__call__", "__post_init__", "__class_getitem__",
}

# Kotlin 文件/类在 Android 端是原创的，不应标为"多余"
ANDROID_NATIVE_FILES = {
    # Android 独有模块
    "HermesConstants.kt", "HermesApp.kt", "HermesService.kt",
    "HolographicHRR.kt",  # HRR 实现（Python 用 numpy）
}

ANDROID_NATIVE_CLASSES = {
    # Android 框架适配类
    "HermesApplication", "HermesActivity", "HermesService",
    "HermesConstants", "BuildConfig",
}

# Kotlin 关键字 / 常见误匹配词，不应作为类名或方法名
KOTLIN_KEYWORDS = {
    # 语言关键字
    "as", "break", "class", "continue", "do", "else", "false", "for",
    "fun", "if", "in", "interface", "is", "null", "object", "package",
    "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
    # 软关键字 & 常见误匹配
    "by", "catch", "constructor", "delegate", "dynamic", "field",
    "file", "finally", "get", "import", "init", "param", "property",
    "receiver", "set", "setparam", "where", "actual", "abstract",
    "annotation", "companion", "const", "crossinline", "data", "enum",
    "expect", "external", "final", "infix", "inline", "inner",
    "internal", "lateinit", "noinline", "open", "operator", "out",
    "override", "private", "protected", "public", "reified", "sealed",
    "suspend", "tailrec", "value", "vararg",
    # 评论/文档中常见英文单词误匹配
    "the", "that", "this", "with", "from", "and", "not", "but", "or",
    "any", "all", "are", "was", "has", "had", "have", "been", "will",
    "can", "may", "should", "would", "could", "each", "some", "than",
    "into", "also", "only", "just", "more", "other", "which", "their",
    "them", "then", "what", "here", "there", "these", "those",
    "used", "using", "like", "name", "type", "based", "case", "same",
}


# ─── 名称转换 ──────────────────────────────────────────────────

def camel_to_snake(name: str) -> str:
    """camelCase / PascalCase → snake_case，保留前导下划线。"""
    prefix = ""
    stripped = name
    while stripped.startswith("_"):
        prefix += "_"
        stripped = stripped[1:]
    if not stripped:
        return name
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", stripped)
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s)
    return prefix + s.lower()


def normalize_name(name: str) -> str:
    """统一为纯小写无分隔符。"""
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    return s.lower().replace("_", "")


# ─── Python 端解析 ────────────────────────────────────────────

def parse_python_file(py_path: Path) -> dict | None:
    try:
        source = py_path.read_text(encoding="utf-8")
        tree = ast.parse(source, filename=str(py_path))
    except (SyntaxError, UnicodeDecodeError):
        return None

    classes = set()
    methods = set()           # class methods (snake_case)
    module_functions = set()  # top-level functions (snake_case)
    constants = set()

    for node in ast.iter_child_nodes(tree):
        if isinstance(node, ast.ClassDef):
            classes.add(node.name)
            for item in node.body:
                if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    if item.name not in SKIP_DUNDERS:
                        methods.add(item.name)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            if not (node.name.startswith("__") and node.name.endswith("__")):
                module_functions.add(node.name)
        elif isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id.isupper() and len(target.id) >= 2:
                    if isinstance(node.value, ast.Call):
                        func = node.value.func
                        func_name = ""
                        if isinstance(func, ast.Name):
                            func_name = func.id
                        elif isinstance(func, ast.Attribute):
                            func_name = func.attr
                        if func_name in ("TypeVar", "NewType", "TypeAlias"):
                            continue
                    constants.add(target.id)

    return {
        "classes": classes,
        "methods": methods,
        "module_functions": module_functions,
        "constants": constants,
    }


def scan_python(hermes_root: Path, exceptions: dict) -> dict:
    """扫描 Python 端。返回 {normalized_stem: {classes, methods, module_functions, constants}}
    多个同 normalized_stem 的文件合并。"""
    skip_dirs = set(exceptions.get("skip_dirs", []))
    skip_files = set(exceptions.get("skip_files", []))
    index: dict[str, dict] = {}

    for py_file in sorted(hermes_root.rglob("*.py")):
        if py_file.name.startswith("__"):
            continue
        if py_file.name in skip_files:
            continue
        rel = py_file.relative_to(hermes_root)
        parts = rel.parts
        if any(p.startswith(".") or p == "__pycache__" for p in parts):
            continue
        rel_str = str(rel)
        rel_dir = str(rel.parent) if rel.parent != Path(".") else ""
        if any(rel_dir == sd or rel_dir.startswith(sd + "/") for sd in skip_dirs):
            continue

        parsed = parse_python_file(py_file)
        if parsed is None:
            continue

        norm = normalize_name(py_file.stem)
        if norm not in index:
            index[norm] = {
                "classes": set(), "methods": set(),
                "module_functions": set(), "constants": set(),
                "files": [],
            }
        entry = index[norm]
        entry["classes"] |= parsed["classes"]
        entry["methods"] |= parsed["methods"]
        entry["module_functions"] |= parsed["module_functions"]
        entry["constants"] |= parsed["constants"]
        entry["files"].append(rel_str)

    return index


# ─── Kotlin 端解析 ─────────────────────────────────────────────

_KT_FUN_RE = re.compile(
    r"(?:(?:suspend|override|private|internal|protected|public|actual|expect|"
    r"inline|operator|infix|tailrec)\s+)*"
    r"fun\s+"
    r"(?:<[^>]+>\s+)?"
    r"(?:\w+\.\s*)?"
    r"(\w+)"
    r"\s*\(",
)

_KT_CLASS_RE = re.compile(
    r"(?:(?:abstract|data|sealed|enum|inner|value|open|private|internal|actual|expect)\s+)*"
    r"(?:class|interface|object)\s+"
    r"(\w+)"
)

_KT_CONST_RE = re.compile(r"(?:const\s+)?val\s+([A-Z][A-Z0-9_]+)\s*[=:]")


def _is_valid_identifier(name: str) -> bool:
    """检查名称是否是有效的用户定义标识符（非关键字、非纯小写短词）。"""
    if name.lower() in KOTLIN_KEYWORDS:
        return False
    # 排除全小写且长度<=4的词（极可能是注释误匹配）
    if name.islower() and len(name) <= 4:
        return False
    return True


def parse_kotlin_file(kt_path: Path) -> dict:
    content = kt_path.read_text(encoding="utf-8", errors="ignore")

    # 检测对齐 stub 标记：文件头部有 "对齐" 或 "1:1" 注释，或方法体内有 "// Python:" 注释
    is_alignment_stub_file = bool(
        re.search(r"(?:对齐|1:1\s*对齐|Python\s*原始)", content[:500])
    )

    # 收集标记为 Python stub 的方法名（方法体内有 "// Python: xxx" 注释）
    stub_methods = set()
    for m in re.finditer(
        r"fun\s+(\w+)\s*\([^)]*\)[^{]*\{[^}]*?//\s*Python:\s*\w+",
        content, re.DOTALL
    ):
        stub_methods.add(m.group(1))
    # 也检测 "return null" 唯一语句的方法（在对齐文件中）
    if is_alignment_stub_file:
        for m in re.finditer(
            r"fun\s+(\w+)\s*\([^)]*\)[^{]*\{\s*(?://[^\n]*)?\s*return\s+null\s*\}",
            content, re.DOTALL
        ):
            stub_methods.add(m.group(1))

    # 收集空壳类（类体内只有 stub 方法或空 init）
    stub_classes = set()
    if is_alignment_stub_file:
        # 在对齐 stub 文件中，所有类视为对齐占位
        for m in _KT_CLASS_RE.finditer(_strip_comments_and_strings(content)):
            name = m.group(1)
            if _is_valid_identifier(name):
                stub_classes.add(name)

    # 去除注释和字符串以减少误匹配
    clean = _strip_comments_and_strings(content)
    methods = set()
    classes = set()
    constants = set()

    for m in _KT_FUN_RE.finditer(clean):
        name = m.group(1)
        if _is_valid_identifier(name):
            # 跳过已标记为 Python stub 的方法
            if name in stub_methods:
                continue
            methods.add(name)
    for m in _KT_CLASS_RE.finditer(clean):
        name = m.group(1)
        if _is_valid_identifier(name):
            # 跳过对齐 stub 文件中的类
            if name in stub_classes:
                continue
            classes.add(name)
    for m in _KT_CONST_RE.finditer(clean):
        # 在对齐 stub 文件中，常量也视为占位
        if not is_alignment_stub_file:
            constants.add(m.group(1))

    return {"methods": methods, "classes": classes, "constants": constants,
            "is_stub_file": is_alignment_stub_file}


def _strip_comments_and_strings(content: str) -> str:
    """去除 Kotlin 源码中的注释和字符串字面量，保留代码结构。"""
    # 移除块注释 /* ... */
    content = re.sub(r"/\*[\s\S]*?\*/", " ", content)
    # 移除行注释 // ...
    content = re.sub(r"//[^\n]*", " ", content)
    # 移除三引号字符串 """..."""
    content = re.sub(r'"""[\s\S]*?"""', '""', content)
    # 移除普通字符串 "..."（不跨行）
    content = re.sub(r'"(?:[^"\\]|\\.)*"', '""', content)
    return content


def scan_kotlin(android_root: Path, skip_platforms: bool) -> list[dict]:
    """扫描 Kotlin 端，返回文件列表。"""
    results = []
    for kt_file in sorted(android_root.rglob("*.kt")):
        if kt_file.stem.endswith("Test"):
            continue
        rel = kt_file.relative_to(android_root)
        rel_str = str(rel)

        if skip_platforms and rel_str in PLATFORM_ADAPTERS_KT:
            continue

        parsed = parse_kotlin_file(kt_file)
        parsed["file"] = rel_str
        parsed["stem"] = kt_file.stem
        parsed["norm"] = normalize_name(kt_file.stem)
        results.append(parsed)
    return results


# ─── 反向匹配：找 Kotlin 多出来的内容 ─────────────────────────

def method_exists_in_python(kt_method: str, py_methods: set, py_module_fns: set) -> bool:
    """检查 Kotlin 方法是否在 Python 端有对应。"""
    snake = camel_to_snake(kt_method)
    # 精确匹配
    if snake in py_methods or snake in py_module_fns:
        return True
    # 去掉前导 _ 尝试
    if snake.startswith("_"):
        bare = snake.lstrip("_")
        if bare in py_methods or bare in py_module_fns:
            return True
    else:
        if "_" + snake in py_methods or "_" + snake in py_module_fns:
            return True
    # camelCase 直接存在
    if kt_method in py_methods or kt_method in py_module_fns:
        return True
    return False


def check_reverse(kt_files: list[dict], py_index: dict) -> dict:
    """反向检查：找 Kotlin 中有但 Python 中没有的内容。"""
    report = {
        "extra_files": [],       # Kotlin 文件无对应 Python 文件
        "extra_classes": [],     # Kotlin 类在 Python 中不存在
        "extra_methods": [],     # Kotlin 方法在 Python 中不存在
        "extra_constants": [],   # Kotlin 常量在 Python 中不存在
        "summary": {},
    }

    total_kt_files = len(kt_files)
    matched_files = 0
    total_classes = 0
    extra_classes = 0
    total_methods = 0
    extra_methods = 0
    total_constants = 0
    extra_constants = 0

    for kt in kt_files:
        kt_file = kt["file"]
        kt_norm = kt["norm"]
        kt_stem = kt["stem"]

        # Skip Android-native files
        if kt_stem in ANDROID_NATIVE_FILES:
            matched_files += 1
            continue

        # Skip alignment stub files entirely (they are intentional placeholders)
        if kt.get("is_stub_file", False):
            matched_files += 1
            continue

        # Find matching Python
        py_entry = py_index.get(kt_norm)
        if py_entry is None:
            report["extra_files"].append(kt_file)
            # 统计该文件中所有内容
            for c in kt["classes"]:
                total_classes += 1
                extra_classes += 1
                report["extra_classes"].append({"class": c, "file": kt_file})
            for m in kt["methods"]:
                total_methods += 1
                extra_methods += 1
                report["extra_methods"].append({"method": m, "snake": camel_to_snake(m), "file": kt_file})
            for c in kt["constants"]:
                total_constants += 1
                extra_constants += 1
                report["extra_constants"].append({"constant": c, "file": kt_file})
            continue

        matched_files += 1
        py_classes = py_entry["classes"]
        py_methods = py_entry["methods"]
        py_module_fns = py_entry["module_functions"]
        py_constants = py_entry["constants"]

        file_extras = {
            "file": kt_file,
            "extra_classes": [],
            "extra_methods": [],
            "extra_constants": [],
        }

        # 类
        for c in kt["classes"]:
            total_classes += 1
            if c in py_classes or c in ANDROID_NATIVE_CLASSES:
                pass
            else:
                extra_classes += 1
                report["extra_classes"].append({"class": c, "file": kt_file})
                file_extras["extra_classes"].append(c)

        # 方法
        for m in kt["methods"]:
            total_methods += 1
            if method_exists_in_python(m, py_methods, py_module_fns):
                pass
            else:
                extra_methods += 1
                report["extra_methods"].append({
                    "method": m,
                    "snake": camel_to_snake(m),
                    "file": kt_file,
                })
                file_extras["extra_methods"].append(m)

        # 常量
        for c in kt["constants"]:
            total_constants += 1
            if c in py_constants:
                pass
            else:
                extra_constants += 1
                report["extra_constants"].append({"constant": c, "file": kt_file})
                file_extras["extra_constants"].append(c)

    report["summary"] = {
        "kt_files": total_kt_files,
        "matched_files": matched_files,
        "extra_files": len(report["extra_files"]),
        "total_classes": total_classes,
        "extra_classes": extra_classes,
        "total_methods": total_methods,
        "extra_methods": extra_methods,
        "total_constants": total_constants,
        "extra_constants": extra_constants,
    }

    return report


# ─── 输出 ──────────────────────────────────────────────────────

def print_report(report: dict, verbose: bool = False):
    s = report["summary"]

    print("=" * 65)
    print("  hermes-android → Hermes Agent 反向对齐检查")
    print("  （找出 Kotlin 端多出来的内容）")
    print("=" * 65)
    print()
    print(f"  Kotlin 文件总数:    {s['kt_files']}")
    print(f"  有 Python 对应:     {s['matched_files']}")
    print(f"  无 Python 对应:     {s['extra_files']}")
    print()
    print(f"  多余类:             {s['extra_classes']} / {s['total_classes']}")
    print(f"  多余方法:           {s['extra_methods']} / {s['total_methods']}")
    print(f"  多余常量:           {s['extra_constants']} / {s['total_constants']}")
    print()

    # 无对应文件
    extra_files = report["extra_files"]
    if extra_files:
        by_dir = defaultdict(list)
        for f in extra_files:
            d = str(Path(f).parent) if str(Path(f).parent) != "." else "(root)"
            by_dir[d].append(Path(f).name)
        print(f"  ── 无 Python 对应的 Kotlin 文件 ({len(extra_files)} 个) ──")
        for d, files in sorted(by_dir.items()):
            print(f"     [{d}] ({len(files)})")
            for fn in sorted(files):
                print(f"        {fn}")
        print()

    # 多余类（按文件分组）
    extra_classes = report["extra_classes"]
    if extra_classes:
        by_file = defaultdict(list)
        for item in extra_classes:
            by_file[item["file"]].append(item["class"])
        print(f"  ── 多余类 ({len(extra_classes)} 个) ──")
        for f, classes in sorted(by_file.items()):
            print(f"     {f}:")
            for c in sorted(classes):
                if verbose:
                    print(f"        class {c}")
                else:
                    print(f"        {c}")
        print()

    # 多余方法（按文件分组）
    extra_methods = report["extra_methods"]
    if extra_methods:
        by_file = defaultdict(list)
        for item in extra_methods:
            by_file[item["file"]].append(item)
        print(f"  ── 多余方法 ({len(extra_methods)} 个) ──")
        if verbose:
            for f, methods in sorted(by_file.items()):
                print(f"     {f}:")
                for m in sorted(methods, key=lambda x: x["method"]):
                    print(f"        fun {m['method']}()  ← py: {m['snake']}()")
            print()
        else:
            # 简洁模式：只显示 top 文件
            items = sorted(by_file.items(), key=lambda x: -len(x[1]))
            print(f"  {'Kotlin File':<50} {'Count':>5}")
            print(f"  {'-' * 56}")
            for f, methods in items[:30]:
                print(f"  {f:<50} {len(methods):>5}")
            if len(items) > 30:
                print(f"  ... +{len(items) - 30} 个文件")
            print()

    # 多余常量
    extra_constants = report["extra_constants"]
    if extra_constants and verbose:
        by_file = defaultdict(list)
        for item in extra_constants:
            by_file[item["file"]].append(item["constant"])
        print(f"  ── 多余常量 ({len(extra_constants)} 个) ──")
        for f, consts in sorted(by_file.items()):
            print(f"     {f}:")
            for c in sorted(consts):
                print(f"        {c}")
        print()


# ─── main ──────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="hermes-android → Hermes Agent 反向对齐检查",
    )
    parser.add_argument("--hermes", required=True, help="Hermes Python 源码根目录")
    parser.add_argument("--android", required=True, help="hermes-android Kotlin 源码根目录")
    parser.add_argument("--exceptions", help="例外配置 JSON")
    parser.add_argument("--include-platforms", action="store_true",
                        help="包含平台适配器文件（默认排除）")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="详细模式：列出每个多余方法/类/常量")
    args = parser.parse_args()

    hermes_root = Path(args.hermes).resolve()
    android_root = Path(args.android).resolve()

    if not hermes_root.exists():
        print(f"Error: {hermes_root} 不存在", file=sys.stderr)
        sys.exit(1)
    if not android_root.exists():
        print(f"Error: {android_root} 不存在", file=sys.stderr)
        sys.exit(1)

    # 加载 exceptions（仅用于 Python 端排除）
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
    py_index = scan_python(hermes_root, exceptions)
    kt_files = scan_kotlin(android_root, skip_platforms=not args.include_platforms)

    # 反向检查
    report = check_reverse(kt_files, py_index)

    # 输出
    if args.json:
        out = {
            "summary": report["summary"],
            "extra_files": report["extra_files"],
            "extra_classes": report["extra_classes"][:100],
            "extra_methods": report["extra_methods"][:200],
            "extra_constants": report["extra_constants"][:100],
        }
        print(json.dumps(out, indent=2, ensure_ascii=False))
    else:
        print_report(report, verbose=args.verbose)


if __name__ == "__main__":
    main()
