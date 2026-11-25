"""Runtime paths, JSON persistence, and configuration loading.

All mutable state lives under ``%LOCALAPPDATA%\\ag-widgets`` so the repository
never contains personal data (ntfy topic, tokens, learned model windows).

Writes are atomic and inter-process safe: every JSON document has a companion
``*.lock`` file guarded through ``msvcrt`` (Windows) or ``fcntl`` (POSIX), and
``os.replace`` is retried because Windows refuses to swap a file that another
process (the widget, the hook, the collector) still has open for reading.
"""

from __future__ import annotations

import json
import os
import re
import secrets
import sys
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Callable, Iterator

from . import crypto

APP_DIR_NAME = "ag-widgets"
NTFY_TOPIC_RE = r"^[A-Za-z0-9_-]{1,64}$"

LOCK_TIMEOUT_SEC = 5.0
LOCK_RETRY_INTERVAL_SEC = 0.02
REPLACE_ATTEMPTS = 5
REPLACE_BASE_DELAY_SEC = 0.02
REPLACE_MAX_DELAY_SEC = 0.16

try:  # Windows
    import msvcrt
except ImportError:  # pragma: no cover - platform dependent
    msvcrt = None  # type: ignore[assignment]

try:  # POSIX
    import fcntl
except ImportError:  # pragma: no cover - platform dependent
    fcntl = None  # type: ignore[assignment]


class ConfigReadError(RuntimeError):
    """Raised when a file exists but cannot be read as a JSON object.

    The distinction matters: a missing file means "first run, safe to create",
    while a corrupt or unreadable one means "the user has data we must not
    overwrite" (dropping a config would regenerate the ntfy topic and silently
    break Android sync).
    """


class _LockState:
    """Per-lock in-process state: a re-entrant mutex plus the OS lock handle."""

    __slots__ = ("path", "rlock", "depth", "handle")

    def __init__(self, path: Path) -> None:
        self.path = path
        self.rlock = threading.RLock()
        self.depth = 0
        self.handle: Any = None


_LOCK_STATE: dict[str, _LockState] = {}
_LOCK_STATE_GUARD = threading.Lock()


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


def lock_path_for(path: Path) -> Path:
    """Return the companion lock file (``secrets.json`` -> ``secrets.lock``)."""
    name = path.name
    if name.lower().endswith(".json"):
        name = name[: -len(".json")]
    return path.with_name(name + ".lock")


def _acquire_os_lock(path: Path, timeout: float) -> Any:
    """Open ``path`` and take an exclusive non-blocking OS lock, retrying."""
    descriptor = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
    handle = os.fdopen(descriptor, "rb+")
    deadline = time.monotonic() + max(0.0, float(timeout))
    try:
        while True:
            try:
                if sys.platform == "win32" and msvcrt is not None:
                    handle.seek(0)
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                elif fcntl is not None:
                    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                return handle
            except OSError as error:
                if time.monotonic() >= deadline:
                    raise TimeoutError(f"Timed out waiting for lock: {path}") from error
                time.sleep(LOCK_RETRY_INTERVAL_SEC)
    except BaseException:
        handle.close()
        raise


def _release_os_lock(handle: Any) -> None:
    try:
        if sys.platform == "win32" and msvcrt is not None:
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        elif fcntl is not None:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    except OSError:  # pragma: no cover - closing releases the lock anyway.
        pass
    finally:
        try:
            handle.close()
        except OSError:  # pragma: no cover
            pass


