#!/usr/bin/env python3
"""Scan hermes-android for unimplemented stubs and generate a report."""

import os
import re
import json
from collections import defaultdict

KBASE = "AndroidForClaw/hermes-android/src/main/java/com/xiaomo/hermes/hermes"

EMPTY_PATTERNS = [
    r'return null',
    r'return emptyList',
    r'return emptyMap',
    r'return emptySet',
    r'return ""',
    r'return 0',
    r'return false',
    r'return Unit',
    r'// TODO:',
    r'throw NotImplementedError',
]

def is_stub(body):
    """Check if a method body is an empty stub."""
    lines = [l.strip() for l in body.strip().split('\n') if l.strip()]
    if not lines:
        return True
    # Single-line body matching empty pattern
    if len(lines) == 1:
        for pat in EMPTY_PATTERNS:
            if re.search(pat, lines[0]):
                return True
    # Multi-line but all lines are empty returns or TODOs
    if all(any(re.search(pat, l) for pat in EMPTY_PATTERNS) for l in lines):
        return True
    return False

def scan_file(filepath):
    """Scan a single Kotlin file for stub methods."""
    with open(filepath, 'r') as f:
        content = f.read()

    # Match method signatures and bodies
    # Pattern: optional suspend, fun name(...): ReturnType { body }
    methods = []

    # Find all function declarations
    pattern = r'(?:(?:private|protected|public|internal)\s+)?(?:suspend\s+)?fun\s+(\w+)\s*\([^)]*\)(?:\s*:\s*([^\n{]+?))?\s*\{'

    for match in re.finditer(pattern, content):
        name = match.group(1)
        return_type = match.group(2).strip() if match.group(2) else "Unit"
        start = match.end()

        # Find matching closing brace (balance braces)
        depth = 1
        pos = start
        while pos < len(content) and depth > 0:
            if content[pos] == '{':
                depth += 1
            elif content[pos] == '}':
                depth -= 1
            pos += 1

        body = content[start:pos-1]

        methods.append({
            'name': name,
            'return_type': return_type,
            'body_lines': len([l for l in body.strip().split('\n') if l.strip()]),
            'is_stub': is_stub(body),
            'body_preview': body.strip()[:80],
        })

    return methods

def main():
    total_methods = 0
    total_stubs = 0
    total_implemented = 0
    file_results = []

    for root, dirs, files in os.walk(KBASE):
        # Skip build directories
        dirs[:] = [d for d in dirs if d != 'build']

        for fname in sorted(files):
            if not fname.endswith('.kt'):
                continue

            filepath = os.path.join(root, fname)
            relpath = os.path.relpath(filepath, KBASE)

            methods = scan_file(filepath)
            stubs = [m for m in methods if m['is_stub']]
            implemented = [m for m in methods if not m['is_stub']]

            total_methods += len(methods)
            total_stubs += len(stubs)
            total_implemented += len(implemented)

            file_results.append({
                'file': relpath,
                'total': len(methods),
                'stubs': len(stubs),
                'implemented': len(implemented),
                'stub_methods': [m['name'] for m in stubs],
            })

    # Sort by stub count descending
    file_results.sort(key=lambda x: x['stubs'], reverse=True)

    # Print summary
    print("=" * 60)
    print(f"hermes-android Stub Scan Report")
    print("=" * 60)
    print(f"Files scanned:  {len(file_results)}")
    print(f"Total methods:  {total_methods}")
    print(f"Implemented:    {total_implemented} ({total_implemented*100//max(total_methods,1)}%)")
    print(f"Empty stubs:    {total_stubs} ({total_stubs*100//max(total_methods,1)}%)")
    print()

    # Print files with stubs
    print(f"{'File':<35} {'Total':>5} {'Stubs':>5} {'Done%':>6}")
    print("-" * 55)
    for f in file_results:
        if f['stubs'] > 0:
            pct = f['implemented'] * 100 // max(f['total'], 1)
            print(f"{f['file']:<35} {f['total']:>5} {f['stubs']:>5} {pct:>5}%")

    print()
    print("=== Stub method names by file ===")
    for f in file_results:
        if f['stubs'] > 0:
            print(f"\n📄 {f['file']} ({f['stubs']} stubs):")
            for name in f['stub_methods']:
                print(f"   - {name}")

    # Write JSON for programmatic use
    report = {
        'summary': {
            'files': len(file_results),
            'total_methods': total_methods,
            'implemented': total_implemented,
            'stubs': total_stubs,
            'pct_implemented': round(total_implemented * 100 / max(total_methods, 1), 1),
        },
        'files': file_results,
    }
    report_path = os.path.join(os.path.dirname(KBASE), 'stub-report.json')
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, 'w') as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\nJSON report: {report_path}")

if __name__ == '__main__':
    main()
