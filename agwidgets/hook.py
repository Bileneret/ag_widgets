"""Antigravity PostInvocation/Stop hook: track the active model's context size."""

from __future__ import annotations

import json
import os
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import context_model as cm, paths

TAIL_BYTES = 512 * 1024
MAX_CONVERSATIONS = 10


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
    }


def read_last_usage_entry(transcript_path: Path) -> dict[str, Any] | None:
    """Read the newest transcript line that contains token usage."""
    if not transcript_path.exists():
        return None

    size = transcript_path.stat().st_size
    with transcript_path.open("rb") as handle:
        if size > TAIL_BYTES:
            handle.seek(size - TAIL_BYTES)
            handle.readline()  # Drop a potentially truncated first line.
        raw_lines = handle.read().splitlines()

    for raw in reversed(raw_lines):
        if not raw.strip():
            continue
        try:
            entry = json.loads(raw.decode("utf-8", errors="replace"))
        except (ValueError, UnicodeDecodeError):
            continue
        if isinstance(entry, dict) and entry.get("input_tokens") is not None:
            return entry
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


def run(raw_input: str) -> None:
    """Persist context usage for one hook invocation."""
    if not raw_input.strip():
        return

    payload = json.loads(raw_input)
    conversation_id = str(payload.get("conversationId") or "").strip()
    transcript_raw = str(payload.get("transcriptPath") or "").strip()
    if not conversation_id or not transcript_raw:
        return

    usage = read_last_usage_entry(Path(os.path.expanduser(transcript_raw)))
    if usage is None:
        return

    config = load_config()
    input_tokens = int(usage.get("input_tokens") or 0)
    cache_read = int(usage.get("cache_read_tokens") or 0)
    tokens = input_tokens + cache_read
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

    snippet = str(payload.get("lastUserInput") or "").replace("\r", " ").replace("\n", " ").strip()[:60]
    now_iso = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

    context_file = state_dir / "context.json"
    existing = paths.read_json(context_file) or {}
    conversations = existing.get("conversations")
    if not isinstance(conversations, dict):
        conversations = {}

    conversations[conversation_id] = {
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

    if len(conversations) > MAX_CONVERSATIONS:
        newest = sorted(
            conversations.items(),
            key=lambda item: str(item[1].get("updated_at") or "") if isinstance(item[1], dict) else "",
            reverse=True,
        )[:MAX_CONVERSATIONS]
        conversations = dict(newest)

    paths.write_json(context_file, {"last": conversation_id, "conversations": conversations})


def main() -> None:
    """Hook entry point. Never fail the agent run, and always emit valid JSON."""
    try:
        run(sys.stdin.buffer.read().decode("utf-8", errors="replace"))
    except Exception:
        try:
            error_log = paths.state_dir() / "hook_errors.log"
            timestamp = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
            with error_log.open("a", encoding="utf-8") as handle:
                handle.write(f"[{timestamp}]\n{traceback.format_exc()}\n")
        except Exception:
            pass
    finally:
        sys.stdout.write("{}\n")
        sys.stdout.flush()
