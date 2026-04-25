from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

MANIFEST_FILENAMES = ("manifest.hjson", "manifest.json")
SYNCABLE_SUFFIXES = {".js", ".toolpkg"}


@dataclass(frozen=True)
class SyncPlanItem:
    mode: str  # copy | pack
    source: Path
    destination_name: str


def _run_checked_command(command: list[str], cwd: Path, *, dry_run: bool) -> None:
    command_text = subprocess.list2cmdline(command)
    if dry_run:
        print(f"DRY-RUN-CMD: (cd {cwd}) {command_text}")
        return

    print(f"RUN-CMD: (cd {cwd}) {command_text}")
    completed = subprocess.run(command, cwd=str(cwd))
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed with exit code {completed.returncode}: {command_text}")


def _prebuild_examples(repo_root: Path, examples_dir: Path, *, dry_run: bool) -> None:
    root_tsconfig = examples_dir / "tsconfig.json"
    if not root_tsconfig.is_file():
        raise FileNotFoundError(f"Missing tsconfig.json: {root_tsconfig}")

    _run_checked_command(
        ["pnpm", "exec", "tsc", "-p", str(root_tsconfig)],
        cwd=repo_root,
        dry_run=dry_run,
    )

    child_dirs = sorted((p for p in examples_dir.iterdir() if p.is_dir()), key=lambda p: p.name.lower())
    for child_dir in child_dirs:
        if child_dir.name == "types":
            print(f"SKIP-TYPES: {child_dir}")
            continue

        tsconfig = child_dir / "tsconfig.json"
        if not tsconfig.is_file():
            raise FileNotFoundError(f"Missing tsconfig.json: {tsconfig}")

        _run_checked_command(
            ["pnpm", "exec", "tsc", "-p", str(tsconfig)],
            cwd=repo_root,
            dry_run=dry_run,
        )

        manifest = child_dir / "manifest.json"
        if manifest.is_file():
            print(f"SKIP-BUILD(MANIFEST): {child_dir}")
            continue

        package_json = child_dir / "package.json"
        if package_json.is_file():
            _run_checked_command(
                ["pnpm", "build"],
                cwd=child_dir,
                dry_run=dry_run,
            )


def _read_whitelist_file(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(str(path))

    if path.suffix.lower() == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, list) or not all(isinstance(x, str) for x in data):
            raise ValueError(f"Whitelist json must be a string array: {path}")
        return [x.strip() for x in data if x.strip()]

    items: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("#"):
            continue
        items.append(s)
    return items


def _normalize_item(item: str) -> str:
    return item.strip().replace("\\", "/").strip().strip("/")


def _default_whitelist(packages_dir: Path) -> list[str]:
    if not packages_dir.exists():
        return []

    names: list[str] = []
    for p in packages_dir.iterdir():
        if not p.is_file():
            continue
        if p.suffix.lower() not in SYNCABLE_SUFFIXES:
            continue
        names.append(p.name)
    return sorted(names)


def _find_manifest_file(folder: Path) -> Path | None:
    for file_name in MANIFEST_FILENAMES:
        manifest = folder / file_name
        if manifest.is_file():
            return manifest
    return None


def _resolve_existing_path(path: Path) -> SyncPlanItem | None:
    if path.is_file() and path.suffix.lower() in SYNCABLE_SUFFIXES:
        return SyncPlanItem(mode="copy", source=path, destination_name=path.name)

    if path.is_dir() and _find_manifest_file(path):
        return SyncPlanItem(mode="pack", source=path, destination_name=f"{path.name}.toolpkg")

    return None


def _resolve_plan_item(examples_dir: Path, item: str) -> SyncPlanItem | None:
    normalized = _normalize_item(item)
    if not normalized:
        return None

    stem = normalized
    lower_stem = stem.lower()
    if lower_stem.endswith(".js"):
        stem = stem[:-3]
    elif lower_stem.endswith(".toolpkg"):
        stem = stem[:-8]

    stem = stem.rstrip("/")

    # Prefer manifest folder over flat .js/.toolpkg with the same stem.
    if stem:
        folder_candidate = examples_dir / stem
        if folder_candidate.is_dir() and _find_manifest_file(folder_candidate):
            return SyncPlanItem(mode="pack", source=folder_candidate, destination_name=f"{folder_candidate.name}.toolpkg")

    direct_path = examples_dir / normalized
    direct_result = _resolve_existing_path(direct_path)
    if direct_result is not None:
        return direct_result

    if not stem:
        return None

    js_candidate = examples_dir / f"{stem}.js"
    if js_candidate.is_file():
        return SyncPlanItem(mode="copy", source=js_candidate, destination_name=js_candidate.name)

    toolpkg_candidate = examples_dir / f"{stem}.toolpkg"
    if toolpkg_candidate.is_file():
        return SyncPlanItem(mode="copy", source=toolpkg_candidate, destination_name=toolpkg_candidate.name)

    return None


def _resolve_plan_item_from_roots(source_roots: list[Path], item: str) -> SyncPlanItem | None:
    for source_root in source_roots:
        plan = _resolve_plan_item(source_root, item)
        if plan is not None:
            return plan
    return None


