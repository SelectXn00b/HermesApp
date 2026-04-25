import argparse
import concurrent.futures
import html
import json
import os
import re
import ssl
import threading
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional


@dataclass
class SourceSkill:
    name: str
    listing_url: str
    listing_desc: str
    section: str


@dataclass
class GitHubTarget:
    owner: str
    repo: str
    ref: str
    path: str
    kind: str  # repo|tree|blob


_ZIP_SKILL_PATH_CACHE: dict[tuple[str, str, str], list[str]] = {}
_ISSUE_CREATE_LOCK = threading.Lock()
_ISSUE_CREATE_LAST_TS = 0.0
_ISSUE_CREATE_MIN_INTERVAL_S = 1.6


def _load_env(env_path: Path) -> None:
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.strip()
        v = v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def _build_ssl_context() -> ssl.SSLContext:
    return ssl.create_default_context()


def _decode_output(data: Optional[bytes]) -> str:
    if not data:
        return ""
    return data.decode("utf-8", errors="replace")


def _parse_link_next(link_header: str) -> str:
    if not link_header:
        return ""
    parts = [p.strip() for p in link_header.split(",")]
    for part in parts:
        if 'rel="next"' in part or "rel=next" in part:
            m = re.search(r"<([^>]+)>", part)
            if m:
                return m.group(1)
    return ""


def _http_json(
    url: str,
    method: str,
    token: str,
    ctx: ssl.SSLContext,
    payload: Optional[dict] = None,
    timeout_s: int = 60,
    retries: int = 5,
) -> tuple[object, dict]:
    data: Optional[bytes] = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")

    headers: dict[str, str] = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "OperitTools/1.0 (import_anthropic_skills.py)",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"

    backoff_s = 1.0
    last_error: Optional[BaseException] = None

    for attempt in range(retries):
        req = urllib.request.Request(url, data=data, method=method.upper())
        for k, v in headers.items():
            req.add_header(k, v)

        try:
            with urllib.request.urlopen(req, timeout=timeout_s, context=ctx) as resp:
                body = resp.read()
                text = _decode_output(body)
                resp_headers = {k.lower(): v for k, v in resp.headers.items()}
                if not text.strip():
                    return None, resp_headers
                return json.loads(text), resp_headers
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = _decode_output(e.read())
            except Exception:
                pass
            retryable = e.code in (403, 429, 500, 502, 503, 504)
            if retryable and attempt + 1 < retries:
                sleep_s = backoff_s * (2**attempt)
                retry_after_s = 0.0
                try:
                    ra = str(e.headers.get("Retry-After") or "").strip()
                    if ra:
                        retry_after_s = float(ra)
                except Exception:
                    retry_after_s = 0.0

                body_lower = body.lower()
                is_secondary_limit = e.code == 403 and "secondary rate limit" in body_lower
                if is_secondary_limit:
                    # Secondary rate limit typically needs a longer cooldown window.
                    sleep_s = max(sleep_s, 45.0 + (attempt * 15.0))

                if retry_after_s > 0:
                    sleep_s = max(sleep_s, retry_after_s)

                if is_secondary_limit:
                    print(f"[retry] GitHub secondary rate limit hit. sleep={sleep_s:.0f}s attempt={attempt + 1}/{retries}")
                elif e.code in (403, 429):
                    print(f"[retry] GitHub throttled request. http={e.code} sleep={sleep_s:.0f}s attempt={attempt + 1}/{retries}")

                time.sleep(sleep_s)
                continue
            raise RuntimeError(f"GitHub API error: HTTP {e.code} {e.reason}\n{body}")
        except (urllib.error.URLError, ssl.SSLError, OSError) as e:
            last_error = e
            if attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            break

    raise RuntimeError(
        "Request failed (network/SSL). "
        "If you are behind a proxy, set HTTPS_PROXY/HTTP_PROXY. "
        f"Original: {last_error!r}"
    )


def _http_text(
    url: str,
    token: str,
    ctx: ssl.SSLContext,
    timeout_s: int = 60,
    retries: int = 4,
) -> str:
    backoff_s = 1.0
    last_error: Optional[BaseException] = None
    host = urllib.parse.urlparse(url).netloc.lower()

    for attempt in range(retries):
        req = urllib.request.Request(url, method="GET")
        req.add_header("User-Agent", "OperitTools/1.0 (import_anthropic_skills.py)")
        if token and host in ("api.github.com",):
            req.add_header("Authorization", f"Bearer {token}")

        try:
            with urllib.request.urlopen(req, timeout=timeout_s, context=ctx) as resp:
                return _decode_output(resp.read())
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = _decode_output(e.read())
            except Exception:
                pass
            retryable = e.code in (403, 429, 500, 502, 503, 504)
            if retryable and attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            body_short = re.sub(r"\s+", " ", body).strip()
            if len(body_short) > 280:
                body_short = body_short[:280].rstrip() + "..."
            if body_short:
                raise RuntimeError(f"HTTP error when fetching {url}: {e.code} {e.reason} | {body_short}")
            raise RuntimeError(f"HTTP error when fetching {url}: {e.code} {e.reason}")
        except (urllib.error.URLError, ssl.SSLError, OSError) as e:
            last_error = e
            if attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            break

    raise RuntimeError(f"Request failed for {url}: {last_error!r}")


def _openai_chat_completion(base_url: str, api_key: str, payload: dict) -> dict:
    url = base_url.rstrip("/") + "/v1/chat/completions"
    data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {api_key}")

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"AI request failed: HTTP {e.code} {e.reason}\n{body}")


def _extract_ai_text(resp: dict) -> str:
    try:
        msg = resp["choices"][0]["message"]["content"]
    except Exception as e:
        raise RuntimeError(f"Unexpected AI response: {e}\n{json.dumps(resp, ensure_ascii=False)[:2000]}")
    return (msg or "").strip()


def _iter_issues(api_base: str, repo: str, token: str, ctx: ssl.SSLContext, state: str, labels: str) -> list[dict]:
    owner, name = repo.split("/", 1)
    params = {
        "state": state,
        "per_page": "100",
        "sort": "updated",
        "direction": "desc",
    }
    if labels:
        params["labels"] = labels

    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"
        + "?"
        + urllib.parse.urlencode(params)
    )

    out: list[dict] = []
    while url:
        data, headers = _http_json(url=url, method="GET", token=token, ctx=ctx)
        if not isinstance(data, list):
            raise RuntimeError(f"Unexpected GitHub response (expected list): {str(data)[:500]}")
        for it in data:
            if not isinstance(it, dict):
                continue
            if "pull_request" in it:
                continue
            out.append(it)
        url = _parse_link_next(str(headers.get("link", "")))

    return out


