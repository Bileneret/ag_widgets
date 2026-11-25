"""Publishing the status payload to a private ntfy topic."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any

NTFY_URL = "https://ntfy.sh"
# ntfy accepts up to 4096 bytes per message. Keep headroom for the JSON envelope.
MAX_BODY_BYTES = 3800


def _encode(payload: dict[str, Any]) -> bytes:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def _shrink(payload: dict[str, Any]) -> None:
    """Drop optional human-readable text if the payload is too large."""
    zcode = payload.get("zcode")
    if isinstance(zcode, dict):
        if zcode.get("matched"):
            zcode["matched"] = str(zcode["matched"])[:64]


def publish(topic: str, payload: dict[str, Any], timeout: int = 15) -> tuple[bool, str]:
    """POST ``payload`` to ``https://ntfy.sh/<topic>``. Returns ``(ok, detail)``."""
    body = _encode(payload)
    if len(body) > MAX_BODY_BYTES:
        _shrink(payload)
        body = _encode(payload)
        if len(body) > MAX_BODY_BYTES:
            return False, f"payload too large ({len(body)} bytes)"

    request = urllib.request.Request(
        f"{NTFY_URL}/{topic}",
        data=body,
        method="POST",
        headers={
            "Title": "ag-widgets-status",
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": "ag-widgets-collector/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return 200 <= response.status < 300, f"HTTP {response.status}"
    except urllib.error.HTTPError as error:
        return False, f"HTTP {error.code}: {error.reason}"
    except OSError as error:
        return False, str(error)
