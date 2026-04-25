#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import re
import sys
import xml.etree.ElementTree as ET

if sys.platform == "win32":
    import io

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")


LANG_DIR_RE = re.compile(r"^values-([a-z]{2,3})(?:-r([A-Z0-9]{2,3}))?$")
LANG_LABELS = {
    "zh": "中文",
    "en": "英文",
    "es": "西班牙语",
    "pt-BR": "葡萄牙语(巴西)",
    "ms": "马来语",
    "id": "印尼语",
}


def _repo_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _dir_to_language_code(dir_name: str):
    if dir_name == "values":
        return "zh"
    match = LANG_DIR_RE.fullmatch(dir_name)
    if not match:
        return None
    language = match.group(1).lower()
    region = match.group(2)
    if language == "in":
        language = "id"
    return f"{language}-{region.upper()}" if region else language


def _language_label(language_code: str) -> str:
    return LANG_LABELS.get(language_code, language_code)


def _discover_files(repo_root: str):
    res_dir = os.path.join(repo_root, "app", "src", "main", "res")
    files = {}

    default_file = os.path.join(res_dir, "values", "strings.xml")
    if os.path.exists(default_file):
        files["zh"] = default_file

    if not os.path.isdir(res_dir):
        return files

    for entry in sorted(os.listdir(res_dir)):
        full_dir = os.path.join(res_dir, entry)
        if not os.path.isdir(full_dir):
            continue
        code = _dir_to_language_code(entry)
        if code in (None, "zh"):
            continue
        file_path = os.path.join(full_dir, "strings.xml")
        if os.path.exists(file_path):
            files[code] = file_path

    return files


def parse_strings_file(filepath):
    strings_dict = {}
    duplicates = []

    if not os.path.exists(filepath):
        print(f"[X] 文件不存在: {filepath}")
        return strings_dict, duplicates

    try:
        tree = ET.parse(filepath)
        root = tree.getroot()

        for string_elem in root.findall("string"):
            name = string_elem.get("name")
            if not name:
                continue
            if name in strings_dict:
                duplicates.append(name)
            strings_dict[name] = string_elem.text or ""

    except Exception as e:
        print(f"[X] 解析文件失败 {filepath}: {e}")

    return strings_dict, duplicates


def _group_missing_keys(missing):
    categories = {}
    for key in missing:
        if "_" in key:
            prefix = key.split("_", 1)[0]
        else:
            prefix = "other"
        categories.setdefault(prefix, []).append(key)
    return categories


def main():
    repo_root = _repo_root()
    files = _discover_files(repo_root)

    simple_mode = len(sys.argv) > 1 and sys.argv[1] == "--simple"

    print("Android Strings.xml 检查结果")
    print("=" * 50)

    if not files:
        print("[X] 未找到任何 strings.xml")
        return

    all_data = {}
    all_keys = set()
    all_duplicates = {}
    total_duplicates = 0

    for language_code, filepath in files.items():
        language_name = _language_label(language_code)
        print(f"正在解析 {language_name} ({language_code}): {filepath}")
        data, duplicates = parse_strings_file(filepath)
        all_data[language_code] = data
        all_keys.update(data.keys())
        all_duplicates[language_code] = duplicates
        total_duplicates += len(duplicates)
        print(f"{language_name}: {len(data)} 个字符串, {len(duplicates)} 个重复项")

    print(f"\n总计: {len(all_keys)} 个唯一字符串键")
    print(f"总重复项: {total_duplicates}")

    if not simple_mode:
        print("\n" + "=" * 50)
        print("重复项详情:")
        for language_code, duplicates in all_duplicates.items():
            language_name = _language_label(language_code)
            if duplicates:
                print(f"\n[X] {language_name} 重复项 ({len(duplicates)}个):")
                for dup in duplicates:
                    print(f"   - {dup}")
            else:
                print(f"\n[OK] {language_name}: 无重复项")

        print("\n" + "=" * 50)
        print("缺失项详情:")

    total_missing = 0
    for language_code, data in all_data.items():
        language_name = _language_label(language_code)
        missing = sorted(all_keys - set(data.keys()))
        total_missing += len(missing)
        if missing:
            if simple_mode:
                print(f"[X] {language_name}: 缺少 {len(missing)} 个字符串")
            else:
                print(f"\n[X] {language_name} 缺失项 ({len(missing)}个):")
                categories = _group_missing_keys(missing)
                for category, keys in sorted(categories.items()):
                    print(f"   [{category}]:")
                    for key in keys[:10]:
                        print(f"     - {key}")
                    if len(keys) > 10:
                        print(f"     ... 还有 {len(keys) - 10} 个")
        else:
            if simple_mode:
                print(f"[OK] {language_name}: 完整")
            else:
                print(f"\n[OK] {language_name}: 完整")

    print(f"\n总缺失项: {total_missing}")

    if total_duplicates == 0 and total_missing == 0:
        print("\n[SUCCESS] 所有语言文件都已完整且无重复项！")
    else:
        print(f"\n[WARNING] 还需修复: {total_duplicates} 个重复项 + {total_missing} 个缺失项")


if __name__ == "__main__":
    main()
