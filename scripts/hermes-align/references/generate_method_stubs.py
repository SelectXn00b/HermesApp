#!/usr/bin/env python3
"""
hermes-android 方法 stub 生成器
从 Python 类型注解自动生成 Kotlin 方法 stub（方法名 + 入参 + 出参）。

用法:
  python3 generate_method_stubs.py                          # 扫描全部，输出统计
  python3 generate_method_stubs.py --apply                  # 扫描 + 写入 Kotlin 文件
  python3 generate_method_stubs.py --apply --file gateway/run.py  # 只处理指定文件
  python3 generate_method_stubs.py --dry-run                # 预览要写入的内容
  python3 generate_method_stubs.py --json                   # JSON 输出所有 stub
  python3 generate_method_stubs.py --clean --apply          # 删除所有 @AutoStub 标记的 stub
"""

import ast
import os
import re
import sys
import json
from collections import defaultdict

# ─── Config ────────────────────────────────────────────────────────────
PYBASE = "hermes-agent"
KBASE = "AndroidForClaw/hermes-android/src/main/java/com/xiaomo/hermes/hermes"
AUTO_STUB_MARKER = "// @AutoStub"

SKIP_FILES = {"cli.py", "mcp_serve.py", "mini_swe_runner.py", "rl_cli.py", "run_agent.py", "__init__.py"}
SKIP_DIRS = {"tests", "scripts", "skills", "optional-skills", "hermes_cli", "website", "cron"}

SKIP_PY = {
    "gateway/platforms/feishu.py", "gateway/platforms/telegram.py", "gateway/platforms/discord.py",
    "gateway/platforms/qqbot.py", "gateway/platforms/slack.py", "gateway/platforms/wecom.py",
    "gateway/platforms/matrix.py", "gateway/platforms/whatsapp.py", "gateway/platforms/signal.py",
    "gateway/platforms/sms.py", "gateway/platforms/weixin.py", "gateway/platforms/api_server.py",
    "gateway/platforms/webhook.py", "gateway/platforms/email.py", "gateway/platforms/dingtalk.py",
    "gateway/platforms/mattermost.py", "gateway/platforms/bluebubbles.py", "gateway/platforms/homeassistant.py",
    "gateway/platforms/wecom_callback.py", "gateway/platforms/wecom_crypto.py", "gateway/platforms/helpers.py",
    "acp_adapter/server.py", "acp_adapter/session.py",
}

SKIP_DUNDERS = {
    "__init__", "__enter__", "__exit__", "__repr__", "__str__", "__hash__",
    "__eq__", "__ne__", "__lt__", "__le__", "__gt__", "__ge__",
    "__del__", "__new__", "__getattr__", "__setattr__", "__delattr__",
    "__iter__", "__next__", "__len__", "__bool__", "__contains__",
    "__getitem__", "__setitem__", "__call__", "__post_init__", "__class_getitem__",
}

