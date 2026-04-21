import argparse
import json
import os
import re
import shutil
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def _print_step(i: int, total: int, msg: str) -> None:
    sys.stdout.write(f"[{i}/{total}] {msg}\n")
    sys.stdout.flush()


def _run(cmd: list[str], cwd: Path) -> int:
    p = subprocess.run(cmd, cwd=str(cwd))
    return int(p.returncode)


def _parse_version_name(kts_path: Path) -> tuple[str, int, bool]:
    s = kts_path.read_text(encoding="utf-8")
    m = re.search(r'versionName\s*=\s*"([^"]+)"', s)
    if not m:
        raise RuntimeError(f"Failed to parse versionName from {kts_path}")
    raw = m.group(1).strip().lstrip("v")
    has_plus = "+" in raw
    if has_plus:
        base, pi = raw.split("+", 1)
    else:
        base, pi = raw, "0"
    patch_index = int(pi) if pi.isdigit() else 0
    base = base.strip()
    if not base:
        raise RuntimeError(f"Invalid versionName: {raw}")
    return base, patch_index, has_plus


def _parse_version_for_compare(v: str) -> tuple[int, int, int, int]:
    s = v.strip().lstrip("v")
    plus_idx = s.find("+")
    base = s[:plus_idx] if plus_idx >= 0 else s
    patch_index = int(s[plus_idx + 1 :]) if plus_idx >= 0 and s[plus_idx + 1 :].isdigit() else 0
    parts = base.split(".")
    major = int(parts[0]) if len(parts) > 0 and parts[0].isdigit() else 0
    minor = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    patch = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
    return major, minor, patch, patch_index


def _compare_versions(v1: str, v2: str) -> int:
    a = _parse_version_for_compare(v1)
    b = _parse_version_for_compare(v2)
    if a < b:
        return -1
    if a > b:
        return 1
    return 0


def _pick_latest_tag(tags: list[str]) -> str:
    best = ""
    for t in tags:
        if not re.match(r"^v?\d+\.\d+\.\d+(?:\+\d+)?$", t.strip()):
            continue
        if not best or _compare_versions(t, best) > 0:
            best = t.strip()
    return best


def _github_latest_tag_via_api(repo: str) -> str:
    url = f"https://api.github.com/repos/{repo}/releases?per_page=20&page=1"
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "hotbuild"}
    tok = os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN") or os.getenv("GITHUB_PAT")
    if tok:
        headers["Authorization"] = f"Bearer {tok}"
    req = urllib.request.Request(url, headers=headers)

    last_err: Exception | None = None
    for i in range(3):
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                data = json.loads(r.read().decode("utf-8"))
            break
        except urllib.error.HTTPError:
            raise
        except (urllib.error.URLError, ssl.SSLError, TimeoutError) as e:
            last_err = e
            time.sleep(0.5 * (i + 1))
    else:
        raise last_err or RuntimeError("GitHub API error")

    tags: list[str] = []
    for rel in data:
        if rel.get("draft"):
            continue
        t = (rel.get("tag_name") or "").strip()
        if t:
            tags.append(t)
    return _pick_latest_tag(tags)