def _iter_files_for_pack(repo_root: Path, folder: Path) -> list[Path]:
    folder_rel = folder.relative_to(repo_root).as_posix()
    completed = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", folder_rel],
        cwd=str(repo_root),
        capture_output=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"git ls-files failed for: {folder_rel}")

    files: list[Path] = []
    seen: set[Path] = set()
    for raw_path in completed.stdout.split(b"\x00"):
        if not raw_path:
            continue
        repo_relative = Path(raw_path.decode("utf-8"))
        file_path = repo_root / repo_relative
        if not file_path.is_file():
            continue
        if file_path in seen:
            continue
        seen.add(file_path)
        files.append(file_path)

    files.sort(key=lambda x: x.relative_to(folder).as_posix())
    return files


def _pack_toolpkg_folder(repo_root: Path, source_folder: Path, destination_file: Path) -> None:
    if _find_manifest_file(source_folder) is None:
        raise ValueError(f"Missing manifest.hjson or manifest.json: {source_folder}")

    destination_file.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination_file, mode="w", compression=zipfile.ZIP_DEFLATED) as zf:
        for file_path in _iter_files_for_pack(repo_root, source_folder):
            arcname = file_path.relative_to(source_folder).as_posix()
            zf.write(file_path, arcname)


def main() -> int:
    repo_root = Path(__file__).resolve().parent
    examples_dir = repo_root / "examples"
    packages_dir = repo_root / "app" / "src" / "main" / "assets" / "packages"
    default_whitelist_file = repo_root / "packages_whitelist.txt"
    source_roots = [examples_dir, repo_root]

    parser = argparse.ArgumentParser(
        description=(
            "Sync packages from examples/ into app/src/main/assets/packages/. "
            "If an item maps to a folder that has manifest.hjson/manifest.json, it is packed as .toolpkg; "
            "otherwise .js/.toolpkg files are copied directly."
        )
    )
    parser.add_argument(
        "--whitelist",
        type=str,
        default=None,
        help=(
            "Path to whitelist file (.txt or .json). If omitted, will use packages_whitelist.txt if it exists; "
            "otherwise uses current files in app/src/main/assets/packages/ as the whitelist."
        ),
    )
    parser.add_argument(
        "--include",
        action="append",
        default=[],
        help=(
            "Add an extra item to whitelist (e.g. github.js, github, windows_control, or a manifest folder path). "
            "Can be provided multiple times."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be copied/packed without writing files.",
    )
    parser.add_argument(
        "--delete-extra",
        action="store_true",
        help="Delete *.js and *.toolpkg in assets/packages that are not in the resolved whitelist outputs.",
    )

    args = parser.parse_args()

    if not examples_dir.exists():
        print(f"ERROR: examples dir not found: {examples_dir}", file=sys.stderr)
        return 2

    try:
        _prebuild_examples(repo_root, examples_dir, dry_run=args.dry_run)
    except Exception as exc:  # pragma: no cover - runtime command failure path
        print(f"ERROR: prebuild step failed: {exc}", file=sys.stderr)
        return 3

    whitelist: list[str]
    if args.whitelist:
        whitelist = _read_whitelist_file(Path(args.whitelist))
    elif default_whitelist_file.exists():
        whitelist = _read_whitelist_file(default_whitelist_file)
    else:
        whitelist = _default_whitelist(packages_dir)

    whitelist.extend(args.include)

    normalized = [_normalize_item(x) for x in whitelist]
    normalized = [x for x in normalized if x]

    seen: set[str] = set()
    final_items: list[str] = []
    for x in normalized:
        if x in seen:
            continue
        seen.add(x)
        final_items.append(x)

    if not final_items:
        print("No whitelist items provided/found. Nothing to do.")
        print("- Provide --include <name> or create packages_whitelist.txt in repo root.")
        return 0

    if not args.dry_run:
        packages_dir.mkdir(parents=True, exist_ok=True)

    copied = 0
    packed = 0
    missing = 0

    plans: list[SyncPlanItem] = []
    seen_dest_names: set[str] = set()

    for item in final_items:
        plan = _resolve_plan_item_from_roots(source_roots, item)
        if plan is None:
            print(f"MISSING: {item}")
            missing += 1
            continue

        if plan.destination_name in seen_dest_names:
            print(f"SKIP-DUP: {item} -> {plan.destination_name}")
            continue

        seen_dest_names.add(plan.destination_name)
        plans.append(plan)

    for plan in plans:
        dest = packages_dir / plan.destination_name

        if plan.mode == "copy":
            action = "COPY" if not args.dry_run else "DRY-COPY"
            print(f"{action}: {plan.source} -> {dest}")
            if not args.dry_run:
                shutil.copy2(plan.source, dest)
                copied += 1
            continue

        action = "PACK" if not args.dry_run else "DRY-PACK"
        print(f"{action}: {plan.source} -> {dest}")
        if not args.dry_run:
            _pack_toolpkg_folder(repo_root, plan.source, dest)
            packed += 1

    if args.delete_extra and packages_dir.exists():
        whitelist_names = {plan.destination_name for plan in plans}
        for p in packages_dir.iterdir():
            if not p.is_file():
                continue
            if p.suffix.lower() not in SYNCABLE_SUFFIXES:
                continue
            if p.name in whitelist_names:
                continue

            action = "DELETE" if not args.dry_run else "DRY-DELETE"
            print(f"{action}: {p}")
            if not args.dry_run:
                p.unlink(missing_ok=True)

    print(
        "Done. "
        f"copied={copied}, packed={packed}, missing={missing}, "
        f"whitelist={len(final_items)}, resolved={len(plans)}, dry_run={bool(args.dry_run)}"
    )
    return 0 if missing == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
