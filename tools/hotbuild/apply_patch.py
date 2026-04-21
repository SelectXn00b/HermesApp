from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Optional

MAGIC = b"OPATCH1\0"
OP_END = 0
OP_COPY = 1
OP_ADD = 2


def sha256_file(path: Path) -> bytes:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            b = f.read(1024 * 1024)
            if not b:
                break
            h.update(b)
    return h.digest()


def sha256_hex(path: Path) -> str:
    return sha256_file(path).hex()


def run(args: list[str]) -> tuple[int, str, str]:
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return p.returncode, p.stdout, p.stderr


def get_token(cli_token: Optional[str]) -> Optional[str]:
    if cli_token:
        return cli_token
    for k in ("GITHUB_TOKEN", "GH_TOKEN", "GITHUB_PAT"):
        v = os.environ.get(k)
        if v:
            return v
    return None


def http_request_json(url: str, token: Optional[str]) -> Any:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "hotbuild",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(req) as r:
        body = r.read()
    return json.loads(body.decode("utf-8")) if body else {}


def download_file(url: str, out_path: Path, token: Optional[str]) -> None:
    headers = {"User-Agent": "hotbuild"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers, method="GET")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(req) as r, out_path.open("wb") as f:
        while True:
            chunk = r.read(1024 * 1024)
            if not chunk:
                break
            f.write(chunk)


def try_download_with_gh(repo: str, tag: str, asset: str, out_dir: Path) -> bool:
    if shutil.which("gh") is None:
        return False
    cmd = ["gh", "release", "download", tag, "-R", repo, "-p", asset, "-D", str(out_dir)]
    code, out, err = run(cmd)
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code == 0


def load_meta_from_release_body(release: dict) -> Optional[dict]:
    body = release.get("body")
    if not body or not isinstance(body, str):
        return None
    try:
        meta = json.loads(body)
        if isinstance(meta, dict) and meta.get("format") in ("opatch-1", "apkzip-1", "apkraw-1"):
            return meta
        return None
    except Exception:
        return None


def load_meta(repo: str, tag: str, token: Optional[str], wd: Path) -> dict:
    base = f"https://api.github.com/repos/{repo}"
    tag_q = urllib.parse.quote(tag)
    release = http_request_json(f"{base}/releases/tags/{tag_q}", token)
    if not isinstance(release, dict):
        raise RuntimeError("bad release json")

    meta = load_meta_from_release_body(release)
    if meta:
        return meta

    assets = release.get("assets")
    if not isinstance(assets, list):
        raise RuntimeError("release has no assets")

    meta_asset = None
    for a in assets:
        name = a.get("name") if isinstance(a, dict) else None
        if isinstance(name, str) and name.endswith(".json"):
            meta_asset = a
            break

    if not meta_asset:
        raise RuntimeError("cannot find meta json in release assets")

    name = str(meta_asset["name"])
    url = str(meta_asset["browser_download_url"])
    out = wd / name
    sys.stdout.write(f"download meta {name}\n")
    if not out.exists():
        if not try_download_with_gh(repo, tag, name, wd):
            download_file(url, out, token)

    meta = json.loads(out.read_text(encoding="utf-8"))
    if not isinstance(meta, dict) or meta.get("format") not in ("opatch-1", "apkzip-1", "apkraw-1"):
        raise RuntimeError("bad meta format")
    return meta


def find_asset_download_url(repo: str, tag: str, asset_name: str, token: Optional[str]) -> str:
    base = f"https://api.github.com/repos/{repo}"
    tag_q = urllib.parse.quote(tag)
    release = http_request_json(f"{base}/releases/tags/{tag_q}", token)
    if not isinstance(release, dict):
        raise RuntimeError("bad release json")
    assets = release.get("assets")
    if not isinstance(assets, list):
        raise RuntimeError("release has no assets")
    for a in assets:
        if isinstance(a, dict) and a.get("name") == asset_name:
            return str(a["browser_download_url"])
    raise RuntimeError(f"asset not found: {asset_name}")


