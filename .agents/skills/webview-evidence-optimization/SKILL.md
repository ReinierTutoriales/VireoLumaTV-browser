---
name: webview-evidence-optimization
description: Use for Android TV browser performance, security, WebView capability adoption, startup, memory, networking, adblock interaction, and future optimization research. Requires official sources, source inspection, measurable acceptance criteria and regression learning.
---

# Evidence-driven WebView optimization

## Mission
Improve VireoLumaTV's real-world browsing speed, resilience, privacy, security and D-pad experience without sacrificing low-memory devices or existing behavior. Innovation means a verified user benefit, not maximum API adoption.

## Read first
1. `AGENTS.md`, `docs/REGRESSION_POLICY.md`, affected source/tests, and the relevant `agent-governance`, `precise-coding` and `testing-discipline` skills.
2. Current branch, SHA, open PRs, CI, dependencies and installed WebView feature gates. Do not infer capability from Android API level or user-agent alone.
3. Primary documentation and release notes from Android Developers, AndroidX WebKit, Chromium and the actual library vendor. Record URL, publication/revision date when available, minimum version, feature availability, caveats and confidence. Resolve contradictions before recommending changes.
4. Use available repository, documentation and CI integrations (including MCP connectors where authorized) as retrieval/verification tools, not as proof that code runs. Never claim a connection, benchmark or test was executed unless it was.

## Source acquisition and verification ladder (mandatory)
Do not begin by asking for physical TV tests. Retrieve and contrast the strongest available evidence in this order:
1. **Official platform contracts:** Android Developers API/reference and behavior-change pages; AndroidX WebKit release notes, `WebViewFeature` support and source; Android TV design/navigation/accessibility guidance; Chromium/WebView documentation and upstream source where applicable; official AGP/Kotlin/Rust and dependency changelogs. Use primary documentation over blogs, forum claims or unsourced benchmark percentages. Record direct URL, version/date, scope and caveats.
2. **Official implementation patterns:** Android/Chromium source, AndroidX tests, samples, CTS and public issue trackers. Check whether guidance is normative, illustrative, experimental or device-dependent. Do not transplant a sample without reconciling its lifecycle, security model and API compatibility.
3. **Our repository:** inspect implementation, dependency lockfiles, manifest, feature checks, test fixtures, CI, prior regressions and exact commit. Prefer a source-to-source gap matrix: official requirement -> current code -> evidence -> minimal change -> acceptance check.
4. **Automated evidence:** compile, lint, unit/instrumented tests, emulator, CI logs and reproducible profiling where available. Use connected GitHub/docs/CI tools or authorized MCP services to retrieve evidence; discover actual capabilities before claiming a connector can fetch, run or change anything. Missing MCP access is not a reason to invent results.
5. **Physical hardware only when material:** hardware decoder behavior, real D-pad focus, frame drops, thermals and device-specific memory pressure may require device measurements to *claim measured device outcomes*. Do not block standards-compliant code improvements, documentation, static analysis or CI work pending a physical TV. Label the narrow residual validation gap instead.

Distinguish three conclusions: **documented compliant** (API/contract verified), **automatically verified** (exact tests/build passed), and **empirically faster** (comparable benchmark). Never conflate them. Do not use generic baseline-profile speedup claims as evidence of this app's gain.

### Research efficiency
- Start with a scoped question and the smallest set of authoritative sources. Prefer targeted source paths and official release diffs over broad repeated searches.
- Cache findings in the existing PR/audit with source URLs and version/date; refresh when the API or dependency changes, not on every unrelated edit.
- Group related findings into a single actionable change plan. Rank by proven correctness/security risk, likely user impact, implementation complexity and regression surface; avoid speculative redesigns.
- Use API capability detection and documented fallback paths rather than device-specific guesses. Prefer standard Android/WebView implementations unless a demonstrated gap requires custom code.
- Every research cycle must yield a concrete output: verified gap, compatible implementation with checks, or a documented no-change decision with reason. Avoid indefinite testing requests.

