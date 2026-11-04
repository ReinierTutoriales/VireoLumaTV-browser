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
