#!/usr/bin/env python3
"""生成 Hermes Agent → hermes-android 的完整映射表。

递归扫描 hermes-agent/ 的所有 Python 文件，提取文件、类、方法、常量，
生成对应的 Kotlin 映射。

用法：
    python3 gen_mapping.py --hermes hermes-agent/ --output mapping.json
    python3 gen_mapping.py --hermes hermes-agent/ --output mapping.json --exceptions exceptions.json
"""

import argparse
import ast
import json
import sys
from pathlib import Path


def snake_to_camel(name: str) -> str:
    """snake_case → camelCase，保留前导下划线。
    _prune_old_tool_results → _pruneOldToolResults
    __init__ → __init__
    get_status → getStatus
    """
    # 提取前导下划线
    prefix = ''
    stripped = name
    while stripped.startswith('_'):
        prefix += '_'
        stripped = stripped[1:]
    if not stripped:
        return name  # 纯下划线
    parts = stripped.split('_')
    result = parts[0] + ''.join(p.capitalize() for p in parts[1:])
    return prefix + result


def snake_to_pascal(name: str) -> str:
    """snake_case → PascalCase"""
    parts = name.lstrip('_').split('_')
    return ''.join(p.capitalize() for p in parts)


def filename_to_kotlin(py_filename: str) -> str:
    """batch_runner.py → BatchRunner.kt"""
    return snake_to_pascal(py_filename.replace('.py', '')) + '.kt'


def extract_classes(tree: ast.Module) -> list[dict]:
    """提取所有顶级和嵌套类"""
    results = []

    def _visit(node, parent_class=None):
        for child in ast.iter_child_nodes(node):
            if isinstance(child, ast.ClassDef):
                results.append({
                    'name': child.name,
                    'parent_class': parent_class,
                    'line': child.lineno,
                })
                _visit(child, parent_class=child.name)

    _visit(tree)
    return results


def extract_methods(tree: ast.Module) -> list[dict]:
    """提取所有函数/方法，正确归属父类"""
    results = []

    def _visit(node, parent_class=None):
        for child in ast.iter_child_nodes(node):
            if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)):
                args = [a.arg for a in child.args.args if a.arg != 'self' and a.arg != 'cls']
                results.append({
                    'name': child.name,
                    'parent_class': parent_class,
                    'line': child.lineno,
                    'args': args,
                    'is_async': isinstance(child, ast.AsyncFunctionDef),
                    'is_private': child.name.startswith('_') and not child.name.startswith('__'),
                    'is_dunder': child.name.startswith('__') and child.name.endswith('__'),
                })
                # 不递归进函数内部的嵌套函数
            elif isinstance(child, ast.ClassDef):
                _visit(child, parent_class=child.name)

    _visit(tree)
    return results


def extract_constants(tree: ast.Module) -> list[dict]:
    """提取模块级常量（UPPER_SNAKE_CASE 赋值）"""
    results = []
    for node in ast.iter_child_nodes(tree):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id.isupper():
                    results.append({
                        'name': target.id,
                        'line': node.lineno,
                    })
    return results


def process_file(py_file: Path, hermes_root: Path) -> dict | None:
    """处理单个 Python 文件"""
    rel_path = str(py_file.relative_to(hermes_root))

    try:
        source = py_file.read_text(encoding='utf-8')
        tree = ast.parse(source, filename=str(py_file))
    except (SyntaxError, UnicodeDecodeError) as e:
        return {
            'hermes_file': rel_path,
            'kotlin_file': filename_to_kotlin(py_file.name),
            'error': str(e),
        }

    classes = extract_classes(tree)
    methods = extract_methods(tree)
    constants = extract_constants(tree)

    module_functions = [m for m in methods if m['parent_class'] is None and not m['is_dunder']]
    class_methods = [m for m in methods if m['parent_class'] is not None and not m['is_dunder']]

    return {
        'hermes_file': rel_path,
        'hermes_dir': str(py_file.relative_to(hermes_root).parent) if py_file.parent != hermes_root else '',
        'kotlin_file': filename_to_kotlin(py_file.name),
        'classes': [
            {
                'hermes_class': c['name'],
                'kotlin_class': c['name'],
                'parent_class': c.get('parent_class'),
                'line': c['line'],
            }
            for c in classes
        ],
        'module_functions': [
            {
                'hermes_method': f['name'],
                'kotlin_method': snake_to_camel(f['name']),
                'line': f['line'],
                'args': f['args'],
                'is_async': f['is_async'],
                'is_private': f['is_private'],
            }
            for f in module_functions
        ],
        'class_methods': [
            {
                'hermes_class': m['parent_class'],
                'hermes_method': m['name'],
                'kotlin_method': snake_to_camel(m['name']) if not m['is_dunder'] else m['name'],
                'line': m['line'],
                'args': m['args'],
                'is_async': m['is_async'],
                'is_private': m['is_private'],
            }
            for m in class_methods
        ],
        'constants': [
            {
                'hermes_name': c['name'],
                'kotlin_name': c['name'],
                'line': c['line'],
            }
            for c in constants
        ],
    }


