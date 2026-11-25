"""Tests for the ZCode OAuth flow, token helpers, and the auth CLI."""

from __future__ import annotations

import contextlib
import http.client
import importlib.util
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import oauth, paths, zcode  # noqa: E402

READY_RESPONSE = {"data": {"status": "ready", "token": "jwt"}}


class TempLocalAppDataTestCase(unittest.TestCase):
    """Run every test against a throwaway ``%LOCALAPPDATA%`` directory."""

    def setUp(self) -> None:
        super().setUp()
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.local_app_data = Path(self._temp.name)
        patcher = mock.patch.dict(os.environ, {"LOCALAPPDATA": str(self.local_app_data)})
        patcher.start()
        self.addCleanup(patcher.stop)


class TrustedAuthorizeUrlTests(TempLocalAppDataTestCase):
    def test_accepts_trusted_urls(self):
        cases = (
            ("https://z.ai/oauth?x=1", "https://z.ai/oauth?x=1"),
            ("https://auth.z.ai/cli/authorize?flow=abc", "https://auth.z.ai/cli/authorize?flow=abc"),
            ("https://Z.AI/path", "https://z.ai/path"),
            ("https://z.ai:443/x", "https://z.ai/x"),
            ("https://z.ai", "https://z.ai"),
        )
        for url, expected in cases:
            with self.subTest(url=url):
                trusted = oauth.is_trusted_authorize_url(url)
                self.assertIsNotNone(trusted)
                self.assertTrue(trusted.startswith("https://"))
                self.assertEqual(trusted, expected)
                # The rebuilt URL never carries userinfo.
                self.assertNotIn("@", trusted)

    def test_rejects_untrusted_schemes(self):
        for url in ("http://z.ai/", "file:///etc/passwd", "ms-msdt:/id"):
            with self.subTest(url=url):
                self.assertIsNone(oauth.is_trusted_authorize_url(url))

    def test_rejects_lookalike_hosts(self):
        for url in ("https://z.ai.evil.com/", "https://evilz.ai/", "https://z.ai./"):
            with self.subTest(url=url):
                self.assertIsNone(oauth.is_trusted_authorize_url(url))

    def test_rejects_userinfo_urls(self):
        for url in ("https://user:pass@z.ai/", "https://user@z.ai/"):
            with self.subTest(url=url):
                self.assertIsNone(oauth.is_trusted_authorize_url(url))

    def test_rejects_non_default_ports(self):
        for url in ("https://z.ai:8443/", "https://z.ai:abc/"):
            with self.subTest(url=url):
                self.assertIsNone(oauth.is_trusted_authorize_url(url))

    def test_rejects_blank_and_non_string_values(self):
        for url in ("", "   ", None, 123):
            with self.subTest(url=url):
                self.assertIsNone(oauth.is_trusted_authorize_url(url))

    def test_normalizes_default_port_and_fragment(self):
        self.assertEqual(
            oauth.is_trusted_authorize_url("https://z.ai:443/x?y=1#z"),
            "https://z.ai/x?y=1#z",
        )


class OAuthHeadersTests(TempLocalAppDataTestCase):
    @mock.patch.object(zcode, "load_secrets", return_value={"device_mid": "mid-1"})
    def test_headers_include_version_and_optional_auth(self, _load_secrets):
        headers = oauth._headers()
        self.assertEqual(headers["Accept"], "application/json")
        self.assertEqual(headers["X-ZCode-App-Version"], zcode.ZCODE_APP_VERSION)
        self.assertEqual(headers["X-Device-Mid"], "mid-1")
        self.assertNotIn("Authorization", headers)

        poll_headers = oauth._headers("poll-token")
        self.assertEqual(poll_headers["Authorization"], "Bearer poll-token")


