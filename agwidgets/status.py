"""Status aggregation, change detection, and ntfy publishing."""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import antigravity, context, ntfy, paths, zcode

MIN_PUBLISH_INTERVAL_SEC = 600  # 10 minutes
HEARTBEAT_INTERVAL_SEC = 3600  # 60 minutes


def _as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _disabled_zcode(checked_at: str) -> dict[str, Any]:
    return {
        "running": False,
        "coupon_visible": False,
        "matched": "",
        "plan_id": "",
        "method": "disabled",
        "auth_ok": None,
        "network_error": False,
        "remaining_units": None,
        "total_units": None,
        "ends_at": None,
        "plan_name": "",
        "checked_at": checked_at,
    }


def build_status(
    state_dir: Path,
    config: dict[str, Any],
    now: datetime,
    force_refresh: bool = False,
) -> dict[str, Any]:
    """Collect quota, ZCode, and context data into the published status document."""
    antigravity.refresh_quota(state_dir, force=force_refresh)
    zcode_status = zcode.fetch_cached_status(state_dir, force=force_refresh)
    checked_at = now.isoformat(timespec="seconds")
    if zcode_status is None:
        zcode_status = _disabled_zcode(checked_at)

    return {
        "updated_at": checked_at,
        "antigravity": antigravity.extract_quota(state_dir),
        "zcode": {
            "running": bool(zcode_status.get("running")),
            "coupon_visible": bool(zcode_status.get("coupon_visible")),
            "matched": zcode_status.get("matched") or "",
            "plan_id": zcode_status.get("plan_id") or "",
            "method": zcode_status.get("method") or "disabled",
            "auth_ok": zcode_status.get("auth_ok"),
            "network_error": bool(zcode_status.get("network_error")),
            "remaining_units": zcode_status.get("remaining_units"),
            "total_units": zcode_status.get("total_units"),
            "ends_at": zcode_status.get("ends_at"),
            "plan_name": zcode_status.get("plan_name") or "",
            "checked_at": zcode_status.get("checked_at") or checked_at,
        },
        "context_summary": context.extract_context_summary(state_dir, config),
    }


def has_meaningful_change(previous: dict[str, Any] | None, current: dict[str, Any]) -> bool:
    """Return ``True`` when the new status differs enough to notify the phone."""
    if not previous:
        return True

    previous_context = _as_dict(previous.get("context_summary"))
    current_context = _as_dict(current.get("context_summary"))
    if previous_context.get("conversation_id") != current_context.get("conversation_id"):
        return True
    if bool(previous_context.get("active")) != bool(current_context.get("active")):
        return True
    if previous_context.get("level") != current_context.get("level"):
        return True
    if previous_context.get("model") != current_context.get("model"):
        return True

    previous_pct = previous_context.get("pct")
    current_pct = current_context.get("pct")
    if isinstance(previous_pct, (int, float)) and isinstance(current_pct, (int, float)):
        if abs(current_pct - previous_pct) >= 2:
            return True
    elif (previous_pct is None) != (current_pct is None):
        return True

    previous_zcode = _as_dict(previous.get("zcode"))
    current_zcode = _as_dict(current.get("zcode"))
    for key in ("coupon_visible", "network_error"):
        if bool(previous_zcode.get(key)) != bool(current_zcode.get(key)):
            return True
    if previous_zcode.get("auth_ok") != current_zcode.get("auth_ok"):
        return True
    if (previous_zcode.get("plan_id") or "") != (current_zcode.get("plan_id") or ""):
        return True

    previous_remaining = previous_zcode.get("remaining_units")
    current_remaining = current_zcode.get("remaining_units")
    current_total = current_zcode.get("total_units")
    if (
        isinstance(previous_remaining, (int, float))
        and isinstance(current_remaining, (int, float))
        and isinstance(current_total, (int, float))
        and current_total > 0
    ):
        if abs(current_remaining - previous_remaining) / current_total >= 0.02:
            return True
    elif (previous_remaining is None) != (current_remaining is None):
        return True

    previous_buckets = {
        bucket.get("id"): bucket
        for bucket in (_as_dict(previous.get("antigravity")).get("buckets") or [])
        if isinstance(bucket, dict)
    }
    current_buckets = {
        bucket.get("id"): bucket
        for bucket in (_as_dict(current.get("antigravity")).get("buckets") or [])
        if isinstance(bucket, dict)
    }
    if set(previous_buckets) != set(current_buckets):
        return True

    for bucket_id, bucket in current_buckets.items():
        old = previous_buckets.get(bucket_id, {})
        if bucket.get("reset_time") != old.get("reset_time"):
            return True
        new_fraction = bucket.get("remaining_fraction")
        old_fraction = old.get("remaining_fraction")
        if isinstance(new_fraction, (int, float)) and isinstance(old_fraction, (int, float)):
            if abs(new_fraction - old_fraction) >= 0.02:
                return True
        elif new_fraction != old_fraction:
            return True

    return False


def run_once(force_publish: bool = False, dry_run: bool = False) -> dict[str, Any]:
    """Collect status, write ``status.json``, and publish when appropriate."""
    state_dir = paths.state_dir()
    topic = paths.ensure_ntfy_topic()
    now = datetime.now(timezone.utc).astimezone()
    now_ts = now.timestamp()
    config = paths.load_config()

    status = build_status(state_dir, config, now, force_refresh=force_publish)
    paths.write_json(state_dir / "status.json", status)

    publish_state_path = state_dir / "publish_state.json"
    publish_state = paths.read_json(publish_state_path) or {}
    last_published_ts = float(publish_state.get("last_published_ts") or 0.0)
    last_payload = publish_state.get("last_payload")

    elapsed = now_ts - last_published_ts
    changed = has_meaningful_change(_as_dict(last_payload) or None, status)
    previous_context = _as_dict(_as_dict(last_payload).get("context_summary"))
    current_context = _as_dict(status.get("context_summary"))
    active_changed = previous_context.get("conversation_id") != current_context.get("conversation_id")

    should_publish = (
        force_publish
        or last_published_ts == 0.0
        or active_changed
        or elapsed >= HEARTBEAT_INTERVAL_SEC
        or (changed and elapsed >= MIN_PUBLISH_INTERVAL_SEC)
    )

    publish_info: dict[str, Any] = {"published": False, "reason": "throttled_or_unchanged"}
    if dry_run:
        publish_info = {
            "published": False,
            "reason": "dry_run",
            "would_publish": bool(should_publish),
            "changed": changed,
        }
    elif should_publish:
        ok, detail = ntfy.publish(topic, status)
        publish_info = {"published": ok, "detail": detail, "changed": changed}
        if ok:
            paths.write_json(
                publish_state_path,
                {
                    "last_published_ts": now_ts,
                    "last_published_at": status["updated_at"],
                    "last_payload": status,
                },
            )

    return {"status_written": True, "topic": topic, "publish": publish_info}


def main() -> None:
    result = run_once(force_publish="--force" in sys.argv, dry_run="--dry-run" in sys.argv)
    sys.stdout.write(json.dumps(result, ensure_ascii=False) + "\n")
