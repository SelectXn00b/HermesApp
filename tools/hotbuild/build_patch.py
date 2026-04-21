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
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Optional

MAGIC = b"OPATCH1\0"
OP_END = 0
OP_COPY = 1
OP_ADD = 2
MAX_ADD_CHUNK_BYTES = 4 * 1024 * 1024


def format_version_with_patch(version: str, patch_index: int) -> str:
    if patch_index <= 0:
        return version
    return f"{version}+{patch_index}"


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


def build_opatch(old_path: Path, new_path: Path, out_patch: Path, block_size: int) -> dict:
    old_size = old_path.stat().st_size
    new_size = new_path.stat().st_size

    old_sha = sha256_file(old_path)
    new_sha = sha256_file(new_path)

    old_seq = []
    old_map = {}

    with old_path.open("rb") as f:
        i = 0
        while True:
            blk = f.read(block_size)
            if not blk:
                break
            h = hashlib.sha1(blk).digest()
            old_seq.append(h)
            old_map.setdefault(h, []).append(i * block_size)
            i += 1

    out_patch.parent.mkdir(parents=True, exist_ok=True)

    with gzip.open(out_patch, "wb", compresslevel=9) as out:
        out.write(MAGIC)
        out.write(struct.pack("<IQQ", block_size, old_size, new_size))
        out.write(old_sha)
        out.write(new_sha)

        pending = None
        copy_off = 0
        copy_len = 0
        add_buf = bytearray()

        def flush() -> None:
            nonlocal pending, copy_off, copy_len, add_buf
            if pending is None:
                return
            if pending == OP_COPY:
                out.write(struct.pack("<BQQ", OP_COPY, copy_off, copy_len))
            elif pending == OP_ADD:
                out.write(struct.pack("<BQ", OP_ADD, len(add_buf)))
                out.write(add_buf)
            else:
                raise RuntimeError("bad opcode")
            pending = None
            copy_off = 0
            copy_len = 0
            add_buf = bytearray()

        with new_path.open("rb") as nf:
            idx = 0
            while True:
                blk = nf.read(block_size)
                if not blk:
                    break
                h = hashlib.sha1(blk).digest()
                off = None
                if idx < len(old_seq) and old_seq[idx] == h:
                    off = idx * block_size
                else:
                    offs = old_map.get(h)
                    if offs:
                        off = offs[0]

                if off is not None:
                    if pending == OP_COPY and copy_off + copy_len == off:
                        copy_len += len(blk)
                    else:
                        flush()
                        pending = OP_COPY
                        copy_off = off
                        copy_len = len(blk)
                else:
                    if pending != OP_ADD:
                        flush()
                        pending = OP_ADD
                    add_buf.extend(blk)
                    if len(add_buf) >= MAX_ADD_CHUNK_BYTES:
                        flush()

                idx += 1

        flush()
        out.write(struct.pack("<B", OP_END))

    return {
        "oldSize": old_size,
        "newSize": new_size,
        "blockSize": block_size,
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


def read_zip_local_record_bytes(apk_path: Path, info: zipfile.ZipInfo) -> tuple[bytes, int]:
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
        return f.read(end_off - off), end_off


def build_apkraw_patch(old_apk: Path, new_apk: Path, out_patch: Path) -> dict:
    out_patch.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(old_apk, "r") as oz, zipfile.ZipFile(new_apk, "r") as nz:
        old_infos = {i.filename: i for i in oz.infolist() if not i.filename.endswith("/")}
        new_infos = [i for i in nz.infolist() if not i.filename.endswith("/")]
        new_infos.sort(key=lambda i: int(i.header_offset))

        tail_start = 0
        new_records: dict[str, tuple[bytes, int]] = {}
        for info in new_infos:
            rec, end_off = read_zip_local_record_bytes(new_apk, info)
            new_records[info.filename] = (rec, end_off)
            if end_off > tail_start:
                tail_start = end_off

        with new_apk.open("rb") as f:
            f.seek(tail_start)
            tail = f.read()

        patch_zip_compress = zipfile.ZIP_DEFLATED
        apk_raw_entries: list[dict] = []
        changed_count = 0
        with zipfile.ZipFile(out_patch, "w", compression=patch_zip_compress, compresslevel=9) as pz:
            for idx, info in enumerate(new_infos):
                name = info.filename
                new_rec = new_records[name][0]
                oi = old_infos.get(name)
                can_copy = False
                if oi is not None:
                    try:
                        old_rec, _ = read_zip_local_record_bytes(old_apk, oi)
                        can_copy = old_rec == new_rec
                    except Exception:
                        can_copy = False

                if can_copy:
                    apk_raw_entries.append({"name": name, "mode": "copy"})
                else:
                    changed_count += 1
                    rec_path = f"records/{idx:05d}.bin"
                    apk_raw_entries.append({"name": name, "mode": "add", "recordPath": rec_path})
                    zi = zipfile.ZipInfo(rec_path)
                    zi.date_time = (1980, 1, 1, 0, 0, 0)
                    zi.compress_type = patch_zip_compress
                    with pz.open(zi, "w") as dst:
                        dst.write(new_rec)

            zi = zipfile.ZipInfo("tail.bin")
            zi.date_time = (1980, 1, 1, 0, 0, 0)
            zi.compress_type = patch_zip_compress
            with pz.open(zi, "w") as dst:
                dst.write(tail)

        return {
            "apkRawEntries": apk_raw_entries,
            "apkRawTailFile": "tail.bin",
            "apkRawChangedCount": changed_count,
        }


def build_apkzip_patch(old_apk: Path, new_apk: Path, out_patch: Path) -> dict:
    out_patch.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(old_apk, "r") as oz, zipfile.ZipFile(new_apk, "r") as nz:
        old_infos = {i.filename: i for i in oz.infolist() if not i.filename.endswith("/")}
        new_infos = {i.filename: i for i in nz.infolist() if not i.filename.endswith("/")}

        changed: list[str] = []
        deleted: list[str] = []

        for name in old_infos.keys():
            if name not in new_infos:
                deleted.append(name)

        for name, ni in new_infos.items():
            oi = old_infos.get(name)
            if oi is None:
                changed.append(name)
                continue
            if oi.CRC != ni.CRC or oi.file_size != ni.file_size or oi.compress_type != ni.compress_type:
                changed.append(name)

        changed_set = set(changed)

        patch_compress = zipfile.ZIP_DEFLATED
        with zipfile.ZipFile(out_patch, "w", compression=patch_compress, compresslevel=9) as pz:
            for name in sorted(changed_set):
                out_name = f"files/{name}"
                zi = zipfile.ZipInfo(out_name)
                zi.date_time = (1980, 1, 1, 0, 0, 0)
                zi.compress_type = patch_compress
                with nz.open(name, "r") as src, pz.open(zi, "w") as dst:
                    while True:
                        chunk = src.read(1024 * 1024)
                        if not chunk:
                            break
                        dst.write(chunk)

        target_entries: list[dict] = []
        for info in nz.infolist():
            name = info.filename
            if name.endswith("/"):
                continue
            target_entries.append(
                {
                    "name": name,
                    "compressType": int(info.compress_type),
                }
            )

        return {
            "changedFiles": sorted(changed_set),
            "deletedFiles": sorted(deleted),
            "targetEntries": target_entries,
            "baseZipTreeSha256": zip_tree_sig(old_apk),
            "targetZipTreeSha256": zip_tree_sig(new_apk),
        }


def get_token(cli_token: Optional[str]) -> Optional[str]:
    if cli_token:
        return cli_token
    for k in ("GITHUB_TOKEN", "GH_TOKEN", "GITHUB_PAT"):
        v = os.environ.get(k)
        if v:
            return v
    return None


def run(args: list[str]) -> tuple[int, str, str]:
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return p.returncode, p.stdout, p.stderr


def gh_api_json(method: str, url: str, token: str, payload: Optional[dict] = None) -> dict:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "hotbuild",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as r:
        body = r.read()
    return json.loads(body.decode("utf-8")) if body else {}


def gh_api_bytes(method: str, url: str, token: str, body: bytes) -> dict:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "hotbuild",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/octet-stream",
        "Content-Length": str(len(body)),
    }
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req) as r:
        rb = r.read()
    return json.loads(rb.decode("utf-8")) if rb else {}