# ─── Type Mapping ──────────────────────────────────────────────────────
TYPE_MAP = {
    # Python primitives
    "str": "String", "int": "Int", "float": "Double", "bool": "Boolean",
    "bytes": "ByteArray", "None": "Unit", "Any": "Any", "NoneType": "Unit",
    # stdlib
    "Path": "String", "datetime": "String", "date": "String", "timedelta": "Long",
    "Decimal": "Double", "UUID": "String", "Exception": "Exception", "BaseException": "Exception",
    # Python built-in types (unparameterized)
    "dict": "Map<String, Any?>", "Dict": "Map<String, Any?>",
    "list": "List<Any?>", "List": "List<Any?>",
    "set": "Set<Any?>", "Set": "Set<Any?>",
    "tuple": "List<Any?>", "Tuple": "Pair<Any?, Any?>",
    "Callable": "(Any?) -> Any?",
    # Python stdlib (no Kotlin equiv)
    "Lock": "Any?", "Popen": "Any?", "Response": "Any?", "Pattern": "Any?",
    "LogRecord": "Any?", "ParseResult": "Any?", "ExecuteResult": "Any?",
    "MessageHandler": "Any?", "ChatCompletionMessageToolCall": "Any?",
    "ToolEntry": "Any?", "ScoredDataGroup": "Any?", "FeedParser": "Any?",
    # Hermes framework types
    "SessionSource": "SessionSource", "SessionContext": "SessionContext",
    "SessionStore": "SessionStore", "SessionManager": "SessionManager",
    "GatewayConfig": "GatewayConfig", "PlatformConfig": "PlatformConfig",
    "Platform": "Platform", "MessageEvent": "MessageEvent",
    "OutboundMessage": "OutboundMessage", "DeliveryResult": "DeliveryResult",
    "HermesState": "HermesState", "DeliveryTarget": "DeliveryTarget",
    # Python-only classes → Any?
    "SkillMeta": "Any?", "SkillBundle": "Any?", "SkillSource": "Any?",
    "WellKnownSkillSource": "Any?", "SkillsShSource": "Any?",
    "GitHubSource": "Any?", "ClawHubSource": "Any?", "SessionEntry": "Any?",
    "CredentialEntry": "Any?", "PooledCredential": "Any?", "CredentialPool": "Any?",
    "AgentConfig": "Any?", "TurnConfig": "Any?", "TokenUsage": "Any?",
    "ProcessSession": "Any?", "CheckContext": "Any?", "ToolAction": "Any?",
    "Toolkit": "Any?", "VoiceMode": "Any?", "SessionVoiceMode": "Any?",
    "ClaudeConfig": "Any?", "Environment": "Any?", "FileSystem": "Any?",
    "ModalityEvent": "Any?", "TrackLifecycle": "Any?", "AssistantMode": "Any?",
    "SubdirectoryHints": "Any?", "RedactLevel": "Any?", "UsagePricing": "Any?",
    "RateLimitTracker": "Any?", "BudgetConfig": "Any?", "ErrorClassifier": "Any?",
    "InsightStore": "Any?", "MemoryEntry": "Any?", "CheckpointManager": "Any?",
    "HonchoPeer": "Any?", "HonchoSession": "Any?", "HonchoMessage": "Any?", "Honcho": "Any?",
    "FeedbackEntry": "Any?", "MCPConfig": "Any?", "MCPServer": "Any?",
    "ManagedModal": "Any?", "EnvironmentSpec": "Any?", "PortForward": "Any?",
    "TrajectoryRoll": "Any?", "HermesLogging": "Any?", "TriggerKey": "Any?",
}


def split_generic_args(s: str) -> list:
    parts, depth, current = [], 0, []
    for ch in s:
        if ch == '[':
            depth += 1; current.append(ch)
        elif ch == ']':
            depth -= 1; current.append(ch)
        elif ch == ',' and depth == 0:
            parts.append(''.join(current).strip()); current = []
        else:
            current.append(ch)
    if current:
        parts.append(''.join(current).strip())
    return parts


def map_python_type(ann_str: str) -> str:
    if not ann_str:
        return "Any?"
    if ann_str == "None":
        return "Unit"
    s = ann_str.strip()

    # Generic types with parameters
    for prefix, open_k, close_k in [
        ("Optional[", "", "?"),
        ("Union[", "", ""),  # handled below
        ("List[", "List<", ">"),
        ("list[", "List<", ">"),
        ("Dict[", "Map<", ">"),
        ("dict[", "Map<", ">"),
        ("Set[", "Set<", ">"),
        ("set[", "Set<", ">"),
        ("Sequence[", "Sequence<", ">"),
        ("Tuple[", "", ""),  # handled below
        ("tuple[", "", ""),  # handled below
    ]:
        if s.startswith(prefix) and s.endswith("]"):
            inner_str = s[len(prefix):-1]
            if prefix.startswith("Optional"):
                return f"{map_python_type(inner_str)}?"
            if prefix.startswith("Union"):
                parts = split_generic_args(inner_str)
                if len(parts) == 2 and parts[1].strip() == "None":
                    return f"{map_python_type(parts[0].strip())}?"
                if len(parts) == 2 and parts[0].strip() == "None":
                    return f"{map_python_type(parts[1].strip())}?"
                return "Any?"
            if prefix.startswith(("Tuple", "tuple")):
                parts = split_generic_args(inner_str)
                if len(parts) == 2:
                    return f"Pair<{map_python_type(parts[0].strip())}, {map_python_type(parts[1].strip())}>"
                return "List<Any?>"
            if prefix.startswith(("Dict", "dict")):
                parts = split_generic_args(inner_str)
                if len(parts) == 2:
                    return f"Map<{map_python_type(parts[0].strip())}, {map_python_type(parts[1].strip())}>"
                return "Map<String, Any?>"
            inner = map_python_type(inner_str)
            return f"{open_k}{inner}{close_k}"

    # Literal → String
    if s.startswith("Literal["):
        return "String"
    # Callable → function type
    if s.startswith("Callable["):
        return "(Any?) -> Any?"
    # Iterators
    if s.startswith(("AsyncIterator[", "Iterator[")):
        return "Sequence<Any?>"
    # Single-letter type vars
    if len(s) == 1 and s.isupper():
        return "Any?"
    # coroutine
    if "coroutine" in s.lower():
        return "Any?"

    # Direct mapping
    if s in TYPE_MAP:
        return TYPE_MAP[s]

    # Dotted class name (e.g., types.ModuleType)
    if "." in s:
        parts = s.split(".")
        if parts[-1][0].isupper():
            return parts[-1]

    # Class name → keep as-is (Kotlin class)
    if s[0].isupper() and s.replace("_", "").replace(".", "").replace("[", "").replace("]", "").isalnum():
        return s

    return "Any?"


