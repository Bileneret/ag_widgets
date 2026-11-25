"""Antigravity quota collection through the ``agy`` CLI."""

from __future__ import annotations

import json
import math
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import paths

AGY_REFRESH_INTERVAL_SEC = 900  # 15 minutes
AGY_FAILURE_BACKOFF_BASE_SEC = 120
AGY_FAILURE_BACKOFF_MAX_EXPONENT = 8

_FIVE_HOUR_RE = re.compile(r"(^|[-_])(5h|five[-_]?hour)($|[-_])")


def _as_finite_float(value: Any) -> float | None:
    """Return ``value`` as a finite float, ignoring booleans and junk."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    try:
        number = float(value)
    except (OverflowError, TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _safe_timestamp(value: Any) -> float:
    """Read a stored timestamp; a malformed value means "never"."""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return 0.0
    return number if math.isfinite(number) else 0.0


def _positive_int(value: Any) -> int | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = int(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return number if number > 0 else None


def find_executable() -> str | None:
    """Locate the Antigravity CLI in ``AGY_PATH``, on PATH, or in its install dir.

    Antigravity 2.0 installs ``agy.exe`` into ``~/.gemini/bin`` and does not add
    that directory to ``PATH``, so the standard locations are tried explicitly.
    """
    override = os.environ.get("AGY_PATH")
    if override:
        candidate = Path(override)
        if candidate.exists():
            return str(candidate)

    found = shutil.which("agy")
    if found:
        return found

    home = Path.home()
    candidates = (
        home / ".gemini" / "bin" / "agy.exe",
        home / ".gemini" / "bin" / "agy",
        paths.local_app_data() / "agy" / "bin" / "agy.exe",
    )
    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    return None


def _parse_usage_output(stdout: str) -> dict[str, dict[str, Any]]:
    """Map bucket id -> quota info from ``agy -p /usage --output-format json``."""
    parsed = json.loads(stdout)
    if not isinstance(parsed, dict):
        return {}
    command = parsed.get("command")
    if not isinstance(command, dict):
        return {}
    data = command.get("data")
    if not isinstance(data, dict):
        return {}
    groups = data.get("groups")
    if not isinstance(groups, list):
        return {}

    quota: dict[str, dict[str, Any]] = {}
    for group in groups:
        if not isinstance(group, dict):
            continue
        buckets = group.get("buckets")
        if not isinstance(buckets, list):
            continue
        for bucket in buckets:
            if not isinstance(bucket, dict):
                continue
            bucket_id = str(bucket.get("id") or "").strip()
            if not bucket_id:
                continue
            reset_time = bucket.get("reset_time")
            if isinstance(reset_time, bool) or not isinstance(reset_time, (str, int, float)):
                reset_time = None
            window = bucket.get("window")
            if isinstance(window, bool) or not isinstance(window, (str, int, float)):
                window = None
            quota[bucket_id] = {
                "remaining_fraction": _as_finite_float(bucket.get("remaining_fraction")),
                "reset_time": reset_time,
                "window": window,
                "group": group.get("name"),
            }
    return quota


def _save_last(
    last_path: Path,
    *,
    attempt_ts: float,
    last_ts: float,
    failures: int,
    error: str | None,
) -> None:
    """Persist refresh bookkeeping; a write error must not break the caller."""
    try:
        paths.update_json(
            last_path,
            lambda data: {
                **data,
                "last_attempt_ts": attempt_ts,
                "last_ts": last_ts,
                "consecutive_failures": failures,
                "last_error": error,
            },
        )
    except (OSError, paths.ConfigReadError):
        pass


def refresh_quota(state_dir: Path, force: bool = False) -> bool:
    """Run ``agy /usage`` and persist fresh quota data. Returns ``True`` on update."""
    state_dir = Path(state_dir)
    last_path = state_dir / "agy_last.json"
    status_path = state_dir / "agy_status.json"
    now_ts = datetime.now(timezone.utc).timestamp()

    last = paths.read_json(last_path) or {}
    last_ts = _safe_timestamp(last.get("last_ts"))
    stored_error = last.get("last_error")
    stored_error = stored_error if isinstance(stored_error, str) else None
    failures = _positive_int(last.get("consecutive_failures")) or 0
    # Legacy files only have ``last_ts``; treat it as the last attempt.
    attempt_ts = _safe_timestamp(last.get("last_attempt_ts")) or last_ts
    if not force:
        interval = AGY_REFRESH_INTERVAL_SEC
        if failures:
            exponent = min(failures - 1, AGY_FAILURE_BACKOFF_MAX_EXPONENT)
            interval = min(AGY_REFRESH_INTERVAL_SEC, AGY_FAILURE_BACKOFF_BASE_SEC * 2 ** exponent)
        if now_ts - attempt_ts < interval:
            return False

    def fail(reason: str) -> bool:
        _save_last(last_path, attempt_ts=now_ts, last_ts=last_ts, failures=failures + 1, error=reason)
        return False

    executable = find_executable()
    if not executable:
        return fail("missing_executable")

    try:
        proc = subprocess.run(
            [executable, "-p", "/usage", "--output-format", "json"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=60,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except subprocess.TimeoutExpired:
        return fail("timeout")
    except (OSError, subprocess.SubprocessError):
        return fail("run_failed")

    if proc.returncode != 0:
        return fail("exit_code")
    if not proc.stdout.strip():
        return fail("empty_output")

    try:
        quota = _parse_usage_output(proc.stdout)
    except (json.JSONDecodeError, TypeError, ValueError):
        return fail("parse_failed")
    if not quota:
        return fail("empty_quota")

    # Keep fields written by the status line (context window, model, plan tier).
    saved_at = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    try:
        paths.update_json(
            status_path,
            lambda data: {
                **data,
                "plan_tier": data.get("plan_tier"),
                "quota": quota,
                "saved_at": saved_at,
            },
        )
    except (OSError, paths.ConfigReadError):
        # The CLI worked but the state file is unreadable/unwritable: remember
        # the attempt so the next tick still respects the refresh interval.
        _save_last(
            last_path,
            attempt_ts=now_ts,
            last_ts=last_ts,
            failures=failures,
            error=stored_error,
        )
        return False

    _save_last(last_path, attempt_ts=now_ts, last_ts=now_ts, failures=0, error=None)
    return True


def classify_bucket_kind(bucket_id: str) -> str:
    """Classify a bucket as a five-hour, weekly, or other limit."""
    lowered = str(bucket_id).lower()
    if "week" in lowered:
        return "weekly"
    if _FIVE_HOUR_RE.search(lowered):
        return "5h"
    return "other"


def extract_quota(state_dir: Path) -> dict[str, Any]:
    """Normalize stored Antigravity quota into ``{plan_tier, buckets, ...}``."""
    state_dir = Path(state_dir)
    last = paths.read_json(state_dir / "agy_last.json") or {}
    failures = _positive_int(last.get("consecutive_failures")) or 0
    error = None
    if failures > 0:
        stored_error = last.get("last_error")
        error = stored_error if isinstance(stored_error, str) and stored_error else None

    raw = paths.read_json(state_dir / "agy_status.json")
    if not raw:
        return {"plan_tier": None, "buckets": [], "saved_at": None, "error": error}

    buckets: list[dict[str, Any]] = []
    quota_map = raw.get("quota")

    if isinstance(quota_map, dict):
        for bucket_id, info in quota_map.items():
            if not isinstance(info, dict):
                continue
            buckets.append(
                {
                    "id": str(bucket_id),
                    "kind": classify_bucket_kind(str(bucket_id)),
                    "remaining_fraction": _as_finite_float(info.get("remaining_fraction")),
                    "reset_time": info.get("reset_time"),
                }
            )
    elif isinstance(raw.get("buckets"), list):
        for item in raw["buckets"]:
            if not isinstance(item, dict):
                continue
            bucket_id = str(item.get("id") or "unknown")
            buckets.append(
                {
                    "id": bucket_id,
                    "kind": item.get("kind") or classify_bucket_kind(bucket_id),
                    "remaining_fraction": _as_finite_float(item.get("remaining_fraction")),
                    "reset_time": item.get("reset_time"),
                }
            )

    return {
        "plan_tier": raw.get("plan_tier"),
        "buckets": buckets,
        "saved_at": raw.get("saved_at"),
        "error": error,
    }


def pick_bucket(quota: dict[str, Any], window_name: str, group: str) -> dict[str, Any] | None:
    """Prefer a bucket for the requested window and group, then any window match."""
    if not isinstance(quota, dict):
        return None
    window_key = str(window_name).lower()
    group_key = str(group).lower()

    for bucket_id, info in quota.items():
        if not isinstance(info, dict):
            continue
        haystacks = (str(bucket_id).lower(), str(info.get("group") or "").lower())
        if any(window_key in text for text in haystacks) and any(group_key in text for text in haystacks):
            return info
    for bucket_id, info in quota.items():
        if not isinstance(info, dict):
            continue
        haystacks = (str(bucket_id).lower(), str(info.get("group") or "").lower())
        if any(window_key in text for text in haystacks):
            return info
    return None