class RunLoginTests(TempLocalAppDataTestCase):
    def _init_response(self, **data_overrides):
        data = {
            "flow_id": "flow-1",
            "authorize_url": "https://z.ai/ok",
            "poll_interval_sec": 3,
        }
        data.update(data_overrides)
        return {"code": 0, "data": data}

    @contextlib.contextmanager
    def _login_mocks(self, init_response=None, poll_side_effect=None, clock=None):
        if init_response is None:
            init_response = self._init_response()
        if poll_side_effect is None:
            poll_side_effect = [READY_RESPONSE]
        init = mock.MagicMock(return_value=init_response)
        poll = mock.MagicMock(side_effect=list(poll_side_effect))
        browser = mock.MagicMock(return_value=True)
        sleep = mock.MagicMock()
        monotonic = (
            mock.MagicMock(return_value=0.0)
            if clock is None
            else mock.MagicMock(side_effect=list(clock))
        )
        save = mock.MagicMock(return_value={"zcode_token": "jwt", "device_mid": "mid-1"})
        invalidate = mock.MagicMock()
        query = mock.MagicMock(return_value={"auth_ok": True})
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(oauth, "_post_json", init))
            stack.enter_context(mock.patch.object(oauth, "_get_json", poll))
            stack.enter_context(mock.patch.object(oauth, "_emit", mock.MagicMock()))
            stack.enter_context(mock.patch.object(oauth.webbrowser, "open", browser))
            stack.enter_context(mock.patch.object(oauth.time, "sleep", sleep))
            stack.enter_context(mock.patch.object(oauth.time, "monotonic", monotonic))
            stack.enter_context(
                mock.patch.object(zcode, "load_secrets", mock.MagicMock(return_value={}))
            )
            stack.enter_context(mock.patch.object(zcode, "save_secrets", save))
            stack.enter_context(mock.patch.object(zcode, "invalidate_cache", invalidate))
            stack.enter_context(mock.patch.object(zcode, "query_zcode_api", query))
            yield {
                "init": init,
                "poll": poll,
                "browser": browser,
                "sleep": sleep,
                "monotonic": monotonic,
                "save": save,
                "invalidate": invalidate,
                "query": query,
            }

    def test_refuses_untrusted_sign_in_url(self):
        init_response = self._init_response(authorize_url="http://evil.example/")
        with self._login_mocks(init_response) as mocks, self.assertRaisesRegex(
            RuntimeError, "Refusing an untrusted sign-in URL"
        ):
            oauth.run_login()
        mocks["browser"].assert_not_called()
        mocks["poll"].assert_not_called()

    def test_saves_token_after_successful_poll(self):
        with self._login_mocks() as mocks:
            oauth.run_login()
        mocks["init"].assert_called_once_with(oauth.INIT_URL, {"provider": "zai"}, mock.ANY)
        mocks["browser"].assert_called_once_with("https://z.ai/ok")
        mocks["save"].assert_called_once_with({"zcode_token": "jwt"})
        mocks["invalidate"].assert_called_once_with(paths.state_dir())
        mocks["query"].assert_called_once_with("jwt", "mid-1")
        mocks["sleep"].assert_called_once()

    def test_survives_transient_poll_errors(self):
        transient = [
            json.JSONDecodeError("truncated body", "", 0),
            OSError("connection reset"),
            http.client.HTTPException("bad status line"),
            ValueError("bad value"),
            READY_RESPONSE,
        ]
        with self._login_mocks(poll_side_effect=transient) as mocks:
            oauth.run_login()
        self.assertEqual(mocks["poll"].call_count, 5)
        mocks["save"].assert_called_once_with({"zcode_token": "jwt"})

    def test_http_400_poll_aborts_login(self):
        error = urllib.error.HTTPError(oauth.POLL_BASE, 400, "Bad Request", {}, None)
        with self._login_mocks(poll_side_effect=[error]) as mocks, self.assertRaisesRegex(
            RuntimeError, "OAuth poll error HTTP 400"
        ):
            oauth.run_login()
        mocks["save"].assert_not_called()

    def test_http_429_poll_is_retried(self):
        limited = urllib.error.HTTPError(oauth.POLL_BASE, 429, "Too Many Requests", {}, None)
        with self._login_mocks(poll_side_effect=[limited, READY_RESPONSE]) as mocks:
            oauth.run_login()
        self.assertEqual(mocks["poll"].call_count, 2)
        mocks["save"].assert_called_once_with({"zcode_token": "jwt"})

    def test_poll_interval_is_clamped(self):
        for requested in (0.1, 100):
            with self.subTest(requested=requested):
                init_response = self._init_response(poll_interval_sec=requested)
                with self._login_mocks(init_response=init_response) as mocks:
                    oauth.run_login()
                args, kwargs = mocks["sleep"].call_args
                self.assertEqual(kwargs, {})
                self.assertGreaterEqual(args[0], 2.0)
                self.assertLessEqual(args[0], 10.0)

    def test_failed_status_raises(self):
        failed = {"data": {"status": "failed"}}
        with self._login_mocks(poll_side_effect=[failed]), self.assertRaisesRegex(
            RuntimeError, "Sign-in was rejected or failed"
        ):
            oauth.run_login()

    def test_ready_without_token_raises(self):
        without_token = {"data": {"status": "ready"}}
        with self._login_mocks(poll_side_effect=[without_token]), self.assertRaisesRegex(
            RuntimeError, "Server did not return data.token"
        ):
            oauth.run_login()

    def test_non_dict_poll_data_keeps_polling(self):
        responses = [{"data": "unexpected"}, {"data": None}, READY_RESPONSE]
        with self._login_mocks(poll_side_effect=responses) as mocks:
            oauth.run_login()
        self.assertEqual(mocks["poll"].call_count, 3)
        mocks["save"].assert_called_once_with({"zcode_token": "jwt"})

    def test_times_out_after_deadline(self):
        # First call sets the deadline at 300, the loop check jumps past it.
        with self._login_mocks(clock=[0.0, 301.0]) as mocks, self.assertRaisesRegex(
            RuntimeError, "Sign-in timed out after 5 minutes."
        ):
            oauth.run_login()
        mocks["poll"].assert_not_called()
        mocks["save"].assert_not_called()


