import argparse
import json
import os
import re
import sys
import ssl
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional


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


def _build_ssl_context(force_tls12: bool, insecure: bool) -> ssl.SSLContext:
    ctx = ssl.create_default_context()
    if insecure:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

    if force_tls12 and hasattr(ssl, "TLSVersion"):
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.maximum_version = ssl.TLSVersion.TLSv1_2

    return ctx


def _decode_output(data: Optional[bytes]) -> str:
    if not data:
        return ""
    return data.decode("utf-8", errors="replace")


def _run_git(args: list[str], cwd: Optional[Path] = None) -> str:
    import subprocess

    p = subprocess.run(
        ["git", *args],
        cwd=str(cwd) if cwd else None,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if p.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed:\n{_decode_output(p.stderr).strip()}")
    return _decode_output(p.stdout)


def _repo_root() -> Path:
    out = _run_git(["rev-parse", "--show-toplevel"])
    if not out.strip():
        raise RuntimeError("Not a git repository (or git not installed).")
    return Path(out.strip())


def _get_origin_url(repo_root: Path) -> str:
    try:
        return _run_git(["remote", "get-url", "origin"], cwd=repo_root).strip()
    except Exception:
        return ""


def _infer_repo_from_origin(origin_url: str) -> str:
    origin_url = origin_url.strip()
    if not origin_url:
        return ""

    # git@github.com:owner/repo.git
    m = re.match(r"^git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$", origin_url)
    if m:
        return f"{m.group(1)}/{m.group(2)}"

    # https://github.com/owner/repo.git
    m = re.match(r"^https?://github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$", origin_url)
    if m:
        return f"{m.group(1)}/{m.group(2)}"

    return ""


def _request_json(url: str, token: str, ctx: ssl.SSLContext) -> tuple[list, dict]:
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "OperitTools/1.0 (list_issues.py)")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    timeout_s = 60
    retries = 5
    backoff_s = 1.0

    last_error: Optional[BaseException] = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=timeout_s, context=ctx) as resp:
                body = resp.read().decode("utf-8", errors="replace")
                data = json.loads(body)
                headers = {k.lower(): v for k, v in resp.headers.items()}
                if not isinstance(data, list):
                    raise RuntimeError(f"Unexpected GitHub response (expected list): {body[:500]}")
                return data, headers
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = e.read().decode("utf-8", errors="replace")
            except Exception:
                pass

            if e.code in (429, 500, 502, 503, 504) and attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            raise RuntimeError(f"GitHub API error: HTTP {e.code} {e.reason}\n{body}")
        except (urllib.error.URLError, ssl.SSLError, OSError) as e:
            last_error = e
            if attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            break

    raise RuntimeError(
        "GitHub request failed (network/SSL). "
        "If you are behind a proxy, set HTTPS_PROXY/HTTP_PROXY. "
        "If TLS handshake keeps failing, try --github-tls12. As a last resort, try --github-insecure. "
        f"Original: {last_error!r}"
    )


def _parse_link_next(link_header: str) -> str:
    # <url>; rel="next", <url>; rel="last"
    if not link_header:
        return ""
    parts = [p.strip() for p in link_header.split(",")]
    for part in parts:
        if 'rel="next"' in part or "rel=next" in part:
            m = re.search(r"<([^>]+)>", part)
            if m:
                return m.group(1)
    return ""


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


@dataclass
class IssueItem:
    number: int
    title: str
    html_url: str
    state: str
    created_at: str
    updated_at: str
    body: str
    labels: list[str]
    assignees: list[str]
    comments: int


def _iter_issues(
    api_base: str,
    repo: str,
    token: str,
    state: str,
    ctx: ssl.SSLContext,
) -> Iterable[IssueItem]:
    owner, name = repo.split("/", 1)

    params = {
        "state": state,
        "per_page": "100",
        "sort": "updated",
        "direction": "desc",
    }
    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"
        + "?"
        + urllib.parse.urlencode(params)
    )

    while url:
        data, headers = _request_json(url, token=token, ctx=ctx)
        for it in data:
            if not isinstance(it, dict):
                continue
            if "pull_request" in it:
                continue
            yield IssueItem(
                number=int(it.get("number") or 0),
                title=str(it.get("title") or ""),
                html_url=str(it.get("html_url") or ""),
                state=str(it.get("state") or ""),
                created_at=str(it.get("created_at") or ""),
                updated_at=str(it.get("updated_at") or ""),
                body=str(it.get("body") or ""),
                labels=[str(l.get("name")) for l in (it.get("labels") or []) if isinstance(l, dict)],
                assignees=[
                    str(a.get("login"))
                    for a in (it.get("assignees") or [])
                    if isinstance(a, dict)
                ],
                comments=int(it.get("comments") or 0),
            )

        url = _parse_link_next(headers.get("link", ""))


