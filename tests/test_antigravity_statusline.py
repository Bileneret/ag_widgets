"""Tests for Antigravity quota collection and the CLI status line."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
import types
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import antigravity, context_model as cm, paths, statusline  # noqa: E402


USAGE_MAP = {
    "gemini-5h": {
        "remaining_fraction": 0.5,
        "reset_time": "2026-01-01T00:00:00Z",
        "window": "5h",
        "group": "gemini",
    },
    "gemini-weekly": {
        "remaining_fraction": 0.9,
        "reset_time": None,
        "window": None,
        "group": "gemini",
    },
}

USAGE_STDOUT = json.dumps(
    {
        "command": {
            "data": {
                "groups": [
                    {
                        "name": "gemini",
                        "buckets": [
                            {
                                "id": "gemini-5h",
                                "remaining_fraction": 0.5,
                                "reset_time": "2026-01-01T00:00:00Z",
                                "window": "5h",
                            },
                            {"id": "gemini-weekly", "remaining_fraction": 0.9},
                        ],
                    }
                ]
            }
        }
    }
)

STATUS_QUOTA = {
    "gemini-5h": {"remaining_fraction": 0.5, "group": "gemini"},
    "gemini-weekly": {"remaining_fraction": 0.9, "group": "gemini"},
}

FULL_PAYLOAD = {
    "quota": STATUS_QUOTA,
    "plan_tier": "Pro",
    "context_window": {
        "context_window_size": 524288,
        "used_percentage": 20,
        "current_usage": {"input_tokens": 100, "cache_read_input_tokens": 50},
    },
    "model": {"id": "gemini-4-neon", "display_name": "Gemini 4 Neon"},
}


def _proc(stdout: str = USAGE_STDOUT, returncode: int = 0) -> mock.Mock:
    """Build a stand-in for ``subprocess.CompletedProcess``."""
    return mock.Mock(returncode=returncode, stdout=stdout, stderr="")


class TempDirTestCase(unittest.TestCase):
    """Give every test a private state directory and a fake LOCALAPPDATA."""

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.state_dir = self.tmp / "state"
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self._env = mock.patch.dict(
            os.environ, {"LOCALAPPDATA": str(self.tmp), "AGY_PATH": ""}
        )
        self._env.start()
        self.addCleanup(self._env.stop)

    def write_json(self, name: str, data: dict) -> None:
        (self.state_dir / name).write_text(json.dumps(data), encoding="utf-8")

    def read_json(self, name: str) -> dict:
        return json.loads((self.state_dir / name).read_text(encoding="utf-8"))


class FindExecutableTests(TempDirTestCase):
    def test_agy_path_override_wins(self):
        override = self.tmp / "custom" / "agy.exe"
        override.parent.mkdir(parents=True)
        override.write_text("", encoding="utf-8")

        with mock.patch.dict(os.environ, {"AGY_PATH": str(override)}), mock.patch.object(
            antigravity.shutil, "which", return_value="C:/tools/agy.exe"
        ) as which:
            found = antigravity.find_executable()

        self.assertEqual(found, str(override))
        which.assert_not_called()

    def test_home_gemini_beats_localappdata(self):
        home = self.tmp / "home"
        home_exe = home / ".gemini" / "bin" / "agy.exe"
        home_exe.parent.mkdir(parents=True)
        home_exe.write_text("", encoding="utf-8")

        local = self.tmp / "local"
        local_exe = local / "agy" / "bin" / "agy.exe"
        local_exe.parent.mkdir(parents=True)
        local_exe.write_text("", encoding="utf-8")

        with mock.patch.dict(os.environ, {"LOCALAPPDATA": str(local)}), mock.patch(
            "pathlib.Path.home", return_value=home
        ), mock.patch.object(antigravity.shutil, "which", return_value=None):
            found = antigravity.find_executable()

        self.assertEqual(found, str(home_exe))

    def test_shutil_which_result_wins_over_home(self):
        home = self.tmp / "home"
        home_exe = home / ".gemini" / "bin" / "agy.exe"
        home_exe.parent.mkdir(parents=True)
        home_exe.write_text("", encoding="utf-8")
        which_hit = str(self.tmp / "path-bin" / "agy.exe")

        with mock.patch("pathlib.Path.home", return_value=home), mock.patch.object(
            antigravity.shutil, "which", return_value=which_hit
        ):
            found = antigravity.find_executable()

        self.assertEqual(found, which_hit)

    def test_returns_none_when_nothing_found(self):
        home = self.tmp / "empty-home"
        home.mkdir()
        local = self.tmp / "empty-local"
        local.mkdir()

        with mock.patch.dict(os.environ, {"LOCALAPPDATA": str(local)}), mock.patch(
            "pathlib.Path.home", return_value=home
        ), mock.patch.object(antigravity.shutil, "which", return_value=None):
            found = antigravity.find_executable()

        self.assertIsNone(found)


class ParseUsageOutputTests(unittest.TestCase):
    def test_maps_buckets_from_groups(self):
        self.assertEqual(antigravity._parse_usage_output(USAGE_STDOUT), USAGE_MAP)

    def test_empty_or_non_mapping_payloads_return_empty(self):
        payloads = (
            "[]",
            "{}",
            '{"command": []}',
            '{"command": {"data": []}}',
            '{"command": {"data": {"groups": "nope"}}}',
            '"text"',
        )
        for raw in payloads:
            with self.subTest(raw=raw):
                self.assertEqual(antigravity._parse_usage_output(raw), {})

    def test_invalid_json_raises(self):
        with self.assertRaises(json.JSONDecodeError):
            antigravity._parse_usage_output("not json")

    def test_remaining_fraction_is_sanitized(self):
        payload = {
            "command": {
                "data": {
                    "groups": [
                        {
                            "name": "g",
                            "buckets": [
                                {"id": "nan", "remaining_fraction": float("nan")},
                                {"id": "inf", "remaining_fraction": float("inf")},
                                {"id": "flag", "remaining_fraction": True},
                                {"id": "text", "remaining_fraction": "0.5"},
                                {"id": "good", "remaining_fraction": 0.42},
                            ],
                        }
                    ]
                }
            }
        }

        parsed = antigravity._parse_usage_output(json.dumps(payload))

        self.assertIsNone(parsed["nan"]["remaining_fraction"])
        self.assertIsNone(parsed["inf"]["remaining_fraction"])
        self.assertIsNone(parsed["flag"]["remaining_fraction"])
        self.assertIsNone(parsed["text"]["remaining_fraction"])
        self.assertEqual(parsed["good"]["remaining_fraction"], 0.42)

    def test_invalid_group_and_bucket_entries_ignored(self):
        payload = {
            "command": {
                "data": {
                    "groups": [
                        "junk",
                        {
                            "name": "g",
                            "buckets": [
                                "junk",
                                {"id": ""},
                                {"id": None},
                                {"remaining_fraction": 0.3},
                                {"id": "ok", "remaining_fraction": 0.1},
                            ],
                        },
                        {"buckets": "not-a-list"},
                    ]
                }
            }
        }

        parsed = antigravity._parse_usage_output(json.dumps(payload))

        self.assertEqual(list(parsed), ["ok"])
        self.assertEqual(parsed["ok"]["group"], "g")
        self.assertEqual(parsed["ok"]["remaining_fraction"], 0.1)


class RefreshQuotaTests(TempDirTestCase):
    def _last_with_failure(self, **overrides):
        data = {
            "consecutive_failures": 1,
            "last_attempt_ts": 0.0,
            "last_ts": 111.0,
            "last_error": "old",
        }
        data.update(overrides)
        self.write_json("agy_last.json", data)

    def test_fresh_last_ts_skips_refresh(self):
        self.write_json("agy_last.json", {"last_ts": time.time() - 100})

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run") as run_mock:
            self.assertFalse(antigravity.refresh_quota(self.state_dir))

        run_mock.assert_not_called()

    def test_force_ignores_interval(self):
        self.write_json("agy_last.json", {"last_ts": time.time() - 10})
        run_mock = mock.Mock(return_value=_proc())

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir, force=True))

        run_mock.assert_called_once()

    def test_backoff_after_one_failure(self):
        now = time.time()
        self._last_with_failure(last_attempt_ts=now - 60, last_ts=now - 500)

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run") as run_mock:
            self.assertFalse(antigravity.refresh_quota(self.state_dir))
        run_mock.assert_not_called()

        self._last_with_failure(last_attempt_ts=time.time() - 130, last_ts=now - 500)
        run_mock = mock.Mock(return_value=_proc())
        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir))
        run_mock.assert_called_once()

    def test_backoff_after_three_failures(self):
        now = time.time()
        self._last_with_failure(consecutive_failures=3, last_attempt_ts=now - 300)

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run") as run_mock:
            self.assertFalse(antigravity.refresh_quota(self.state_dir))
        run_mock.assert_not_called()

        self._last_with_failure(
            consecutive_failures=3, last_attempt_ts=time.time() - 500
        )
        run_mock = mock.Mock(return_value=_proc())
        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir))
        run_mock.assert_called_once()

    def test_missing_executable_records_failure(self):
        self._last_with_failure()
        run_mock = mock.Mock()

        with mock.patch.object(
            antigravity, "find_executable", return_value=None
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertFalse(antigravity.refresh_quota(self.state_dir))

        run_mock.assert_not_called()
        data = self.read_json("agy_last.json")
        self.assertEqual(data["consecutive_failures"], 2)
        self.assertEqual(data["last_error"], "missing_executable")
        self.assertEqual(data["last_ts"], 111.0)
        self.assertGreater(data["last_attempt_ts"], 111.0)

    def test_backward_clock_jump_does_not_freeze_refresh(self):
        future_ts = time.time() + 3600.0
        self.write_json(
            "agy_last.json",
            {
                "last_ts": future_ts,
                "last_attempt_ts": future_ts,
                "consecutive_failures": 0,
                "last_error": None,
            },
        )
        run_mock = mock.Mock(return_value=_proc())
        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir))
        run_mock.assert_called_once()

    def test_timeout_records_failure(self):
        self._last_with_failure()
        run_mock = mock.Mock(
            side_effect=subprocess.TimeoutExpired(cmd=["agy"], timeout=60)
        )

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertFalse(antigravity.refresh_quota(self.state_dir))

        data = self.read_json("agy_last.json")
        self.assertEqual(data["consecutive_failures"], 2)
        self.assertEqual(data["last_error"], "timeout")
        self.assertEqual(data["last_ts"], 111.0)

    def test_cli_failure_reasons_recorded(self):
        cases = {
            "exit_code": lambda: _proc(stdout="ignored", returncode=1),
            "empty_output": lambda: _proc(stdout="  \n"),
            "parse_failed": lambda: _proc(stdout="not json"),
            "empty_quota": lambda: _proc(
                stdout='{"command": {"data": {"groups": []}}}'
            ),
        }

        for reason, make_proc in cases.items():
            with self.subTest(reason=reason):
                self._last_with_failure()
                with mock.patch.object(
                    antigravity, "find_executable", return_value="agy"
                ), mock.patch.object(
                    antigravity.subprocess, "run", return_value=make_proc()
                ):
                    self.assertFalse(antigravity.refresh_quota(self.state_dir))

                data = self.read_json("agy_last.json")
                self.assertEqual(data["consecutive_failures"], 2)
                self.assertEqual(data["last_error"], reason)
                self.assertEqual(data["last_ts"], 111.0)

    def test_success_updates_quota_and_resets_bookkeeping(self):
        self._last_with_failure(consecutive_failures=2, last_ts=100.0)
        run_mock = mock.Mock(return_value=_proc())

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir))

        status = self.read_json("agy_status.json")
        self.assertEqual(status["quota"], USAGE_MAP)
        self.assertIn("plan_tier", status)
        self.assertIsNone(status["plan_tier"])
        self.assertIn("saved_at", status)

        last = self.read_json("agy_last.json")
        self.assertEqual(last["consecutive_failures"], 0)
        self.assertIsNone(last["last_error"])
        self.assertEqual(last["last_ts"], last["last_attempt_ts"])
        self.assertGreater(last["last_ts"], 0.0)

    def test_success_preserves_existing_status_fields(self):
        previous_context = {"context_window_size": 1048576}
        self.write_json(
            "agy_status.json",
            {
                "plan_tier": "Ultra",
                "quota": {"old": {"remaining_fraction": 0.1}},
                "saved_at": "old",
                "context_window": previous_context,
                "model": "gemini-4-carbon",
            },
        )
        run_mock = mock.Mock(return_value=_proc())

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir, force=True))

        status = self.read_json("agy_status.json")
        self.assertEqual(status["plan_tier"], "Ultra")
        self.assertEqual(status["context_window"], previous_context)
        self.assertEqual(status["model"], "gemini-4-carbon")
        self.assertEqual(status["quota"], USAGE_MAP)
        self.assertNotEqual(status["saved_at"], "old")

    def test_run_receives_creationflags_and_works_without_support(self):
        run_mock = mock.Mock(return_value=_proc())

        with mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ), mock.patch.object(antigravity.subprocess, "run", run_mock):
            self.assertTrue(antigravity.refresh_quota(self.state_dir, force=True))

        kwargs = run_mock.call_args.kwargs
        self.assertIn("creationflags", kwargs)
        self.assertEqual(
            kwargs["creationflags"], getattr(subprocess, "CREATE_NO_WINDOW", 0)
        )
        self.assertEqual(run_mock.call_args.args[0][0], "agy")

        # Simulate a platform whose ``subprocess`` has no CREATE_NO_WINDOW.
        fake_run = mock.Mock(return_value=_proc())
        fake_subprocess = types.SimpleNamespace(run=fake_run)
        with mock.patch.object(antigravity, "subprocess", fake_subprocess), mock.patch.object(
            antigravity, "find_executable", return_value="agy"
        ):
            self.assertTrue(antigravity.refresh_quota(self.state_dir, force=True))

        self.assertEqual(fake_run.call_args.kwargs["creationflags"], 0)


class BucketKindTests(unittest.TestCase):
    def test_classify_bucket_kind(self):
        cases = {
            "gemini-5h": "5h",
            "five-hour": "5h",
            "24hour": "other",
            "1hour": "other",
            "3p-weekly": "weekly",
            "other": "other",
        }
        for bucket_id, expected in cases.items():
            with self.subTest(bucket_id=bucket_id):
                self.assertEqual(antigravity.classify_bucket_kind(bucket_id), expected)


class PickBucketTests(unittest.TestCase):
    def test_prefers_window_and_group_match(self):
        quota = {
            "plain-weekly": {"group": "other", "remaining_fraction": 0.1},
            "gemini-5h": {"group": "gemini", "remaining_fraction": 0.2},
            "gemini-weekly": {"group": "gemini", "remaining_fraction": 0.9},
        }

        self.assertIs(
            antigravity.pick_bucket(quota, "week", "gemini"), quota["gemini-weekly"]
        )

    def test_matches_group_from_bucket_info(self):
        quota = {"bucket-a": {"group": "gemini-weekly", "remaining_fraction": 0.7}}

        self.assertIs(
            antigravity.pick_bucket(quota, "week", "gemini"), quota["bucket-a"]
        )

    def test_falls_back_to_window_only(self):
        quota = {
            "a": {"group": "other", "remaining_fraction": 0.1},
            "b-weekly": {"group": "other", "remaining_fraction": 0.4},
        }

        self.assertIs(
            antigravity.pick_bucket(quota, "week", "gemini"), quota["b-weekly"]
        )

    def test_ignores_non_dict_entries_and_empty_quota(self):
        quota = {"junk": "text", "gemini-weekly": {"group": "gemini"}}

        self.assertIs(
            antigravity.pick_bucket(quota, "week", "gemini"), quota["gemini-weekly"]
        )
        self.assertIsNone(antigravity.pick_bucket({"a": None}, "week", "gemini"))
        self.assertIsNone(antigravity.pick_bucket({}, "week", "gemini"))


class ExtractQuotaTests(TempDirTestCase):
    def test_missing_status_file(self):
        self.assertEqual(
            antigravity.extract_quota(self.state_dir),
            {"plan_tier": None, "buckets": [], "saved_at": None, "error": None},
        )

    def test_quota_map_normalized(self):
        self.write_json(
            "agy_status.json",
            {
                "plan_tier": "Pro",
                "saved_at": "2026-01-01T00:00:00+00:00",
                "quota": {
                    "gemini-5h": {
                        "remaining_fraction": 0.5,
                        "reset_time": "2026-01-01T01:00:00Z",
                    },
                    "weird": {"remaining_fraction": True},
                    "nan": {"remaining_fraction": float("nan")},
                    "sparse": {},
                },
            },
        )

        result = antigravity.extract_quota(self.state_dir)
        buckets = {bucket["id"]: bucket for bucket in result["buckets"]}

        self.assertEqual(result["plan_tier"], "Pro")
        self.assertEqual(result["saved_at"], "2026-01-01T00:00:00+00:00")
        self.assertIsNone(result["error"])
        self.assertEqual(
            set(buckets), {"gemini-5h", "weird", "nan", "sparse"}
        )
        self.assertEqual(buckets["gemini-5h"]["kind"], "5h")
        self.assertEqual(buckets["gemini-5h"]["remaining_fraction"], 0.5)
        self.assertEqual(
            buckets["gemini-5h"]["reset_time"], "2026-01-01T01:00:00Z"
        )
        self.assertIsNone(buckets["weird"]["remaining_fraction"])
        self.assertIsNone(buckets["nan"]["remaining_fraction"])
        self.assertIsNone(buckets["sparse"]["remaining_fraction"])
        self.assertEqual(buckets["weird"]["kind"], "other")

    def test_legacy_bucket_list(self):
        self.write_json(
            "agy_status.json",
            {
                "buckets": [
                    {
                        "id": "legacy-5h",
                        "remaining_fraction": 0.25,
                        "reset_time": "2026-01-01T00:00:00Z",
                    },
                    "junk",
                    {"id": "five-hour"},
                ]
            },
        )

        result = antigravity.extract_quota(self.state_dir)

        self.assertEqual(
            result["buckets"],
            [
                {
                    "id": "legacy-5h",
                    "kind": "5h",
                    "remaining_fraction": 0.25,
                    "reset_time": "2026-01-01T00:00:00Z",
                },
                {
                    "id": "five-hour",
                    "kind": "5h",
                    "remaining_fraction": None,
                    "reset_time": None,
                },
            ],
        )

    def test_error_reflects_failures(self):
        self.write_json(
            "agy_last.json", {"consecutive_failures": 2, "last_error": "timeout"}
        )
        self.write_json(
            "agy_status.json",
            {"quota": {}, "saved_at": "2026-01-01T00:00:00+00:00"},
        )

        self.assertEqual(antigravity.extract_quota(self.state_dir)["error"], "timeout")

        self.write_json(
            "agy_last.json", {"consecutive_failures": 0, "last_error": "timeout"}
        )

        self.assertIsNone(antigravity.extract_quota(self.state_dir)["error"])


class StatuslineRunTests(TempDirTestCase):
    def test_full_payload_line(self):
        line = statusline.run(json.dumps(FULL_PAYLOAD))

        self.assertEqual(line, "AG 5h 50% · wk 90% · ctx 20% · 150/512K")

    def test_second_payload_keeps_previous_quota(self):
        first = statusline.run(json.dumps(FULL_PAYLOAD))
        self.assertIn("5h 50%", first)

        second = statusline.run(
            json.dumps(
                {
                    "context_window": {
                        "context_window_size": 524288,
                        "used_percentage": 10,
                    }
                }
            )
        )

        self.assertEqual(second, "AG 5h 50% · wk 90% · ctx 10% · ?/512K")

    def test_zero_tokens_render_as_zero(self):
        payload = {
            "quota": STATUS_QUOTA,
            "context_window": {
                "context_window_size": 524288,
                "current_usage": {
                    "input_tokens": 0,
                    "cache_read_input_tokens": 0,
                },
            },
        }

        line = statusline.run(json.dumps(payload))

        self.assertEqual(line, "AG 5h 50% · wk 90% · 0/512K")

    def test_invalid_token_values_are_ignored(self):
        payload = {
            "quota": STATUS_QUOTA,
            "context_window": {
                "context_window_size": 524288,
                "current_usage": {
                    "input_tokens": "100",
                    "cache_read_input_tokens": True,
                },
            },
        }

        line = statusline.run(json.dumps(payload))

        self.assertIn("0/512K", line)
        self.assertNotIn("?/512K", line)

    def test_missing_current_usage_renders_question_mark(self):
        payload = {
            "quota": STATUS_QUOTA,
            "context_window": {"context_window_size": 524288},
        }

        self.assertIn("?/512K", statusline.run(json.dumps(payload)))

    def test_learned_window_failure_does_not_break_run(self):
        with mock.patch.object(
            cm, "save_learned_window", side_effect=OSError("disk full")
        ):
            line = statusline.run(json.dumps(FULL_PAYLOAD))

        self.assertTrue(line.startswith("AG "))
        self.assertIn("5h 50%", line)
        self.assertIn("150/512K", line)

    def test_status_write_failure_does_not_break_run(self):
        with mock.patch.object(paths, "update_json", side_effect=OSError("disk full")):
            line = statusline.run(json.dumps(FULL_PAYLOAD))

        self.assertTrue(line.startswith("AG "))
        self.assertIn("5h 50%", line)

    def test_empty_payloads_return_empty_string(self):
        for raw in ("", "   ", "[]", "null", "not json"):
            with self.subTest(raw=raw):
                self.assertEqual(statusline.run(raw), "")


class PercentTextTests(unittest.TestCase):
    def test_invalid_percent_values_render_dashes(self):
        for value in (True, False, float("nan"), float("inf"), None, "0.5", [], {}):
            with self.subTest(value=value):
                self.assertEqual(
                    statusline._percent_text({"remaining_fraction": value}), "--"
                )
        self.assertEqual(statusline._percent_text(None), "--")
        self.assertEqual(statusline._percent_text({}), "--")

    def test_fraction_and_amount_fallback(self):
        self.assertEqual(statusline._percent_text({"remaining_fraction": 0.5}), "50%")
        self.assertEqual(
            statusline._percent_text({"remaining_fraction": 1.5}), "100%"
        )
        self.assertEqual(
            statusline._percent_text({"remaining_fraction": -0.2}), "0%"
        )
        self.assertEqual(
            statusline._percent_text({"remaining_amount": 12345}), "12K"
        )
        self.assertEqual(statusline._percent_text({"remaining_amount": 500}), "500")


if __name__ == "__main__":
    unittest.main()
