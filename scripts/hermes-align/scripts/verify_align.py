#!/usr/bin/env python3
"""验证 Hermes Agent → hermes-android 的对齐度。

双端独立扫描，不完全依赖映射表。
1. 扫描 Hermes 上游 .py 文件（排除 exceptions 中的目录/文件）
2. 扫描 Android 端 .kt 文件
3. 用 normalized name 做文件匹配
4. 对已匹配的文件，用映射表做方法/类/常量级对齐检查

用法：
    python3 verify_align.py --hermes hermes-agent/ --android hermes-android/src/main/java/.../hermes/
    python3 verify_align.py --hermes hermes-agent/ --android ... --mapping mapping.json --json
"""

import argparse
import json
import re
import sys
from pathlib import Path


# ─── 名称归一化 ─────────────────────────────────────────────

def normalize_name(name: str) -> str:
    """把 snake_case / PascalCase / camelCase 统一为纯小写无分隔符。
    batch_runner → batchrunner
    BatchRunner → batchrunner
    batchRunner → batchrunner
    """
    # 先在大写字母前插下划线（拆 PascalCase）
    s = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', name)
    return s.lower().replace('_', '')


def stem_py(filename: str) -> str:
    return normalize_name(filename.replace('.py', ''))


def stem_kt(filename: str) -> str:
    return normalize_name(filename.replace('.kt', ''))


# ─── Python 端扫描 ──────────────────────────────────────────

def scan_py_files(hermes_root: Path, exceptions: dict) -> dict[str, Path]:
    """扫描上游 Python 文件。返回 {normalized_stem: relative_path}"""
    skip_dirs = set(exceptions.get('skip_dirs', []))
    skip_files = set(exceptions.get('skip_files', []))

    results = {}
    for py_file in sorted(hermes_root.rglob('*.py')):
        if py_file.name.startswith('__'):
            continue
        if py_file.name in skip_files:
            continue

        rel = py_file.relative_to(hermes_root)
        parts = rel.parts
        if any(p.startswith('.') or p == '__pycache__' or p == 'node_modules' for p in parts):
            continue

        rel_dir = str(rel.parent) if rel.parent != Path('.') else ''
        skip = False
        for sd in skip_dirs:
            if rel_dir == sd or rel_dir.startswith(sd + '/'):
                skip = True
                break
        if skip:
            continue

        norm = stem_py(py_file.name)
        results[norm] = rel
    return results


# ─── Kotlin 端扫描 ──────────────────────────────────────────

def scan_kt_files(android_root: Path) -> dict[str, list[Path]]:
    """扫描 Android Kotlin 文件。返回 {normalized_stem: [relative_paths]}"""
    results = {}
    for kt_file in sorted(android_root.rglob('*.kt')):
        if 'Test' in kt_file.stem and kt_file.stem.endswith('Test'):
            continue
        norm = stem_kt(kt_file.name)
        if norm not in results:
            results[norm] = []
        results[norm].append(kt_file.relative_to(android_root))
    return results


# ─── Kotlin 方法提取（改进版正则）──────────────────────────

def extract_kt_methods(kt_path: Path) -> set[str]:
    """从 .kt 文件提取方法名集合。处理多行、泛型、扩展函数。"""
    if not kt_path.exists():
        return set()
    content = kt_path.read_text(encoding='utf-8', errors='ignore')
    methods = set()

    # 匹配 fun 声明，兼容：
    #   fun foo(...)
    #   suspend fun foo(...)
    #   override fun foo(...)
    #   fun <T> foo(...)
    #   fun String.foo(...)
    #   private fun foo(...)
    pattern = re.compile(
        r'(?:(?:suspend|override|private|internal|protected|public|actual|expect|inline|operator|infix|tailrec)\s+)*'
        r'fun\s+'
        r'(?:<[^>]+>\s+)?'         # 泛型 <T>
        r'(?:\w+\.\s*)?'           # 扩展接收者 String.
        r'(\w+)'                   # 方法名
        r'\s*\(',                   # (
    )
    for m in pattern.finditer(content):
        methods.add(m.group(1))
    return methods


def extract_kt_classes(kt_path: Path) -> set[str]:
    """从 .kt 文件提取类/接口/对象名集合。"""
    if not kt_path.exists():
        return set()
    content = kt_path.read_text(encoding='utf-8', errors='ignore')
    classes = set()
    pattern = re.compile(
        r'(?:(?:abstract|data|sealed|enum|inner|value|open|private|internal|actual|expect)\s+)*'
        r'(?:class|interface|object)\s+'
        r'(\w+)'
    )
    for m in pattern.finditer(content):
        classes.add(m.group(1))
    return classes


