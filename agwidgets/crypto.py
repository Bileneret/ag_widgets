"""Windows DPAPI encryption for small secrets (ZCode token, ntfy topic).

The ZCode token is shared by the Python collector and the Electron shell. Electron
``safeStorage`` is not compatible with DPAPI blobs, so Python is the single
crypto provider: the desktop app writes the token through
``collector/zcode_auth.py --set-token-stdin`` instead of touching ``secrets.json``.

Only the standard library is used. On non-Windows platforms (Linux/macOS CI)
encryption is unavailable: ``protect_secret`` returns the plaintext so the rest
of the pipeline keeps working, and ``unprotect_secret`` refuses DPAPI blobs
because they cannot be decrypted there.

Storage format: ``dpapi:v1:<base64 of the CryptProtectData blob>``. Values
without the prefix are treated as legacy plaintext and passed through, which
makes migration transparent for existing installations.
"""

from __future__ import annotations

import base64
import binascii
import ctypes
import sys
from ctypes import wintypes

DPAPI_PREFIX = "dpapi:v1:"
DEFAULT_ENTROPY = b"ag-widgets:secret:v1"
CRYPTPROTECT_UI_FORBIDDEN = 0x01


class CryptoError(RuntimeError):
    """Base class for secret storage failures."""


class CryptoUnavailableError(CryptoError):
    """Raised when encryption is required but the platform cannot provide it."""


class SecretDecryptError(CryptoError):
    """Raised when a stored secret looks encrypted but cannot be decrypted."""


class _DataBlob(ctypes.Structure):
    _fields_ = [
        ("cbData", wintypes.DWORD),
        ("pbData", ctypes.POINTER(ctypes.c_byte)),
    ]


_API: tuple[ctypes.CDLL, ctypes.CDLL] | None = None


def encryption_available() -> bool:
    """Return ``True`` when DPAPI encryption can be used on this platform."""
    return sys.platform == "win32"


def is_encrypted(value: object) -> bool:
    """Return ``True`` when ``value`` carries the DPAPI envelope prefix."""
    return isinstance(value, str) and value.startswith(DPAPI_PREFIX)


def _load_api() -> tuple[ctypes.CDLL, ctypes.CDLL]:
    """Bind ``crypt32``/``kernel32`` with explicit signatures (64-bit safe)."""
    global _API
    if _API is not None:
        return _API

    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32

    crypt32.CryptProtectData.argtypes = [
        ctypes.POINTER(_DataBlob),
        wintypes.LPCWSTR,
        ctypes.POINTER(_DataBlob),
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(_DataBlob),
    ]
    crypt32.CryptProtectData.restype = wintypes.BOOL
    crypt32.CryptUnprotectData.argtypes = [
        ctypes.POINTER(_DataBlob),
        ctypes.POINTER(wintypes.LPWSTR),
        ctypes.POINTER(_DataBlob),
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(_DataBlob),
    ]
    crypt32.CryptUnprotectData.restype = wintypes.BOOL
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    kernel32.LocalFree.restype = ctypes.c_void_p

    _API = (crypt32, kernel32)
    return _API


def _make_blob(data: bytes) -> tuple[_DataBlob, ctypes.Array[ctypes.c_char]]:
    """Build a ``DATA_BLOB`` plus the buffer that keeps its memory alive."""
    buffer = ctypes.create_string_buffer(data, len(data))
    blob = _DataBlob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_byte)))
    return blob, buffer


def _free(blob: _DataBlob) -> None:
    if blob.pbData:
        _load_api()[1].LocalFree(ctypes.cast(blob.pbData, ctypes.c_void_p))


def protect_secret(
    plaintext: str,
    *,
    entropy: bytes = DEFAULT_ENTROPY,
    require_encryption: bool = False,
) -> str:
    """Encrypt ``plaintext`` with DPAPI and return ``dpapi:v1:<base64>``.

    ``entropy`` is passed as ``pOptionalEntropy`` so a blob copied from another
    application's store cannot be decrypted here. On non-Windows platforms the
    plaintext is returned unchanged unless ``require_encryption`` is set.
    """
    text = plaintext if isinstance(plaintext, str) else str(plaintext)
    if not encryption_available():
        if require_encryption:
            raise CryptoUnavailableError("DPAPI encryption is only available on Windows")
        return text

    crypt32, _ = _load_api()
    in_blob, in_buffer = _make_blob(text.encode("utf-8"))
    entropy_blob, entropy_buffer = _make_blob(entropy or b"")
    out_blob = _DataBlob()

    ok = crypt32.CryptProtectData(
        ctypes.byref(in_blob),
        None,
        ctypes.byref(entropy_blob) if entropy else None,
        None,
        None,
        CRYPTPROTECT_UI_FORBIDDEN,
        ctypes.byref(out_blob),
    )
    # ``in_buffer``/``entropy_buffer`` stay referenced until here on purpose:
    # DATA_BLOB only holds raw pointers into them.
    _ = (in_buffer, entropy_buffer)
    if not ok:
        raise CryptoError(f"CryptProtectData failed (error {ctypes.GetLastError()})")

    try:
        cipher = ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        _free(out_blob)
    return DPAPI_PREFIX + base64.b64encode(cipher).decode("ascii")


def unprotect_secret(stored: str, *, entropy: bytes = DEFAULT_ENTROPY) -> str:
    """Decrypt a ``dpapi:v1:`` value; legacy plaintext is returned as-is."""
    text = stored if isinstance(stored, str) else ""
    if not is_encrypted(text):
        return text
    if not encryption_available():
        raise SecretDecryptError("DPAPI secrets can only be decrypted on Windows")

    encoded = text[len(DPAPI_PREFIX) :]
    try:
        cipher = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as error:
        raise SecretDecryptError("Stored secret is not valid base64") from error
    if not cipher:
        raise SecretDecryptError("Stored secret is empty")

    crypt32, _ = _load_api()
    in_blob, in_buffer = _make_blob(cipher)
    entropy_blob, entropy_buffer = _make_blob(entropy or b"")
    out_blob = _DataBlob()

    ok = crypt32.CryptUnprotectData(
        ctypes.byref(in_blob),
        None,
        ctypes.byref(entropy_blob) if entropy else None,
        None,
        None,
        CRYPTPROTECT_UI_FORBIDDEN,
        ctypes.byref(out_blob),
    )
    _ = (in_buffer, entropy_buffer)
    if not ok:
        raise SecretDecryptError(f"CryptUnprotectData failed (error {ctypes.GetLastError()})")

    try:
        plain = ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        _free(out_blob)
    try:
        return plain.decode("utf-8")
    except UnicodeDecodeError as error:
        raise SecretDecryptError("Decrypted secret is not valid UTF-8") from error