def apply_opatch(old_apk: Path, patch_path: Path, out_tmp: Path) -> dict:
    with gzip.open(patch_path, "rb") as pf:
        magic = pf.read(len(MAGIC))
        if magic != MAGIC:
            raise RuntimeError("bad patch magic")

        hdr = pf.read(struct.calcsize("<IQQ"))
        if len(hdr) != struct.calcsize("<IQQ"):
            raise RuntimeError("bad patch header")
        block_size, old_size, new_size = struct.unpack("<IQQ", hdr)

        old_sha = pf.read(32)
        new_sha = pf.read(32)
        if len(old_sha) != 32 or len(new_sha) != 32:
            raise RuntimeError("bad patch sha")

        if old_apk.stat().st_size != old_size:
            raise RuntimeError("old apk size mismatch")

        actual_old_sha = sha256_file(old_apk)
        if actual_old_sha != old_sha:
            raise RuntimeError("old apk sha256 mismatch")

        out_tmp.parent.mkdir(parents=True, exist_ok=True)

        with old_apk.open("rb") as oldf, out_tmp.open("wb") as out:
            written = 0
            while True:
                opb = pf.read(1)
                if not opb:
                    raise RuntimeError("unexpected eof")
                op = opb[0]

                if op == OP_END:
                    break

                if op == OP_COPY:
                    b = pf.read(16)
                    if len(b) != 16:
                        raise RuntimeError("bad copy op")
                    off, length = struct.unpack("<QQ", b)
                    oldf.seek(off)
                    remaining = length
                    while remaining:
                        chunk = oldf.read(min(1024 * 1024, remaining))
                        if not chunk:
                            raise RuntimeError("copy read underflow")
                        out.write(chunk)
                        remaining -= len(chunk)
                        written += len(chunk)
                    continue

                if op == OP_ADD:
                    b = pf.read(8)
                    if len(b) != 8:
                        raise RuntimeError("bad add op")
                    length = struct.unpack("<Q", b)[0]
                    remaining = length
                    while remaining:
                        chunk = pf.read(min(1024 * 1024, remaining))
                        if not chunk:
                            raise RuntimeError("add read underflow")
                        out.write(chunk)
                        remaining -= len(chunk)
                        written += len(chunk)
                    continue

                raise RuntimeError(f"unknown opcode {op}")

        if written != new_size:
            raise RuntimeError("new apk size mismatch")

        actual_new_sha = sha256_file(out_tmp)
        if actual_new_sha != new_sha:
            raise RuntimeError("new apk sha256 mismatch")

        return {
            "blockSize": block_size,
            "oldSize": old_size,
            "newSize": new_size,
            "oldSha256": old_sha.hex(),
            "newSha256": new_sha.hex(),
        }


def zip_tree_sig(apk_path: Path) -> str:
    h = hashlib.sha256()
    with zipfile.ZipFile(apk_path, "r") as zf:
        for info in sorted(zf.infolist(), key=lambda x: x.filename):
            name = info.filename
            if name.endswith("/"):
                continue
            line = f"{name}\t{info.CRC}\t{info.file_size}\t{info.compress_type}\n".encode("utf-8")
            h.update(line)
    return h.hexdigest()


def apply_apkzip_patch(old_apk: Path, patch_zip: Path, out_tmp: Path, meta: dict) -> None:
    target_entries = meta.get("targetEntries")
    if not isinstance(target_entries, list):
        raise RuntimeError("apkzip meta missing targetEntries")

    out_tmp.parent.mkdir(parents=True, exist_ok=True)
    if out_tmp.exists():
        out_tmp.unlink()

    with zipfile.ZipFile(old_apk, "r") as oz, zipfile.ZipFile(patch_zip, "r") as pz:
        with zipfile.ZipFile(out_tmp, "w") as out:
            for ent in target_entries:
                if not isinstance(ent, dict):
                    raise RuntimeError("bad targetEntries")
                name = ent.get("name")
                if not isinstance(name, str) or not name:
                    raise RuntimeError("bad target entry name")
                compress_type = ent.get("compressType")
                if not isinstance(compress_type, int):
                    compress_type = zipfile.ZIP_DEFLATED

                patch_name = f"files/{name}"
                src_zip = pz if patch_name in pz.namelist() else oz
                src_name = patch_name if src_zip is pz else name

                zi = zipfile.ZipInfo(name)
                zi.date_time = (1980, 1, 1, 0, 0, 0)
                zi.compress_type = int(compress_type)

                with src_zip.open(src_name, "r") as src, out.open(zi, "w") as dst:
                    while True:
                        chunk = src.read(1024 * 1024)
                        if not chunk:
                            break
                        dst.write(chunk)