def to_camel(name: str) -> str:
    prefix = ""
    clean = name
    while clean.startswith("_"):
        prefix += "_"; clean = clean[1:]
    parts = clean.split("_")
    return prefix + parts[0] + "".join(p.capitalize() for p in parts[1:])


def generate_stub_body(method_name: str, kt_ret: str, camel_name: str) -> str:
    if kt_ret == "Unit":
        return f"        // TODO: implement {camel_name}  {AUTO_STUB_MARKER}"
    if kt_ret == "Unit?":
        return "        return null"
    if kt_ret == "Boolean":
        return "        return false"
    if kt_ret == "Boolean?":
        return "        return null"
    if kt_ret == "Int":
        return "        return 0"
    if kt_ret == "Long":
        return "        return 0L"
    if kt_ret == "Double":
        return "        return 0.0"
    if kt_ret == "String":
        return '        return ""'
    if kt_ret == "String?":
        return "        return null"
    if kt_ret.startswith("List<"):
        return "        return emptyList()"
    if kt_ret.startswith("Map<"):
        return "        return emptyMap()"
    if kt_ret.startswith("Set<"):
        return "        return emptySet()"
    if kt_ret.startswith("Sequence<"):
        return "        return emptySequence()"
    if kt_ret.endswith("?"):
        return "        return null"
    return f'        throw NotImplementedError("{camel_name}")'


class MethodInfo:
    def __init__(self, class_name, method_name, is_async, args, return_type, docstring):
        self.class_name = class_name
        self.method_name = method_name
        self.is_async = is_async
        self.args = args
        self.return_type = return_type
        self.docstring = docstring

    def to_kotlin_signature(self):
        camel_name = to_camel(self.method_name)
        kt_args = []
        for arg in self.args:
            if arg["name"] in ("self", "cls"):
                continue
            kt_name = to_camel(arg["name"])
            kt_type = map_python_type(arg["type"])
            default = ""
            if arg.get("default"):
                d = arg["default"]
                if d == "None":
                    if "?" not in kt_type:
                        kt_type = f"{kt_type}?"
                    default = " = null"
                elif d in ("True", "False"):
                    default = f" = {d.lower()}"
                elif d.isdigit() or (d.startswith("-") and d[1:].isdigit()):
                    default = f" = {d}"
                elif d.startswith('"') or d.startswith("'"):
                    default = f" = {d}"
                elif d == "[]":
                    default = " = emptyList()"
                elif d == "{}":
                    default = " = emptyMap()"
            kt_args.append(f"{kt_name}: {kt_type}{default}")

        kt_ret = map_python_type(self.return_type)
        prefix = "suspend fun " if self.is_async else "fun "
        args_str = ", ".join(kt_args)

        # Doc with marker
        if self.docstring:
            first_line = self.docstring.strip().split("\n")[0]
            doc = f"    /** {first_line} */\n"
        else:
            doc = f"    /** @AutoStub {camel_name} */\n"

        body = generate_stub_body(self.method_name, kt_ret, camel_name)
        return f"{doc}    {prefix}{camel_name}({args_str}): {kt_ret} {{\n{body}\n    }}"


