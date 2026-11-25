"""Tests for status aggregation, change detection, and active-chat selection."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import antigravity, context, status  # noqa: E402


class ChangeDetectionTests(unittest.TestCase):
    def base_status(self):
        return {
            "antigravity": {
                "buckets": [
                    {"id": "gemini-5h", "remaining_fraction": 0.5, "reset_time": "2026-01-01T00:00:00Z"}
                ]
            },
            "zcode": {
                "coupon_visible": False,
                "network_error": False,
                "auth_ok": True,
                "remaining_units": 100,
                "total_units": 1000,
            },
            "context_summary": {"level": "green", "pct": 10.0, "model": "gemini-4-neon-high"},
        }

    def test_context_percent_change_triggers_publish(self):
        previous = self.base_status()
        current = self.base_status()
        current["context_summary"]["pct"] = 12.5
        self.assertTrue(status.has_meaningful_change(previous, current))

    def test_small_context_change_is_throttled(self):
        previous = self.base_status()
        current = self.base_status()
        current["context_summary"]["pct"] = 11.0
        self.assertFalse(status.has_meaningful_change(previous, current))

    def test_active_conversation_change_triggers_publish(self):
        previous = self.base_status()
        current = self.base_status()
        previous["context_summary"]["conversation_id"] = "a"
        current["context_summary"]["conversation_id"] = "b"
        self.assertTrue(status.has_meaningful_change(previous, current))

    def test_network_error_change_triggers_publish(self):
        previous = self.base_status()
        current = self.base_status()
        current["zcode"]["network_error"] = True
        self.assertTrue(status.has_meaningful_change(previous, current))

    def test_quota_change_triggers_publish(self):
        previous = self.base_status()
        current = self.base_status()
        current["antigravity"]["buckets"][0]["remaining_fraction"] = 0.55
        self.assertTrue(status.has_meaningful_change(previous, current))

    def test_non_dict_previous_payload_is_safe(self):
        self.assertTrue(status.has_meaningful_change(None, self.base_status()))


class ActiveContextTests(unittest.TestCase):
    def _state(self):
        tmp = tempfile.TemporaryDirectory()
        state = Path(tmp.name)
        (state / "context.json").write_text(
            json.dumps(
                {
                    "last": "a",
                    "conversations": {
                        "a": {
                            "model": "gemini-4-neon-high",
                            "tokens": 100,
                            "window": 524288,
                            "level": "green",
                            "updated_at": "2026-01-01T00:00:00+00:00",
                        },
                        "b": {
                            "model": "gemini-4-carbon-high",
                            "tokens": 200,
                            "window": 1048576,
                            "level": "green",
                            "updated_at": "2026-01-01T01:00:00+00:00",
                        },
                    },
                }
            ),
            encoding="utf-8",
        )
        return tmp, state

    def test_open_chat_is_selected(self):
        tmp, state = self._state()
        with tmp, mock.patch.object(
            context, "detect_active_chat", return_value={"id": "b", "title": "Demo"}
        ):
            summary = context.extract_context_summary(state)
        self.assertEqual(summary["conversation_id"], "b")
        self.assertTrue(summary["active"])
        self.assertEqual(summary["model"], "gemini-4-carbon-high")
        self.assertEqual(summary["tokens"], 200)

    def test_open_chat_without_response_waits(self):
        tmp, state = self._state()
        with tmp, mock.patch.object(
            context, "detect_active_chat", return_value={"id": "new", "title": "New"}
        ):
            summary = context.extract_context_summary(state)
        self.assertEqual(summary["conversation_id"], "new")
        self.assertTrue(summary["active"])
        self.assertIsNone(summary["tokens"])
        self.assertIsNone(summary["model"])

    def test_no_active_detection_falls_back_to_last(self):
        tmp, state = self._state()
        with tmp, mock.patch.object(context, "detect_active_chat", return_value=None):
            summary = context.extract_context_summary(state)
        self.assertEqual(summary["conversation_id"], "a")
        self.assertFalse(summary["active"])


class BucketTests(unittest.TestCase):
    def test_classify_bucket_kind(self):
        self.assertEqual(antigravity.classify_bucket_kind("gemini-5h"), "5h")
        self.assertEqual(antigravity.classify_bucket_kind("3p-weekly"), "weekly")
        self.assertEqual(antigravity.classify_bucket_kind("other-limit"), "other")


if __name__ == "__main__":
    unittest.main()