def _repo_slug_from_github_url(repo_url: str) -> str:
    s = str(repo_url or "").strip()
    m = re.match(r"^https?://github\.com/([^/\s]+)/([^/\s#?]+)", s, re.IGNORECASE)
    if not m:
        raise RuntimeError(f"Invalid source repo URL: {repo_url}")
    owner = m.group(1).strip()
    repo = m.group(2).strip()
    if repo.endswith(".git"):
        repo = repo[: -len(".git")]
    return f"{owner}/{repo}"


def _is_github_token_valid(api_base: str, token: str, ctx: ssl.SSLContext) -> bool:
    if not token:
        return False
    url = api_base.rstrip("/") + "/rate_limit"
    try:
        _http_json(url=url, method="GET", token=token, ctx=ctx, timeout_s=30, retries=1)
        return True
    except Exception:
        return False


def _normalize_url(url: str) -> str:
    u = urllib.parse.urlsplit(str(url or "").strip())
    path = u.path.rstrip("/")
    return urllib.parse.urlunsplit((u.scheme, u.netloc, path, u.query, ""))


def _normalize_title(title: str) -> str:
    t = str(title or "").strip().lower()
    t = re.sub(r"\s+", " ", t)
    return t


def _clean_html_text(raw: str) -> str:
    s = str(raw or "")
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def _html_to_text(raw_html: str) -> str:
    s = str(raw_html or "")
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", s, flags=re.IGNORECASE | re.DOTALL)
    s = re.sub(r"</?(p|div|br|li|tr|h1|h2|h3|h4|h5|h6)[^>]*>", "\n", s, flags=re.IGNORECASE)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"\r", "", s)
    s = re.sub(r"\n\s*\n+", "\n\n", s)
    s = re.sub(r"[ \t]+", " ", s)
    return s.strip()


def _list_source_skills_from_awesome_readme(readme_url: str, token: str, ctx: ssl.SSLContext) -> list[SourceSkill]:
    markdown = _http_text(url=readme_url, token=token, ctx=ctx, timeout_s=120)
    if not markdown.strip():
        raise RuntimeError(f"README is empty: {readme_url}")

    details_depth = 0
    current_section = ""
    skills: list[SourceSkill] = []
    seen_urls: set[str] = set()

    summary_re = re.compile(r"<summary>\s*<h3[^>]*>(.*?)</h3>\s*</summary>", re.IGNORECASE)
    item_re = re.compile(r"^\s*-\s+(?:\*\*)?\[([^\]]+)\]\((https?://[^)\s]+)\)(?:\*\*)?\s*(?:-\s*(.+))?$")

    for raw_line in markdown.splitlines():
        line = raw_line.strip()
        if not line:
            continue

        if line.startswith("<details"):
            details_depth += 1
            continue
        if line.startswith("</details>"):
            if details_depth > 0:
                details_depth -= 1
            continue

        sm = summary_re.search(line)
        if sm:
            current_section = _clean_html_text(sm.group(1))
            continue

        if details_depth <= 0:
            continue

        m = item_re.match(line)
        if not m:
            continue

        skill_name = _clean_html_text(m.group(1))
        skill_url = _normalize_url(str(m.group(2) or "").strip())
        skill_desc = _clean_html_text(m.group(3) or "")

        if not skill_name or not skill_url:
            continue
        if skill_url in seen_urls:
            continue

        seen_urls.add(skill_url)
        skills.append(SourceSkill(name=skill_name, listing_url=skill_url, listing_desc=skill_desc, section=current_section))

    if not skills:
        raise RuntimeError(f"No skills parsed from README: {readme_url}")

    skills.sort(key=lambda x: x.name.lower())
    return skills


def _parse_github_target(url: str) -> Optional[GitHubTarget]:
    u = urllib.parse.urlsplit(url)
    if u.scheme not in ("http", "https"):
        return None
    if u.netloc.lower() != "github.com":
        return None

    segs = [x for x in u.path.split("/") if x]
    if len(segs) < 2:
        return None

    owner = segs[0]
    repo = segs[1]
    if repo.endswith(".git"):
        repo = repo[: -len(".git")]

    if len(segs) >= 4 and segs[2] == "tree":
        ref = segs[3]
        path = "/".join(segs[4:])
        return GitHubTarget(owner=owner, repo=repo, ref=ref, path=path, kind="tree")

    if len(segs) >= 5 and segs[2] == "blob":
        ref = segs[3]
        path = "/".join(segs[4:])
        return GitHubTarget(owner=owner, repo=repo, ref=ref, path=path, kind="blob")

    return GitHubTarget(owner=owner, repo=repo, ref="", path="", kind="repo")


def _encode_ref_for_codeload(ref: str) -> str:
    return urllib.parse.quote(ref, safe="")


def _download_binary(url: str, out_path: Path, ctx: ssl.SSLContext, timeout_s: int = 120, retries: int = 4) -> None:
    backoff_s = 1.0
    last_error: Optional[BaseException] = None
    for attempt in range(retries):
        req = urllib.request.Request(url, method="GET")
        req.add_header("User-Agent", "OperitTools/1.0 (import_anthropic_skills.py)")
        try:
            with urllib.request.urlopen(req, timeout=timeout_s, context=ctx) as resp:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                with out_path.open("wb") as f:
                    while True:
                        chunk = resp.read(64 * 1024)
                        if not chunk:
                            break
                        f.write(chunk)
                return
        except (urllib.error.HTTPError, urllib.error.URLError, ssl.SSLError, OSError) as e:
            last_error = e
            if attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            break
    raise RuntimeError(f"Failed to download binary: {url} | err={last_error!r}")


def _zip_cache_path(owner: str, repo: str, ref: str) -> Path:
    safe = f"{owner}__{repo}__{ref}".replace("/", "_").replace("\\", "_")
    return Path(r"D:\Code\prog\assistance\temp") / "skill-market-zip-cache" / f"{safe}.zip"


def _get_repo_zip_path(owner: str, repo: str, ref: str, ctx: ssl.SSLContext) -> Path:
    p = _zip_cache_path(owner=owner, repo=repo, ref=ref)
    if p.exists() and p.stat().st_size > 0:
        return p
    url = f"https://codeload.github.com/{owner}/{repo}/zip/{_encode_ref_for_codeload(ref)}"
    _download_binary(url=url, out_path=p, ctx=ctx, timeout_s=180)
    return p


