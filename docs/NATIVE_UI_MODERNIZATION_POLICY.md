# Native UI modernization policy

Status: target architecture; migration is not complete.

## Architecture
- Jetpack Compose is the sole target UI layer for toolbar, tabs, menus, settings, dialogs and browser controls.
- Material 3 defines visual tokens, typography, states and motion, adapted for Android TV remote interaction.
- Material Symbols Rounded is the target icon identity. Use one centralized, typed icon catalog and one rendering mechanism; do not preserve XML-ID-to-ligature bridges as final architecture.
- AMOLED uses pure black backgrounds, accessible contrast and unmistakable D-pad focus.
- Preserve Android WebView as the web engine and preserve existing content blocking behavior.

## Dependency acceptance
1. Prefer official, stable, compatible implementations.
2. Consider maintained third-party libraries only after checking provenance, license, compatibility, APK/memory impact and measurable value.
3. Otherwise implement the smallest maintainable in-project alternative.
- A newer version alone is not justification.
- Do not call third-party wrappers Google-official.
- Record dependency versions, licenses, provenance and evaluation results before adoption.

## Migration completion criteria
- Toolbar, tabs, main menu, settings, dialogs and navigation controls all use Compose.
- All icon call sites use the centralized symbol catalog, without legacy drawable resource IDs in the public Compose API.
- Delete obsolete XML resources and compatibility adapters only after all references and supported API levels are verified.
- Keep temporary compatibility isolated, explicitly tracked and removed before declaring migration complete.
- Android minimum SDK policy must be decided from supported hardware, not from UI legacy constraints.

## Performance and accessibility
- Minimize unnecessary recompositions, allocations and main-thread work.
- Test on 2 GB RAM Android TV hardware; measure memory, startup, input-to-frame latency and frame timing.
- Generate and validate Baseline Profiles for startup, toolbar, tabs, menu, settings, bookmarks and video transitions.
- Use Macrobenchmark and FrameTimingMetric on supported test devices; report before/after metrics.
- Motion must never delay D-pad actions. Verify focus restoration, long press, Back and screen-reader semantics.

## Regression gates
- Preserve navigation, tabs, bookmarks, downloads, video, incognito isolation and ad blocking.
- Build, unit/instrumented tests and launch tests on API 24/28/34/37 (unless minimum API changes by documented decision).
- Check remote-control flows and visual appearance on physical TV hardware.
- Do not treat green CI as proof of visual quality or real-device performance.

## Execution
1. Audit PR #232 (third-party Material Symbols integration). Do not merge its permanent fallback as final architecture. Check license, provenance, dependency resolution, API compatibility, APK size and TV focus/visuals.
2. Migrate all icon references to one typed catalog and renderer; remove obsolete XML once unused.
3. Finish Compose migration of menus, settings and dialogs.
4. Optimize D-pad, transitions, recomposition, Baseline Profiles, memory and latency.
5. Remove obsolete code, complete regression gates and publish a verified release.

## PR #232 review notes (2026-11-06)
- The draft adds `dev.vicart:compose-material-symbols:1.1.6` and a `materialSymbolName(@DrawableRes iconRes)` mapping for 30 legacy resources.
- The rendering path selects the third-party symbol on API 26+ and existing XML vectors on API 24/25.
- This is an experiment, not a single icon architecture. It must not be merged as the definitive solution.
- License, transitive dependencies, artifact provenance, binary size, instrumentation and visual behavior remain unverified.
- Combined commit status returned no legacy status entries; that does not establish GitHub Actions check-run success or failure.
