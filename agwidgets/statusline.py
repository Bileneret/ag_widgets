"""Antigravity CLI status line: persist usage data and print a compact summary."""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import antigravity, context_model as cm, paths


def _percent_text(info: dict[str, Any] | None) -> str:
    if not isinstance(info, dict):
        return "--"
    fraction = info.get("remaining_fraction")
    if isinstance(fraction, (int, float)):
        return f"{round(fraction * 100)}%"
    amount = info.get("remaining_amount")
    if isinstance(amount, (int, float)):
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
        except (TypeError, ValueError):
            pass

    previous = _load_previous(state_dir)
    status = {
        **previous,
        "plan_tier": plan_tier if plan_tier is not None else previous.get("plan_tier"),
        "quota": quota if quota else (previous.get("quota") or {}),
        "context_window": context_window or previous.get("context_window"),
        "model": model_name or previous.get("model"),
        "saved_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
    }
    try:
        paths.write_json(state_dir / "agy_status.json", status)
    except OSError:
        pass

    five_hour = antigravity.pick_bucket(quota, "5h", "gemini")
    weekly = antigravity.pick_bucket(quota, "week", "gemini")

    parts: list[str] = []
    if five_hour is not None:
        parts.append(f"5h {_percent_text(five_hour)}")
    if weekly is not None:
        parts.append(f"wk {_percent_text(weekly)}")

    if isinstance(context_window, dict):
        used_pct = context_window.get("used_percentage")
        size = context_window.get("context_window_size")
        usage = context_window.get("current_usage") if isinstance(context_window.get("current_usage"), dict) else {}
        used_tokens = (usage.get("input_tokens") or 0) + (usage.get("cache_read_input_tokens") or 0)
        if isinstance(used_pct, (int, float)):
            parts.append(f"ctx {round(float(used_pct))}%")
        if isinstance(size, (int, float)) and size > 0:
            used_text = cm.format_tokens(used_tokens) if isinstance(used_tokens, int) and used_tokens > 0 else None
            parts.append(f"{used_text or '?'}/{cm.format_window(size)}")

    return "AG " + " · ".join(parts) if parts else "AG ready"


def main() -> None:
    raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
    line = run(raw)
    if line:
        sys.stdout.write(line + "\n")
        sys.stdout.flush()
