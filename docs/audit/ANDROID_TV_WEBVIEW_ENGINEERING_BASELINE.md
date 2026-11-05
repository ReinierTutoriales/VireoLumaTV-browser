# Android TV + WebView engineering baseline (2026-11-04)

This policy is a **source-grounded implementation contract**, not proof of performance gains or successful playback on physical hardware. Before changing a component, record the official API contract, the affected code path, a regression test, and compatibility behavior for API 24/28/34/37.

## 1. Fullscreen video

Official Android contract: [WebChromeClient.onShowCustomView / onHideCustomView](https://developer.android.com/reference/android/webkit/WebChromeClient). The host must attach the supplied custom view to a fullscreen window, remove it on exit, and implement both callbacks. The callback can be invoked by the host to request exit. Preserve the original site's playback and do not replace it with a second engine by default.

Current implementation: `BrowserChromeClient.kt`, `WebViewPageEngine.kt`, `TabWindow.kt`. Required invariants: one active fullscreen session per WebView; no stale callback after exit; idempotent close; no focus on a detached view; exact restoration of pre-video system UI and keep-screen-on state; switching/closing tabs must release the custom view and notify Chromium. Reentrant callbacks must not leave a stale session. Never cap media resolution without a measured hardware or platform limitation.

Regression scenarios: enter/exit by player button; remote Back; rapid repeated enter/exit; change/close tab while playing; popup detachment; renderer loss; fullscreen from a page while browser controls are visible; pointer and D-pad modes. Test HTML5 playback separately from mobile YouTube. Startup-only CI does **not** establish playback correctness.

## 2. Remote focus and accessibility

Official guidance: [TV navigation](https://developer.android.com/training/tv/get-started/navigation) and [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system). All visible actionable controls must be reachable using D-pad; focused state must be visible; Back must have a predictable effect. Focus restoration must check that its destination is still visible/attached. Do not open the keyboard merely because a favorite row received focus.

## 3. WebView capability checks

Official API: [WebViewFeature.isFeatureSupported](https://developer.android.com/reference/androidx/webkit/WebViewFeature). Features vary with the installed WebView APK and OS version. Check feature support at runtime before using a feature-gated WebKit API; use `isStartupFeatureSupported` for startup-only features. Preserve API 24 minimum support; never assume that compileSdk or the Android OS version implies a WebView feature is available.

## 4. Runtime performance

Official guidance: [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview). Generate profiles for **measured** critical journeys: cold start, address navigation, switching tabs, opening settings, opening/closing video fullscreen. Distinguish Baseline Profiles (runtime compilation) from Startup Profiles (DEX layout). Record before/after frame timing, memory, CPU, and launch latency on comparable builds and the same physical TV. No percentage improvement is claimed without data.

## 5. Compatibility, security and release gates

- Preserve WebView-first playback, mobile YouTube policy, origin-aware bridge policy, adblock-rust/uBO +js filters, incognito separation and no mandatory Play Services.
- Keep UI animations short, cancel stale animations, respect disabled animator scale, and avoid unnecessary main-thread work. Do not add another player or heavyweight framework without measured benefit.
- Compile and run unit/instrumented checks; validate API 24/28/34/37. Run a real TV acceptance matrix for remote navigation, media controls, fullscreen and 2 GB memory pressure before asserting hardware compatibility.
- Document evidence per change: official URL, code path, regression test, CI run, physical test result (if available), and remaining limitations. Do not call a release fully validated when only startup tests pass.