def read_zip_local_record_bytes(apk_path: Path, info: zipfile.ZipInfo) -> bytes:
    off = int(info.header_offset)
    with apk_path.open("rb") as f:
        f.seek(off)
        hdr = f.read(30)
        if len(hdr) != 30:
            raise RuntimeError("bad zip local header")
        sig, ver, flags, comp, modt, modd, crc, csize, usize, fnl, exl = struct.unpack("<IHHHHHIIIHH", hdr)
        if sig != 0x04034B50:
            raise RuntimeError("bad zip local header signature")
        name_extra = f.read(int(fnl) + int(exl))
        if len(name_extra) != int(fnl) + int(exl):
            raise RuntimeError("bad zip local header name/extra")

        data_start = off + 30 + int(fnl) + int(exl)
        comp_size = int(info.compress_size)
        data_end = data_start + comp_size
        f.seek(data_end)

        dd_len = 0
        if flags & 0x08:
            b4 = f.read(4)
            if len(b4) != 4:
                raise RuntimeError("bad zip data descriptor")
            sig_dd = struct.unpack("<I", b4)[0]
            if sig_dd == 0x08074B50:
                dd_rest = f.read(12)
                if len(dd_rest) != 12:
                    raise RuntimeError("bad zip data descriptor")
                dd_len = 16
            else:
                dd_rest = f.read(8)
                if len(dd_rest) != 8:
                    raise RuntimeError("bad zip data descriptor")
                dd_len = 12

        end_off = data_end + dd_len
        f.seek(off)
        return f.read(end_off - off)


def apply_apkraw_patch(old_apk: Path, patch_zip: Path, out_tmp: Path, meta: dict) -> None:
    entries = meta.get("apkRawEntries")
    tail_name = str(meta.get("apkRawTailFile") or "tail.bin")
    if not isinstance(entries, list):
        raise RuntimeError("apkraw meta missing apkRawEntries")

    with zipfile.ZipFile(old_apk, "r") as oz:
        old_infos = {i.filename: i for i in oz.infolist() if not i.filename.endswith("/")}

    out_tmp.parent.mkdir(parents=True, exist_ok=True)
    if out_tmp.exists():
        out_tmp.unlink()

    with zipfile.ZipFile(patch_zip, "r") as pz, out_tmp.open("wb") as out:
        for ent in entries:
            if not isinstance(ent, dict):
                raise RuntimeError("bad apkRawEntries")
            name = ent.get("name")
            mode = ent.get("mode")
            if not isinstance(name, str) or not name:
                raise RuntimeError("bad apkraw entry name")
            if mode == "copy":
                oi = old_infos.get(name)
                if oi is None:
                    raise RuntimeError(f"apkraw copy missing in base apk: {name}")
                out.write(read_zip_local_record_bytes(old_apk, oi))
            elif mode == "add":
                rec_path = ent.get("recordPath")
                if not isinstance(rec_path, str) or not rec_path:
                    raise RuntimeError("apkraw add missing recordPath")
                out.write(pz.read(rec_path))
            else:
                raise RuntimeError("apkraw bad mode")

        out.write(pz.read(tail_name))


def parse_version(v: str) -> tuple[int, int, int, int]:
    s = v.strip()
    if s.startswith("v"):
        s = s[1:]
    plus = s.find("+")
    if plus >= 0:
        base = s[:plus]
        patch_index = int(s[plus + 1 :] or "0") if (s[plus + 1 :].isdigit()) else 0
    else:
        base = s
        patch_index = 0
    parts = base.split(".")
    major = int(parts[0]) if len(parts) > 0 and parts[0].isdigit() else 0
    minor = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    patch = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
    return major, minor, patch, patch_index


def compare_versions(v1: str, v2: str) -> int:
    p1 = parse_version(v1)
    p2 = parse_version(v2)
    if p1 < p2:
        return -1
    if p1 > p2:
        return 1
    return 0


def get_meta_target_version(meta: dict) -> str:
    to_v = meta.get("toVersion")
    to_pi = meta.get("toPatchIndex")
    if isinstance(to_v, str) and to_v:
        pi = int(to_pi) if isinstance(to_pi, int) else 0
        return f"{to_v}+{pi}" if pi > 0 else to_v
    return "0.0.0"


def get_meta_base_key(meta: dict) -> tuple[str, str]:
    fmt = str(meta.get("format") or "")
    if fmt == "apkzip-1":
        return "zipTree", str(meta.get("baseZipTreeSha256") or "")
    return "sha256", str(meta.get("baseSha256") or "")


def get_meta_target_key(meta: dict) -> tuple[str, str]:
    fmt = str(meta.get("format") or "")
    if fmt == "apkzip-1":
        return "zipTree", str(meta.get("targetZipTreeSha256") or "")
    return "sha256", str(meta.get("targetSha256") or "")


