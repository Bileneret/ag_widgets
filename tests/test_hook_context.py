"""Tests for the Antigravity hook and active-chat context extraction."""

from __future__ import annotations

import io
import json
import os
import sys
import tempfile
import time
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import context, hook  # noqa: E402

VIEW_SECONDS = 1_764_000_000
VIEW_ISO = "2025-11-24T16:00:00Z"


class HookTestCase(unittest.TestCase):
    """Shared isolated environment: state lives under a temporary LOCALAPPDATA."""

    def setUp(self):
        self._tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmpdir.cleanup)
        self.tmp = Path(self._tmpdir.name)
        env_patch = mock.patch.dict(os.environ, {"LOCALAPPDATA": str(self.tmp)})
        env_patch.start()
        self.addCleanup(env_patch.stop)

    @property
    def state_dir(self) -> Path:
        return self.tmp / "ag-widgets" / "state"

    def write_transcript(self, entries, name: str = "transcript.jsonl") -> Path:
        path = self.tmp / name
        with path.open("w", encoding="utf-8") as handle:
            for entry in entries:
                handle.write(json.dumps(entry) + "\n")
        return path

    def write_context(self, conversations, last) -> Path:
        self.state_dir.mkdir(parents=True, exist_ok=True)
        path = self.state_dir / "context.json"
        path.write_text(
            json.dumps({"last": last, "conversations": conversations}),
            encoding="utf-8",
        )
        return path

    def read_context(self):
        return json.loads((self.state_dir / "context.json").read_text(encoding="utf-8"))

    def run_hook(self, conversation_id="conv-1", transcript=None, **extra):
        payload = {
            "conversationId": conversation_id,
            "transcriptPath": str(transcript),
            "workspacePaths": [str(self.tmp / "repo")],
            "lastUserInput": "hello",
        }
        payload.update(extra)
        hook.run(json.dumps(payload))


class DetectActiveChatTests(HookTestCase):
    def annotations_dir(self) -> Path:
        directory = self.tmp / "annotations"
        directory.mkdir()
        return directory

    def test_detect_prefers_view_time_over_newer_mtime(self):
        directory = self.annotations_dir()
        newer_mtime = directory / "newer_mtime.pbtxt"
        recent_view = directory / "recent_view.pbtxt"
        newer_mtime.write_text(
            'title: "Older view"\nlast_user_view_time: { seconds: 1763000000 nanos: 0 }\n',
            encoding="utf-8",
        )
        recent_view.write_text(
            'title: "Recent view"\nlast_user_view_time: { seconds: 1764000000 nanos: 0 }\n',
            encoding="utf-8",
        )
        now = time.time()
        os.utime(newer_mtime, (now, now))
        os.utime(recent_view, (now - 60.0, now - 60.0))

        chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["id"], "recent_view")
        self.assertEqual(chat["title"], "Recent view")

    def test_detect_falls_back_to_newest_mtime(self):
        directory = self.annotations_dir()
        older = directory / "older.pbtxt"
        newer = directory / "newer.pbtxt"
        older.write_text('title: "Older chat"\n', encoding="utf-8")
        newer.write_text('title: "Newer chat"\n', encoding="utf-8")
        now = time.time()
        os.utime(older, (now - 120.0, now - 120.0))
        os.utime(newer, (now - 60.0, now - 60.0))

        chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["id"], "newer")
        self.assertEqual(chat["title"], "Newer chat")

    def test_detect_missing_title_is_empty(self):
        directory = self.annotations_dir()
        (directory / "no_title.pbtxt").write_text(
            "last_user_view_time: { seconds: 1764000000 nanos: 0 }\n",
            encoding="utf-8",
        )

        chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["title"], "")

    def test_detect_reads_title(self):
        directory = self.annotations_dir()
        (directory / "titled.pbtxt").write_text('title: "Demo"\n', encoding="utf-8")

        chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["title"], "Demo")

    def test_detect_viewed_at_is_iso_z(self):
        directory = self.annotations_dir()
        (directory / "viewed.pbtxt").write_text(
            "last_user_view_time: { seconds: 1764000000 nanos: 0 }\n",
            encoding="utf-8",
        )

        chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["viewed_at"], VIEW_ISO)

    def test_detect_missing_view_time_has_no_viewed_at(self):
        directory = self.annotations_dir()
        (directory / "unseen.pbtxt").write_text('title: "No view"\n', encoding="utf-8")

        chat = context.detect_active_chat(directory=directory)

        self.assertIsNone(chat["viewed_at"])

    def test_detect_skips_file_that_disappears(self):
        directory = self.annotations_dir()
        gone = directory / "gone.pbtxt"
        alive = directory / "alive.pbtxt"
        gone.write_text(
            "last_user_view_time: { seconds: 1764000000 nanos: 0 }\n",
            encoding="utf-8",
        )
        alive.write_text(
            "last_user_view_time: { seconds: 1763000000 nanos: 0 }\n",
            encoding="utf-8",
        )

        original_read_text = Path.read_text

        def fake_read_text(self, *args, **kwargs):
            if self.name == "gone.pbtxt":
                raise FileNotFoundError(str(self))
            return original_read_text(self, *args, **kwargs)

        with mock.patch.object(Path, "read_text", fake_read_text):
            chat = context.detect_active_chat(directory=directory)

        self.assertEqual(chat["id"], "alive")

    def test_detect_empty_directory_returns_none(self):
        directory = self.annotations_dir()

        self.assertIsNone(context.detect_active_chat(directory=directory))

    def test_detect_missing_directory_returns_none(self):
        self.assertIsNone(
            context.detect_active_chat(directory=self.tmp / "missing-annotations")
        )