def _to_markdown(issues: list[IssueItem], repo: str, state: str) -> str:
    lines: list[str] = []
    lines.append(f"# GitHub Issues ({repo})")
    lines.append("")
    lines.append(f"State: `{state}`")
    lines.append(f"Count: `{len(issues)}`")
    lines.append("")

    for it in issues:
        labels = ", ".join(it.labels) if it.labels else ""
        assignees = ", ".join(it.assignees) if it.assignees else ""

        meta_parts = []
        if labels:
            meta_parts.append(f"labels: {labels}")
        if assignees:
            meta_parts.append(f"assignees: {assignees}")
        meta_parts.append(f"comments: {it.comments}")
        meta_parts.append(f"updated: {it.updated_at}")
        meta = " | ".join(meta_parts)

        title = it.title.replace("\r", " ").replace("\n", " ").strip()
        lines.append(f"- #{it.number} [{title}]({it.html_url}) ({it.state})")
        lines.append(f"  - {meta}")

    lines.append("")
    return "\n".join(lines)


def _chunked(items: list[IssueItem], chunk_size: int) -> list[list[IssueItem]]:
    if chunk_size <= 0:
        return [items]
    return [items[i : i + chunk_size] for i in range(0, len(items), chunk_size)]


def _issues_for_ai_text(issues: list[IssueItem], body_chars: int) -> str:
    rows = []
    for it in issues:
        body = (it.body or "").replace("\r", " ").replace("\n", " ").strip()
        if body_chars <= 0:
            body = ""
        elif len(body) > body_chars:
            body = body[:body_chars].rstrip() + "…"
        rows.append(
            {
                "number": it.number,
                "title": it.title,
                "url": it.html_url,
                "labels": it.labels,
                "assignees": it.assignees,
                "comments": it.comments,
                "created_at": it.created_at,
                "updated_at": it.updated_at,
                "body": body,
            }
        )
    return json.dumps(rows, ensure_ascii=False, indent=2)


def _extract_ai_text(resp: dict) -> str:
    try:
        msg = resp["choices"][0]["message"]["content"]
    except Exception as e:
        raise RuntimeError(f"Unexpected AI response: {e}\n{json.dumps(resp, ensure_ascii=False)[:2000]}")
    return (msg or "").strip()