def fetch_release_list(repo: str, token: Optional[str], page: int, per_page: int) -> list[dict]:
    base = f"https://api.github.com/repos/{repo}"
    url = f"{base}/releases?page={page}&per_page={per_page}"
    data = http_request_json(url, token)
    if not isinstance(data, list):
        return []
    out: list[dict] = []
    for r in data:
        if isinstance(r, dict):
            out.append(r)
    return out


def load_all_metas(repo: str, token: Optional[str], wd: Path, max_pages: int, per_page: int) -> list[dict]:
    metas: list[dict] = []
    for page in range(1, max_pages + 1):
        releases = fetch_release_list(repo, token, page=page, per_page=per_page)
        if not releases:
            break
        for release in releases:
            meta = load_meta_from_release_body(release)
            if meta:
                metas.append(meta)
                continue

            tag = release.get("tag_name")
            if not isinstance(tag, str) or not tag:
                continue
            try:
                meta = load_meta(repo, tag, token, wd)
            except Exception:
                continue
            if meta:
                metas.append(meta)
    return metas


def select_next_meta(metas: list[dict], current_sha: str) -> Optional[dict]:
    candidates: list[dict] = []
    for m in metas:
        if not isinstance(m, dict):
            continue
        key_type, key_val = get_meta_base_key(m)
        if not key_val:
            continue
        if key_type == "sha256" and key_val == current_sha:
            candidates.append(m)
            continue
        if key_type == "zipTree" and key_val == current_sha:
            candidates.append(m)
    if not candidates:
        return None
    best = candidates[0]
    best_v = get_meta_target_version(best)
    for m in candidates[1:]:
        v = get_meta_target_version(m)
        if compare_versions(v, best_v) > 0:
            best = m
            best_v = v
    return best


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="AAswordman/OperitNightlyRelease")
    ap.add_argument("--tag", default=None)
    ap.add_argument("--meta", default=None, help="local meta json path (skip download)")
    ap.add_argument("--auto", action="store_true", help="auto apply patch chain to latest")
    ap.add_argument("--max-pages", type=int, default=5)
    ap.add_argument("--per-page", type=int, default=30)
    ap.add_argument("--token", default=None)
    ap.add_argument("--from", dest="from_path", default="from.apk")
    ap.add_argument("--to", dest="to_path", default="to.apk")
    ap.add_argument("--keep", action="store_true", help="keep downloaded patch/meta files")
    args = ap.parse_args()

    wd = Path.cwd()
    from_apk = (wd / args.from_path).resolve()
    to_apk = (wd / args.to_path).resolve()
    if not from_apk.exists():
        sys.stderr.write("missing from.apk\n")
        return 2

    token = get_token(args.token)

    if args.auto:
        metas = load_all_metas(args.repo, token, wd, max_pages=int(args.max_pages), per_page=int(args.per_page))
        if not metas:
            sys.stderr.write("no patch metas found\n")
            return 3

        work_apk = wd / "_opatch_work.apk"
        if work_apk.exists():
            work_apk.unlink()
        shutil.copy2(from_apk, work_apk)

        applied = 0
        while True:
            current_sha = sha256_hex(work_apk)
            current_tree = zip_tree_sig(work_apk)
            meta = select_next_meta(metas, current_sha)
            if not meta:
                meta = select_next_meta(metas, current_tree)
            if not meta:
                break

            key_type, base_key = get_meta_base_key(meta)
            _, target_key = get_meta_target_key(meta)
            patch_file = str(meta.get("patchFile") or "")
            patch_sha = str(meta.get("patchSha256") or "")
            tag = str(meta.get("tag") or "")
            if not base_key or not target_key or not patch_file or not patch_sha or not tag:
                break

            if key_type == "sha256" and base_key != current_sha:
                break
            if key_type == "zipTree" and base_key != current_tree:
                break

            patch_path = (wd / patch_file).resolve()
            if not patch_path.exists():
                sys.stdout.write(f"download patch {patch_file}\n")
                if not try_download_with_gh(args.repo, tag, patch_file, wd):
                    url = find_asset_download_url(args.repo, tag, patch_file, token)
                    download_file(url, patch_path, token)

            actual_patch_sha = sha256_hex(patch_path)
            if actual_patch_sha != patch_sha:
                sys.stderr.write("patch sha256 mismatch\n")
                return 4

            out_tmp = work_apk.with_suffix(work_apk.suffix + ".tmp")
            if out_tmp.exists():
                out_tmp.unlink()

            sys.stdout.write(f"apply {tag}...\n")
            fmt = str(meta.get("format") or "")
            if fmt == "apkzip-1":
                apply_apkzip_patch(work_apk, patch_path, out_tmp, meta)
                actual_to_sig = zip_tree_sig(out_tmp)
                if actual_to_sig != target_key:
                    sys.stderr.write("target zip tree mismatch\n")
                    return 5
            elif fmt == "apkraw-1":
                apply_apkraw_patch(work_apk, patch_path, out_tmp, meta)
                actual_to_sha = sha256_hex(out_tmp)
                if actual_to_sha != target_key:
                    sys.stderr.write("target sha256 mismatch\n")
                    return 5
            else:
                apply_opatch(work_apk, patch_path, out_tmp)
                actual_to_sha = sha256_hex(out_tmp)
                if actual_to_sha != target_key:
                    sys.stderr.write("target sha256 mismatch\n")
                    return 5

            os.replace(out_tmp, work_apk)
            applied += 1

        if applied == 0:
            sys.stderr.write("no applicable patch found for current apk (fallback to full download)\n")
            if work_apk.exists():
                work_apk.unlink()
            return 3

        if to_apk.exists():
            to_apk.unlink()
        os.replace(work_apk, to_apk)
        sys.stdout.write(f"ok -> {to_apk}\n")
        return 0

    meta = None
    meta_path = None
    if args.meta:
        meta_path = (wd / args.meta).resolve()
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
    else:
        if not args.tag:
            sys.stderr.write("need --tag or --meta\n")
            return 2
        meta = load_meta(args.repo, args.tag, token, wd)

    if not isinstance(meta, dict) or meta.get("format") not in ("opatch-1", "apkzip-1", "apkraw-1"):
        sys.stderr.write("bad meta\n")
        return 2

    key_type, base_key = get_meta_base_key(meta)
    _, target_key = get_meta_target_key(meta)
    patch_file = str(meta.get("patchFile") or "")
    patch_sha = str(meta.get("patchSha256") or "")
    tag = str(meta.get("tag") or args.tag or "")

    if not base_key or not target_key or not patch_file or not patch_sha or not tag:
        sys.stderr.write("meta missing fields\n")
        return 2

    if key_type == "sha256":
        actual_base = sha256_hex(from_apk)
        if actual_base != base_key:
            sys.stderr.write("baseline mismatch (from.apk sha256 != baseSha256). fallback to full download.\n")
            return 3
    else:
        actual_base = zip_tree_sig(from_apk)
        if actual_base != base_key:
            sys.stderr.write("baseline mismatch (from.apk zip tree != baseZipTreeSha256). fallback to full download.\n")
            return 3

    patch_path = (wd / patch_file).resolve()
    if not patch_path.exists():
        sys.stdout.write(f"download patch {patch_file}\n")
        if not try_download_with_gh(args.repo, tag, patch_file, wd):
            url = find_asset_download_url(args.repo, tag, patch_file, token)
            download_file(url, patch_path, token)

    actual_patch_sha = sha256_hex(patch_path)
    if actual_patch_sha != patch_sha:
        sys.stderr.write("patch sha256 mismatch\n")
        return 4

    out_tmp = to_apk.with_suffix(to_apk.suffix + ".tmp")
    if out_tmp.exists():
        out_tmp.unlink()

    sys.stdout.write("apply patch...\n")
    fmt = str(meta.get("format") or "")
    if fmt == "apkzip-1":
        apply_apkzip_patch(from_apk, patch_path, out_tmp, meta)
        actual_sig = zip_tree_sig(out_tmp)
        if actual_sig != target_key:
            sys.stderr.write("target zip tree mismatch\n")
            return 5
    elif fmt == "apkraw-1":
        apply_apkraw_patch(from_apk, patch_path, out_tmp, meta)
        actual_to_sha = sha256_hex(out_tmp)
        if actual_to_sha != target_key:
            sys.stderr.write("target sha256 mismatch\n")
            return 5
    else:
        apply_opatch(from_apk, patch_path, out_tmp)
        actual_to_sha = sha256_hex(out_tmp)
        if actual_to_sha != target_key:
            sys.stderr.write("target sha256 mismatch\n")
            return 5

    os.replace(out_tmp, to_apk)
    sys.stdout.write(f"ok -> {to_apk}\n")

    if not args.keep:
        if args.tag and meta_path is None:
            for p in wd.glob("patch_*.json"):
                if p.is_file():
                    pass
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