def _strip_zip_root(path_in_zip: str) -> str:
    parts = [x for x in str(path_in_zip or "").split("/") if x]
    if len(parts) <= 1:
        return ""
    return "/".join(parts[1:])


def _list_skill_md_paths_from_repo_zip(owner: str, repo: str, ref: str, ctx: ssl.SSLContext) -> list[str]:
    key = (owner.lower(), repo.lower(), ref)
    cached = _ZIP_SKILL_PATH_CACHE.get(key)
    if cached is not None:
        return list(cached)

    zip_path = _get_repo_zip_path(owner=owner, repo=repo, ref=ref, ctx=ctx)
    out: list[str] = []
    with zipfile.ZipFile(zip_path, "r") as zf:
        for n in zf.namelist():
            rp = _strip_zip_root(n)
            if not rp:
                continue
            if rp.endswith("/"):
                continue
            low = rp.lower()
            if low == "skill.md" or low.endswith("/skill.md"):
                out.append(rp)
    out = sorted(set(out))
    _ZIP_SKILL_PATH_CACHE[key] = out
    return list(out)


def _candidate_refs(ref: str) -> list[str]:
    out: list[str] = []
    for r in (ref, "main", "master"):
        rr = str(r or "").strip()
        if not rr:
            continue
        if rr not in out:
            out.append(rr)
    return out


def _skill_dir_from_skill_md(path: str) -> str:
    p = str(path or "").strip().strip("/")
    if not p:
        return ""
    if "/" not in p:
        return ""
    return p.rsplit("/", 1)[0]


def _is_under_prefix(path: str, prefix: str) -> bool:
    p = str(path or "").strip().strip("/")
    pre = str(prefix or "").strip().strip("/")
    if not pre:
        return True
    return p == pre or p.startswith(pre + "/")


def _expand_source_skill_by_skill_md(skill: SourceSkill, ctx: ssl.SSLContext) -> list[SourceSkill]:
    t = _parse_github_target(skill.listing_url)
    if not t:
        # Strict rule: only GitHub repositories that actually contain SKILL.md are importable.
        return []

    # blob URL: if already points to SKILL.md keep as-is; otherwise only keep when sibling SKILL exists
    if t.kind == "blob":
        lp = t.path.lower()
        if lp.endswith("skill.md"):
            dir_path = _skill_dir_from_skill_md(t.path)
            tree_url = f"https://github.com/{t.owner}/{t.repo}/tree/{t.ref}/{dir_path}".rstrip("/")
            return [SourceSkill(name=skill.name, listing_url=tree_url, listing_desc=skill.listing_desc, section=skill.section)]
        return []

    prefix = t.path if t.kind == "tree" else ""
    refs = _candidate_refs(t.ref if t.kind == "tree" else "")
    expanded: list[SourceSkill] = []
    seen_urls: set[str] = set()

    for ref in refs:
        try:
            skill_md_paths = _list_skill_md_paths_from_repo_zip(owner=t.owner, repo=t.repo, ref=ref, ctx=ctx)
        except Exception:
            continue
        filtered = [p for p in skill_md_paths if _is_under_prefix(_skill_dir_from_skill_md(p), prefix)]
        if not filtered and prefix:
            # Some curated links drift after repo refactors. If the exact prefix no longer exists,
            # try remapping by leaf directory name (e.g. ".github/skills/foo" -> ".../skills/foo").
            wanted_leaf = prefix.strip("/").split("/")[-1].lower()
            by_leaf: list[str] = []
            for p in skill_md_paths:
                d = _skill_dir_from_skill_md(p)
                if not d:
                    continue
                leaf = d.split("/")[-1].lower()
                if leaf == wanted_leaf:
                    by_leaf.append(p)
            if by_leaf:
                by_leaf.sort(key=lambda x: (_skill_dir_from_skill_md(x).count("/"), _skill_dir_from_skill_md(x)))
                filtered = [by_leaf[0]]
        if not filtered:
            continue

        for mdp in filtered:
            d = _skill_dir_from_skill_md(mdp)
            if d:
                repo_url = f"https://github.com/{t.owner}/{t.repo}/tree/{ref}/{d}"
                leaf = d.split("/")[-1]
            else:
                repo_url = f"https://github.com/{t.owner}/{t.repo}/tree/{ref}"
                leaf = t.repo
            repo_url = _normalize_url(repo_url)
            if repo_url in seen_urls:
                continue
            seen_urls.add(repo_url)
            expanded.append(
                SourceSkill(
                    name=f"{t.owner}/{leaf}",
                    listing_url=repo_url,
                    listing_desc=skill.listing_desc,
                    section=skill.section,
                )
            )

        # first valid ref is enough
        if expanded:
            break

    return expanded


def _repo_key_for_skill(skill: SourceSkill) -> str:
    target = _parse_github_target(skill.listing_url)
    if not target:
        return _normalize_url(skill.listing_url).lower()
    return f"{target.owner.lower()}/{target.repo.lower()}"


def _get_tree_sha_for_ref(api_base: str, owner: str, repo: str, token: str, ctx: ssl.SSLContext, ref: str) -> str:
    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}/commits/{urllib.parse.quote(ref)}"
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    if not isinstance(data, dict):
        return ""
    commit = data.get("commit")
    if isinstance(commit, dict):
        tree = commit.get("tree")
        if isinstance(tree, dict):
            sha = str(tree.get("sha") or "").strip()
            return sha
    return ""


def _github_default_branch(api_base: str, owner: str, repo: str, token: str, ctx: ssl.SSLContext) -> str:
    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}"
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    if not isinstance(data, dict):
        raise RuntimeError(f"Unexpected repo response for {owner}/{repo}")
    branch = str(data.get("default_branch") or "").strip()
    if not branch:
        raise RuntimeError(f"Cannot get default branch for {owner}/{repo}")
    return branch


def _github_contents(api_base: str, owner: str, repo: str, ref: str, path: str, token: str, ctx: ssl.SSLContext) -> object:
    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}/contents/{urllib.parse.quote(path.strip('/'))}"
        + "?"
        + urllib.parse.urlencode({"ref": ref})
    )
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    return data


def _download_github_raw(owner: str, repo: str, ref: str, path: str, token: str, ctx: ssl.SSLContext) -> tuple[str, str]:
    url = f"https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path.lstrip('/')}"
    content = _http_text(url=url, token=token, ctx=ctx, timeout_s=90)
    file_name = path.split("/")[-1]
    return file_name, content


