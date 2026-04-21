import argparse
import json
import os
import re
import subprocess
import sys
import textwrap
import urllib.error
import urllib.request
from pathlib import Path
from typing import Optional


def _decode_output(data: Optional[bytes]) -> str:
    if not data:
        return ""
    return data.decode("utf-8", errors="replace")


def _run_git(args: list[str], cwd: Path) -> str:
    p = subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if p.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed:\n{_decode_output(p.stderr).strip()}")
    return _decode_output(p.stdout)


def _repo_root() -> Path:
    p = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if p.returncode != 0:
        raise RuntimeError("Not a git repository (or git not installed).")
    return Path(_decode_output(p.stdout).strip())


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


def _openai_chat_completion(base_url: str, api_key: str, payload: dict) -> dict:
    url = base_url.rstrip("/") + "/v1/chat/completions"
    data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {api_key}")

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"AI request failed: HTTP {e.code} {e.reason}\n{body}")


def _get_staged_diff(cwd: Path, max_chars: int) -> str:
    diff = _run_git(["diff", "--cached"], cwd=cwd)
    if len(diff) <= max_chars:
        return diff
    return diff[:max_chars] + "\n\n[diff truncated]\n"


def _get_status(cwd: Path) -> str:
    return _run_git(["status", "--porcelain"], cwd=cwd).strip()


def _get_stat(cwd: Path) -> str:
    return _run_git(["diff", "--cached", "--stat"], cwd=cwd).strip()


def _read_multiline_user_input(header: str) -> str:
    print(header)
    lines: list[str] = []
    while True:
        try:
            line = input()
        except EOFError:
            break
        if not line.strip():
            break
        lines.append(line)
    return "\n".join(lines).strip()


