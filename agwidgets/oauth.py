"""Interactive ZCode OAuth login for the command line."""

from __future__ import annotations

import json
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from typing import Any

from . import paths, zcode

INIT_URL = f"{zcode.ZCODE_ORIGIN}/api/v1/oauth/cli/init"
POLL_BASE = f"{zcode.ZCODE_ORIGIN}/api/v1/oauth/cli/poll"


def _post_json(url: str, body: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    payload = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={**headers, "Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8", errors="replace"))


def _get_json(url: str, headers: dict[str, str]) -> dict[str, Any]:
    request = urllib.request.Request(url, method="GET", headers={**headers, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8", errors="replace"))


def run_login(provider: str = "zai") -> None:
    """Run the OAuth flow in the browser and persist the resulting token."""
    poll_token = secrets.token_hex(32)
    auth_header = {"Authorization": f"Bearer {poll_token}"}

    print(f"Initializing ZCode OAuth flow (provider={provider})...")
    init_response = _post_json(INIT_URL, {"provider": provider}, auth_header)
    if init_response.get("code") != 0 or not isinstance(init_response.get("data"), dict):
        raise RuntimeError(f"OAuth init failed: {init_response}")

    data = init_response["data"]
    flow_id = str(data["flow_id"])
    authorize_url = str(data["authorize_url"])
    poll_interval = max(2.0, float(data.get("poll_interval_sec") or 3.0))

    print("\nOpen this URL and sign in to Z.ai:")
    print(f"  {authorize_url}\n")
    try:
        webbrowser.open(authorize_url)
    except OSError:
        pass

    poll_url = f"{POLL_BASE}/{urllib.parse.quote(flow_id, safe='')}"
    deadline = time.monotonic() + 300.0
    print("Waiting for sign-in to complete...")

    while time.monotonic() < deadline:
        time.sleep(poll_interval)
        try:
            poll_response = _get_json(poll_url, auth_header)
        except urllib.error.HTTPError as error:
            if 400 <= error.code < 500 and error.code not in (408, 429):
                raise RuntimeError(f"OAuth poll error HTTP {error.code}") from error
            continue
        except OSError:
            continue

        poll_data = poll_response.get("data")
        if not isinstance(poll_data, dict):
            continue
        status = str(poll_data.get("status") or "")
        if status == "pending":
            continue
        if status == "failed":
            raise RuntimeError("Sign-in was rejected or failed.")
        if status == "ready":
            token = str(poll_data.get("token") or "").strip()
            if not token:
                raise RuntimeError("Server did not return data.token")
            saved = zcode.save_secrets({"zcode_token": token})
            print("\nSuccess. Token saved locally to:")
            print(f"  {paths.secrets_path()}")
            print("\nVoucher and balance check:")
            result = zcode.query_zcode_api(token, saved["device_mid"])
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return

    raise RuntimeError("Sign-in timed out after 5 minutes.")


def status() -> dict[str, Any]:
    """Test the saved token and return the normalized ZCode status."""
    saved = zcode.load_secrets()
    token = str(saved.get("zcode_token") or "").strip()
    if not token:
        raise RuntimeError("No ZCode token configured. Run: python collector/zcode_auth.py")
    return zcode.query_zcode_api(token, str(saved.get("device_mid") or ""))


def save_token(token: str) -> dict[str, Any]:
    """Persist a manually supplied JWT and immediately verify it."""
    saved = zcode.save_secrets({"zcode_token": token.strip()})
    return zcode.query_zcode_api(saved["zcode_token"], saved["device_mid"])
