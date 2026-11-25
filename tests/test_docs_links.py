"""Documentation integrity tests for the READMEs and the install guides.

Three kinds of rot are guarded here:

* every relative link in the published docs points at a real file;
* the agent runbook covers every check that ``collector.py --doctor`` can report;
* no document leaks a secret or a personal path.

Everything is local and deterministic: no network, no subprocess, no writes.
"""

from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

DOCS = (
    "README.md",
    "README.ru.md",
    "docs/INSTALL_GUIDE.md",
    "docs/INSTALL_GUIDE.ru.md",
    "docs/AGENT_INSTALL.md",
    "docs/CONTEXT_HEALTH.md",
)

REQUIRED_CHECKS = frozenset(
    {
        "python_version",
        "agy_cli",
        "annotations_dir",
        "antigravity_hook",
        "antigravity_sidecar",
        "runtime_config",
        "state_freshness",
        "secrets_storage",
        "ntfy_reachability",
        "zcode_api",
    }
)

# A fenced block starts with ``` (plus an optional info string) and ends at a
# line that begins with ``` — links inside fences are never rendered.
_FENCE_RE = re.compile(r"^```.*?^```[^\n]*$", re.DOTALL | re.MULTILINE)
# Inline links and images share one shape; the leading ``!`` is optional.
_LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]*)\)")
_SKIP_PREFIXES = ("http://", "https://", "mailto:", "ftp://", "#", "<")

# Split so that this test file would not match itself if it ever got listed.
_USER_TAG = "Le" "novo"
_PERSONAL_PATTERNS = (
    (re.compile(re.escape(_USER_TAG), re.IGNORECASE), "personal user name"),
    (re.compile(r"C:\\Users\\" + re.escape(_USER_TAG)), "personal home path"),
    (re.compile(r"\bagw-[0-9a-f]{24}\b"), "real-looking ntfy topic"),
    (re.compile(r"\beyJ[A-Za-z0-9_-]{20,}"), "JWT"),
)


def _link_targets(text: str) -> list[str]:
    """Return relative link/image targets from *text*, fenced blocks removed."""
    content = _FENCE_RE.sub("", text)
    targets: list[str] = []
    for match in _LINK_RE.finditer(content):
        raw = match.group(1).strip()
        if not raw or raw.startswith("<"):
            continue
        # Drop an optional markdown title: ``(path "title")``.
        target = raw.split()[0]
        if target.startswith(_SKIP_PREFIXES):
            continue
        # Fragments and queries are not part of the file name.
        target = target.split("#", 1)[0].split("?", 1)[0]
        if target:
            targets.append(target)
    return targets


class DocumentationIntegrityTests(unittest.TestCase):
    def test_all_docs_exist_and_non_empty(self) -> None:
        for relative in DOCS:
            with self.subTest(doc=relative):
                path = ROOT / relative
                self.assertTrue(path.exists(), f"{relative} does not exist")
                self.assertTrue(path.is_file(), f"{relative} is not a regular file")
                self.assertGreater(path.stat().st_size, 0, f"{relative} is empty")
                text = path.read_text(encoding="utf-8")
                self.assertTrue(text.strip(), f"{relative} contains only whitespace")

    def test_relative_markdown_links_resolve(self) -> None:
        failures: list[str] = []
        for relative in DOCS:
            doc = ROOT / relative
            if not doc.exists():
                failures.append(f"{relative} -> <document is missing>")
                continue
            text = doc.read_text(encoding="utf-8")
            for target in _link_targets(text):
                candidate = (doc.parent / unquote(target)).resolve()
                if not candidate.exists():
                    failures.append(f"{relative} -> {target}")
        self.assertEqual(
            failures,
            [],
            "Broken relative links:\n" + "\n".join(failures),
        )

    def test_agent_runbook_covers_all_doctor_checks(self) -> None:
        from agwidgets import doctor

        # ``_HINTS`` is the single source of truth for check names; the two
        # online-only checks are added defensively in case they ever move.
        expected = set(doctor._HINTS) | {"ntfy_reachability", "zcode_api"}

        self.assertLessEqual(
            REQUIRED_CHECKS,
            set(doctor._HINTS),
            "doctor._HINTS lost a check that the docs promise",
        )
        self.assertEqual(len(expected), 10, f"unexpected doctor check set: {sorted(expected)}")

        runbook = (ROOT / "docs" / "AGENT_INSTALL.md").read_text(encoding="utf-8")
        missing = sorted(name for name in expected if name not in runbook)
        self.assertEqual(
            missing,
            [],
            "docs/AGENT_INSTALL.md does not mention doctor checks: " + ", ".join(missing),
        )
        self.assertIn("--doctor-json", runbook)

    def test_docs_contain_no_secrets_or_personal_paths(self) -> None:
        problems: list[str] = []
        for relative in DOCS:
            doc = ROOT / relative
            if not doc.exists():
                problems.append(f"{relative}: document is missing")
                continue
            text = doc.read_text(encoding="utf-8")
            for pattern, label in _PERSONAL_PATTERNS:
                if pattern.search(text):
                    problems.append(f"{relative}: matches {label}")
        self.assertEqual(
            problems,
            [],
            "Secrets or personal paths found in docs:\n" + "\n".join(problems),
        )


if __name__ == "__main__":
    unittest.main()
