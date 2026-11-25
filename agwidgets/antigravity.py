"""Antigravity quota collection through the ``agy`` CLI."""

from __future__ import annotations

import json
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import paths

AGY_REFRESH_INTERVAL_SEC = 900  # 15 minutes


def find_executable() -> str | None:
    """Locate the Antigravity CLI on PATH or in the default install directory."""
    found = shutil.which("agy")
    if found:
        return found
    candidate = paths.local_app_data() / "agy" / "bin" / "agy.exe"
    return str(candidate) if candidate.exists() else None


def _parse_usage_output(stdout: str) -> dict[str, dict[str, Any]]:
    """Map bucket id -> quota info from ``agy -p /usage --output-format json``."""
    parsed = json.loads(stdout)
    groups = (((parsed.get("command") or {}).get("data") or {}).get("groups")) or []
    quota: dict[str, dict[str, Any]] = {}
    for group in groups:
        if not isinstance(group, dict):
            continue
        for bucket in group.get("buckets") or []:
            if not isinstance(bucket, dict):
                continue
            bucket_id = str(bucket.get("id") or "").strip()
            if not bucket_id:
                continue
            quota[bucket_id] = {
                "remaining_fraction": bucket.get("remaining_fraction"),
                "reset_time": bucket.get("reset_time"),
                "window": bucket.get("window"),
                "group": group.get("name"),
            }
    return quota


def refresh_quota(state_dir: Path, force: bool = False) -> bool:
    """Run ``agy /usage`` and persist fresh quota data. Returns ``True`` on update."""
    last_path = state_dir / "agy_last.json"
    now_ts = datetime.now(timezone.utc).timestamp()
    if not force:
        last = paths.read_json(last_path) or {}
        if now_ts - float(last.get("last_ts") or 0.0) < AGY_REFRESH_INTERVAL_SEC:
            return False

    executable = find_executable()
    if not executable:
        return False

    try:
        proc = subprocess.run(
            [executable, "-p", "/usage", "--output-format", "json"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=60,
        )
    except (OSError, subprocess.SubprocessError):
        return False

    if proc.returncode != 0 or not proc.stdout.strip():
        return False

    try:
        quota = _parse_usage_output(proc.stdout)
    except (json.JSONDecodeError, TypeError):
        return False
    if not quota:
        return False

    # Keep fields written by the status line (context window, model, plan tier).
    existing = paths.read_json(state_dir / "agy_status.json") or {}
    payload = {
        **existing,
        "plan_tier": existing.get("plan_tier") or "Pro",
        "quota": quota,
        "saved_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
    }
    paths.write_json(state_dir / "agy_status.json", payload)
    paths.write_json(last_path, {"last_ts": now_ts})
    return True


def classify_bucket_kind(bucket_id: str) -> str:
    """Classify a bucket as a five-hour, weekly, or other limit."""
    lowered = bucket_id.lower()
    if "week" in lowered:
        return "weekly"
    if re.search(r"(5h|five|hour)", lowered):
        return "5h"
    return "other"


def extract_quota(state_dir: Path) -> dict[str, Any]:
    """Normalize stored Antigravity quota into ``{plan_tier, buckets}``."""
    raw = paths.read_json(state_dir / "agy_status.json")
    if not raw:
        return {"plan_tier": None, "buckets": []}

    buckets: list[dict[str, Any]] = []
    quota_map = raw.get("quota")

    if isinstance(quota_map, dict):
        for bucket_id, info in quota_map.items():
            if not isinstance(info, dict):
                continue
            fraction = info.get("remaining_fraction")
            buckets.append(
                {
                    "id": str(bucket_id),
                    "kind": classify_bucket_kind(str(bucket_id)),
                    "remaining_fraction": float(fraction) if isinstance(fraction, (int, float)) else None,
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
                    "remaining_fraction": item.get("remaining_fraction"),
                    "reset_time": item.get("reset_time"),
                }
            )

    return {"plan_tier": raw.get("plan_tier"), "buckets": buckets}


def pick_bucket(quota: dict[str, Any], window_name: str, group: str) -> dict[str, Any] | None:
    """Prefer a Gemini bucket for the requested window, then any matching bucket."""
    for bucket_id, info in quota.items():
        lowered = str(bucket_id).lower()
        if group in lowered and window_name in lowered:
            return info if isinstance(info, dict) else None
    for bucket_id, info in quota.items():
        if window_name in str(bucket_id).lower():
            return info if isinstance(info, dict) else None
    return None
