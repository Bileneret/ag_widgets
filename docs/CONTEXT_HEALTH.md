# Context health: model windows and degradation zones

AG Widgets does not try to predict a "hallucination token count". Research shows that context quality degrades continuously, and the exact point depends on the task, distractors, and where the relevant fact sits in the prompt. What the widget can do reliably is compare current usage with the **real** window reported by Antigravity.

## Why percentage zones

- **NoLiMa** ([arXiv 2502.05167](https://arxiv.org/abs/2502.05167)): at 32K tokens, 11 models drop below 50% of their short-context accuracy; GPT-4o falls from 99.3% to 69.7%.
- **RULER** ([arXiv 2404.06654](https://arxiv.org/abs/2404.06654)): only about half of models claiming 32K+ keep quality at 32K; effective context is typically 50–65% of the advertised size.
- **Critical thresholds** ([arXiv 2601.15300](https://arxiv.org/abs/2601.15300)): the tested model collapsed at 40–50% of its maximum window, with F1 dropping from 0.55 to 0.30.
- **Chroma Context Rot** (2025): all 18 tested models degrade as input grows, long before the window is full; distractors accelerate the fall.
- Antigravity itself exposes `exceeds_200k_tokens`: 200K is its own long-context warning boundary.

Defaults used by AG Widgets:

| Zone | Threshold | Meaning |
|---|---:|---|
| 🟢 OK | < 40% | normal working range |
| 🟡 Attention | 40–65% | quality on hard tasks is already dropping |
| 🔴 Risk | > 65% | the reliable part of the window is over |

For important work, compact earlier — around 100–200K even on a 1M window. Both percentages are configurable in `%LOCALAPPDATA%\ag-widgets\config.json`:

```json
{
  "context_thresholds": {
    "yellow_pct": 40,
    "red_pct": 65
  }
}
```

## Model windows in Antigravity

Verified on 2026-11-25 against Antigravity and Gemini API documentation. Antigravity windows differ from the API: Neon is smaller there.

| Model | Window | Note |
|---|---:|---|
| Gemini 4 Neon | **512K** | 1M only in the Gemini API |
| Gemini 4 Carbon | **1M** | Antigravity doubled the Gemini 4 window |
| Gemini 4 Argon | **1M** | |
| Gemini 4 Hydrogen | **1M** | |
| Gemini 3.8 Flash | **1M** | native window |
| Claude Opus 5.5 / Sonnet 5.5 | **1M** | native window; Antigravity does not publish its own |
| GPT-OSS 120B | **128K** | 131,072 tokens |
| Orchestrator (Neon/Carbon/Argon) | planner window | Neon 512K, Carbon/Argon 1M |

Absolute zone boundaries:

| Model | 🟡 from | 🔴 after |
|---|---:|---:|
| Neon (512K) | ~210K | ~341K |
| 1M models | ~419K | ~682K |
| GPT-OSS (128K) | ~52K | ~85K |

## How the window is resolved

Priority, highest first:

1. `contextWindowSize` / `context_window_size` from the Antigravity hook payload (`payload`).
2. `model_windows` in the runtime config (`config`).
3. `context_window.context_window_size` cached from the CLI status line in `model_windows_learned.json` (`cli`).
4. Built-in table in `agwidgets/context_model.py` (`table`).

If Google changes a window, the CLI value is picked up automatically and wins over the table. For an unknown model with no CLI value, the widget reports `unknown` instead of inventing a number.

## Sources

- [Antigravity models](https://antigravity.google/docs/models)
- [Gemini 4 Neon API](https://aistudio.google.com/docs/models/gemini-4-neon)
- [Gemini 4 Hydrogen](https://aistudio.google.com/docs/models/gemini-4-hydrogen)
- [Claude 5.5](https://platform.claude.com/docs/en/models/sonnet-5-5/overview)
- [gpt-oss-120b](https://developers.openai.com/api/docs/models/gpt-oss-120b)
