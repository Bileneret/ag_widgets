"""Runtime paths, JSON persistence, and configuration loading.

All mutable state lives under ``%LOCALAPPDATA%\\ag-widgets`` so the repository
never contains personal data (ntfy topic, tokens, learned model windows).
"""

from __future__ import annotations

import json
import os
import re
import secrets
from pathlib import Path
from typing import Any

APP_DIR_NAME = "ag-widgets"
NTFY_TOPIC_RE = r"^[A-Za-z0-9_-]{1,64}$"


def local_app_data() -> Path:
    value = os.environ.get("LOCALAPPDATA")
    return Path(value) if value else Path.home() / "AppData" / "Local"


def app_dir() -> Path:
    """Return the runtime directory, creating it when missing."""
    path = local_app_data() / APP_DIR_NAME
    path.mkdir(parents=True, exist_ok=True)
    return path


def state_dir() -> Path:
    """Return the directory holding hook, collector, and status state."""
    path = app_dir() / "state"
    path.mkdir(parents=True, exist_ok=True)
    return path


def config_path() -> Path:
    return app_dir() / "config.json"


def secrets_path() -> Path:
    return app_dir() / "secrets.json"


def annotations_dir() -> Path:
    """Antigravity writes one ``*.pbtxt`` annotation per viewed chat."""
    return Path.home() / ".gemini" / "antigravity" / "annotations"


def read_json(path: Path) -> dict[str, Any] | None:
    """Read a JSON object, returning ``None`` for missing or malformed files."""
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def write_json(path: Path, data: Any) -> None:
    """Atomically replace ``path`` with pretty-printed JSON."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f"{path.name}.{os.getpid()}.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)


def load_config() -> dict[str, Any]:
    return read_json(config_path()) or {}


def ensure_ntfy_topic() -> str:
    """Return the configured ntfy topic, generating a private 96-bit one if needed."""
    config = load_config()
    topic = str(config.get("ntfy_topic") or "").strip()
    if not re.fullmatch(NTFY_TOPIC_RE, topic):
        topic = f"agw-{secrets.token_hex(12)}"
        config["ntfy_topic"] = topic
        write_json(config_path(), config)
    return topic