def extract_kt_constants(kt_path: Path) -> set[str]:
    """从 .kt 文件提取 UPPER_SNAKE_CASE 常量。"""
    if not kt_path.exists():
        return set()
    content = kt_path.read_text(encoding='utf-8', errors='ignore')
    consts = set()
    pattern = re.compile(r'(?:const\s+)?val\s+([A-Z][A-Z0-9_]+)\s*[=:]')
    for m in pattern.finditer(content):
        consts.add(m.group(1))
    return consts


# ─── 对齐验证主逻辑 ─────────────────────────────────────────

def verify(hermes_root: Path, android_root: Path, exceptions: dict, mapping: list[dict] | None) -> dict:
    py_files = scan_py_files(hermes_root, exceptions)
    kt_files = scan_kt_files(android_root)

    report = {
        'files': {
            'hermes_total': len(py_files),
            'android_total': sum(len(v) for v in kt_files.values()),
            'matched': 0,
            'missing_in_android': [],
            'extra_in_android': [],
        },
        'classes': {'total': 0, 'matched': 0, 'missing': []},
        'methods': {'total': 0, 'matched': 0, 'missing': []},
        'constants': {'total': 0, 'matched': 0, 'missing': []},
        'score': 0.0,
    }

    # ── 文件级对齐 ──
    matched_pairs = []  # [(py_norm, py_rel, kt_rel)]
    for norm, py_rel in py_files.items():
        if norm in kt_files:
            report['files']['matched'] += 1
            # 用第一个匹配的 kt 文件
            matched_pairs.append((norm, py_rel, kt_files[norm][0]))
        else:
            report['files']['missing_in_android'].append(str(py_rel))

    for norm, kt_paths in kt_files.items():
        if norm not in py_files:
            for kt_p in kt_paths:
                report['files']['extra_in_android'].append(str(kt_p))

    # ── 方法/类/常量级对齐（需要映射表）──
    if mapping:
        # 构建 {normalized_hermes_file: mapping_entry} 索引
        mapping_index = {}
        for entry in mapping:
            hf = entry.get('hermes_file', '')
            norm = stem_py(Path(hf).name)
            mapping_index[norm] = entry

        for norm, py_rel, kt_rel in matched_pairs:
            entry = mapping_index.get(norm)
            if not entry:
                continue

            kt_full = android_root / kt_rel

            # 类
            kt_classes = extract_kt_classes(kt_full)
            for cls in entry.get('classes', []):
                report['classes']['total'] += 1
                if cls['kotlin_class'] in kt_classes:
                    report['classes']['matched'] += 1
                else:
                    report['classes']['missing'].append({
                        'hermes_class': cls['hermes_class'],
                        'expected_kt': cls['kotlin_class'],
                        'file': str(py_rel),
                    })

            # 方法（Android 端 _ 前缀风格不统一，两种都试）
            kt_methods = extract_kt_methods(kt_full)
            all_methods = entry.get('module_functions', []) + entry.get('class_methods', [])
            for method in all_methods:
                report['methods']['total'] += 1
                kt_name = method['kotlin_method']
                # 尝试原名 (_pruneOldToolResults) 和去前缀名 (pruneOldToolResults)
                alt_name = kt_name.lstrip('_') if kt_name.startswith('_') else '_' + kt_name
                if kt_name in kt_methods or alt_name in kt_methods:
                    report['methods']['matched'] += 1
                else:
                    report['methods']['missing'].append({
                        'hermes_method': method['hermes_method'],
                        'expected_kt': kt_name,
                        'file': str(py_rel),
                        'class': method.get('hermes_class', ''),
                    })

            # 常量
            kt_consts = extract_kt_constants(kt_full)
            for const in entry.get('constants', []):
                report['constants']['total'] += 1
                if const['kotlin_name'] in kt_consts:
                    report['constants']['matched'] += 1
                else:
                    report['constants']['missing'].append({
                        'hermes_name': const['hermes_name'],
                        'expected_kt': const['kotlin_name'],
                        'file': str(py_rel),
                    })

    # ── 综合评分 ──
    # 文件级权重 40%，方法级 40%，类级 10%，常量级 10%
    file_pct = (report['files']['matched'] / report['files']['hermes_total'] * 100) if report['files']['hermes_total'] else 0

    if mapping and report['methods']['total'] > 0:
        method_pct = report['methods']['matched'] / report['methods']['total'] * 100
        class_pct = (report['classes']['matched'] / report['classes']['total'] * 100) if report['classes']['total'] else 100
        const_pct = (report['constants']['matched'] / report['constants']['total'] * 100) if report['constants']['total'] else 100
        report['score'] = round(file_pct * 0.4 + method_pct * 0.4 + class_pct * 0.1 + const_pct * 0.1, 1)
    else:
        # 没有映射表时只报文件级
        report['score'] = round(file_pct, 1)

    report['file_pct'] = round(file_pct, 1)
    return report