class ExtractContextSummaryTests(HookTestCase):
    def summary(self, active=None, config=None):
        with mock.patch.object(context, "detect_active_chat", return_value=active):
            return context.extract_context_summary(self.state_dir, config)

    def test_summary_without_context_uses_active_chat(self):
        summary = self.summary({"id": "open-chat", "title": "Open", "viewed_at": None})

        self.assertEqual(summary["conversation_id"], "open-chat")
        self.assertTrue(summary["active"])
        self.assertIsNone(summary["tokens"])
        self.assertIsNone(summary["window"])
        self.assertIsNone(summary["level"])
        self.assertIsNone(summary["updated_at"])

    def test_summary_without_context_and_no_active_chat_is_none(self):
        self.assertIsNone(self.summary(None))

    def test_summary_recomputes_window_from_config(self):
        self.write_context(
            {
                "conv-1": {
                    "model": "gemini-4-neon-high",
                    "tokens": 100_000,
                    "window": 524_288,
                }
            },
            "conv-1",
        )

        summary = self.summary(None, {"model_windows": {"gemini-4-neon": 262_144}})

        self.assertEqual(summary["window"], 262_144)
        self.assertEqual(summary["window_source"], "config")

    def test_summary_prefers_learned_window_over_table(self):
        self.state_dir.mkdir(parents=True, exist_ok=True)
        (self.state_dir / "model_windows_learned.json").write_text(
            json.dumps({"windows": {"gemini-4-neon-high": {"size": 600_000}}}),
            encoding="utf-8",
        )
        self.write_context(
            {
                "conv-1": {
                    "model": "gemini-4-neon-high",
                    "tokens": 100_000,
                    "window": 524_288,
                }
            },
            "conv-1",
        )

        summary = self.summary(None, {})

        self.assertEqual(summary["window"], 600_000)
        self.assertEqual(summary["window_source"], "cli")

    def test_summary_ignores_string_tokens(self):
        self.write_context(
            {
                "conv-1": {
                    "model": "gemini-4-neon-high",
                    "tokens": "12345",
                    "pct": "77.5",
                }
            },
            "conv-1",
        )

        summary = self.summary(None)

        self.assertIsNone(summary["tokens"])
        self.assertIsNone(summary["pct"])
        self.assertEqual(summary["level"], "unknown")

    def test_summary_ignores_bool_tokens(self):
        self.write_context(
            {
                "conv-1": {
                    "model": "gemini-4-neon-high",
                    "tokens": True,
                    "pct": True,
                }
            },
            "conv-1",
        )

        summary = self.summary(None)

        self.assertIsNone(summary["tokens"])
        self.assertIsNone(summary["pct"])
        self.assertEqual(summary["level"], "unknown")

    def level_for(self, tokens: int) -> str:
        self.write_context(
            {"conv-1": {"model": "gemini-4-neon-high", "tokens": tokens}},
            "conv-1",
        )
        return self.summary(None)["level"]

    def test_summary_level_ok(self):
        self.assertEqual(self.level_for(100_000), "ok")

    def test_summary_level_warn(self):
        self.assertEqual(self.level_for(210_000), "warn")

    def test_summary_level_risk(self):
        self.assertEqual(self.level_for(341_000), "risk")