def extract_python_methods(py_path: str) -> list:
    with open(py_path) as f:
        source = f.read()
    tree = ast.parse(source)
    results = []
    for node in ast.iter_child_nodes(tree):
        if not isinstance(node, ast.ClassDef):
            continue
        for item in node.body:
            if not isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            if item.name in SKIP_DUNDERS:
                continue
            is_async = isinstance(item, ast.AsyncFunctionDef)
            args = []
            defaults = item.args.defaults
            defaults_offset = len(item.args.args) - len(defaults)
            for i, arg in enumerate(item.args.args):
                ann_str = ast.unparse(arg.annotation) if arg.annotation else ""
                def_val = None
                def_idx = i - defaults_offset
                if def_idx >= 0 and def_idx < len(defaults):
                    try:
                        def_val = ast.unparse(defaults[def_idx])
                    except Exception:
                        pass
                args.append({"name": arg.arg, "type": ann_str, "default": def_val})
            if item.args.vararg:
                args.append({"name": item.args.vararg.arg, "type": "Any", "default": None})
            if item.args.kwarg:
                args.append({"name": item.args.kwarg.arg, "type": "Any", "default": None})
            ret_str = ""
            if item.returns:
                try:
                    ret_str = ast.unparse(item.returns)
                except Exception:
                    pass
            docstring = ast.get_docstring(item) or ""
            results.append(MethodInfo(node.name, item.name, is_async, args, ret_str, docstring))
    return results


def find_kotlin_file(py_rel: str) -> str | None:
    base = os.path.splitext(py_rel)[0]
    parts = base.split("/")
    kt_parts = [p.replace("_", "").title() for p in parts]
    kt_rel = "/".join(kt_parts) + ".kt"
    kt_full = os.path.join(KBASE, kt_rel)
    if os.path.exists(kt_full):
        return kt_full
    target = os.path.basename(kt_rel).lower()
    for root, dirs, files in os.walk(KBASE):
        for f in files:
            if f.lower() == target:
                return os.path.join(root, f)
    return None


def get_kotlin_methods(kt_path: str) -> set:
    with open(kt_path) as f:
        return set(re.findall(r"(?:suspend\s+)?fun\s+(\w+)", f.read()))


def insert_stubs_into_file(kt_path: str, stubs: list) -> str:
    with open(kt_path) as f:
        content = f.read()
    if not stubs:
        return content
    stubs_block = "\n".join(stubs) + "\n"
    # Insert before last closing brace
    last_brace = content.rfind('}')
    if last_brace > 0:
        return content[:last_brace] + "\n" + stubs_block + "\n" + content[last_brace:]
    return content


def scan_all(filter_file=None):
    all_stubs = []
    for root, dirs, files in os.walk(PYBASE):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fname in sorted(files):
            if not fname.endswith(".py") or fname in SKIP_FILES:
                continue
            py_path = os.path.join(root, fname)
            rel = os.path.relpath(py_path, PYBASE)
            if filter_file and rel != filter_file:
                continue
            if rel in SKIP_PY:
                continue
            kt_path = find_kotlin_file(rel)
            if not kt_path:
                continue
            py_methods = extract_python_methods(py_path)
            if not py_methods:
                continue
            kt_methods = get_kotlin_methods(kt_path)
            by_class = defaultdict(list)
            for m in py_methods:
                by_class[m.class_name].append(m)
            for class_name, methods in by_class.items():
                missing = [m for m in methods if to_camel(m.method_name) not in kt_methods and m.method_name not in kt_methods]
                if missing:
                    stubs = [m.to_kotlin_signature() for m in missing]
                    all_stubs.append({
                        "py_file": rel, "kt_file": kt_path,
                        "py_class": class_name, "missing_count": len(missing), "stubs": stubs,
                    })
    return all_stubs


