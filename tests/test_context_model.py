"""Tests for context windows, threshold normalization, and health levels."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import context_model as cm  # noqa: E402


class ModelWindowTests(unittest.TestCase):
    def test_default_windows_use_longest_prefix(self):
        cases = {
            "gemini-4-neon-high": (524_288, "table"),
            "gemini-4-carbon-medium": (1_048_576, "table"),
            "gemini-4-hydrogen-xhigh": (1_048_576, "table"),
            "gemini-pro-orchestrator-neon": (524_288, "table"),
            "gemini-pro-orchestrator": (1_048_576, "table"),
            "claude-opus-5-5-high": (1_000_000, "table"),
            "claude-sonnet-5-5-low": (1_000_000, "table"),
            "gpt-oss-120b-medium": (131_072, "table"),
        }
        for model, expected in cases.items():
            with self.subTest(model=model):
                self.assertEqual(cm.resolve_model_window(model), expected)

    def test_unknown_model_has_no_window(self):
        self.assertEqual(cm.resolve_model_window("some-unknown-model"), (None, "unknown"))
        self.assertEqual(cm.resolve_model_window(""), (None, "unknown"))

    def test_priority_explicit_config_learned_default(self):
        self.assertEqual(
            cm.resolve_model_window("gemini-4-neon-high", explicit=200_000),
            (200_000, "payload"),
        )
        self.assertEqual(
            cm.resolve_model_window(
                "gemini-4-neon-high",
                config_windows={"gemini-4-neon": 600_000},
            ),
            (600_000, "config"),
        )
        self.assertEqual(
            cm.resolve_model_window(
                "gemini-4-neon-high",
                learned_windows={"gemini-4-neon-high": {"size": 512_000}},
            ),
            (512_000, "cli"),
        )

    def test_normalize_display_name(self):
        self.assertEqual(cm.normalize_model_key("Gemini 4 Neon (High)"), "gemini-4-neon-high")
        self.assertEqual(cm.normalize_model_key("Claude Opus 5.5 (High)"), "claude-opus-5-5-high")
        self.assertEqual(cm.normalize_model_key("Gemini 3.8 Flash (High)"), "gemini-3-8-flash-high")

    def test_dotted_display_name_resolves_to_table(self):
        self.assertEqual(cm.resolve_model_window("Claude Opus 5.5 (High)"), (1_000_000, "table"))
        self.assertEqual(cm.resolve_model_window("gemini-3.8-flash-high"), (1_048_576, "table"))

    def test_learned_window_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            self.assertTrue(cm.save_learned_window(state, "Gemini 4 Carbon (Medium)", 1_048_576))
            learned = cm.load_learned_windows(state)
            self.assertEqual(learned["gemini-4-carbon-medium"]["size"], 1_048_576)
            self.assertEqual(
                cm.resolve_model_window("gemini-4-carbon-medium", learned_windows=learned),
                (1_048_576, "cli"),
            )

    def test_normalize_model_key_handles_empty(self):
        self.assertEqual(cm.normalize_model_key(None), "")
        self.assertEqual(cm.normalize_model_key(""), "")
        self.assertEqual(cm.normalize_model_key("   "), "")
        self.assertEqual(cm.normalize_model_key("Gemini -- 4  Neon!!!"), "gemini-4-neon")

    def test_prefix_boundary_requires_dash(self):
        # "gemini-4-neon2-high" must not match the "gemini-4-neon" prefix.
        self.assertEqual(cm.resolve_model_window("gemini-4-neon2-high"), (None, "unknown"))
        self.assertEqual(cm.resolve_model_window("gemini-4-neon-high"), (524_288, "table"))

    def test_prefix_boundary_accepts_dashed_claude_variant(self):
        self.assertEqual(
            cm.resolve_model_window("claude-opus-5-5-high"),
            (1_000_000, "table"),
        )
        self.assertEqual(cm.resolve_model_window("claude-opus-5-50"), (None, "unknown"))

    def test_invalid_config_windows_fall_back_to_table(self):
        config_windows = {
            "gemini-4-neon": 0,
            "claude-opus-5-5": "not-a-number",
            "gpt-oss-120b": {"size": True},
        }
        self.assertEqual(
            cm.resolve_model_window("gemini-4-neon-high", config_windows=config_windows),
            (524_288, "table"),
        )
        self.assertEqual(
            cm.resolve_model_window("claude-opus-5-5-high", config_windows=config_windows),
            (1_000_000, "table"),
        )
        self.assertEqual(
            cm.resolve_model_window("gpt-oss-120b-medium", config_windows=config_windows),
            (131_072, "table"),
        )

    def test_learned_window_rejects_invalid_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            self.assertFalse(cm.save_learned_window(state, "gemini-4-neon-high", True))
            self.assertFalse(cm.save_learned_window(state, "", 1_000))
            self.assertFalse(cm.save_learned_window(state, "gemini-4-neon-high", 0))
            self.assertEqual(cm.load_learned_windows(state), {})

    def test_load_learned_windows_missing_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(cm.load_learned_windows(Path(tmp)), {})


class ContextLevelTests(unittest.TestCase):
    def test_neon_boundaries(self):
        ok = cm.compute_context(100_000, 524_288)
        self.assertEqual(ok["level"], "ok")
        self.assertEqual(ok["warn_tokens"], 209_715)
        self.assertEqual(ok["risk_tokens"], 340_787)

        self.assertEqual(cm.compute_context(210_000, 524_288)["level"], "warn")
        self.assertEqual(cm.compute_context(341_000, 524_288)["level"], "risk")

    def test_window_formatting(self):
        self.assertEqual(cm.format_window(524_288), "512K")
        self.assertEqual(cm.format_window(1_048_576), "1M")
        self.assertEqual(cm.format_window(131_072), "128K")
        self.assertEqual(cm.format_window(1_000_000), "1M")
        self.assertEqual(cm.format_window(None), "—")

    def test_unknown_window(self):
        result = cm.compute_context(1234, None)
        self.assertIsNone(result["pct"])
        self.assertEqual(result["level"], "unknown")

    def test_percent_thresholds_are_clamped(self):
        thresholds = cm.normalize_thresholds({"context_thresholds": {"yellow_pct": 90, "red_pct": 20}})
        self.assertEqual(thresholds["yellow_pct"], 90.0)
        self.assertEqual(thresholds["red_pct"], 91.0)

    def test_defaults_when_config_missing(self):
        self.assertEqual(cm.normalize_thresholds(None), {"yellow_pct": 40.0, "red_pct": 65.0})

    def test_format_tokens_million_boundary(self):
        self.assertEqual(cm.format_tokens(999_999), "1M")
        self.assertEqual(cm.format_tokens(999_500), "1M")
        self.assertEqual(cm.format_tokens(999_499), "999K")
        self.assertEqual(cm.format_tokens(1_000_000), "1M")

    def test_format_tokens_millions(self):
        self.assertEqual(cm.format_tokens(1_234_567), "1.2M")

    def test_format_tokens_zero(self):
        self.assertEqual(cm.format_tokens(0), "0")

    def test_format_tokens_rejects_none(self):
        self.assertEqual(cm.format_tokens(None), "—")

    def test_format_tokens_rejects_bool(self):
        self.assertEqual(cm.format_tokens(True), "—")
        self.assertEqual(cm.format_tokens(False), "—")

    def test_format_tokens_rejects_nan(self):
        self.assertEqual(cm.format_tokens(float("nan")), "—")

    def test_format_window_megabytes(self):
        self.assertEqual(cm.format_window(2_097_152), "2M")
        self.assertEqual(cm.format_window(1_048_576), "1M")
        self.assertEqual(cm.format_window(1_000_000), "1M")

    def test_format_window_kilobytes(self):
        self.assertEqual(cm.format_window(524_288), "512K")
        self.assertEqual(cm.format_window(131_072), "128K")

    def test_format_window_zero(self):
        self.assertEqual(cm.format_window(0), "—")

    def test_format_window_rejects_invalid(self):
        self.assertEqual(cm.format_window(None), "—")
        self.assertEqual(cm.format_window(True), "—")
        self.assertEqual(cm.format_window(float("nan")), "—")

    def test_compute_context_clamps_negative_tokens(self):
        result = cm.compute_context(-5, 524_288)
        self.assertEqual(result["tokens"], 0)
        self.assertEqual(result["pct"], 0.0)
        self.assertEqual(result["level"], "ok")

    def test_compute_context_bool_tokens(self):
        result = cm.compute_context(True, 524_288)
        self.assertEqual(result["tokens"], 0)
        self.assertEqual(result["level"], "ok")

    def test_compute_context_zero_tokens_is_ok(self):
        self.assertEqual(cm.compute_context(0, 524_288)["level"], "ok")

    def test_compute_context_zero_window_unknown(self):
        result = cm.compute_context(100, 0)
        self.assertEqual(result["level"], "unknown")
        self.assertIsNone(result["window"])
        self.assertIsNone(result["pct"])
        self.assertEqual(result["tokens"], 100)

    def test_compute_context_custom_thresholds(self):
        thresholds = {"yellow_pct": 10.0, "red_pct": 20.0}
        self.assertEqual(cm.compute_context(99, 1_000, thresholds)["level"], "ok")
        self.assertEqual(cm.compute_context(100, 1_000, thresholds)["level"], "warn")
        self.assertEqual(cm.compute_context(200, 1_000, thresholds)["level"], "risk")


if __name__ == "__main__":
    unittest.main()