def _pick_markdown_file_from_contents(files: list[dict]) -> Optional[dict]:
    if not files:
        return None

    lower = {str(it.get("name") or "").lower(): it for it in files}
    for name in ("skill.md", "readme.md"):
        if name in lower:
            return lower[name]

    md_files = [it for it in files if str(it.get("name") or "").lower().endswith(".md")]
    md_files.sort(key=lambda it: str(it.get("name") or "").lower())
    return md_files[0] if md_files else None


def _fetch_markdown_from_github_path(
    api_base: str,
    owner: str,
    repo: str,
    ref: str,
    path: str,
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[str, str]:
    data = _github_contents(api_base=api_base, owner=owner, repo=repo, ref=ref, path=path, token=token, ctx=ctx)

    if isinstance(data, dict):
        if str(data.get("type") or "") != "file":
            raise RuntimeError(f"Path is not a file: {owner}/{repo}@{ref}:{path}")
        name = str(data.get("name") or "").strip()
        if not name.lower().endswith(".md"):
            raise RuntimeError(f"Path is not markdown: {owner}/{repo}@{ref}:{path}")
        download_url = str(data.get("download_url") or "").strip()
        if not download_url:
            raise RuntimeError(f"No download_url for file: {owner}/{repo}@{ref}:{path}")
        return name, _http_text(url=download_url, token=token, ctx=ctx, timeout_s=90)

    if isinstance(data, list):
        files = [it for it in data if isinstance(it, dict) and str(it.get("type") or "") == "file"]
        pick = _pick_markdown_file_from_contents(files)
        if not pick:
            raise RuntimeError(f"No markdown file found under: {owner}/{repo}@{ref}:{path}")
        name = str(pick.get("name") or "").strip()
        download_url = str(pick.get("download_url") or "").strip()
        if not download_url:
            p = str(pick.get("path") or "").strip()
            return _download_github_raw(owner=owner, repo=repo, ref=ref, path=p, token=token, ctx=ctx)
        return name, _http_text(url=download_url, token=token, ctx=ctx, timeout_s=90)

    raise RuntimeError(f"Unexpected contents response for {owner}/{repo}@{ref}:{path}")


def _list_repo_markdown_paths(api_base: str, owner: str, repo: str, ref: str, token: str, ctx: ssl.SSLContext) -> list[str]:
    tree_sha = _get_tree_sha_for_ref(api_base=api_base, owner=owner, repo=repo, token=token, ctx=ctx, ref=ref)
    if not tree_sha:
        raise RuntimeError(f"Failed to resolve tree sha for {owner}/{repo}@{ref}")

    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}/git/trees/{urllib.parse.quote(tree_sha)}?recursive=1"
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx, timeout_s=120)
    if not isinstance(data, dict):
        raise RuntimeError(f"Unexpected git tree response for {owner}/{repo}@{ref}")

    tree = data.get("tree")
    if not isinstance(tree, list):
        raise RuntimeError(f"Missing tree for {owner}/{repo}@{ref}")

    paths: list[str] = []
    for it in tree:
        if not isinstance(it, dict):
            continue
        if str(it.get("type") or "") != "blob":
            continue
        p = str(it.get("path") or "")
        if p.lower().endswith(".md"):
            paths.append(p)
    return paths


def _skill_hint(skill: SourceSkill) -> str:
    name_part = str(skill.name or "").split("/")[-1].strip().lower()
    if not name_part:
        return ""
    return re.sub(r"[^a-z0-9\-_.]+", "", name_part)


def _best_markdown_path(paths: list[str], hint: str) -> str:
    if not paths:
        return ""

    def score(path: str) -> tuple[int, int, str]:
        p = path.lower()
        s = 0
        if p.endswith("/skill.md") or p == "skill.md":
            s -= 100
        elif p.endswith("/readme.md") or p == "readme.md":
            s -= 70
        else:
            s -= 10

        if "/skills/" in p or "/plugins/" in p:
            s -= 20

        if hint and hint in p:
            s -= 30

        depth = p.count("/")
        return (s, depth, p)

    best = min(paths, key=score)
    return best


def _fetch_markdown_from_generic_url(url: str, token: str, ctx: ssl.SSLContext) -> tuple[str, str]:
    text = _http_text(url=url, token=token, ctx=ctx, timeout_s=90)
    if not text.strip():
        raise RuntimeError(f"Source URL is empty: {url}")

    lower = text[:1000].lower()
    if "<html" in lower or "<!doctype html" in lower:
        text = _html_to_text(text)

    file_name = Path(urllib.parse.urlsplit(url).path).name or "SOURCE.md"
    return file_name, text