def clean_stubs(kt_path: str) -> int:
    """Remove all @AutoStub stubs from a Kotlin file. Returns count removed."""
    with open(kt_path) as f:
        content = f.read()
    if AUTO_STUB_MARKER not in content:
        return 0

    lines = content.split("\n")
    new_lines = []
    i = 0
    removed = 0

    while i < len(lines):
        line = lines[i]
        # Check for @AutoStub in doc comment or method signature
        if AUTO_STUB_MARKER not in line:
            new_lines.append(line)
            i += 1
            continue

        # Found @AutoStub — skip the stub
        # If it's a doc comment line, skip the entire doc block
        if "/**" in line:
            while i < len(lines) and "*/" not in lines[i]:
                i += 1
            i += 1  # skip */
        # Skip the method signature line and body
        if i < len(lines):
            # Skip to opening brace
            while i < len(lines) and "{" not in lines[i]:
                if AUTO_STUB_MARKER in lines[i]:
                    i += 1  # single-line @AutoStub TODO
                    break
                i += 1
            # Count braces to find close
            if i < len(lines) and "{" in lines[i]:
                depth = lines[i].count("{") - lines[i].count("}")
                i += 1
                while i < len(lines) and depth > 0:
                    depth += lines[i].count("{") - lines[i].count("}")
                    i += 1
        # Skip trailing blank lines
        while i < len(lines) and lines[i].strip() == "":
            i += 1
        removed += 1

    if removed > 0:
        with open(kt_path, "w") as f:
            f.write("\n".join(new_lines))
    return removed


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Generate Kotlin method stubs from Python")
    parser.add_argument("--file", help="Only process this Python file")
    parser.add_argument("--apply", action="store_true", help="Write stubs to Kotlin files")
    parser.add_argument("--dry-run", action="store_true", help="Preview what would be written")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--clean", action="store_true", help="Remove all @AutoStub stubs from Kotlin files")
    args = parser.parse_args()

    # ─── Clean mode ────────────────────────────────────────────────────
    if args.clean:
        total = 0
        for root, dirs, files in os.walk(KBASE):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for fname in sorted(files):
                if not fname.endswith(".kt"):
                    continue
                kt_path = os.path.join(root, fname)
                removed = clean_stubs(kt_path)
                if removed:
                    total += removed
                    action = "Removed" if args.apply else "Would remove"
                    print(f"  {action} {removed} stubs: {kt_path}")
        if not args.apply:
            print(f"\n  (dry-run — pass --apply to actually remove {total} stubs)")
        else:
            print(f"\n  Cleaned {total} stubs total")
        return

    # ─── Generate mode ─────────────────────────────────────────────────
    all_stubs = scan_all(args.file)

    if args.json:
        print(json.dumps(all_stubs, indent=2, ensure_ascii=False))
        return

    total = sum(s["missing_count"] for s in all_stubs)
    print(f"Found {total} missing methods across {len(all_stubs)} class groups in {len(set(s['py_file'] for s in all_stubs))} files\n")

    if args.apply or args.dry_run:
        by_file = defaultdict(list)
        for s in all_stubs:
            by_file[s["kt_file"]].append(s)

        for kt_file, file_stubs in sorted(by_file.items()):
            all_stubs_for_file = []
            for s in file_stubs:
                all_stubs_for_file.extend(s["stubs"])

            if args.dry_run:
                print(f"  Would insert {len(all_stubs_for_file)} stubs into {kt_file}")
            elif args.apply:
                current = insert_stubs_into_file(kt_file, all_stubs_for_file)
                with open(kt_file, "w") as f:
                    f.write(current)
                print(f"  ✅ Inserted {len(all_stubs_for_file)} stubs into {kt_file}")
    else:
        for s in sorted(all_stubs, key=lambda x: -x["missing_count"])[:15]:
            print(f"  {s['py_file']:<45} {s['py_class']:<30} {s['missing_count']:>3} stubs")
            for stub in s["stubs"][:2]:
                for line in stub.split("\n"):
                    if "fun " in line:
                        print(f"    {line.strip()}")
            if s["missing_count"] > 2:
                print(f"    ... and {s['missing_count'] - 2} more")
            print()


if __name__ == "__main__":
    main()
