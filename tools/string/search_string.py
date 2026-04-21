#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import argparse
import os
import sys
import xml.etree.ElementTree as ET

if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def _repo_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _default_files(repo_root: str) -> dict:
    return {
        "zh": os.path.join(repo_root, "app", "src", "main", "res", "values", "strings.xml"),
        "en": os.path.join(repo_root, "app", "src", "main", "res", "values-en", "strings.xml"),
    }


def _iter_strings(file_path: str):
    tree = ET.parse(file_path)
    root = tree.getroot()
    for e in root.findall("string"):
        name = e.get("name")
        value = e.text or ""
        yield name, value


def main() -> int:
    parser = argparse.ArgumentParser(prog="search_string.py")
    parser.add_argument("--lang", choices=["zh", "en"], help="target language file")
    parser.add_argument("--file", help="explicit strings.xml path")
    parser.add_argument("--name", help="match key (substring, case-insensitive)")
    parser.add_argument("--value", help="match value (substring, case-insensitive)")
    args = parser.parse_args()

    if not args.name and not args.value:
        print("[X] need --name and/or --value")
        return 2

    repo_root = _repo_root()
    defaults = _default_files(repo_root)

    file_paths = []
    if args.file:
        file_paths = [args.file]
    elif args.lang:
        file_paths = [defaults[args.lang]]
    else:
        file_paths = [defaults["zh"], defaults["en"]]

    name_q = (args.name or "").lower()
    value_q = (args.value or "").lower()

    total = 0
    for file_path in file_paths:
        if not os.path.exists(file_path):
            print(f"[X] file not found: {file_path}")
            continue

        try:
            matches = []
            for name, value in _iter_strings(file_path):
                if not name:
                    continue
                if name_q and name_q not in name.lower():
                    continue
                if value_q and value_q not in (value or "").lower():
                    continue
                matches.append((name, value))

            if matches:
                print(file_path)
                for name, value in matches:
                    print(f"  {name} = {value}")
                total += len(matches)
        except Exception as e:
            print(f"[X] failed to parse: {file_path}: {e}")

    print(f"\nTotal matches: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
