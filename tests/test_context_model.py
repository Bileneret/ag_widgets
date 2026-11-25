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


class ContextLevelTests(unittest.TestCase):
    def test_neon_boundaries(self):
        green = cm.compute_context(100_000, 524_288)
        self.assertEqual(green["level"], "green")
        self.assertEqual(green["warn_tokens"], 209_715)
        self.assertEqual(green["risk_tokens"], 340_787)

        self.assertEqual(cm.compute_context(210_000, 524_288)["level"], "yellow")
        self.assertEqual(cm.compute_context(341_000, 524_288)["level"], "red")

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


if __name__ == "__main__":
    unittest.main()
