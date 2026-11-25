"""Model context windows and context-health thresholds.

These values are shared by the Antigravity hook, the collector, and the CLI
status line.

Context windows (verified 2026-11-25)
-------------------------------------
* Gemini 4 Neon: 512K in Antigravity (1M only through the Gemini API).
* Gemini 4 Carbon / Argon / Hydrogen and Gemini 3.8 Flash: 1M.
* Claude Opus/Sonnet 5.5: 1M (native). GPT-OSS 120B: 128K.
* Orchestrator windows follow the planner model.

Sources: https://antigravity.google/docs/models and the Gemini API model pages.

Why percentage thresholds instead of a token limit
--------------------------------------------------
Context degradation is continuous and task-dependent; there is no universal
"hallucination token count".
  * NoLiMa (arXiv 2502.05167): at 32K, 11 models drop below 50% of their
    short-context accuracy.
  * RULER (arXiv 2404.06654): only about half of models claiming 32K+ keep
    quality at 32K; effective context is typically 50-65% of the advertised size.
  * Critical-threshold study (arXiv 2601.15300): sharp collapse at 40-50% of
    the maximum window.
  * Chroma "Context Rot" (2025): every tested model degrades as input grows,
    well before the window fills.

The defaults therefore warn at 40% and flag risk at 65% of the real window.
Both percentages are configurable through ``context_thresholds``.
"""

from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_MODEL_WINDOWS: dict[str, int] = {
    "gemini-4-neon": 524_288,
    "gemini-4-carbon": 1_048_576,
    "gemini-4-argon": 1_048_576,
    "gemini-4-hydrogen": 1_048_576,
    "gemini-3.8-flash": 1_048_576,
    "gemini-pro-orchestrator-neon": 524_288,
    "gemini-pro-orchestrator-argon": 1_048_576,
    "gemini-pro-orchestrator": 1_048_576,
    "claude-opus-5-5": 1_000_000,
    "claude-sonnet-5-5": 1_000_000,
    "gpt-oss-120b": 131_072,
}

DEFAULT_CONTEXT_THRESHOLDS: dict[str, float] = {
    "yellow_pct": 40.0,
    "red_pct": 65.0,
}

LEARNED_WINDOWS_FILE = "model_windows_learned.json"

_EFFORT_TOKENS = ("low", "medium", "high", "xhigh", "max", "minimal")


def normalize_model_key(value: Any) -> str:
    """Normalize model ids and display names into a kebab-case key.

    ``"Gemini 4 Neon (High)"`` and ``"gemini-4-neon-high"`` both become
    ``"gemini-4-neon-high"`` so learned windows match transcript ids.
    """
    raw = str(value or "").strip().lower()
    raw = re.sub(r"[()]", " ", raw)
    raw = re.sub(r"[^a-z0-9]+", "-", raw)
    raw = re.sub(r"-{2,}", "-", raw).strip("-")
    return raw


def _positive_int(value: Any) -> int | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def _match(model_key: str, table: dict[str, Any]) -> tuple[int, str] | None:
    """Return the longest matching prefix in ``table`` as ``(size, key)``."""
    best: tuple[int, str] | None = None
    for raw_key, raw_size in table.items():
        key = normalize_model_key(raw_key)
        size = raw_size.get("size") if isinstance(raw_size, dict) else raw_size
        size = _positive_int(size)
        if not key or not size:
            continue
        if model_key == key or model_key.startswith(key):
            if best is None or len(key) > len(best[1]):
                best = (size, key)
    return best


def learned_windows_path(state_dir: Path) -> Path:
    return state_dir / LEARNED_WINDOWS_FILE


def load_learned_windows(state_dir: Path) -> dict[str, Any]:
    try:
        data = json.loads(learned_windows_path(state_dir).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    windows = data.get("windows") if isinstance(data, dict) else None
    return windows if isinstance(windows, dict) else {}


def save_learned_window(state_dir: Path, model: Any, size: Any) -> bool:
    """Persist a context size reported by the CLI (``context_window_size``)."""
    key = normalize_model_key(model)
    window = _positive_int(size)
    if not key or not window:
        return False

    path = learned_windows_path(state_dir)
    windows = load_learned_windows(state_dir)
    windows[key] = {
        "size": window,
        "source": "cli",
        "updated_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f"{path.name}.{os.getpid()}.tmp")
    tmp.write_text(json.dumps({"windows": windows}, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)
    return True


def resolve_model_window(
    model: Any,
    *,
    config_windows: dict[str, Any] | None = None,
    learned_windows: dict[str, Any] | None = None,
    explicit: Any = None,
) -> tuple[int | None, str]:
    """Resolve a window size. Priority: explicit > config > learned CLI > defaults."""
    explicit_window = _positive_int(explicit)
    if explicit_window:
        return explicit_window, "payload"

    key = normalize_model_key(model)
    if not key:
        return None, "unknown"

    for source, table in (
        ("config", config_windows or {}),
        ("cli", learned_windows or {}),
        ("table", DEFAULT_MODEL_WINDOWS),
    ):
        found = _match(key, table)
        if found:
            return found[0], source

    return None, "unknown"


def normalize_thresholds(config: dict[str, Any] | None) -> dict[str, float]:
    """Read and clamp ``context_thresholds`` percentages."""
    thresholds = DEFAULT_CONTEXT_THRESHOLDS.copy()
    values = (config or {}).get("context_thresholds")
    if isinstance(values, dict):
        for name in ("yellow_pct", "red_pct"):
            value = values.get(name)
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                thresholds[name] = float(value)

    yellow_pct = max(1.0, min(99.0, thresholds["yellow_pct"]))
    red_pct = max(yellow_pct + 1.0, min(100.0, thresholds["red_pct"]))
    return {"yellow_pct": yellow_pct, "red_pct": red_pct}


def compute_context(
    tokens: int,
    window: int | None,
    thresholds: dict[str, float] | None = None,
) -> dict[str, Any]:
    """Compute percentage, health level, and absolute warning boundaries."""
    thresholds = thresholds or DEFAULT_CONTEXT_THRESHOLDS
    if not window or window <= 0:
        return {
            "tokens": tokens,
            "window": None,
            "pct": None,
            "level": "unknown",
            "yellow_pct": thresholds["yellow_pct"],
            "red_pct": thresholds["red_pct"],
            "warn_tokens": None,
            "risk_tokens": None,
        }

    pct = round(tokens / window * 100.0, 2)
    level = "red" if pct >= thresholds["red_pct"] else "yellow" if pct >= thresholds["yellow_pct"] else "green"
    return {
        "tokens": tokens,
        "window": window,
        "pct": pct,
        "level": level,
        "yellow_pct": thresholds["yellow_pct"],
        "red_pct": thresholds["red_pct"],
        "warn_tokens": int(round(window * thresholds["yellow_pct"] / 100.0)),
        "risk_tokens": int(round(window * thresholds["red_pct"] / 100.0)),
    }


def format_window(value: Any) -> str:
    """Format a nominal window: 524288 -> 512K, 1048576 -> 1M, 131072 -> 128K."""
    if not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0:
        return "—"
    number = float(value)
    if number >= 1_000_000:
        millions = round(number / 1_000_000 * 10) / 10
        return f"{millions:.1f}".rstrip("0").rstrip(".") + "M"
    if number >= 1024:
        return f"{round(number / 1024)}K"
    return str(int(number))


def format_tokens(value: Any) -> str:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return "—"
    number = float(value)
    if number >= 1_000_000:
        return f"{number / 1_000_000:.2f}M"
    if number >= 1_000:
        return f"{number / 1_000:.0f}K"
    return str(int(number))
