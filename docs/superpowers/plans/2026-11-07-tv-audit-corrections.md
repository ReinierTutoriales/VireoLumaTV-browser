# TV audit corrections implementation plan

**Goal:** close the six findings approved after the PR #235 audit, preserving browser behavior and API 24+ support.

**Architecture:** retain WebView, TV Material tokens and Foundation input. Exercise real mouse/touch events in Compose tests; share mandatory D-pad journeys between profile generation and Macrobenchmark. Use exported semantic IDs instead of translated labels.

**Tech Stack:** Kotlin, Compose Foundation/TV Material, UI Automator, Macrobenchmark, GitHub Actions.

**Spec:** the owner's approved six findings in this chat; contracts N01–N10 in `docs/REGRESSION_POLICY.md`.

## Global constraints

- Keep the existing branch and owner identity; no merge or release.
- Preserve incognito, adblock, saved data, fullscreen and the one-live-WebView policy.
- Check official stable metadata; adopt updates only with a compatible verified benefit.
- Distinguish automated correctness from physical-TV performance.

## Review focus

- Pointer click before D-pad focus: one action, no activation on hover.
- Every menu entry reachable in both directions, including scrolled rows.
- Tab close affects the intended tab; selection is observable independently of focus.
- Missing controls or wrong destinations fail both the generator and benchmarks.
- Profile updates require verification on the resulting SHA.

## Tasks

1. Add regressions for manifest metadata and Spanish long-click accessibility; extend menu tests with mouse, touch, hover and return to D-pad. Run before the product correction where the Android environment is available.
2. Correct metadata and localized long-click labels; export menu/tab semantic IDs and selected state. Run focused Compose tests, adjacent navigation suites, lint and minified build.
3. Replace optional/click-based journeys in `TvFlows.kt` with bounded D-pad movement, focus assertions, OK activation and destination/return checks. Both callers use the same mandatory journeys; isolate generator data before each collected iteration. Compile and run Benchmark and Baseline profile workflows.
4. Expand Benchmark path filters to app sources, tests, build/dependency inputs and its summary script. Regenerate profiles, verify exact-head CI, update existing evidence and inventory.

## Verification

`testGenericDebugUnitTest`, `lintGenericDebug`, `assembleGenericRelease`; full CI API 24/28/34/37; Macrobenchmark on AOSP API 34; profile generation on API 34. Local `git diff --check`, inventory and available script tests. Physical-TV performance remains unmeasured.