def _try_download_github_raw_candidates(
    owner: str,
    repo: str,
    ref: str,
    candidates: list[str],
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[str, str]:
    last_error: Optional[Exception] = None
    for p in candidates:
        try:
            return _download_github_raw(owner=owner, repo=repo, ref=ref, path=p, token=token, ctx=ctx)
        except Exception as e:
            last_error = e
            continue
    raise RuntimeError(f"No raw candidate resolved for {owner}/{repo}@{ref}: {candidates} | last={last_error}")


def _extract_github_blob_candidates_from_html(page_html: str, owner: str, repo: str) -> list[tuple[str, str]]:
    txt = html.unescape(str(page_html or ""))
    owner_q = re.escape(owner)
    repo_q = re.escape(repo)
    pattern = re.compile(
        rf'href="/{owner_q}/{repo_q}/blob/([^"/?#]+?)/([^"#?]+?)"',
        re.IGNORECASE,
    )
    out: list[tuple[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for m in pattern.finditer(txt):
        ref = urllib.parse.unquote(str(m.group(1) or "").strip())
        path = urllib.parse.unquote(str(m.group(2) or "").strip())
        if not ref or not path:
            continue
        low = path.lower()
        if not (low.endswith(".md") or low.endswith(".mdx")):
            continue
        key = (ref, path)
        if key in seen:
            continue
        seen.add(key)
        out.append(key)
    return out


def _fetch_markdown_from_github_page_listing(
    page_url: str,
    owner: str,
    repo: str,
    ref_hint: str,
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[str, str]:
    page_html = _http_text(url=page_url, token=token, ctx=ctx, timeout_s=60)
    candidates = _extract_github_blob_candidates_from_html(page_html=page_html, owner=owner, repo=repo)
    if not candidates:
        raise RuntimeError(f"No markdown blob links found on GitHub page: {page_url}")

    def _score(x: tuple[str, str]) -> tuple[int, str, str]:
        ref, path = x
        p = path.lower()
        s = 100
        if ref_hint and ref == ref_hint:
            s -= 30
        name = Path(p).name
        if name == "skill.md":
            s -= 50
        elif name == "readme.md":
            s -= 40
        elif name == "readme.mdx":
            s -= 35
        if "/skills/" in p or "/plugins/" in p:
            s -= 10
        s += p.count("/")
        return (s, ref, p)

    ordered = sorted(candidates, key=_score)
    last_error: Optional[Exception] = None
    for ref, path in ordered[:30]:
        try:
            return _download_github_raw(owner=owner, repo=repo, ref=ref, path=path, token=token, ctx=ctx)
        except Exception as e:
            last_error = e
            continue
    raise RuntimeError(f"Markdown blob candidates found but all failed on page: {page_url} | last={last_error}")


def _fetch_skill_markdown_without_api(
    skill: SourceSkill,
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[str, str]:
    target = _parse_github_target(skill.listing_url)
    if not target:
        raise RuntimeError(f"Only GitHub skill sources are supported: {skill.listing_url}")

    if target.kind == "blob":
        if not target.path.lower().endswith("skill.md"):
            raise RuntimeError(f"GitHub blob is not SKILL.md: {skill.listing_url}")
        return _download_github_raw(
            owner=target.owner,
            repo=target.repo,
            ref=target.ref,
            path=target.path,
            token=token,
            ctx=ctx,
        )

    if target.kind == "tree" and target.path:
        if target.path.lower().endswith("skill.md"):
            return _download_github_raw(
                owner=target.owner,
                repo=target.repo,
                ref=target.ref,
                path=target.path,
                token=token,
                ctx=ctx,
            )
        raw_path = f"{target.path.rstrip('/')}/SKILL.md"
        return _download_github_raw(
            owner=target.owner,
            repo=target.repo,
            ref=target.ref,
            path=raw_path,
            token=token,
            ctx=ctx,
        )

    if target.kind == "repo":
        for ref in ("main", "master"):
            try:
                return _download_github_raw(
                    owner=target.owner,
                    repo=target.repo,
                    ref=ref,
                    path="SKILL.md",
                    token=token,
                    ctx=ctx,
                )
            except Exception:
                continue
        raise RuntimeError(f"No root SKILL.md in repo URL: {skill.listing_url}")

    raise RuntimeError(f"Unsupported source URL: {skill.listing_url}")


def _fetch_skill_markdown(
    api_base: str,
    skill: SourceSkill,
    token: str,
    ctx: ssl.SSLContext,
    allow_api_fallback: bool = True,
) -> tuple[str, str]:
    try:
        return _fetch_skill_markdown_without_api(skill=skill, token=token, ctx=ctx)
    except Exception as e_no_api:
        if not allow_api_fallback:
            raise e_no_api

    target = _parse_github_target(skill.listing_url)
    if not target:
        raise RuntimeError(f"Only GitHub skill sources are supported: {skill.listing_url}")

    if target.kind == "blob":
        if not target.path.lower().endswith("skill.md"):
            raise RuntimeError(f"GitHub blob is not SKILL.md: {skill.listing_url}")
        return _download_github_raw(
            owner=target.owner,
            repo=target.repo,
            ref=target.ref,
            path=target.path,
            token=token,
            ctx=ctx,
        )

    if target.kind == "tree" and target.path:
        if target.path.lower().endswith("skill.md"):
            return _download_github_raw(
                owner=target.owner,
                repo=target.repo,
                ref=target.ref,
                path=target.path,
                token=token,
                ctx=ctx,
            )
        return _download_github_raw(
            owner=target.owner,
            repo=target.repo,
            ref=target.ref,
            path=f"{target.path.rstrip('/')}/SKILL.md",
            token=token,
            ctx=ctx,
        )

    if target.kind == "tree" and not target.ref:
        raise RuntimeError(f"Invalid tree URL (missing ref): {skill.listing_url}")

    ref = target.ref or _github_default_branch(api_base=api_base, owner=target.owner, repo=target.repo, token=token, ctx=ctx)
    return _download_github_raw(
        owner=target.owner,
        repo=target.repo,
        ref=ref,
        path="SKILL.md",
        token=token,
        ctx=ctx,
    )


def _ai_generate_description(
    ai_base_url: str,
    ai_api_key: str,
    ai_model: str,
    ai_temperature: float,
    skill: SourceSkill,
    md_file_name: str,
    md_content: str,
) -> str:
    md = (md_content or "").strip()
    if len(md) > 8000:
        md = md[:8000].rstrip() + "\n\n[truncated]\n"

    system_prompt = (
        "You write concise marketplace descriptions for software skills. "
        "You must only use the given source content. "
        "Do not invent capabilities not supported by the text."
    )

    user_prompt = textwrap.dedent(
        f"""
        Write a short Chinese description for an Operit Skill Market listing.

        Constraints:
        - Output ONLY the description text, no Markdown headings, no lists.
        - 1-2 sentences, ideally 30-120 Chinese characters.
        - Do not include "##" or "**".

        Skill name: {skill.name}
        Section: {skill.section}
        Curated one-line description: {skill.listing_desc}
        Listing URL: {skill.listing_url}
        Source file: {md_file_name}

        Source content:
        {md}
        """
    ).strip()

    payload: dict = {
        "model": ai_model,
        "temperature": ai_temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    resp = _openai_chat_completion(base_url=ai_base_url, api_key=ai_api_key, payload=payload)
    out = _extract_ai_text(resp)
    out = out.replace("\r", " ").replace("\n", " ").strip()
    out = re.sub(r"\s+", " ", out)
    return out


def _build_operit_issue_body(description: str, repository_url: str, version: str) -> str:
    metadata = {
        "description": description,
        "repositoryUrl": repository_url,
        "category": "",
        "tags": "",
        "version": version,
    }

    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    lines: list[str] = []
    lines.append(f"<!-- operit-skill-json: {json.dumps(metadata, ensure_ascii=False, separators=(',', ':'))} -->")
    lines.append(f"<!-- operit-parser-version: {version} -->")
    lines.append("")
    lines.append("## 📋 Skill 信息")
    lines.append("")
    lines.append(f"**描述:** {description}")
    lines.append("")

    lines.append("## 🔗 仓库信息")
    lines.append("")
    lines.append(f"**仓库地址:** {repository_url}")
    lines.append("")

    lines.append("## 📦 安装方式")
    lines.append("")
    lines.append("1. 打开 Operit → 包管理 → Skills")
    lines.append("2. 点击「导入 Skill」→ 「从仓库导入」")
    lines.append(f"3. 输入仓库地址：`{repository_url}`")
    lines.append("4. 确认导入")
    lines.append("")

    lines.append("## 🛠️ 技术信息")
    lines.append("")
    lines.append("| 项目 | 值 |")
    lines.append("|------|-----|")
    lines.append("| 发布平台 | Operit Skill 市场 |")
    lines.append(f"| 解析版本 | {version} |")
    lines.append(f"| 发布时间 | {now_str} |")
    lines.append("| 状态 | ⏳ Pending Review |")
    lines.append("")

    return "\n".join(lines)


def _create_issue(api_base: str, repo: str, token: str, ctx: ssl.SSLContext, title: str, body: str, labels: list[str]) -> dict:
    global _ISSUE_CREATE_LAST_TS
    owner, name = repo.split("/", 1)
    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"

    payload = {
        "title": title,
        "body": body,
        "labels": labels,
    }

    # Content creation is especially sensitive to GitHub secondary rate limits.
    # Throttle globally so submit workers can still prepare in parallel but create steadily.
    with _ISSUE_CREATE_LOCK:
        now_ts = time.time()
        wait_s = _ISSUE_CREATE_MIN_INTERVAL_S - (now_ts - _ISSUE_CREATE_LAST_TS)
        if wait_s > 0:
            time.sleep(wait_s)
        _ISSUE_CREATE_LAST_TS = time.time()

    data, _ = _http_json(
        url=url,
        method="POST",
        token=token,
        ctx=ctx,
        payload=payload,
        timeout_s=120,
        retries=8,
    )
    if isinstance(data, dict):
        return data
    raise RuntimeError(f"Unexpected create issue response: {str(data)[:500]}")


def _safe_title(base: str) -> str:
    t = (base or "").strip()
    if not t:
        t = "Untitled Skill"
    t = t.replace("\r", " ").replace("\n", " ").strip()
    t = re.sub(r"\s+", " ", t)
    if len(t) > 120:
        t = t[:120].rstrip()
    return t


def _issue_title_name(skill_name: str) -> str:
    s = str(skill_name or "").strip()
    if "/" in s:
        s = s.split("/")[-1].strip()
    return s or "Untitled Skill"


def _safe_file_stem(base: str, limit: int = 80) -> str:
    s = (base or "").strip().lower()
    if not s:
        s = "untitled-skill"
    s = re.sub(r"[^\w\-.]+", "-", s, flags=re.UNICODE)
    s = re.sub(r"-{2,}", "-", s).strip("-._")
    if not s:
        s = "untitled-skill"
    if len(s) > limit:
        s = s[:limit].rstrip("-._")
    return s or "untitled-skill"


def _write_dry_run_issue_file(run_dir: Path, index: int, title: str, repository_url: str, body: str) -> Path:
    run_dir.mkdir(parents=True, exist_ok=True)
    stem = _safe_file_stem(title)
    out_path = run_dir / f"{index:04d}-{stem}.md"
    content = "\n".join(
        [
            f"# {title}",
            "",
            f"- repositoryUrl: {repository_url}",
            "",
            "## Issue Body",
            "",
            body,
            "",
        ]
    )
    out_path.write_text(content, encoding="utf-8")
    return out_path


def _write_validate_progress(
    progress_path: Path,
    total: int,
    processed: int,
    ok: int,
    failed: int,
    failures: list[dict],
) -> None:
    payload = {
        "total": total,
        "processed": processed,
        "ok": ok,
        "failed": failed,
        "failedItems": failures,
    }
    progress_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Import skills from awesome-agent-skills into Operit Skill Market by creating GitHub issues."
    )
    parser.add_argument(
        "--env",
        default=str(Path(__file__).resolve().parent / ".env"),
        help="Path to .env file (default: tools/github/.env)",
    )
    parser.add_argument(
        "--target-repo",
        default="AAswordman/OperitSkillMarket",
        help="Target GitHub repo to create issues in",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit how many skills to import (0 means all)",
    )
    parser.add_argument(
        "--only-skill",
        default="",
        help="Only import a single skill by exact name",
    )
    parser.add_argument(
        "--sample-per-repo",
        action="store_true",
        help="Only keep one sampled skill per repository",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Do not create issues; only print planned actions",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print progress logs",
    )
    parser.add_argument(
        "--validate-sources",
        action="store_true",
        help="Only validate source links can be resolved to markdown (no AI, no issue creation)",
    )
    parser.add_argument(
        "--jobs",
        type=int,
        default=12,
        help="Parallel worker count for expansion/validation/submit/dry-run (default: 12)",
    )
    parser.add_argument(
        "--submit-jobs",
        type=int,
        default=0,
        help="Parallel worker count for real issue submission only (default: auto=min(3, --jobs))",
    )

    args = parser.parse_args()

    _load_env(Path(args.env))

    github_token = os.environ.get("GITHUB_TOKEN", "").strip()
    github_api_base = os.environ.get("GITHUB_API_URL", "https://api.github.com").strip() or "https://api.github.com"

    ai_base_url = os.environ.get("AI_BASE_URL", "").strip()
    ai_api_key = os.environ.get("AI_API_KEY", "").strip()
    ai_model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    try:
        ai_temperature = float(os.environ.get("AI_TEMPERATURE", "0.2") or 0.2)
    except Exception:
        ai_temperature = 0.2

    if not ai_base_url or not ai_api_key:
        print("Missing AI_BASE_URL/AI_API_KEY. Fill tools/github/.env (gitignored) or set env vars.")
        return 2

    if not github_token and not args.dry_run:
        print("Missing GITHUB_TOKEN. Creating issues requires a GitHub token.")
        return 2

    if not args.target_repo or "/" not in args.target_repo:
        print("Invalid --target-repo. Expect owner/name.")
        return 2

    ctx = _build_ssl_context()
    jobs = max(1, int(args.jobs or 1))
    submit_jobs = max(1, int(args.submit_jobs or min(3, jobs)))

    source_repo_url = "https://github.com/VoltAgent/awesome-agent-skills"
    source_readme_url = "https://raw.githubusercontent.com/VoltAgent/awesome-agent-skills/main/README.md"
    labels = ["skill-plugin"]
    issue_version = "v1"
    skip_existing = not args.dry_run

    try:
        source_repo = _repo_slug_from_github_url(source_repo_url)
    except Exception as e:
        print(str(e))
        return 2

    token_valid = _is_github_token_valid(api_base=github_api_base, token=github_token, ctx=ctx)
    read_github_token = github_token if token_valid else ""
    issue_read_token = "" if args.dry_run else read_github_token

    if not args.dry_run and not token_valid:
        print("Invalid GITHUB_TOKEN: GitHub API auth failed (401 Bad credentials).")
        print("Please update GITHUB_TOKEN, then retry.")
        return 2

    if args.verbose:
        print(f"[info] target repo: {args.target_repo}")
        print(f"[info] labels: {','.join(labels)}")
        print(f"[info] source repo: {source_repo}")
        print(f"[info] source readme: {source_readme_url}")
        print(f"[info] read token: {'enabled' if read_github_token else 'disabled'}")

    existing_issue_titles: set[str] = set()
    if skip_existing:
        issues = _iter_issues(
            api_base=github_api_base,
            repo=args.target_repo,
            token=issue_read_token,
            ctx=ctx,
            state="open",
            labels="",
        )
        for it in issues:
            title_key = _normalize_title(str(it.get("title") or ""))
            if title_key:
                existing_issue_titles.add(title_key)

    source_skills = _list_source_skills_from_awesome_readme(readme_url=source_readme_url, token=read_github_token, ctx=ctx)

    skills: list[SourceSkill] = []
    expand_no_skill = 0
    expand_error = 0
    if args.verbose:
        print(f"[info] expand jobs: {jobs}")

    def _expand_one(item_index: int, source_skill: SourceSkill) -> dict:
        try:
            expanded = _expand_source_skill_by_skill_md(skill=source_skill, ctx=ctx)
            return {
                "ok": True,
                "index": item_index,
                "source": source_skill,
                "expanded": expanded,
            }
        except Exception as e:
            return {
                "ok": False,
                "index": item_index,
                "source": source_skill,
                "error": str(e),
            }

    total_sources = len(source_skills)
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
        future_to_input = {
            executor.submit(_expand_one, idx, s): (idx, s)
            for idx, s in enumerate(source_skills, start=1)
        }
        for done_idx, future in enumerate(concurrent.futures.as_completed(future_to_input), start=1):
            idx, s = future_to_input[future]
            res = future.result()
            if not bool(res.get("ok")):
                expand_error += 1
                if args.verbose:
                    print(f"[skip] expand failed: {s.name} | {res.get('error')}")
                continue

            ex = list(res.get("expanded") or [])
            if not ex:
                expand_no_skill += 1
                if args.verbose:
                    print(f"[skip] no SKILL.md found for: {s.name} | {s.listing_url}")
                continue

            skills.extend(ex)
            if args.verbose and len(ex) > 1:
                print(f"[info] expanded skill-set: {s.name} -> {len(ex)} skills")

            if args.verbose and (done_idx % 25 == 0 or done_idx == total_sources):
                print(f"[info] expand progress: {done_idx}/{total_sources}")

    if args.verbose:
        print(
            f"[info] source links: {len(source_skills)} | expanded skills: {len(skills)} "
            f"| skipped(no-skill): {expand_no_skill} | expand-errors: {expand_error}"
        )

    unique_by_url: dict[str, SourceSkill] = {}
    for s in skills:
        u = _normalize_url(s.listing_url)
        if u not in unique_by_url:
            unique_by_url[u] = s
    skills = list(unique_by_url.values())
    skills.sort(key=lambda x: (x.name.lower(), _normalize_url(x.listing_url)))

    if str(args.only_skill).strip():
        only = str(args.only_skill).strip()
        only_l = only.lower()
        skills = [s for s in skills if s.name == only or s.name.lower() == only_l or _normalize_url(s.listing_url) == _normalize_url(only)]
        if not skills:
            print(f"No such skill after SKILL.md expansion: {only}")
            return 2

    if args.sample_per_repo:
        sampled: dict[str, SourceSkill] = {}
        for s in skills:
            key = _repo_key_for_skill(s)
            if key not in sampled:
                sampled[key] = s
        if args.verbose:
            print(f"[info] sample-per-repo enabled: {len(skills)} -> {len(sampled)}")
        skills = list(sampled.values())

    if args.limit and args.limit > 0:
        skills = skills[: int(args.limit)]

    pre_skipped_title = 0
    if skip_existing and existing_issue_titles:
        before = len(skills)
        skills = [
            s
            for s in skills
            if _normalize_title(_safe_title(_issue_title_name(s.name))) not in existing_issue_titles
        ]
        pre_skipped_title = max(0, before - len(skills))
        if args.verbose:
            print(f"[info] pre-skip existing titles: {before} -> {len(skills)} (skipped={pre_skipped_title})")

    if args.validate_sources:
        run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        report_dir = Path(r"D:\Code\prog\assistance\temp") / "skill-market-validate" / run_id
        report_dir.mkdir(parents=True, exist_ok=True)
        report_path = report_dir / "report.json"
        progress_path = report_dir / "progress.json"

        ok = 0
        fail = 0
        failures: list[dict] = []
        total = len(skills)
        processed = 0
        print(f"[validate] output dir: {report_dir}")
        print(f"[validate] jobs: {jobs}")
        _write_validate_progress(
            progress_path=progress_path,
            total=total,
            processed=processed,
            ok=ok,
            failed=fail,
            failures=failures,
        )

        def _validate_one(skill: SourceSkill) -> dict:
            try:
                md_file_name, md_content = _fetch_skill_markdown(
                    api_base=github_api_base,
                    skill=skill,
                    token=read_github_token,
                    ctx=ctx,
                    allow_api_fallback=False,
                )
                if not (md_content or "").strip():
                    raise RuntimeError("resolved markdown is empty")
                return {
                    "ok": True,
                    "name": skill.name,
                    "url": skill.listing_url,
                    "md": md_file_name,
                }
            except Exception as e:
                return {
                    "ok": False,
                    "name": skill.name,
                    "url": skill.listing_url,
                    "error": str(e),
                }

        with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
            future_to_skill = {executor.submit(_validate_one, skill): skill for skill in skills}
            for idx, future in enumerate(concurrent.futures.as_completed(future_to_skill), start=1):
                res = future.result()
                processed = idx
                if bool(res.get("ok")):
                    ok += 1
                    if args.verbose:
                        print(f"[validate][ok] {res.get('name')} -> {res.get('md')}")
                else:
                    fail += 1
                    msg = str(res.get("error") or "")
                    failures.append(
                        {
                            "name": str(res.get("name") or ""),
                            "url": str(res.get("url") or ""),
                            "error": msg,
                        }
                    )
                    print(f"[validate][fail] {res.get('name')}: {msg}")

                if args.verbose and (idx % 25 == 0 or idx == total):
                    print(f"[validate] progress: {idx}/{total} ok={ok} failed={fail}")

                _write_validate_progress(
                    progress_path=progress_path,
                    total=total,
                    processed=processed,
                    ok=ok,
                    failed=fail,
                    failures=failures,
                )

        report = {
            "total": total,
            "processed": total,
            "ok": ok,
            "failed": fail,
            "failedItems": failures,
        }
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[validate] report: {report_path}")
        print(f"[validate] done. total={total} ok={ok} failed={fail}")
        return 0 if fail == 0 else 1

    created = 0
    skipped = pre_skipped_title
    failed = 0
    dry_run_run_dir: Optional[Path] = None
    if args.dry_run:
        run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        dry_run_run_dir = Path(r"D:\Code\prog\assistance\temp") / "skill-market-dry-run" / run_id
        dry_run_run_dir.mkdir(parents=True, exist_ok=True)
        print(f"[dry-run] output dir: {dry_run_run_dir}")
        print(f"[dry-run] jobs: {jobs}")

        def _prepare_dry_run_item(item_index: int, skill: SourceSkill) -> dict:
            repo_url = _normalize_url(skill.listing_url)

            md_file_name, md_content = _fetch_skill_markdown(
                api_base=github_api_base,
                skill=skill,
                token=read_github_token,
                ctx=ctx,
            )

            description = _ai_generate_description(
                ai_base_url=ai_base_url,
                ai_api_key=ai_api_key,
                ai_model=ai_model,
                ai_temperature=ai_temperature,
                skill=skill,
                md_file_name=md_file_name,
                md_content=md_content,
            )
            if not description:
                raise RuntimeError("AI returned empty description")

            title = _safe_title(_issue_title_name(skill.name))
            body = _build_operit_issue_body(description=description, repository_url=repo_url, version=issue_version)
            return {
                "ok": True,
                "skip": False,
                "index": item_index,
                "skill_name": skill.name,
                "repo_url": repo_url,
                "title": title,
                "body": body,
            }

        total = len(skills)
        with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
            futures = {
                executor.submit(_prepare_dry_run_item, idx, skill): (idx, skill)
                for idx, skill in enumerate(skills, start=1)
            }
            for done_idx, future in enumerate(concurrent.futures.as_completed(futures), start=1):
                idx, skill = futures[future]
                try:
                    res = future.result()
                    if bool(res.get("skip")):
                        skipped += 1
                        print(f"[skip] {res.get('skill_name')} already exists: {res.get('repo_url')}")
                    else:
                        out_path = _write_dry_run_issue_file(
                            run_dir=dry_run_run_dir or Path(r"D:\Code\prog\assistance\temp"),
                            index=int(res.get("index") or idx),
                            title=str(res.get("title") or skill.name),
                            repository_url=str(res.get("repo_url") or ""),
                            body=str(res.get("body") or ""),
                        )
                        created += 1
                        print(f"[dry-run] create issue: {res.get('title')} | {res.get('repo_url')}")
                        print(f"[dry-run] wrote: {out_path}")
                except Exception as e:
                    failed += 1
                    print(f"[fail] {skill.name}: {e}")

                if args.verbose and (done_idx % 25 == 0 or done_idx == total):
                    print(f"[dry-run] progress: {done_idx}/{total} created={created} skipped={skipped} failed={failed}")
    else:
        total = len(skills)
        print(f"[submit] jobs: {submit_jobs}")
        planned_title_lock = threading.Lock()
        planned_title_keys: set[str] = set()

        def _submit_one(item_index: int, skill: SourceSkill) -> dict:
            repo_url = _normalize_url(skill.listing_url)
            title = _safe_title(_issue_title_name(skill.name))
            title_key = _normalize_title(title)

            if skip_existing and title_key in existing_issue_titles:
                return {
                    "ok": True,
                    "skip": True,
                    "skip_reason": "title",
                    "index": item_index,
                    "skill_name": skill.name,
                    "repo_url": repo_url,
                    "title": title,
                }

            # Also avoid duplicate title generation within the same run.
            with planned_title_lock:
                if title_key in planned_title_keys:
                    return {
                        "ok": True,
                        "skip": True,
                        "skip_reason": "title-in-run",
                        "index": item_index,
                        "skill_name": skill.name,
                        "repo_url": repo_url,
                        "title": title,
                    }
                planned_title_keys.add(title_key)

            md_file_name, md_content = _fetch_skill_markdown(
                api_base=github_api_base,
                skill=skill,
                token=read_github_token,
                ctx=ctx,
            )

            description = _ai_generate_description(
                ai_base_url=ai_base_url,
                ai_api_key=ai_api_key,
                ai_model=ai_model,
                ai_temperature=ai_temperature,
                skill=skill,
                md_file_name=md_file_name,
                md_content=md_content,
            )
            if not description:
                raise RuntimeError("AI returned empty description")

            body = _build_operit_issue_body(description=description, repository_url=repo_url, version=issue_version)

            issue = _create_issue(
                api_base=github_api_base,
                repo=args.target_repo,
                token=github_token,
                ctx=ctx,
                title=title,
                body=body,
                labels=labels,
            )
            return {
                "ok": True,
                "skip": False,
                "index": item_index,
                "skill_name": skill.name,
                "repo_url": repo_url,
                "md_file_name": md_file_name,
                "issue_number": issue.get("number"),
                "issue_url": str(issue.get("html_url") or ""),
            }

        with concurrent.futures.ThreadPoolExecutor(max_workers=submit_jobs) as executor:
            futures = {
                executor.submit(_submit_one, idx, skill): (idx, skill)
                for idx, skill in enumerate(skills, start=1)
            }
            for done_idx, future in enumerate(concurrent.futures.as_completed(futures), start=1):
                idx, skill = futures[future]
                try:
                    res = future.result()
                    if bool(res.get("skip")):
                        skipped += 1
                        reason = str(res.get("skip_reason") or "")
                        if reason == "title":
                            print(f"[skip] {res.get('skill_name')} title already exists: {res.get('title')}")
                        elif reason == "title-in-run":
                            print(f"[skip] {res.get('skill_name')} title duplicated in current run: {res.get('title')}")
                    else:
                        created += 1
                        if args.verbose:
                            print(f"[info] source markdown: {res.get('md_file_name')} | {res.get('skill_name')}")
                        print(f"[ok] #{res.get('issue_number')} {res.get('issue_url')}")
                except Exception as e:
                    failed += 1
                    print(f"[fail] {skill.name}: {e}")

                if args.verbose and (done_idx % 25 == 0 or done_idx == total):
                    print(f"[submit] progress: {done_idx}/{total} created={created} skipped={skipped} failed={failed}")

    print(f"Done. created={created} skipped={skipped} failed={failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