class TokenHelperTests(TempLocalAppDataTestCase):
    def test_save_token_rejects_empty_values(self):
        for token in ("", "   "):
            with self.subTest(token=token), mock.patch.object(zcode, "save_secrets") as save:
                with self.assertRaisesRegex(ValueError, "Token must not be empty"):
                    oauth.save_token(token)
                save.assert_not_called()

    def test_save_token_persists_and_verifies(self):
        api_result = {"auth_ok": True}
        with (
            mock.patch.object(
                zcode, "save_secrets", return_value={"device_mid": "mid-1"}
            ) as save,
            mock.patch.object(zcode, "invalidate_cache") as invalidate,
            mock.patch.object(zcode, "query_zcode_api", return_value=api_result) as query,
        ):
            self.assertIs(oauth.save_token("jwt"), api_result)
        save.assert_called_once_with({"zcode_token": "jwt"})
        invalidate.assert_called_once_with(paths.state_dir())
        query.assert_called_once_with("jwt", "mid-1")

    def test_status_without_token_raises(self):
        with mock.patch.object(zcode, "load_secrets", return_value={"device_mid": "mid-1"}):
            with self.assertRaisesRegex(RuntimeError, "No ZCode token configured"):
                oauth.status()

    def test_status_returns_api_result(self):
        api_result = {"auth_ok": True}
        with (
            mock.patch.object(
                zcode,
                "load_secrets",
                return_value={"zcode_token": " jwt ", "device_mid": "mid-1"},
            ),
            mock.patch.object(zcode, "query_zcode_api", return_value=api_result) as query,
        ):
            self.assertIs(oauth.status(), api_result)
        query.assert_called_once_with("jwt", "mid-1")

    def test_clear_token_reports_bool(self):
        for removed in (True, False):
            with (
                self.subTest(removed=removed),
                mock.patch.object(
                    zcode, "clear_zcode_token", return_value=removed
                ) as clear,
                mock.patch.object(zcode, "invalidate_cache") as invalidate,
            ):
                self.assertIs(oauth.clear_token(), removed)
            clear.assert_called_once_with()
            invalidate.assert_called_once_with(paths.state_dir())


