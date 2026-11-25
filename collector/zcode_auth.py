"""ZCode OAuth login helper.

Usage:
    python collector/zcode_auth.py                                  # browser sign-in
    python collector/zcode_auth.py --status                         # test the saved token
    python collector/zcode_auth.py --set-token-stdin [--no-verify]  # save a JWT from stdin
    python collector/zcode_auth.py --set-token <jwt>                # legacy argv variant
    python collector/zcode_auth.py --clear-token                    # forget the saved token
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import crypto, oauth, paths, zcode  # noqa: E402


def _emit(text: str) -> None:
    """Write ``text`` plus a newline as UTF-8, tolerating legacy code pages."""
    data = (text + "\n").encode("utf-8", errors="replace")
    stream = getattr(sys.stdout, "buffer", None)
    if stream is not None:
        try:
            stream.write(data)
            stream.flush()
            return
        except (AttributeError, OSError):
            pass
    try:
        sys.stdout.write(text + "\n")
        sys.stdout.flush()
    except (AttributeError, OSError, UnicodeEncodeError):
        pass


def _set_token_from_stdin(verify: bool) -> None:
    """Save the JWT read from stdin (never from argv) and optionally verify it."""
    token = sys.stdin.read().strip()
    if not token:
        print("Error: token must not be empty", file=sys.stderr)
        sys.exit(2)

    saved = zcode.save_secrets({"zcode_token": token})
    zcode.invalidate_cache(paths.state_dir())
    device_mid = str(saved.get("device_mid") or "")
    result: dict[str, Any] = {
        "ok": True,
        "encrypted": crypto.encryption_available(),
        "device_mid": device_mid,
    }
    if verify:
        result["status"] = zcode.query_zcode_api(token, device_mid)
    _emit(json.dumps(result, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description="ZCode OAuth login helper for AG Widgets")
    parser.add_argument("--provider", default="zai", choices=["zai", "zai_cn", "bigmodel"])
    parser.add_argument("--set-token", help="Save an existing ZCode JWT token from the command line")
    parser.add_argument(
        "--set-token-stdin",
        action="store_true",
        help="Read the ZCode JWT from stdin (recommended over --set-token)",
    )
    parser.add_argument(
        "--no-verify",
        action="store_true",
        help="With --set-token-stdin: skip the network check of the new token",
    )
    parser.add_argument("--clear-token", action="store_true", help="Remove the saved token")
    parser.add_argument("--status", action="store_true", help="Test the saved token")
    args = parser.parse_args()

    try:
        if args.set_token_stdin:
            _set_token_from_stdin(verify=not args.no_verify)
        elif args.clear_token:
            oauth.clear_token()
            zcode.invalidate_cache(paths.state_dir())
            _emit(json.dumps({"ok": True}, ensure_ascii=False))
        elif args.set_token is not None:
            token = args.set_token.strip()
            if not token:
                print("Error: token must not be empty", file=sys.stderr)
                sys.exit(2)
            print(
                "Warning: passing tokens via CLI arguments exposes them in process lists; "
                "prefer --set-token-stdin",
                file=sys.stderr,
            )
            _emit(json.dumps(oauth.save_token(token), ensure_ascii=False, indent=2))
        elif args.status:
            _emit(json.dumps(oauth.status(), ensure_ascii=False, indent=2))
        else:
            oauth.run_login(provider=args.provider)
    except Exception as error:  # noqa: BLE001 - CLI should print a readable message.
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
