"""Antigravity PostInvocation/Stop hook: track the active model's context size."""

from __future__ import annotations

import json
import math
import os
import re
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from . import context_model as cm, paths

TAIL_BYTES = 512 * 1024
FALLBACK_TAIL_BYTES = 4 * 1024 * 1024
MAX_CONVERSATIONS = 10
MAX_ERROR_LOG_BYTES = 256 * 1024

# Secrets that must never reach context.json: JWTs, provider keys, ntfy topics.
SECRET_PATTERNS = (
    re.compile(r"eyJ[A-Za-z0-9_-]{4,}(?:\.[A-Za-z0-9_-]{4,}){0,2}"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{8,}"),
    re.compile(r"\bAIza[A-Za-z0-9_-]{6,}"),
    re.compile(r"\bghp_[A-Za-z0-9]{10,}"),
    re.compile(r"\bagw-[A-Za-z0-9_-]{6,}"),
)


def _as_int(value: Any) -> int:
    """Coerce a JSON number to ``int``; bools, non-numbers, NaN, and inf become 0."""
    if isinstance(value, bool):
        return 0
    if isinstance(value, str):
        try:
            value = float(value.strip())
        except ValueError:
            return 0
    if not isinstance(value, (int, float)):
        return 0
    try:
        number = float(value)
    except OverflowError:  # A JSON literal such as 10**400 has no float value.
        return 0
    return int(number) if math.isfinite(number) else 0


def load_config() -> dict[str, Any]:
    """Return only the config keys the hook cares about.

    Empty ``model_windows`` means "no user overrides": learned CLI values and the
    built-in table are used instead.
    """
    config = paths.load_config()
    windows = config.get("model_windows")
    thresholds = config.get("context_thresholds")
    return {
        "model_windows": windows if isinstance(windows, dict) else {},
        "context_thresholds": thresholds if isinstance(thresholds, dict) else None,
        "store_prompt_snippet": config.get("store_prompt_snippet") is True,
    }


def _iter_tail_lines(handle: Any, size: int, limit: int) -> list[bytes]:
    """Return the last ``limit`` bytes of ``handle`` as complete lines."""
    handle.seek(max(0, size - limit))
    if size > limit:
        handle.readline()  # Drop a potentially truncated first line.
    return handle.read().splitlines()


def _scan_lines(lines: list[bytes]) -> dict[str, Any] | None:
    """Return the newest parsed JSON object that carries token usage."""
    for raw in reversed(lines):
        if not raw.strip():
            continue
        try:
            entry = json.loads(raw.decode("utf-8", errors="replace"))
        except (ValueError, UnicodeDecodeError):
            continue
        if isinstance(entry, dict) and entry.get("input_tokens") is not None:
            return entry
    return None


def read_last_usage_entry(transcript_path: Path) -> dict[str, Any] | None:
    """Read the newest transcript line that contains token usage."""
    if not transcript_path.is_file():
        return None

    try:
        size = transcript_path.stat().st_size
        with transcript_path.open("rb") as handle:
            entry = _scan_lines(_iter_tail_lines(handle, size, TAIL_BYTES))
            if entry is None and size > TAIL_BYTES:
                # Large tool outputs can push the usage line past the first window.
                entry = _scan_lines(_iter_tail_lines(handle, size, FALLBACK_TAIL_BYTES))
        return entry
    except OSError:
        return None


def explicit_window(payload: dict[str, Any]) -> int | None:
    """Extract a context window size when Antigravity includes one in the payload."""
    for key in ("contextWindowSize", "context_window_size", "maxContextTokens", "max_context_tokens"):
        value = payload.get(key)
        if isinstance(value, (int, float)) and not isinstance(value, bool) and value > 0:
            return int(value)

    for key in ("contextWindow", "context_window"):
        value = payload.get(key)
        if isinstance(value, dict):
            for inner in ("context_window_size", "size", "max_tokens", "total_tokens"):
                size = value.get(inner)
                if isinstance(size, (int, float)) and not isinstance(size, bool) and size > 0:
                    return int(size)
    return None


def _sanitize_snippet(value: Any, store: bool) -> str:
    """Keep the prompt snippet short and free of secrets (empty unless opted in)."""
    if not store:
        return ""
    snippet = str(value or "").replace("\r", " ").replace("\n", " ").strip()
    for pattern in SECRET_PATTERNS:
        snippet = pattern.sub("[redacted]", snippet)
    return snippet[:60]


def _parse_updated_at(value: Any) -> datetime:
    """Parse an ISO timestamp as an aware UTC datetime; junk sorts as oldest."""
    text = str(value or "").strip()
    if text.endswith("Z"):  # ``fromisoformat`` accepts "Z" only from 3.11.
        text = text[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except (TypeError, ValueError):
        return datetime.min.replace(tzinfo=timezone.utc)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _prune_conversations(conversations: dict[str, Any], keep_id: str) -> dict[str, Any]:
    """Keep the newest chats but never evict the conversation being updated."""
    if len(conversations) <= MAX_CONVERSATIONS:
        return conversations

    def timestamp(item: tuple[str, Any]) -> datetime:
        entry = item[1]
        return _parse_updated_at(entry.get("updated_at") if isinstance(entry, dict) else None)

    ordered = sorted(conversations.items(), key=timestamp, reverse=True)
    kept = dict(ordered[:MAX_CONVERSATIONS])
    if keep_id in conversations and keep_id not in kept:
        kept.pop(ordered[MAX_CONVERSATIONS - 1][0], None)
        kept[keep_id] = conversations[keep_id]
    return kept


def _context_updater(conversation_id: str, entry: dict[str, Any]) -> Callable[[dict[str, Any]], dict[str, Any]]:
    """Build an ``update_json`` callback that merges one conversation."""

    def updater(data: dict[str, Any]) -> dict[str, Any]:
        conversations = data.get("conversations")
        if not isinstance(conversations, dict):
            conversations = {}
        conversations[conversation_id] = entry
        return {
            "last": conversation_id,
            "conversations": _prune_conversations(conversations, conversation_id),
        }

    return updater


def run(raw_input: str) -> None:
    """Persist context usage for one hook invocation."""
    if not raw_input.strip():
        return

    payload = json.loads(raw_input)
    if not isinstance(payload, dict):
        return

    conversation_id = str(payload.get("conversationId") or "").strip()
    transcript_raw = str(payload.get("transcriptPath") or "").strip()
    if not conversation_id or not transcript_raw:
        return

    usage = read_last_usage_entry(Path(os.path.expanduser(transcript_raw)))
    if usage is None:
        return

    config = load_config()
    tokens = max(0, _as_int(usage.get("input_tokens")) + _as_int(usage.get("cache_read_tokens")))
    model = str(usage.get("model") or payload.get("modelName") or "").strip()

    state_dir = paths.state_dir()
    window, window_source = cm.resolve_model_window(
        model,
        config_windows=config.get("model_windows"),
        learned_windows=cm.load_learned_windows(state_dir),
        explicit=explicit_window(payload),
    )
    thresholds = cm.normalize_thresholds(config)
    context = cm.compute_context(tokens, window, thresholds)

    workspace_paths = payload.get("workspacePaths") or []
    workspace = ""
    if isinstance(workspace_paths, list) and workspace_paths:
        first = str(workspace_paths[0]).rstrip("\\/")
        workspace = os.path.basename(first) or first

    snippet = _sanitize_snippet(payload.get("lastUserInput"), config["store_prompt_snippet"])
    now_iso = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

    entry = {
        "model": model,
        "tokens": tokens,
        "window": window,
        "window_source": window_source,
        "pct": context["pct"],
        "level": context["level"],
        "yellow_pct": context["yellow_pct"],
        "red_pct": context["red_pct"],
        "warn_tokens": context["warn_tokens"],
        "risk_tokens": context["risk_tokens"],
        "workspace": workspace,
        "snippet": snippet,
        "updated_at": now_iso,
    }

    try:
        paths.update_json(state_dir / "context.json", _context_updater(conversation_id, entry))
    except (OSError, paths.ConfigReadError):
        return


def main() -> None:
    """Hook entry point. Never fail the agent run, and always emit valid JSON."""
    try:
        run(sys.stdin.buffer.read().decode("utf-8", errors="replace"))
    except Exception:
        try:
            error_log = paths.state_dir() / "hook_errors.log"
            try:
                if error_log.stat().st_size > MAX_ERROR_LOG_BYTES:
                    os.replace(error_log, error_log.with_name("hook_errors.log.1"))
            except OSError:
                pass
            timestamp = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
            with error_log.open("a", encoding="utf-8") as handle:
                handle.write(f"[{timestamp}]\n{traceback.format_exc()}\n")
        except Exception:
            pass
    finally:
        sys.stdout.write("{}\n")
        sys.stdout.flush()
