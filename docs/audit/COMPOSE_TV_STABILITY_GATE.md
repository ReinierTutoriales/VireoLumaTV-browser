# Compose TV stability gate

Baseline: `master@2a88558a801f7a785b2d17429af2dd59277a165c` (PR #229 merged; post-merge CI succeeded).

## Scope and ownership

- Keep WebView navigation, tab lifecycle, ad blocking and video/fullscreen behavior owned by the existing activity/engine. Compose remains a presentation layer; do not duplicate browser state or side effects.
- Preserve `TvBrowserShell`, `TvBrowserCommandBar`, `TvAddressField`, `TvTabStrip` public callback contracts and exported test tags unless the corresponding consumers and tests are migrated together.
- Never cherry-pick or merge stale PR #228 (`migracion-compose-tv`) into master. Reimplement menu changes from current master after a behavior comparison.
- Keep Android Views/XML until all live call sites are identified and their behavior is covered. File presence alone does not establish a live dependency.
- No unrelated dependency upgrades, WebView engine changes, or blocker changes in visual/UI PRs.

## Required regression matrix before merge

1. Compile and run unit tests, lint, and startup CI on API 24, 28, 34, 37.
2. D-pad: initial focus; Up/Down/Left/Right; Back restoration; disabled actions; tabs (select, close, long press); address input/keyboard; menu open/close/select; fullscreen enter/exit.
3. Preserve home shortcuts and suggestions, normal/incognito isolation, and shield enable/disable and detail actions.
4. Check process survival **and** actual focused nodes. A log-only or soft focus assertion is not evidence of passing focus behavior.
5. Compare baseline/profile coverage and Macrobenchmark frame timing for representative TV flows before claiming smoother UI. Collect memory/CPU metrics on a low-RAM device before claiming efficiency gains.
6. A green CI is necessary but not sufficient for a physical-TV release; manually validate remote input and video on supported hardware.

## Change procedure

- Branch from the latest green `master`; one subsystem per PR.
- Record before/after behavior, affected callbacks, API compatibility and a rollback plan.
- Keep focus checks strict only when the relevant Compose test tags are reliably visible to UIAutomator; otherwise explicitly mark the gap and add instrumentation coverage. Do not silently weaken failures.
- Never merge a red or conflicting PR. Distinguish emulator/SDK provisioning failures from app regressions using job logs.
- Require successful post-merge CI; if regressions occur, revert the isolated PR rather than rewriting unrelated stable code.

## Official Android reference points

- Compose for TV: https://developer.android.com/training/tv/playback/compose
- Focus behavior in Compose: https://developer.android.com/develop/ui/compose/touch-input/focus
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

These references are guidance, not proof that a particular optimization improves this browser; validate on the app and target hardware.
