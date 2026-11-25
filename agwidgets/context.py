"""Active Antigravity chat detection and context summary extraction."""

from __future__ import annotations

import math
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import context_model as cm
from . import paths

_VIEW_TIME_RE = re.compile(
    r"last_user_view_time\s*:\s*\{\s*seconds\s*:\s*(\d+)(?:\s+nanos\s*:\s*(\d+))?"
)
_TITLE_RE = re.compile(r'title\s*:\s*"([^"]*)"')


def _as_int(value: Any) -> int | None:
    """Coerce a JSON number to ``int``, ignoring bools, NaN, and infinities."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    try:
        return int(value)
    except (ValueError, OverflowError):
        return None


def _as_float(value: Any) -> float | None:
    """Coerce a JSON number to a finite ``float``, ignoring bools and NaN."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    try:
        number = float(value)
    except OverflowError:  # A JSON literal such as 10**400 has no float value.
        return None
    return number if math.isfinite(number) else None


def detect_active_chat(directory: Path | None = None) -> dict[str, Any] | None:
    """Return the chat the user viewed most recently.

    Antigravity writes ``last_user_view_time`` into ``<conversation-id>.pbtxt``
    when a chat is viewed; the file mtime is only a fallback.
    """
    directory = directory or paths.annotations_dir()
    candidates: list[tuple[tuple[float, float], dict[str, Any]]] = []
    for item in sorted(directory.glob("*.pbtxt")):
        try:
            if not item.is_file():
                continue
            mtime = item.stat().st_mtime
            text = item.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue  # The file may vanish between glob() and the read.

        viewed_ts = 0.0
        viewed_at: str | None = None
        match = _VIEW_TIME_RE.search(text)
        if match:
            seconds = int(match.group(1))
            nanos = int(match.group(2) or 0)
            viewed_ts = seconds + nanos / 1e9
            try:
                viewed_at = datetime.fromtimestamp(seconds, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            except (OSError, OverflowError, ValueError):
                viewed_at = None

        title = _TITLE_RE.search(text)
        candidates.append(
            (
                (viewed_ts, mtime),
                {
                    "id": item.stem,
                    "title": title.group(1) if title else "",
                    "viewed_at": viewed_at,
                },
            )
        )

    if not candidates:
        return None
    return max(candidates, key=lambda candidate: candidate[0])[1]


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
    thresholds = cm.normalize_thresholds(config)
    stored = paths.read_json(state_dir / "context.json")
    active = detect_active_chat()

    conversations = stored.get("conversations") if isinstance(stored, dict) else None
    if not isinstance(conversations, dict) or not conversations:
        # An open chat without a hook write yet still deserves a card.
        return _empty_summary(active["id"], True, thresholds) if active else None

    selected_id = active["id"] if active else stored.get("last")
    if not selected_id:
        return None

    is_active = bool(active and active.get("id") == selected_id)
    conversation = conversations.get(selected_id)
    if not isinstance(conversation, dict):
        return _empty_summary(selected_id, is_active, thresholds)

    tokens = _as_int(conversation.get("tokens"))
    model = conversation.get("model")
    stored_window = _as_int(conversation.get("window"))
    explicit_window = stored_window if conversation.get("window_source") == "payload" else None
    # Always re-resolve: config.json edits and freshly learned CLI windows win
    # over cached table/cli/config windows, while explicit hook payload sizes
    # for unknown models are preserved.
    window, source = cm.resolve_model_window(
        model,
        config_windows=config.get("model_windows"),
        learned_windows=cm.load_learned_windows(state_dir),
        explicit=explicit_window,
    )

    if tokens is not None and window:
        computed = cm.compute_context(tokens, window, thresholds)
    else:
        pct = _as_float(conversation.get("pct"))
        level = "unknown"
        if pct is not None:
            level = "red" if pct >= thresholds["red_pct"] else "yellow" if pct >= thresholds["yellow_pct"] else "green"
        computed = {
            "tokens": tokens,
            "window": window,
            "pct": pct,
            "level": level,
            "yellow_pct": thresholds["yellow_pct"],
            "red_pct": thresholds["red_pct"],
            "warn_tokens": (
                int(round(window * thresholds["yellow_pct"] / 100.0))
                if window
                else _as_int(conversation.get("warn_tokens"))
            ),
            "risk_tokens": (
                int(round(window * thresholds["red_pct"] / 100.0))
                if window
                else _as_int(conversation.get("risk_tokens"))
            ),
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