def _ai_triage_report(
    issues: list[IssueItem],
    repo: str,
    state: str,
    base_url: str,
    api_key: str,
    model: str,
    temperature: float,
    body_chars: int,
    chunk_size: int,
    language: str,
    max_tokens: Optional[int],
) -> str:
    system_prompt = (
        "You are a senior software triage assistant. You help organize GitHub issues for a repo."
    )

    lang_hint = "Write the report in Chinese." if language.lower().startswith("zh") else "Write the report in English."

    chunk_summaries: list[str] = []
    chunks = _chunked(issues, chunk_size)
    for idx, ch in enumerate(chunks, start=1):
        issues_json = _issues_for_ai_text(ch, body_chars=body_chars)
        user_prompt = textwrap.dedent(
            f"""
            {lang_hint}

            You are given a JSON array of GitHub issues for repo {repo} (state={state}).
            For each issue, infer:
            - category/module (e.g. UI, chat, streaming, terminal, tools, i18n, performance, crash, build)
            - priority (P0/P1/P2)
            - 1-sentence summary
            - suggested next action (investigate/fix/design/needs info)

            Output Markdown with sections:
            - ## Chunk {idx}/{len(chunks)}
            - ### Categories
              Group issues by category; for each issue use: `#<num> <title>` and add `(P0|P1|P2)` and 1-line action.
            - ### Possible duplicates / overlaps

            Issues JSON:
            {issues_json}
            """
        ).strip()

        payload: dict = {
            "model": model,
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        if max_tokens is not None:
            payload["max_tokens"] = int(max_tokens)

        resp = _openai_chat_completion(base_url=base_url, api_key=api_key, payload=payload)
        chunk_summaries.append(_extract_ai_text(resp))

    merged = "\n\n".join(chunk_summaries)

    final_prompt = textwrap.dedent(
        f"""
        {lang_hint}

        Consolidate the following chunk triage notes into ONE final report for repo {repo} (state={state}).

        Output Markdown with sections:
        - # Issue Triage Report
        - ## Overall summary (counts by category, quick highlights)
        - ## Top priorities (P0 then P1)
        - ## By category (each category heading, list issues)
        - ## Duplicate/overlap candidates
        - ## Questions / info needed (what to ask users)

        Chunk notes:
        {merged}
        """
    ).strip()

    payload2: dict = {
        "model": model,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": final_prompt},
        ],
    }
    if max_tokens is not None:
        payload2["max_tokens"] = int(max_tokens)

    resp2 = _openai_chat_completion(base_url=base_url, api_key=api_key, payload=payload2)
    return _extract_ai_text(resp2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fetch GitHub issues for the current repo and write a Markdown list for manual review."
    )
    parser.add_argument(
        "--env",
        default=str(Path(__file__).resolve().parent / ".env"),
        help="Path to .env file (default: tools/github/.env)",
    )
    parser.add_argument(
        "--repo",
        default="",
        help="GitHub repo in owner/name form. If omitted, uses GITHUB_REPO env or infers from git origin.",
    )
    parser.add_argument(
        "--state",
        default="all",
        choices=["open", "closed", "all"],
        help="Issue state to fetch (default: all)",
    )
    parser.add_argument(
        "--out",
        default="",
        help="Output markdown file path (default: tools/github/issues_<state>.md)",
    )
    parser.add_argument(
        "--github-tls12",
        action="store_true",
        help="Force TLS 1.2 for GitHub HTTPS requests (workaround for some networks/proxies).",
    )
    parser.add_argument(
        "--github-insecure",
        action="store_true",
        help="Disable TLS certificate verification for GitHub HTTPS requests (NOT recommended).",
    )
    parser.add_argument(
        "--ai-summary",
        action="store_true",
        help="Also call AI (OpenAI-compatible) to generate a triage report markdown.",
    )
    parser.add_argument(
        "--ai-out",
        default="",
        help="AI report output path (default: tools/github/issues_<state>_ai.md)",
    )
    parser.add_argument(
        "--ai-body-chars",
        type=int,
        default=400,
        help="How many issue body characters to include per issue for AI input (default: 400). Use 0 to omit bodies.",
    )
    parser.add_argument(
        "--ai-chunk-size",
        type=int,
        default=25,
        help="How many issues to send per AI call before merging (default: 25).",
    )
    parser.add_argument(
        "--ai-language",
        default="zh",
        help="AI report language hint: zh or en (default: zh)",
    )
    parser.add_argument(
        "--ai-max-tokens",
        type=int,
        default=1800,
        help="max_tokens for AI responses (default: 1800)",
    )
    args = parser.parse_args()

    _load_env(Path(args.env))

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    api_base = os.environ.get("GITHUB_API_URL", "https://api.github.com").strip() or "https://api.github.com"

    repo = (args.repo or os.environ.get("GITHUB_REPO", "") or "").strip()
    if not repo:
        repo_root = _repo_root()
        origin = _get_origin_url(repo_root)
        repo = _infer_repo_from_origin(origin)

    if not repo or "/" not in repo:
        print(
            "Missing repo. Provide --repo owner/name or set GITHUB_REPO, or ensure git remote origin is a GitHub URL.",
            file=sys.stderr,
        )
        return 2

    github_ctx = _build_ssl_context(force_tls12=bool(args.github_tls12), insecure=bool(args.github_insecure))
    issues = list(_iter_issues(api_base=api_base, repo=repo, token=token, state=args.state, ctx=github_ctx))

    if args.out:
        out_path = Path(args.out)
    else:
        out_path = Path(__file__).resolve().parent / f"issues_{args.state}.md"

    out_path.write_text(_to_markdown(issues, repo=repo, state=args.state), encoding="utf-8")
    print(f"Wrote: {out_path}")

    if args.ai_summary:
        ai_base_url = os.environ.get("AI_BASE_URL", "").strip()
        ai_api_key = os.environ.get("AI_API_KEY", "").strip()
        ai_model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
        try:
            ai_max_tokens = int(args.ai_max_tokens) if args.ai_max_tokens > 0 else None
        except Exception:
            ai_max_tokens = None

        try:
            ai_temperature = float(os.environ.get("AI_TEMPERATURE", "0.2") or 0.2)
        except Exception:
            ai_temperature = 0.2

        if not ai_base_url or not ai_api_key:
            print(
                "Missing AI_BASE_URL/AI_API_KEY. Fill tools/github/.env (gitignored) or set env vars.",
                file=sys.stderr,
            )
            return 2

        if args.ai_out:
            ai_out_path = Path(args.ai_out)
        else:
            ai_out_path = Path(__file__).resolve().parent / f"issues_{args.state}_ai.md"

        body_chars = int(args.ai_body_chars)
        if body_chars <= 0:
            body_chars = 0

        report = _ai_triage_report(
            issues=issues,
            repo=repo,
            state=args.state,
            base_url=ai_base_url,
            api_key=ai_api_key,
            model=ai_model,
            temperature=ai_temperature,
            body_chars=body_chars,
            chunk_size=int(args.ai_chunk_size),
            language=str(args.ai_language),
            max_tokens=ai_max_tokens,
        )
        ai_out_path.write_text(report, encoding="utf-8")
        print(f"Wrote: {ai_out_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