# ─── 打印报告 ────────────────────────────────────────────────

def print_report(report: dict, has_mapping: bool):
    print("=" * 60)
    print("Hermes → Android 对齐度报告")
    print("=" * 60)

    fr = report['files']
    file_pct = report['file_pct']
    s = "✅" if file_pct >= 90 else "⚠️" if file_pct >= 70 else "❌"
    print(f"{s} FILES         {fr['matched']}/{fr['hermes_total']}  ({file_pct}%)")
    print(f"  Android 端共 {fr['android_total']} 个 .kt 文件（含 Android 独有）")

    if has_mapping:
        for cat in ['classes', 'methods', 'constants']:
            d = report[cat]
            total = d['total']
            matched = d['matched']
            pct = (matched / total * 100) if total > 0 else 0
            s = "✅" if pct >= 90 else "⚠️" if pct >= 70 else "❌"
            print(f"{s} {cat.upper():12s}  {matched}/{total}  ({pct:.1f}%)")

    print("-" * 60)
    overall = report['score']
    s = "✅" if overall >= 90 else "⚠️" if overall >= 70 else "❌"
    mode = "加权综合" if has_mapping else "文件级"
    print(f"{s} {mode}对齐度: {overall}%")
    if has_mapping:
        print(f"  (文件40% + 方法40% + 类10% + 常量10%)")

    # 缺失文件
    missing = fr['missing_in_android']
    if missing:
        print(f"\n❌ Android 缺失文件 ({len(missing)} 个):")
        # 按目录分组
        by_dir = {}
        for f in missing:
            d = str(Path(f).parent)
            by_dir.setdefault(d, []).append(Path(f).name)
        for d, files in sorted(by_dir.items()):
            print(f"   [{d}] ({len(files)})")
            for fn in files[:5]:
                print(f"      {fn}")
            if len(files) > 5:
                print(f"      ... +{len(files) - 5}")

    # 缺失方法（前 15 个）
    if has_mapping and report['methods']['missing']:
        mm = report['methods']['missing']
        print(f"\n❌ 缺失方法 ({len(mm)} 个，显示前 15):")
        for m in mm[:15]:
            cls = f"{m['class']}." if m.get('class') else ''
            print(f"   {cls}{m['hermes_method']} → {m['expected_kt']}  ({m['file']})")
        if len(mm) > 15:
            print(f"   ... +{len(mm) - 15}")

    # Android 独有文件
    extra = fr['extra_in_android']
    if extra:
        print(f"\n📎 Android 独有文件 ({len(extra)} 个):")
        for f in extra[:10]:
            print(f"   {f}")
        if len(extra) > 10:
            print(f"   ... +{len(extra) - 10}")