def _github_latest_tag_via_gh(repo: str) -> str:
    if shutil.which("gh") is None:
        return ""
    p = subprocess.run(
        ["gh", "release", "view", "-R", repo, "--json", "tagName"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if p.returncode != 0:
        return ""
    try:
        obj = json.loads(p.stdout)
    except Exception:
        return ""
    tag = (obj.get("tagName") or "").strip()
    return tag


def _github_latest_tag_via_git(repo: str) -> str:
    if shutil.which("git") is None:
        return ""
    url = f"https://github.com/{repo}.git"
    p = subprocess.run(
        ["git", "ls-remote", "--tags", "--refs", url],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if p.returncode != 0:
        return ""
    tags: list[str] = []
    for line in p.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            continue
        ref = parts[1]
        if not ref.startswith("refs/tags/"):
            continue
        tags.append(ref[len("refs/tags/") :])
    return _pick_latest_tag(tags)


def _github_latest_tag(repo: str) -> str:
    try:
        t = _github_latest_tag_via_api(repo)
        if t:
            return t
    except urllib.error.HTTPError as e:
        if e.code == 403:
            sys.stderr.write("GitHub API 403 (rate limit). Set GITHUB_TOKEN/GH_TOKEN/GITHUB_PAT, or use gh/git fallback.\n")
        else:
            sys.stderr.write(f"GitHub API HTTP {e.code}. Falling back to gh/git.\n")
    except (urllib.error.URLError, ssl.SSLError, TimeoutError) as e:
        sys.stderr.write(f"GitHub API network/ssl error: {e}. Falling back to gh/git.\n")
    except Exception as e:
        sys.stderr.write(f"GitHub API error: {e}. Falling back to gh/git.\n")

    t = _github_latest_tag_via_gh(repo)
    if t:
        return t

    t = _github_latest_tag_via_git(repo)
    if t:
        return t

    return ""


def _parse_tag_version(tag: str) -> tuple[str, int]:
    t = tag.strip().lstrip("v")
    m = re.match(r"^(\d+\.\d+\.\d+)(?:\+(\d+))?$", t)
    if not m:
        return "", 0
    base = m.group(1)
    patch = int(m.group(2)) if m.group(2) else 0
    return base, patch


def _find_latest_apk(apk_dir: Path) -> Path:
    apks = sorted(apk_dir.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not apks:
        raise RuntimeError(f"APK not found under: {apk_dir}")
    return apks[0]


def _rollback_to_apk(from_apk: Path, to_apk: Path) -> None:
    if not from_apk.exists():
        return
    try:
        shutil.copy2(from_apk, to_apk)
        sys.stderr.write(f"rollback: restored {to_apk.name} from {from_apk.name}\n")
    except Exception as e:
        sys.stderr.write(f"rollback failed: {e}\n")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="AAswordman/OperitNightlyRelease")
    ap.add_argument("--format", default="apkraw", choices=("apkraw", "apkzip", "opatch"))
    ap.add_argument("--from-version", default=None)
    ap.add_argument("--from-patch-index", type=int, default=None)
    ap.add_argument("--no-publish", action="store_true")
    args = ap.parse_args()

    hotbuild_dir = Path(__file__).resolve().parent
    root_dir = (hotbuild_dir / ".." / "..").resolve()
    kts_path = (root_dir / "app" / "build.gradle.kts").resolve()

    from_apk: Path | None = None
    to_apk: Path | None = None
    wrote_new_to_apk = False

    total = 6
    try:
        _print_step(1, total, "parse target version from build.gradle.kts")
        to_base, to_patch, to_has_plus = _parse_version_name(kts_path)
        if to_has_plus:
            sys.stdout.write(f"target: {to_base}+{to_patch}\n")
        else:
            sys.stdout.write(f"target: {to_base}\n")

        allow_nonplus = (not to_has_plus) and to_patch == 0
        if allow_nonplus:
            build_variant = "release"
            gradle_task = ":app:assembleRelease"
            apk_dir = root_dir / "app" / "build" / "outputs" / "apk" / "release"
            apk_out = apk_dir / "app-release.apk"
        else:
            build_variant = "nightly"
            gradle_task = ":app:assembleNightly"
            apk_dir = root_dir / "app" / "build" / "outputs" / "apk" / "nightly"
            apk_out = apk_dir / "app-nightly.apk"

        from_apk = hotbuild_dir / "from.apk"
        to_apk = hotbuild_dir / "to.apk"

        _print_step(2, total, "rotate from/to")
        if to_apk.exists():
            shutil.copy2(to_apk, from_apk)
        elif not from_apk.exists():
            raise RuntimeError(f"missing from.apk and to.apk in {hotbuild_dir}")

        _print_step(3, total, f"assemble {build_variant}")
        gradlew = root_dir / "gradlew.bat"
        if not gradlew.exists():
            raise RuntimeError(f"gradlew.bat not found: {gradlew}")
        rc = _run([str(gradlew), gradle_task], cwd=root_dir)
        if rc != 0:
            return rc

        if not apk_out.exists():
            apk_out = _find_latest_apk(apk_dir)

        shutil.copy2(apk_out, to_apk)
        wrote_new_to_apk = True

        _print_step(4, total, "fetch last published version from GitHub")

        if args.from_version is not None:
            from_base = str(args.from_version).strip().lstrip("v")
            from_patch = int(args.from_patch_index or 0)
        else:
            tag = _github_latest_tag(args.repo)
            from_base, from_patch = _parse_tag_version(tag)
        if not from_base:
            if to_has_plus and to_patch > 0 and args.from_version is None:
                sys.stderr.write(
                    "Failed to fetch latest published tag; falling back to from=to_patch_index-1 for patch build.\n"
                )
                from_base = to_base
                from_patch = int(to_patch) - 1
            else:
                raise RuntimeError(
                    "Failed to determine last published version. Set GITHUB_TOKEN/GH_TOKEN/GITHUB_PAT, "
                    "or install/configure gh or git, or pass --from-version/--from-patch-index."
                )

        sys.stdout.write(f"from: {from_base}+{from_patch}\n")

        if from_base == to_base and (to_patch <= from_patch) and not allow_nonplus:
            raise RuntimeError("target patch index must be greater than last published patch index")

        _print_step(5, total, "build_patch.py")
        build_patch_py = hotbuild_dir / "build_patch.py"
        if not build_patch_py.exists():
            raise RuntimeError(f"build_patch.py not found: {build_patch_py}")

        cmd = [
            sys.executable,
            str(build_patch_py),
            "--from",
            "from.apk",
            "--to",
            "to.apk",
            "--format",
            args.format,
            "--from-version",
            str(from_base),
            "--from-patch-index",
            str(int(from_patch)),
            "--to-version",
            str(to_base),
            "--to-patch-index",
            str(int(to_patch)),
            "--repo",
            args.repo,
        ]
        if args.no_publish:
            cmd.append("--no-publish")

        rc = _run(cmd, cwd=hotbuild_dir)
        if rc != 0:
            if wrote_new_to_apk and from_apk is not None and to_apk is not None:
                _rollback_to_apk(from_apk, to_apk)
            return rc

        _print_step(6, total, "done")
        return 0
    except KeyboardInterrupt:
        if wrote_new_to_apk and from_apk is not None and to_apk is not None:
            _rollback_to_apk(from_apk, to_apk)
        return 130
    except Exception as e:
        if wrote_new_to_apk and from_apk is not None and to_apk is not None:
            _rollback_to_apk(from_apk, to_apk)
        sys.stderr.write(str(e) + "\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