def _extract_commit_subject(resp: dict) -> str:
    try:
        msg = resp["choices"][0]["message"]["content"]
    except Exception as e:
        raise RuntimeError(f"Unexpected AI response: {e}\n{json.dumps(resp, ensure_ascii=False)[:2000]}")

    content = (msg or "").strip()
    content = re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL | re.IGNORECASE).strip()
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    subject = (lines[0] if lines else content).strip()
    subject = subject.strip('"').strip("'")

    # Guard: keep it one-line and reasonably short
    if not subject:
        raise RuntimeError("AI returned an empty commit message.")
    
    return subject


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate an English commit message using an OpenAI-compatible API, then run git commit."
    )
    parser.add_argument(
        "--env",
        default=str(Path(__file__).resolve().parent / ".env"),
        help="Path to .env file (default: tools/github/.env)",
    )
    parser.add_argument(
        "--max-diff-chars",
        type=int,
        default=12000,
        help="Max staged diff characters sent to AI",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Do not prompt; commit immediately",
    )
    parser.add_argument(
        "--push",
        action="store_true",
        help="Run git push after commit",
    )
    parser.add_argument(
        "--stage-all",
        action="store_true",
        help="Run git add -A before generating message",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Generate and print the commit message, but do not commit",
    )
    parser.add_argument(
        "--debug-prompt",
        action="store_true",
        help="Print the system/user prompts before sending the AI request",
    )
    parser.add_argument(
        "--debug-response",
        action="store_true",
        help="Print the raw AI response JSON before extracting the subject",
    )
    parser.add_argument(
        "--extra-zh",
        default="",
        help="Optional extra notes in Chinese to help the AI write a better English commit subject",
    )
    args = parser.parse_args()

    _load_env(Path(args.env))

    base_url = os.environ.get("AI_BASE_URL", "").strip()
    api_key = os.environ.get("AI_API_KEY", "").strip()
    model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    temperature = float(os.environ.get("AI_TEMPERATURE", "0.2") or 0.2)

    if not base_url:
        print("Missing AI_BASE_URL in env.", file=sys.stderr)
        return 2
    if not api_key:
        print("Missing AI_API_KEY in env.", file=sys.stderr)
        return 2

    root = _repo_root()

    if args.stage_all:
        _run_git(["add", "-A"], cwd=root)

    status = _get_status(root)
    if not status:
        print("No changes to commit (working tree clean).")
        return 0

    stat = _get_stat(root)
    diff = _get_staged_diff(root, max_chars=args.max_diff_chars)

    if not diff.strip():
        print(
            "No staged changes found. Stage changes first (git add ...) or run with --stage-all.",
            file=sys.stderr,
        )
        return 2

    extra_zh = (args.extra_zh or "").strip()
    if not extra_zh and (not args.yes) and sys.stdin.isatty():
        extra_zh = _read_multiline_user_input(
            "Optional: enter extra notes in Chinese (press Enter on an empty line to finish; leave blank to skip):"
        )

    system_prompt = textwrap.dedent(
        """
        Write one English git commit subject line from the staged --stat, staged diff, and optional author notes.

        Goal:
        - Make the subject useful to future maintainers reading git history.
        - Prefer concrete features, behavior changes, or fixes.
        - Prefer product/feature wording over implementation wording.

        Rules:
        - Capture all meaningful change themes concisely.
        - Do not omit an independently meaningful feature or fix just to keep the subject shorter.
        - Do not choose themes only by changed line count; use distinct file groups from --stat.
        - Do not let one very large file outweigh a second theme that appears across multiple files.
        - If the diff is truncated, recover missing themes from --stat.
        - Mentally group files into feature clusters before writing.
        - Prefer specific capabilities over broad buckets.
        - Treat changed user-facing descriptions, option names, strings, and docs in the diff as strong clues for the real feature name.
        - Use verb phrases, not noun fragments.
        - Never invent changes.

        Avoid:
        - generic filler like update, misc, improvements
        - leading with refactor/internal wording when there is a clearer user-visible change
        - internal class/function/API names unless there is no better product-level description
        - broad labels like tools, UI, system, support, handling, integration, or infrastructure when a more specific feature name is visible
        - subjects so short or vague that a maintainer cannot tell what changed

        Output:
        - one single-line English subject only
        - no explanation, no quotes, no markdown

        THINKING PROCESS GUIDELINES:
        - Before providing your final response, you MUST use a <think> block to outline your thought process. This is for your internal monologue.
        - In your thoughts, deconstruct the user's request, consider alternatives, anticipate outcomes, and reflect on the best strategy. Formulate a precise action plan. Your plan should be efficient, and you may use tools in parallel or sequentially as appropriate. The tool system will decide and handle execution conflicts automatically.
        - The user will see your thoughts but cannot reply to them directly. This block is NOT saved in the chat history, so your final answer must be self-contained.
        - The <think> block must be immediately followed by your final answer or tool call without any newlines.
        - CRITICAL REMINDER: Even if previous messages in the chat history do not show a <think> block, you MUST include one in your current response. This is a mandatory instruction for this conversation mode.
        """
    ).strip()

    user_prompt = textwrap.dedent(
        f"""
        Write a single-line English git commit subject for the following staged changes.

        Rules:
        - Your final visible answer must be the subject line.
        - Must be English, even if the author notes are Chinese.
        - Use imperative mood.
        - Prefer Conventional Commits when obvious, especially feat/fix.
        - No trailing period.
        - Prefer product/feature wording, not implementation wording.
        - Cover the meaningful changes faithfully; do not drop an important theme just to make the subject shorter.
        - First group the staged files into feature clusters using path names and repeated areas in --stat.
        - Then decide what combination of feature clusters best represents the whole commit, not just the biggest file or the first diff hunk.
        - Prefer repeated feature names across multiple files over a single large follow-up screen or config file.
        - If multiple themes exist, summarize them naturally instead of dropping one by default.
        - Do not focus on two details from the same feature cluster while omitting a separate meaningful cluster.
        - Then look for user-visible clues in the diff such as changed descriptions, option names, parameter names, strings, and docs; prefer those words over code plumbing words.
        - If a settings/strings screen follows a clearer underlying feature, name the feature, not the follow-up UI.
        - If a clearly named feature cluster and a generic settings cluster both changed, prefer the clearly named feature cluster.
        - If there is a user-visible change, do not lead with refactor/internal wording.
        - Do not use internal API names if a feature-level description is available.
        - If a more specific capability is visible, do not fall back to broad wording like browser tools.
        - Use verb phrases, not noun fragments.
        - Think of the result as a tiny changelog sentence for future maintainers: in one line, say what this commit adds, fixes, or changes so the reader can understand the commit without opening the diff.
        - Good style sounds like a tiny changelog sentence, for example: 'feat: add workspace renaming and improve history navigation' or 'fix: improve TTS error handling and stabilize SSH output capture'.
        - Bad style sounds like implementation shorthand, for example: 'feat: enhance tools and refactor bridge'.
        - Keep it compact but informative; usually 80-140 characters.

        Optional author notes (may be Chinese):
        {extra_zh or '[none]'}

        git diff --cached --stat:
        {stat}

        git diff --cached:
        {diff}
        """
    ).strip()

    if args.debug_prompt:
        print("=== SYSTEM PROMPT ===\n")
        print(system_prompt)
        print("\n=== USER PROMPT ===\n")
        print(user_prompt)
        print("")

    payload = {
        "model": model,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    resp = _openai_chat_completion(base_url=base_url, api_key=api_key, payload=payload)

    if args.debug_response:
        print("=== RAW AI RESPONSE ===\n")
        print(json.dumps(resp, ensure_ascii=False, indent=2))
        print("")

    subject = _extract_commit_subject(resp)

    print("Proposed commit message:\n")
    print(subject)
    print("")

    if args.dry_run:
        print("Dry run; not committing.")
        return 0

    if not args.yes:
        ans = input("Commit with this message? [y/N] ").strip().lower()
        if ans not in ("y", "yes"):
            print("Aborted.")
            return 1

    _run_git(["commit", "-m", subject], cwd=root)
    print("Committed.")

    if args.push:
        _run_git(["push"], cwd=root)
        print("Pushed.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