class ZCodeAuthCliTests(TempLocalAppDataTestCase):
    @property
    def secrets_file(self) -> Path:
        return self.local_app_data / "ag-widgets" / "secrets.json"

    def _run_cli(self, *args: str, input_text: str = ""):
        env = dict(os.environ)
        env["LOCALAPPDATA"] = str(self.local_app_data)
        return subprocess.run(
            [sys.executable, "collector/zcode_auth.py", *args],
            input=input_text,
            capture_output=True,
            text=True,
            cwd=str(ROOT),
            env=env,
            timeout=120,
        )

    @staticmethod
    def _load_cli_module():
        spec = importlib.util.spec_from_file_location(
            "agwidgets_zcode_auth_cli", ROOT / "collector" / "zcode_auth.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_set_token_stdin_rejects_empty_input(self):
        for stdin_text in ("", "   \n"):
            with self.subTest(stdin_text=stdin_text):
                result = self._run_cli("--set-token-stdin", input_text=stdin_text)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("token must not be empty", result.stderr)
                self.assertFalse(self.secrets_file.exists())

    def test_set_token_stdin_no_verify_saves_token(self):
        result = self._run_cli(
            "--set-token-stdin", "--no-verify", input_text="jwt-from-stdin\n"
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["ok"])
        self.assertIsInstance(payload["encrypted"], bool)
        self.assertTrue(payload["device_mid"])
        self.assertNotIn("status", payload)
        self.assertTrue(self.secrets_file.exists())

        saved = zcode.load_secrets()
        self.assertEqual(saved["zcode_token"], "jwt-from-stdin")
        self.assertEqual(saved["device_mid"], payload["device_mid"])

    def test_set_token_empty_argument_exits_2(self):
        result = self._run_cli("--set-token", "")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("token must not be empty", result.stderr)
        self.assertFalse(self.secrets_file.exists())

    def test_set_token_argument_warns_and_succeeds(self):
        # ``--no-verify`` does not cover ``--set-token``, so the network
        # verification is mocked instead of hitting the real API.
        cli = self._load_cli_module()
        stderr = io.StringIO()
        with (
            mock.patch.object(sys, "argv", ["zcode_auth.py", "--set-token", "jwt"]),
            mock.patch.object(
                cli.oauth, "save_token", return_value={"auth_ok": True}
            ) as save_token,
            mock.patch.object(cli, "_emit"),
            contextlib.redirect_stderr(stderr),
        ):
            cli.main()  # Returning normally means exit code 0.
        self.assertIn(
            "Warning: passing tokens via CLI arguments exposes them in process lists",
            stderr.getvalue(),
        )
        save_token.assert_called_once_with("jwt")

    def test_clear_token_keeps_device_mid(self):
        seed = self._run_cli(
            "--set-token-stdin", "--no-verify", input_text="jwt\n"
        )
        self.assertEqual(seed.returncode, 0, seed.stderr)
        before = json.loads(self.secrets_file.read_text(encoding="utf-8"))
        self.assertTrue(before["device_mid"])

        result = self._run_cli("--clear-token")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {"ok": True})
        after = json.loads(self.secrets_file.read_text(encoding="utf-8"))
        self.assertNotIn("zcode_token", after)
        self.assertNotIn("zcode_token_enc", after)
        self.assertEqual(after["device_mid"], before["device_mid"])


if __name__ == "__main__":
    unittest.main()