## Investigation lanes (evaluate, do not blindly enable)
- **Startup and UI:** WebView startup APIs and main-thread blocking; StrictMode/Perfetto traces; startup timing; Baseline Profiles and Macrobenchmark for app-owned Kotlin/Java paths; D-pad focus latency, frame pacing, accessibility and TV-native components.
- **Memory and lifecycle:** host plus renderer memory, PSS/private dirty/RSS as appropriate, native allocations, renderer recovery via `onRenderProcessGone`, WebView disposal, tab restoration, bounded caches, low-memory pressure and long sessions. Never equate host heap with total WebView cost.
- **Navigation and network:** conditional preconnect, QUIC hints, prefetch and prerender only with feature support, origin/privacy constraints, network budgets and measured gains. Verify `shouldInterceptRequest` and adblock interaction, redirects, cookies, cache, incognito and failed/offline connections before enabling speculative loading. Prerender is opt-in experimental on constrained hardware until measured.
- **Security:** Safe Browsing, certificate validation, origin-scoped WebMessage bridges, hostile iframes, external intents, schemes, downloads, file/content URI exposure, scriptlet trust boundaries, private profiles and backup/data persistence. Never weaken TLS, origin checks or adblock to fix playback.
- **Browser quality:** history/back/fullscreen state machine, focus and keyboard behavior, tabs/bookmarks/downloads, video compatibility, search and session recovery; preserve owner-defined UX contracts.
- **Dependencies:** compare currently used AndroidX/AGP/Kotlin/Rust versions with official stable releases and compatibility matrix; upgrade only with migration evidence and reproducible verification. Avoid speculative dependencies and parallel network engines.

## Decision protocol for each candidate
Create a compact evidence record in the existing relevant audit or PR:
- **Observed:** exact source location, SHA, repro/log/measurement and current behavior; otherwise mark hypothesis.
- **Source:** official API/release documentation, availability gate, known limitations and interaction with current architecture.
- **Options:** no change, minimal native change, alternative; compare risk, maintenance, security, binary size, CPU, RAM, network and UX.
- **Metric:** define baseline, scenario, device/emulator class, WebView version, repetitions and success threshold *before* implementation. Track median and tail latency, startup, dropped frames, memory peaks, crashes, or requests as appropriate. Do not invent percentage gains.
- **Decision:** implement only a compatible, reversible change with measurable expected benefit or a verified correctness/security fix. Seek owner approval for new behavior per `agent-governance`.

## Execution and gates
1. Fix one proven root cause or cohesive capability at a time. Add a regression test for a confirmed defect; test feature-on/feature-off where relevant.
2. Preserve existing tests and neighboring contracts; check release build, lint, unit tests and exact-head CI. For rendering, video, network and memory, report physical-device validation separately.
3. Benchmark before/after on comparable builds, WebView versions and conditions; record uncertainty. If a benefit is absent or a regression appears, revert or leave disabled.
4. Prefer feature detection and bounded fallback over device-model exceptions. Keep safe defaults for low-RAM TVs, incognito and untrusted sites.
5. Do not merge, release or delete branches without owner authorization. No unverified success claims.

## Continuous improvement loop
For each reproducible regression or owner correction: identify the violated contract and verified root cause; fix and add a test first; add **one short reusable rule** to the relevant existing skill only if it prevents recurrence. Remove obsolete or contradictory advice. Keep incident evidence in existing audit/PR and product contracts in `docs/REGRESSION_POLICY.md`; do not grow an unbounded incident diary. Revisit prior assumptions when AndroidX/WebView releases change their validity.

## Primary starting references (revalidate on every task)
- https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup
- https://developer.android.com/develop/ui/views/layout/webapps/speculative-loading
- https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory
- https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge
- https://developer.android.com/jetpack/androidx/releases/webkit
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://developer.android.com/training/tv/get-started/navigation

This skill defines an investigation workflow, not a claim that any candidate optimization is implemented or benchmarked.

## Macrobenchmark harness lessons (verified 2026-11-07)

- Benchmarks and the profile generator share mandatory `TvFlows.kt` journeys: assert focus, activate with D-pad OK, verify destinations and return. Positional tab counts cannot detect a wrong-tab close; verify the surviving tab identity. Missing controls must fail both callers.
- Frame journeys run without `startupMode`: a cold mode kills the process after `setupBlock`, before the journey. Clear app data before each measurement, but only before the collection test for profiles, preserving ART between collection iterations. Use the launcher path: a VIEW intent appends a tab on each launch.
- Open the capsule with Back only when absent. Use native resource IDs and exported Compose tags rather than translated text. AGP 9 writes benchmark JSON, traces and screen dumps under `build/intermediates/managed_device_android_test_additional_output/`.
- Emulator frame time is dominated by the emulated GPU channel (`eglSwapBuffersWithDamageKHR`, `rcCreateSyncKHR`); the same case varied about 30 % between runs. Compare modes within one run, inspect Perfetto before attributing cost to app code, and claim no speed gain from emulator data.