class ReadLastUsageEntryTests(HookTestCase):
    def test_read_small_transcript_returns_last_usage(self):
        transcript = self.write_transcript(
            [
                {"input_tokens": 111, "cache_read_tokens": 1},
                {"type": "assistant", "text": "no usage here"},
                {"input_tokens": 222, "cache_read_tokens": 5},
                {"type": "result", "text": "trailing entry without usage"},
            ]
        )

        entry = hook.read_last_usage_entry(transcript)

        self.assertEqual(entry["input_tokens"], 222)
        self.assertEqual(entry["cache_read_tokens"], 5)

    def test_read_usage_falls_back_past_tail_window(self):
        transcript = self.tmp / "large.jsonl"
        record = json.dumps({"input_tokens": 424_242, "cache_read_tokens": 1}) + "\n"
        junk = ("x" * 1023 + "\n") * 1200
        transcript.write_text(record + junk, encoding="utf-8")

        self.assertGreater(transcript.stat().st_size, hook.TAIL_BYTES)
        self.assertGreater(transcript.stat().st_size, 1_100_000)

        entry = hook.read_last_usage_entry(transcript)

        self.assertEqual(entry["input_tokens"], 424_242)

    def test_read_missing_transcript_returns_none(self):
        self.assertIsNone(hook.read_last_usage_entry(self.tmp / "missing.jsonl"))

    def test_read_directory_returns_none(self):
        self.assertIsNone(hook.read_last_usage_entry(self.tmp))

    def test_read_skips_broken_json_lines(self):
        transcript = self.tmp / "broken.jsonl"
        transcript.write_text(
            '{"input_tokens": 7}\n{not-json\n[1, 2, 3]\nnull\n',
            encoding="utf-8",
        )

        entry = hook.read_last_usage_entry(transcript)

        self.assertEqual(entry["input_tokens"], 7)


