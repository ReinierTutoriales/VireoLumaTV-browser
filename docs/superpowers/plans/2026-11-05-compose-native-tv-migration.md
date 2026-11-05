# Compose-native Android TV UI — migration contract

**Goal:** Replace legacy browser UI widgets with a single native Compose UI while retaining WebView, adblocking, media handling and TV pointer behavior.

**Current evidence (branch modernize/compose-tv-ui):** `browser_window.xml` still instantiates `BrowserControls`, `AddressSuggestionsView`, `TabStrip`, and `PointerMenu`. `TvConnectedBrowserCommandBar` depends on `BrowserControls.CommandActions`. The XML also hosts `PointerLayout`, the home container, preview overlays and progress indicators.

## Non-negotiable boundaries
- Preserve the existing WebView engine and blocking pipeline; Compose is presentation, not a replacement web engine.
- Preserve TV D-pad focus, long-press, pointer mode, fullscreen and keyboard behavior.
- AMOLED black theme, TV-safe insets, accessible focus indication; no card-based home redesign.
- Do not delete functional views before all their call sites have been migrated and verified.
- Do not mask Compose test failures by deleting behavioral assertions; use Compose-aware UI tests for Compose.
- No claim of completion until the full CI matrix passes and TV interaction flows are validated.

## Migration sequence (each phase independently testable)
1. **Inventory and baseline:** enumerate constructors, IDs, listeners and tests referencing legacy views. Establish CI baseline, failure stack traces and reproducible D-pad flows.
2. **UI state and events:** introduce a lifecycle-aware presentation state for address editing, navigation availability, shield, tabs, suggestions and menus. One-way events to existing browser controller; no duplicated WebView state.
3. **Compose tab strip and suggestions:** replace `TabStrip` and `AddressSuggestionsView` with Compose implementations. Add tests for focus traversal, selecting a suggestion, opening/closing a tab and long-press.
4. **Compose overlays and menus:** replace legacy `PointerMenu` and visual progress/preview overlays where appropriate. Preserve pointer input infrastructure and preview rendering that requires Android views.
5. **Compose root:** replace `browser_window.xml` UI composition with a Compose root and explicit `AndroidView` interop for WebView/pointer hosting. Remove obsolete IDs, styles and adapter classes only after call-site and test inventory reaches zero.
6. **Performance and release gate:** run unit, instrumented focus/navigation tests, lint and CI; generate/validate Baseline Profile for startup, tab switching, address entry and fullscreen. Compare frame timing, RAM and regressions on target-class hardware.

## Gate for each phase
- Write a failing behavior test first (RED).
- Implement one bounded change and show the test passing (GREEN).
- Run the full relevant test suite and inspect failures before moving on.
- Review deleted files against a repository-wide reference search.
- Keep a concise commit log mapping each removal to its replacement and test evidence.

## Explicit exclusions
- No speculative library version bump without compatibility verification.
- No simultaneous refactor of adblock or video playback while replacing UI.
- No wholesale XML deletion until Compose replacement and interop are tested.
