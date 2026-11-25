# Changelog

## 1.0.0 — 2026-11-25

First public release. Two Android builds from one codebase:

- **AG Widgets** (`dev.agwidgets`): Antigravity context and quotas plus ZCode vouchers/balance.
- **AG Context** (`dev.agwidgets.context`): Antigravity context and quotas only; no ZCode client, OAuth, token storage, or ZCode network calls.

Features:

- Context card for the chat that is open right now, with model, tokens/window, and health zones.
- Antigravity 5h/weekly quota cards with reset countdowns.
- ZCode voucher and balance checks, including OAuth sign-in (full build).
- First-run choice between "ZCode only" and "With a PC".
- Android 4×2 Glance widget with light/dark themes.
- Local reset reminders through WorkManager.
- Private ntfy channel between the desktop collector and the phone.
- Signed Android release APKs; Windows remains source-only.
