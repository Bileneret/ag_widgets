"""Active Antigravity chat detection and context summary extraction."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from . import context_model as cm
from . import paths


def detect_active_chat(directory: Path | None = None) -> dict[str, Any] | None:
    """Return the chat whose annotation file was modified most recently.

    Antigravity refreshes ``<conversation-id>.pbtxt`` when a chat is viewed, so
    the newest file is a reliable proxy for the currently open tab.
    """
    directory = directory or paths.annotations_dir()
    try:
        files = [item for item in directory.glob("*.pbtxt") if item.is_file()]
        if not files:
            return None
        latest = max(files, key=lambda item: item.stat().st_mtime)
        text = latest.read_text(encoding="utf-8", errors="replace")
        title = re.search(r'title\s*:\s*"([^"]*)"', text)
        return {"id": latest.stem, "title": title.group(1) if title else ""}
    except OSError:
        return None


def _empty_summary(
    conversation_id: str,
    active: bool,
    thresholds: dict[str, float],
) -> dict[str, Any]:
    return {
        "conversation_id": conversation_id,
        "active": active,
        "model": None,
        "tokens": None,
        "window": None,
        "pct": None,
        "level": None,
        "yellow_pct": thresholds["yellow_pct"],
        "red_pct": thresholds["red_pct"],
        "warn_tokens": None,
        "risk_tokens": None,
        "window_source": None,
        "updated_at": None,
    }


def extract_context_summary(state_dir: Path, config: dict[str, Any] | None = None) -> dict[str, Any] | None:
    """Build the context card payload from ``context.json`` plus the open-chat probe."""
    config = config or {}
    stored = paths.read_json(state_dir / "context.json")
    if not stored:
        return None

    conversations = stored.get("conversations")
    if not isinstance(conversations, dict) or not conversations:
        return None

    active = detect_active_chat()
    selected_id = active["id"] if active else stored.get("last")
    if not selected_id:
        return None

    is_active = bool(active and active.get("id") == selected_id)
    conversation = conversations.get(selected_id)
    thresholds = cm.normalize_thresholds(config)

    if not isinstance(conversation, dict):
        return _empty_summary(selected_id, is_active, thresholds)

    tokens = conversation.get("tokens")
    model = conversation.get("model")
    window = conversation.get("window")

    if not isinstance(window, int) or window <= 0:
        window, source = cm.resolve_model_window(
            model,
            config_windows=config.get("model_windows"),
            learned_windows=cm.load_learned_windows(state_dir),
        )
    else:
        source = conversation.get("window_source") or "hook"

    if isinstance(tokens, int) and isinstance(window, int) and window > 0:
        computed = cm.compute_context(tokens, window, thresholds)
    else:
        computed = {
            "tokens": tokens if isinstance(tokens, int) else None,
            "window": window if isinstance(window, int) else None,
            "pct": conversation.get("pct") if isinstance(conversation.get("pct"), (int, float)) else None,
            "level": conversation.get("level") or "unknown",
            "yellow_pct": thresholds["yellow_pct"],
            "red_pct": thresholds["red_pct"],
            "warn_tokens": conversation.get("warn_tokens"),
            "risk_tokens": conversation.get("risk_tokens"),
        }

    return {
        "conversation_id": selected_id,
        "active": is_active,
        "model": model,
        "tokens": computed["tokens"],
        "window": computed["window"],
        "pct": computed["pct"],
        "level": computed["level"],
        "yellow_pct": computed["yellow_pct"],
        "red_pct": computed["red_pct"],
        "warn_tokens": computed["warn_tokens"],
        "risk_tokens": computed["risk_tokens"],
        "window_source": source,
        "updated_at": conversation.get("updated_at"),
    }