def publish_release(repo: str, tag: str, title: str, notes: str, assets: list[Path], token: Optional[str]) -> None:
    if shutil.which("gh") is not None:
        code, _, _ = run(["gh", "release", "view", tag, "-R", repo])
        if code == 0:
            cmd = ["gh", "release", "upload", tag, "-R", repo, "--clobber"] + [str(a) for a in assets]
        else:
            cmd = [
                "gh",
                "release",
                "create",
                tag,
                "-R",
                repo,
                "--title",
                title,
                "--notes",
                notes,
            ] + [str(a) for a in assets]
        code, out, err = run(cmd)
        sys.stdout.write(out)
        sys.stderr.write(err)
        if code == 0:
            return

    if not token:
        raise RuntimeError("publish failed: no gh CLI, and no token for GitHub API")

    tag_q = urllib.parse.quote(tag)
    base = f"https://api.github.com/repos/{repo}"
    release = None
    try:
        release = gh_api_json("GET", f"{base}/releases/tags/{tag_q}", token)
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise

    if not release:
        release = gh_api_json(
            "POST",
            f"{base}/releases",
            token,
            {
                "tag_name": tag,
                "target_commitish": "main",
                "name": title,
                "body": notes,
                "draft": False,
                "prerelease": True,
            },
        )

    release_id = int(release["id"])
    upload_url = str(release["upload_url"]).split("{")[0]

    existing = gh_api_json("GET", f"{base}/releases/{release_id}/assets", token)
    existing_by_name = {a.get("name"): int(a.get("id")) for a in existing if a.get("name") and a.get("id")}

    for a in assets:
        aid = existing_by_name.get(a.name)
        if aid:
            gh_api_json("DELETE", f"{base}/releases/assets/{aid}", token)
        data = a.read_bytes()
        url = f"{upload_url}?name={urllib.parse.quote(a.name)}"
        gh_api_bytes("POST", url, token, data)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="from_path", default="from.apk")
    ap.add_argument("--to", dest="to_path", default="to.apk")
    ap.add_argument("--format", choices=("apkraw", "apkzip", "opatch"), default="apkraw")
    ap.add_argument("--from-version", default=None)
    ap.add_argument("--to-version", default=None)
    ap.add_argument("--from-patch-index", type=int, default=None)
    ap.add_argument("--to-patch-index", type=int, default=None)
    ap.add_argument("--block-size", type=int, default=4096)
    ap.add_argument("--repo", default="AAswordman/OperitNightlyRelease")
    ap.add_argument("--tag", default=None)
    ap.add_argument("--token", default=None)
    ap.add_argument("--no-publish", action="store_true")
    args = ap.parse_args()

    wd = Path.cwd()
    from_apk = (wd / args.from_path).resolve()
    to_apk = (wd / args.to_path).resolve()

    if not from_apk.exists() or not to_apk.exists():
        sys.stderr.write("missing from/to apk. put from.apk and to.apk in this folder or pass --from/--to\n")
        return 2

    base_sha = sha256_hex(from_apk)
    target_sha = sha256_hex(to_apk)
    base_short = base_sha[:12]
    target_short = target_sha[:12]

    from_version = args.from_version
    to_version = args.to_version
    from_patch_index = args.from_patch_index
    to_patch_index = args.to_patch_index
    if to_version is not None:
        if to_patch_index is None:
            to_patch_index = 0
        if from_version is None:
            from_version = to_version
        if from_patch_index is None:
            if from_version == to_version and to_patch_index > 0:
                from_patch_index = to_patch_index - 1
            else:
                from_patch_index = 0

    if args.tag:
        tag = args.tag
    elif to_version is not None:
        tag = f"v{format_version_with_patch(str(to_version), int(to_patch_index or 0))}"
    else:
        tag = f"patch_{base_short}_{target_short}"

    if to_version is not None:
        from_vs = format_version_with_patch(str(from_version), int(from_patch_index or 0))
        to_vs = format_version_with_patch(str(to_version), int(to_patch_index or 0))
        if args.format == "apkraw":
            patch_name = f"apkrawpatch_{from_vs}_to_{to_vs}_{base_short}_{target_short}.zip"
        elif args.format == "apkzip":
            patch_name = f"apkpatch_{from_vs}_to_{to_vs}_{base_short}_{target_short}.zip"
        else:
            patch_name = f"opatch_{from_vs}_to_{to_vs}_{base_short}_{target_short}.opatch.gz"
        meta_name = f"patch_{from_vs}_to_{to_vs}_{base_short}_{target_short}.json"
    else:
        if args.format == "apkraw":
            patch_name = f"apkrawpatch_{base_short}_{target_short}.zip"
        elif args.format == "apkzip":
            patch_name = f"apkpatch_{base_short}_{target_short}.zip"
        else:
            patch_name = f"patch_{base_short}_{target_short}.opatch.gz"
        meta_name = f"patch_{base_short}_{target_short}.json"

    patch_path = (wd / patch_name).resolve()
    meta_path = (wd / meta_name).resolve()

    sys.stdout.write(f"build patch {patch_name}\n")
    extra = {}
    if args.format == "apkraw":
        extra = build_apkraw_patch(from_apk, to_apk, patch_path)
    elif args.format == "apkzip":
        extra = build_apkzip_patch(from_apk, to_apk, patch_path)
    else:
        build_opatch(from_apk, to_apk, patch_path, int(args.block_size))
    patch_sha = sha256_hex(patch_path)

    meta = {
        "format": "apkraw-1" if args.format == "apkraw" else ("apkzip-1" if args.format == "apkzip" else "opatch-1"),
        "repo": args.repo,
        "tag": tag,
        "fromVersion": from_version,
        "toVersion": to_version,
        "fromPatchIndex": from_patch_index,
        "toPatchIndex": to_patch_index,
        "baseSha256": base_sha,
        "targetSha256": target_sha,
        "patchFile": patch_name,
        "patchSha256": patch_sha,
        "blockSize": int(args.block_size),
        "createdAt": int(time.time()),
    }

    meta.update(extra)

    meta_path.write_text(json.dumps(meta, indent=2, sort_keys=True), encoding="utf-8")

    if args.no_publish:
        sys.stdout.write("no publish\n")
        return 0

    token = get_token(args.token)
    notes = {
        "format": meta.get("format"),
        "tag": meta.get("tag"),
        "fromVersion": meta.get("fromVersion"),
        "toVersion": meta.get("toVersion"),
        "fromPatchIndex": meta.get("fromPatchIndex"),
        "toPatchIndex": meta.get("toPatchIndex"),
        "baseSha256": meta.get("baseSha256"),
        "targetSha256": meta.get("targetSha256"),
        "patchFile": meta.get("patchFile"),
        "patchSha256": meta.get("patchSha256"),
        "metaFile": meta_path.name,
        "createdAt": meta.get("createdAt"),
    }
    publish_release(args.repo, tag, tag, json.dumps(notes, indent=2, sort_keys=True), [patch_path, meta_path], token)
    sys.stdout.write(f"published {args.repo} {tag}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