def generate_todo_md(report: dict, output_path: Path):
    """生成未对齐清单 Markdown 文档。"""
    from datetime import datetime
    now = datetime.now().strftime('%Y-%m-%d %H:%M')

    fr = report['files']
    mr = report['methods']
    cr = report['classes']
    kr = report['constants']

    lines = []
    lines.append(f'# hermes-android 未对齐清单')
    lines.append(f'')
    lines.append(f'> 自动生成于 {now} | 加权对齐度: {report["score"]}%')
    lines.append(f'> 文件 {fr["matched"]}/{fr["hermes_total"]} ({report["file_pct"]}%) | '
                 f'方法 {mr["matched"]}/{mr["total"]} ({mr["matched"]/mr["total"]*100:.1f}%) | '
                 f'类 {cr["matched"]}/{cr["total"]} ({cr["matched"]/cr["total"]*100:.1f}%) | '
                 f'常量 {kr["matched"]}/{kr["total"]} ({kr["matched"]/kr["total"]*100:.1f}%)')
    lines.append(f'')

    # ── 缺失文件 ──
    missing_files = fr['missing_in_android']
    if missing_files:
        lines.append(f'## 缺失文件（{len(missing_files)} 个）')
        lines.append(f'')
        lines.append(f'以下上游 Python 文件在 Android 端不存在，需要创建对应 .kt 文件。')
        lines.append(f'')
        by_dir = {}
        for f in missing_files:
            d = str(Path(f).parent) if str(Path(f).parent) != '.' else '(root)'
            by_dir.setdefault(d, []).append(Path(f).name)
        for d, files in sorted(by_dir.items()):
            lines.append(f'### {d}/ ({len(files)} 文件)')
            for fn in sorted(files):
                lines.append(f'- [ ] `{fn}`')
            lines.append(f'')

    # ── 缺失方法（按文件分组）──
    missing_methods = mr['missing']
    if missing_methods:
        by_file = {}
        for m in missing_methods:
            by_file.setdefault(m['file'], []).append(m)
        # 按缺失数降序
        sorted_files = sorted(by_file.items(), key=lambda x: -len(x[1]))

        lines.append(f'## 缺失方法（{len(missing_methods)} 个，按文件分组）')
        lines.append(f'')
        for f, methods in sorted_files:
            lines.append(f'### `{f}` — 缺 {len(methods)} 个方法')
            lines.append(f'')
            lines.append(f'| Python 方法 | 期望 Kotlin 方法 | 所属类 |')
            lines.append(f'|-------------|-----------------|--------|')
            for m in methods:
                cls = m.get('class', '') or '-'
                lines.append(f'| `{m["hermes_method"]}` | `{m["expected_kt"]}` | {cls} |')
            lines.append(f'')

    # ── 缺失类 ──
    missing_classes = cr['missing']
    if missing_classes:
        lines.append(f'## 缺失类（{len(missing_classes)} 个）')
        lines.append(f'')
        lines.append(f'| 上游 Python 类 | 期望 Kotlin 类 | 文件 |')
        lines.append(f'|---------------|---------------|------|')
        for c in missing_classes:
            lines.append(f'| `{c["hermes_class"]}` | `{c["expected_kt"]}` | `{c["file"]}` |')
        lines.append(f'')

    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f'\n📝 未对齐清单已写入: {output_path}')


def main():
    parser = argparse.ArgumentParser(description='验证 Hermes → Android 对齐度')
    parser.add_argument('--hermes', required=True, help='Hermes 项目根目录')
    parser.add_argument('--android', required=True, help='Android hermes-android 源码根目录')
    parser.add_argument('--mapping', help='映射表 JSON（可选，有则做方法级检查）')
    parser.add_argument('--exceptions', help='例外配置 JSON（可选）')
    parser.add_argument('--json', action='store_true', help='输出 JSON')
    parser.add_argument('--output-todo', help='输出未对齐清单 Markdown 文件路径')
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
        default_exc = Path(__file__).parent.parent / 'references' / 'exceptions.json'
        if default_exc.exists():
            exc_path = str(default_exc)
    if exc_path:
        with open(exc_path, encoding='utf-8') as f:
            exceptions = json.load(f)

    # 加载映射表（可选）
    mapping = None
    if args.mapping:
        mp = Path(args.mapping)
        if mp.exists():
            with open(mp, encoding='utf-8') as f:
                mapping = json.load(f)
        else:
            print(f"Warning: {mp} 不存在，仅做文件级检查", file=sys.stderr)

    report = verify(hermes_root, android_root, exceptions, mapping)

    if args.json:
        # JSON 输出时精简 missing 列表
        out = {**report}
        out['files'] = {**report['files']}
        out['files']['missing_in_android'] = report['files']['missing_in_android'][:50]
        out['files']['extra_in_android'] = report['files']['extra_in_android'][:20]
        if 'methods' in out and 'missing' in out['methods']:
            out['methods'] = {**report['methods']}
            out['methods']['missing'] = report['methods']['missing'][:50]
        print(json.dumps(out, indent=2, ensure_ascii=False))
    else:
        print_report(report, has_mapping=mapping is not None)

    # 输出未对齐清单
    if args.output_todo and mapping:
        generate_todo_md(report, Path(args.output_todo))

    sys.exit(0 if report['score'] >= 90 else 1)


if __name__ == '__main__':
    main()