class HookRunTests(HookTestCase):
    def test_run_persists_usage_entry(self):
        transcript = self.write_transcript(
            [
                {
                    "input_tokens": 1000,
                    "cache_read_tokens": 500,
                    "model": "gemini-4-neon-high",
                }
            ]
        )

        self.run_hook(conversation_id="conv-42", transcript=transcript)

        data = self.read_context()
        self.assertEqual(data["last"], "conv-42")
        entry = data["conversations"]["conv-42"]
        self.assertEqual(entry["tokens"], 1500)
        self.assertEqual(entry["window"], 524_288)
        self.assertEqual(entry["window_source"], "table")
        self.assertTrue(entry["updated_at"])
        self.assertEqual(entry["workspace"], "repo")
        self.assertEqual(entry["snippet"], "")

    def test_run_tolerates_non_numeric_tokens(self):
        cases = [
            ({"input_tokens": True, "cache_read_tokens": 5}, 5),
            ({"input_tokens": "77", "cache_read_tokens": "3"}, 80),
            ({"input_tokens": float("nan"), "cache_read_tokens": float("inf")}, 0),
        ]
        for index, (usage, _expected) in enumerate(cases):
            transcript = self.write_transcript(
                [dict(usage, model="gemini-4-neon-high")],
                name=f"tokens-{index}.jsonl",
            )
            self.run_hook(conversation_id=f"conv-{index}", transcript=transcript)

        data = self.read_context()
        for index, (_usage, expected) in enumerate(cases):
            with self.subTest(index=index):
                self.assertEqual(
                    data["conversations"][f"conv-{index}"]["tokens"], expected
                )

    def test_run_ignores_non_object_payload(self):
        for raw in ("[]", "123"):
            with self.subTest(raw=raw):
                hook.run(raw)

        self.assertFalse((self.state_dir / "context.json").exists())

    def test_run_keeps_corrupt_context_file(self):
        self.state_dir.mkdir(parents=True, exist_ok=True)
        broken = self.state_dir / "context.json"
        broken.write_text("{broken", encoding="utf-8")
        transcript = self.write_transcript([{"input_tokens": 10}])

        self.run_hook(transcript=transcript)

        self.assertEqual(broken.read_text(encoding="utf-8"), "{broken")

    def test_run_snippet_empty_by_default(self):
        transcript = self.write_transcript([{"input_tokens": 10}])

        self.run_hook(transcript=transcript, lastUserInput="my private prompt")

        entry = self.read_context()["conversations"]["conv-1"]
        self.assertEqual(entry["snippet"], "")

    def test_run_snippet_redacts_secrets(self):
        config_dir = self.tmp / "ag-widgets"
        config_dir.mkdir()
        (config_dir / "config.json").write_text(
            json.dumps({"store_prompt_snippet": True}),
            encoding="utf-8",
        )
        transcript = self.write_transcript([{"input_tokens": 10}])
        secret = "key sk-abcdefgh12345 jwt eyJhbGciOiJIUzI1NiJ9abc"

        self.run_hook(transcript=transcript, lastUserInput=secret)

        snippet = self.read_context()["conversations"]["conv-1"]["snippet"]
        self.assertNotIn("sk-abcdefgh12345", snippet)
        self.assertNotIn("eyJhbGciOiJIUzI1NiJ9abc", snippet)
        self.assertIn("[redacted]", snippet)
        self.assertLessEqual(len(snippet), 60)

    def test_run_prunes_conversations_to_limit(self):
        conversations = {
            f"old-{index:02d}": {
                "updated_at": f"2026-01-{index + 1:02d}T00:00:00+00:00"
            }
            for index in range(12)
        }
        self.write_context(conversations, "old-00")
        transcript = self.write_transcript([{"input_tokens": 10}])

        self.run_hook(conversation_id="conv-current", transcript=transcript)

        data = self.read_context()
        self.assertEqual(len(data["conversations"]), hook.MAX_CONVERSATIONS)
        self.assertEqual(data["last"], "conv-current")
        self.assertIn("conv-current", data["conversations"])

    def test_run_never_evicts_oldest_current_conversation(self):
        conversations = {
            f"old-{index:02d}": {
                "updated_at": f"2026-01-{index + 1:02d}T00:00:00+00:00"
            }
            for index in range(11)
        }
        self.write_context(conversations, "old-00")
        transcript = self.write_transcript([{"input_tokens": 10}])

        class FrozenDateTime(datetime):
            @classmethod
            def now(cls, tz=None):
                moment = datetime(2025, 1, 1, 12, tzinfo=timezone.utc)
                return moment if tz else moment.replace(tzinfo=None)

        with mock.patch.object(hook, "datetime", FrozenDateTime):
            self.run_hook(conversation_id="conv-current", transcript=transcript)

        data = self.read_context()
        self.assertEqual(len(data["conversations"]), hook.MAX_CONVERSATIONS)
        self.assertEqual(data["last"], "conv-current")
        expected_updated_at = datetime(
            2025, 1, 1, 12, tzinfo=timezone.utc
        ).astimezone().isoformat(timespec="seconds")
        self.assertEqual(
            data["conversations"]["conv-current"]["updated_at"],
            expected_updated_at,
        )

    def dated_conversations(self):
        return {
            "old-x": {"updated_at": "2026-05-01T20:00:00+02:00"},
            "q1": {"updated_at": "2026-05-01T18:30:00+00:00"},
            "q2": {"updated_at": "2026-05-01T19:00:00+00:00"},
            "q3": {"updated_at": "2026-05-01T20:00:00+00:00"},
            "q4": {"updated_at": "2026-05-01T21:00:00+00:00"},
            "q5": {"updated_at": "2026-05-01T22:00:00+00:00"},
            "q6": {"updated_at": "2026-05-01T23:00:00+00:00"},
            "q7": {"updated_at": "2026-05-02T01:00:00+00:00"},
            "q8": {"updated_at": "2026-05-02T02:00:00+00:00"},
            "q9": {"updated_at": "2026-05-02T03:00:00+00:00"},
            "q10": {"updated_at": "2026-05-02T04:00:00+00:00"},
            "q11": {"updated_at": "2026-05-02T05:00:00+00:00"},
        }

    def test_prune_keeps_newest_by_parsed_date(self):
        conversations = self.dated_conversations()

        kept = hook._prune_conversations(conversations, "not-present")

        self.assertEqual(len(kept), hook.MAX_CONVERSATIONS)
        self.assertEqual(set(kept), {f"q{index}" for index in range(2, 12)})
        self.assertNotIn("old-x", kept)

    def test_prune_never_evicts_current_conversation(self):
        conversations = self.dated_conversations()

        kept = hook._prune_conversations(conversations, "old-x")

        self.assertEqual(len(kept), hook.MAX_CONVERSATIONS)
        self.assertIn("old-x", kept)
        self.assertNotIn("q2", kept)
        self.assertIn("q3", kept)

    def test_main_rotates_oversized_error_log(self):
        self.state_dir.mkdir(parents=True, exist_ok=True)
        error_log = self.state_dir / "hook_errors.log"
        old_content = b"x" * (hook.MAX_ERROR_LOG_BYTES + 100)
        error_log.write_bytes(old_content)

        class FakeStdin:
            def __init__(self, data):
                self.buffer = io.BytesIO(data)

        output = io.StringIO()
        with mock.patch("sys.stdin", FakeStdin(b"{invalid json")), mock.patch(
            "sys.stdout", output
        ):
            hook.main()

        rotated = self.state_dir / "hook_errors.log.1"
        self.assertTrue(rotated.exists())
        self.assertEqual(rotated.stat().st_size, len(old_content))
        text = error_log.read_text(encoding="utf-8")
        self.assertIn("Traceback (most recent call last)", text)
        self.assertIn("JSONDecodeError", text)
        self.assertEqual(output.getvalue().strip(), "{}")


if __name__ == "__main__":
    unittest.main()