def scan_hermes(hermes_root: Path, exceptions: dict) -> list[dict]:
    """递归扫描 Hermes 项目"""
    skip_dirs = set(exceptions.get('skip_dirs', []))
    skip_files = set(exceptions.get('skip_files', []))

    results = []
    for py_file in sorted(hermes_root.rglob('*.py')):
        # 跳过 __init__.py 和 __pycache__
        if py_file.name.startswith('__'):
            continue
        if py_file.name in skip_files:
            continue

        # 计算相对目录
        rel = py_file.relative_to(hermes_root)
        rel_dir = str(rel.parent) if rel.parent != Path('.') else ''

        # 检查是否在跳过的目录下
        skip = False
        for sd in skip_dirs:
            if rel_dir == sd or rel_dir.startswith(sd + '/') or rel_dir.startswith(sd + '\\'):
                skip = True
                break
        if skip:
            continue

        # 跳过 .git / .venv / __pycache__
        parts = rel.parts
        if any(p.startswith('.') or p == '__pycache__' or p == 'node_modules' for p in parts):
            continue

        entry = process_file(py_file, hermes_root)
        if entry:
            results.append(entry)

    return results


def main():
    parser = argparse.ArgumentParser(description='生成 Hermes → Android 映射表')
    parser.add_argument('--hermes', required=True, help='Hermes 项目根目录')
    parser.add_argument('--output', required=True, help='输出 JSON 文件路径')
    parser.add_argument('--exceptions', help='例外配置 JSON（可选）')
    args = parser.parse_args()

    hermes_root = Path(args.hermes).resolve()
    if not hermes_root.exists():
        print(f"Error: {hermes_root} does not exist", file=sys.stderr)
        sys.exit(1)

    # 加载 exceptions
    exceptions = {}
    exceptions_path = args.exceptions
    if not exceptions_path:
        default_exc = Path(__file__).parent.parent / 'references' / 'exceptions.json'
        if default_exc.exists():
            exceptions_path = str(default_exc)
    if exceptions_path:
        with open(exceptions_path, encoding='utf-8') as f:
            exceptions = json.load(f)

    results = scan_hermes(hermes_root, exceptions)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2, ensure_ascii=False)

    # 统计
    total_files = len(results)
    total_classes = sum(len(r.get('classes', [])) for r in results)
    total_methods = sum(len(r.get('module_functions', [])) + len(r.get('class_methods', [])) for r in results)
    total_constants = sum(len(r.get('constants', [])) for r in results)
    errors = sum(1 for r in results if 'error' in r)

    print(f"✅ 映射表已生成: {output_path}")
    print(f"   文件: {total_files}")
    print(f"   类: {total_classes}")
    print(f"   方法/函数: {total_methods}")
    print(f"   常量: {total_constants}")
    if errors:
        print(f"   ⚠️ 解析错误: {errors}")

    # 按目录统计
    by_dir = {}
    for r in results:
        d = r.get('hermes_dir', '') or '(root)'
        by_dir.setdefault(d, 0)
        by_dir[d] += 1
    print("\n   按目录:")
    for d, cnt in sorted(by_dir.items(), key=lambda x: -x[1]):
        print(f"   {cnt:4d}  {d}")


if __name__ == '__main__':
    main()
