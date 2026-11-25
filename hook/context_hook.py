"""Antigravity hook entry point.

Antigravity runs this command on PostInvocation/Stop and passes a JSON payload
on stdin. The hook must always exit cleanly and print a JSON object.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets.hook import main  # noqa: E402

if __name__ == "__main__":
    main()
