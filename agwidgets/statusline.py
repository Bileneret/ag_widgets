"""Antigravity CLI status line: persist usage data and print a compact summary."""

from __future__ import annotations

import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import antigravity, context_model as cm, paths


def _as_finite_float(value: Any) -> float | None:
    """Return ``value`` as a finite float, ignoring booleans and junk."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    try:
        number = float(value)
    except (OverflowError, TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _token_count(value: Any) -> int:
    number = _as_finite_float(value)
    return int(number) if number is not None else 0


def _percent_text(info: dict[str, Any] | None) -> str:
    if not isinstance(info, dict):
        return "--"
    fraction = _as_finite_float(info.get("remaining_fraction"))
    if fraction is not None:
        return f"{max(0, min(100, round(fraction * 100)))}%"
    amount = _as_finite_float(info.get("remaining_amount"))
    if amount is not None:
        return cm.format_tokens(amount)
    return "--"


def _load_previous(state_dir: Path) -> dict[str, Any]:
    return paths.read_json(state_dir / "agy_status.json") or {}


def run(raw_input: str) -> str:
    """Handle one status line payload and return the line to display."""
    if not raw_input.strip():
        return ""
    try:
        payload = json.loads(raw_input)
    except json.JSONDecodeError:
        return ""
    if not isinstance(payload, dict):
        return ""

    quota = payload.get("quota") if isinstance(payload.get("quota"), dict) else {}
    plan_tier = payload.get("plan_tier")
    context_window = payload.get("context_window") if isinstance(payload.get("context_window"), dict) else None
    model_info = payload.get("model") if isinstance(payload.get("model"), dict) else {}
    model_name = model_info.get("id") or model_info.get("display_name")
    if not model_name and isinstance(payload.get("model"), str):
        model_name = payload["model"]

    state_dir = paths.state_dir()

    # The CLI is the source of truth for the real window size: cache it per model.
    if isinstance(context_window, dict):
        size = context_window.get("context_window_size")
        try:
            if size and int(size) > 0 and model_name:
                cm.save_learned_window(state_dir, model_name, size)
        except (TypeError, ValueError, OSError, OverflowError):
            pass

    def merge_status(previous: dict[str, Any]) -> dict[str, Any]:
        previous_quota = previous.get("quota")
        return {
            **previous,
            "plan_tier": plan_tier if plan_tier is not None else previous.get("plan_tier"),
            "quota": quota if quota else (previous_quota if isinstance(previous_quota, dict) else {}),
            "context_window": context_window or previous.get("context_window"),
            "model": model_name or previous.get("model"),
            "saved_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
        }

    status = merge_status(_load_previous(state_dir))
    try:
        status = paths.update_json(state_dir / "agy_status.json", merge_status)
    except (OSError, paths.ConfigReadError):
        pass

    status_quota = status.get("quota") if isinstance(status.get("quota"), dict) else {}
    five_hour = antigravity.pick_bucket(status_quota, "5h", "gemini")
    weekly = antigravity.pick_bucket(status_quota, "week", "gemini")

    parts: list[str] = []
    if five_hour is not None:
        parts.append(f"5h {_percent_text(five_hour)}")
    if weekly is not None:
        parts.append(f"wk {_percent_text(weekly)}")

    if isinstance(context_window, dict):
        used_pct = _as_finite_float(context_window.get("used_percentage"))
        size_number = _as_finite_float(context_window.get("context_window_size"))
        usage = context_window.get("current_usage")
        used_tokens = None
        if isinstance(usage, dict):
            used_tokens = _token_count(usage.get("input_tokens")) + _token_count(
                usage.get("cache_read_input_tokens")
            )
        if used_pct is not None:
            parts.append(f"ctx {round(used_pct)}%")
        if size_number is not None and size_number > 0:
            used_text = cm.format_tokens(used_tokens) if used_tokens is not None else "?"
            parts.append(f"{used_text}/{cm.format_window(size_number)}")

    return "AG " + " · ".join(parts) if parts else "AG ready"


def main() -> None:
    raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
    line = run(raw)
    if not line:
        return
    data = line.encode("utf-8", errors="replace") + b"\n"
    buffer = getattr(sys.stdout, "buffer", None)
    if buffer is not None:
        buffer.write(data)
        buffer.flush()
        return
    try:
        sys.stdout.write(line + "\n")
    except UnicodeEncodeError:
        encoding = getattr(sys.stdout, "encoding", None) or "ascii"
        sys.stdout.write(line.encode(encoding, errors="replace").decode(encoding, errors="replace") + "\n")
    sys.stdout.flush()
