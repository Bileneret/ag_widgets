"""ZCode OAuth login helper.

Usage:
    python collector/zcode_auth.py                 # browser sign-in
    python collector/zcode_auth.py --status        # test the saved token
    python collector/zcode_auth.py --set-token ... # save an existing JWT
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import oauth  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="ZCode OAuth login helper for AG Widgets")
    parser.add_argument("--provider", default="zai", choices=["zai", "zai_cn", "bigmodel"])
    parser.add_argument("--set-token", help="Save an existing ZCode JWT token")
    parser.add_argument("--status", action="store_true", help="Test the saved token")
    args = parser.parse_args()

    try:
        if args.set_token:
            print(json.dumps(oauth.save_token(args.set_token), ensure_ascii=False, indent=2))
        elif args.status:
            print(json.dumps(oauth.status(), ensure_ascii=False, indent=2))
        else:
            oauth.run_login(provider=args.provider)
    except Exception as error:  # noqa: BLE001 - CLI should print a readable message.
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