@contextmanager
def file_lock(lock_path: Path, timeout: float = LOCK_TIMEOUT_SEC) -> Iterator[None]:
    """Hold an inter-process lock on ``lock_path`` for the duration of the block.

    The lock is re-entrant inside one process and one thread, so helpers may be
    composed (``update_json`` calling ``write_json``, ``save_secrets`` nested in
    a migration) without deadlocking against themselves.
    """
    path = Path(lock_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    key = os.path.normcase(str(path.absolute()))

    with _LOCK_STATE_GUARD:
        state = _LOCK_STATE.get(key)
        if state is None:
            state = _LockState(path)
            _LOCK_STATE[key] = state

    state.rlock.acquire()  # Blocks while another thread of this process holds it.
    try:
        # Holding the mutex makes ``depth`` stable: only this thread can change it.
        if state.depth == 0:
            state.handle = _acquire_os_lock(state.path, timeout)
        state.depth += 1
        try:
            yield
        finally:
            state.depth -= 1
            if state.depth == 0 and state.handle is not None:
                handle, state.handle = state.handle, None
                _release_os_lock(handle)
    finally:
        state.rlock.release()


def read_json(path: Path) -> dict[str, Any] | None:
    """Read a JSON object, returning ``None`` for missing or malformed files."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def read_json_strict(path: Path) -> dict[str, Any] | None:
    """Read a JSON object, telling "missing" apart from "unreadable".

    Returns ``None`` only when the file does not exist. A file that exists but
    is unreadable, not JSON, or not an object raises :class:`ConfigReadError`
    instead of being silently treated as empty.
    """
    path = Path(path)
    try:
        raw = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return None
    except OSError as error:
        raise ConfigReadError(f"Cannot read {path.name}: {error}") from error

    if not raw.strip():
        raise ConfigReadError(f"{path.name} is empty")
    try:
        data = json.loads(raw)
    except ValueError as error:
        raise ConfigReadError(f"{path.name} is not valid JSON: {error}") from error
    if not isinstance(data, dict):
        raise ConfigReadError(f"{path.name} does not contain a JSON object")
    return data


def _replace_with_retry(tmp: Path, path: Path) -> None:
    delay = REPLACE_BASE_DELAY_SEC
    for attempt in range(1, REPLACE_ATTEMPTS + 1):
        try:
            os.replace(tmp, path)
            return
        except OSError:
            # Windows keeps the target locked while the widget has it open.
            if attempt >= REPLACE_ATTEMPTS:
                raise
            time.sleep(delay)
            delay = min(REPLACE_MAX_DELAY_SEC, delay * 2)


def write_json(path: Path, data: Any) -> None:
    """Atomically replace ``path`` with pretty-printed JSON."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f"{path.name}.{os.getpid()}.{uuid.uuid4().hex[:8]}.tmp")
    try:
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        _replace_with_retry(tmp, path)
    finally:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:  # pragma: no cover - a stale tmp must not mask the real error
            pass


def update_json(
    path: Path,
    updater: Callable[[dict[str, Any]], dict[str, Any]],
) -> dict[str, Any]:
    """Read, transform, and atomically write one JSON document under a lock."""
    path = Path(path)
    with file_lock(lock_path_for(path)):
        current = read_json_strict(path) or {}
        updated = updater(dict(current))
        if not isinstance(updated, dict):
            raise TypeError("update_json updater must return a dict")
        write_json(path, updated)
        return updated


def load_config() -> dict[str, Any]:
    return read_json(config_path()) or {}


def _is_valid_topic(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(NTFY_TOPIC_RE, value.strip()) is not None


def _store_topic(secrets_data: dict[str, Any], topic: str) -> None:
    """Write the topic to ``secrets.json`` using the platform's storage key."""
    secrets_data.pop("ntfy_topic", None)
    if crypto.encryption_available():
        secrets_data["ntfy_topic_enc"] = crypto.protect_secret(topic)
    else:
        secrets_data["ntfy_topic"] = topic


def _read_secrets_and_config() -> tuple[Path, Path]:
    return secrets_path(), config_path()


def ensure_ntfy_topic() -> str:
    """Return the ntfy topic, migrating it into ``secrets.json`` when needed.

    Order: encrypted value in ``secrets.json`` -> legacy plaintext there ->
    legacy ``ntfy_topic`` in ``config.json`` (moved and removed) -> a freshly
    generated private topic. An existing but unreadable file raises
    :class:`ConfigReadError` instead of regenerating the topic, which would
    desynchronise every paired phone.
    """
    secrets_file, config_file = _read_secrets_and_config()

    with file_lock(lock_path_for(secrets_file)):
        secrets_data = read_json_strict(secrets_file) or {}

        stored = secrets_data.get("ntfy_topic_enc")
        if isinstance(stored, str) and stored.strip():
            topic = crypto.unprotect_secret(stored.strip())  # Raises on tampering.
            if _is_valid_topic(topic):
                return topic.strip()

        legacy = secrets_data.get("ntfy_topic")
        if _is_valid_topic(legacy):
            topic = str(legacy).strip()
            _store_topic(secrets_data, topic)
            write_json(secrets_file, secrets_data)
            return topic

        config = read_json_strict(config_file) or {}
        config_topic = config.get("ntfy_topic")
        if _is_valid_topic(config_topic):
            topic = str(config_topic).strip()
            _store_topic(secrets_data, topic)
            write_json(secrets_file, secrets_data)
            config.pop("ntfy_topic", None)
            write_json(config_file, config)
            return topic

        topic = f"agw-{secrets.token_hex(12)}"
        _store_topic(secrets_data, topic)
        write_json(secrets_file, secrets_data)
        return topic
