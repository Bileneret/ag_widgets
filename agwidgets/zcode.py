"""ZCode API client: coupon detection and token balance."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import paths

ZCODE_ORIGIN = "https://zcode.z.ai"
ZCODE_APP_VERSION = "3.5.0"
ZCODE_POLL_INTERVAL_SEC = 600  # 10 minutes


def load_secrets() -> dict[str, Any]:
    return paths.read_json(paths.secrets_path()) or {}


def save_secrets(updates: dict[str, Any]) -> dict[str, Any]:
    """Merge ``updates`` into ``secrets.json`` and ensure a stable device id."""
    data = load_secrets()
    data.update(updates)
    data.setdefault("device_mid", str(uuid.uuid4()))
    paths.write_json(paths.secrets_path(), data)
    return data


def _http_get_json(url: str, headers: dict[str, str], timeout: int = 15) -> tuple[int, dict[str, Any] | None]:
    request = urllib.request.Request(url, method="GET", headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            return response.status, json.loads(raw)
    except urllib.error.HTTPError as error:
        try:
            body = json.loads(error.read().decode("utf-8", errors="replace"))
        except (ValueError, OSError):
            body = None
        return error.code, body
    except (OSError, ValueError):
        return 0, None


def _to_number(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number else None  # NaN guard


def _parse_timestamp_iso(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        timestamp = float(value)
        if timestamp > 1e12:
            timestamp /= 1000.0
        if timestamp <= 0:
            return None
        return datetime.fromtimestamp(timestamp, tz=timezone.utc).isoformat().replace("+00:00", "Z")
    text = str(value).strip()
    if not text:
        return None
    try:
        return _parse_timestamp_iso(float(text))
    except ValueError:
        return text


def _extract_claim_coupon(touch_payload: dict[str, Any]) -> tuple[bool, str, str]:
    """Return ``(coupon_visible, plan_id, label)`` from the marketing touch response."""
    data = touch_payload.get("data")
    if not isinstance(data, dict):
        return False, "", ""
    deliveries = data.get("deliveries")
    if not isinstance(deliveries, list):
        return False, "", ""

    for item in deliveries:
        if not isinstance(item, dict):
            continue
        position = item.get("resource_position")
        container = item.get("banner") if position == "banner" else item.get("popup")
        if not isinstance(container, dict):
            continue
        buttons = container.get("buttons") or []
        if not isinstance(buttons, list):
            continue
        for button in buttons:
            if not isinstance(button, dict):
                continue
            action = button.get("action")
            if isinstance(action, dict) and action.get("type") == "claim_zcode_plan":
                plan_id = str((action.get("args") or {}).get("plan_id") or "").strip()
                button_text = str(((button.get("text") or {}).get("content")) or "").strip()
                title = str(((container.get("title") or {}).get("content")) or "").strip()
                label = button_text or title or f"plan:{plan_id}"
                return True, plan_id, label[:100]
    return False, "", ""


def _extract_balance(balance_payload: dict[str, Any]) -> dict[str, Any]:
    """Summarize active plans and balances from the billing response."""
    empty = {"remaining_units": None, "total_units": None, "ends_at": None, "plan_name": ""}
    data = balance_payload.get("data")
    if not isinstance(data, dict):
        return empty

    now_sec = _to_number(data.get("server_time")) or datetime.now(timezone.utc).timestamp()
    plans = data.get("plans") if isinstance(data.get("plans"), list) else []
    active_plan_ids: set[str] = set()
    active_user_plan_ids: set[str] = set()
    soonest_end: float | None = None
    plan_name = ""

    for plan in plans:
        if not isinstance(plan, dict):
            continue
        status = str(plan.get("status") or "").strip().lower()
        end = _to_number(plan.get("ends_at"))
        if end is not None and end > 1e12:
            end /= 1000.0
        expired = status == "expired" or (end is not None and 0 < end <= now_sec)
        if expired:
            continue
        if plan.get("plan_id"):
            active_plan_ids.add(str(plan["plan_id"]))
        if plan.get("user_plan_id"):
            active_user_plan_ids.add(str(plan["user_plan_id"]))
        if not plan_name and plan.get("name"):
            plan_name = str(plan["name"]).strip()
        if end is not None and end > now_sec and (soonest_end is None or end < soonest_end):
            soonest_end = end

    balances = data.get("balances") if isinstance(data.get("balances"), list) else []
    total_sum = 0.0
    remaining_sum = 0.0
    found = False

    for balance in balances:
        if not isinstance(balance, dict):
            continue
        user_plan_id = str(balance.get("user_plan_id") or "")
        plan_id = str(balance.get("plan_id") or "")
        if active_user_plan_ids or active_plan_ids:
            belongs = (user_plan_id and user_plan_id in active_user_plan_ids) or (
                plan_id and plan_id in active_plan_ids
            )
            if (user_plan_id or plan_id) and not belongs:
                continue

        total = _to_number(balance.get("total_units"))
        remaining = _to_number(balance.get("available_units"))
        if remaining is None:
            remaining = _to_number(balance.get("remaining_units"))
        if remaining is None and total is not None:
            used = _to_number(balance.get("used_units")) or 0.0
            remaining = max(0.0, total - used)

        if total is not None or remaining is not None:
            found = True
            total_sum += total or 0.0
            remaining_sum += remaining or 0.0
            period_end = _to_number(balance.get("period_end"))
            if period_end is not None and period_end > 1e12:
                period_end /= 1000.0
            if period_end is not None and period_end > now_sec:
                if soonest_end is None or period_end < soonest_end:
                    soonest_end = period_end

    if not found:
        return {
            "remaining_units": None,
            "total_units": None,
            "ends_at": _parse_timestamp_iso(soonest_end),
            "plan_name": plan_name,
        }

    return {
        "remaining_units": int(round(remaining_sum)),
        "total_units": int(round(total_sum)) if total_sum > 0 else None,
        "ends_at": _parse_timestamp_iso(soonest_end),
        "plan_name": plan_name,
    }


def query_zcode_api(token: str, device_mid: str, seq: int = 0) -> dict[str, Any]:
    """Call the marketing and billing endpoints and normalize their result."""
    headers = {
        "Authorization": f"Bearer {token.strip()}",
        "X-Device-Mid": device_mid.strip(),
        "X-Client-Language": "ru-RU",
        "X-ZCode-App-Version": ZCODE_APP_VERSION,
        "Accept": "application/json",
    }

    touch_code, touch_body = _http_get_json(f"{ZCODE_ORIGIN}/api/v1/marketing/touch?seq={seq}", headers)
    balance_code, balance_body = _http_get_json(f"{ZCODE_ORIGIN}/api/v1/zcode-plan/billing/balance", headers)

    checked_at = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    network_error = touch_code == 0 and balance_code == 0

    if touch_code == 401 or balance_code == 401:
        return {
            "running": True,
            "coupon_visible": False,
            "matched": "",
            "plan_id": "",
            "method": "api",
            "auth_ok": False,
            "network_error": False,
            "remaining_units": None,
            "total_units": None,
            "ends_at": None,
            "plan_name": "",
            "checked_at": checked_at,
        }

    coupon_visible, plan_id, matched = False, "", ""
    if touch_code == 200 and isinstance(touch_body, dict) and touch_body.get("code") == 0:
        coupon_visible, plan_id, matched = _extract_claim_coupon(touch_body)

    balance = {"remaining_units": None, "total_units": None, "ends_at": None, "plan_name": ""}
    if balance_code == 200 and isinstance(balance_body, dict) and (
        balance_body.get("code") == 0 or balance_body.get("success") is True
    ):
        balance = _extract_balance(balance_body)

    return {
        "running": True,
        "coupon_visible": coupon_visible,
        "matched": matched,
        "plan_id": plan_id,
        "method": "api",
        # auth_ok is False only when the server rejects the token; a network
        # outage leaves it unknown instead of pretending the token expired.
        "auth_ok": False if (touch_code == 401 or balance_code == 401)
        else True if (touch_code == 200 or balance_code == 200)
        else None,
        "network_error": network_error,
        "remaining_units": balance["remaining_units"],
        "total_units": balance["total_units"],
        "ends_at": balance["ends_at"],
        "plan_name": balance["plan_name"],
        "checked_at": checked_at,
    }


def fetch_cached_status(state_dir: Path, force: bool = False) -> dict[str, Any] | None:
    """Return ZCode status from the API, or ``None`` when no token is configured."""
    secrets = load_secrets()
    token = str(secrets.get("zcode_token") or "").strip()
    if not token:
        return None

    device_mid = str(secrets.get("device_mid") or "").strip()
    if not device_mid:
        device_mid = str(save_secrets({})["device_mid"])

    cache_path = state_dir / "zcode_api_cache.json"
    now_ts = datetime.now(timezone.utc).timestamp()
    seq = 0
    if cache_path.exists():
        try:
            cached = json.loads(cache_path.read_text(encoding="utf-8"))
            seq = int(cached.get("seq") or 0)
            fresh = now_ts - float(cached.get("ts") or 0.0) < ZCODE_POLL_INTERVAL_SEC
            if not force and fresh and isinstance(cached.get("result"), dict):
                return cached["result"]
        except (OSError, ValueError, TypeError):
            pass

    result = query_zcode_api(token, device_mid, seq=seq)
    paths.write_json(cache_path, {"ts": now_ts, "seq": seq + 1, "result": result})
    return result
