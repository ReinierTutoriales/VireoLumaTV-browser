---
name: tv-ui
description: Use for UI, focus, navigation, or layout work. Ensures polished Android TV remote (D-pad) experience and low-memory friendliness.
---

# TV UI & Remote

- Optimize for Android TV remote: proper focus handling, D-pad navigation, clear focus indicators, and accessibility.
- Prefer native Compose for browser UI and official TV Material 3 patterns; retain Android WebView solely for web content. Keep layouts simple and lightweight.
- Respect existing Spanish UI, theme, and compact TV-oriented design.
- Avoid heavy animations or memory-intensive views that hurt low-RAM devices.
- Trace focus in the actual layout and callback; cover adjacent controls and
  accessibility state with a focused test when behavior changes.
- The current UI uses one top capsule and tabs. Apply N01–N10 from
  `docs/REGRESSION_POLICY.md`; do not revive the removed bottom toolbar.
- Record what still needs a real remote/device check. Do not invent UI behaviors.

## Recheck the interaction that actually failed

- Distinguish D-pad focus, the remote's virtual pointer, touch/mouse input and
  keyboard-owned suggestions. Exercise the reported route and return path; a
  focus-only test does not prove that the virtual pointer can select a row.
- Check effective CSS specificity and native state styling for normal, focused,
  pressed and disabled controls in the supported theme. A dark-theme declaration
  does not override a more specific light selector; verify contrast where rendered
  evidence is available and label static review as static review.
- Preserve the requested YouTube mobile browsing path and its filters. Do not
  replace it with an embedded player merely to expose another control API.
- Dynamic list refreshes must preserve the focused item by identity when it
  remains available; selection-only updates must not restart content loaders.
- Check horizontal commands in both LTR and RTL layouts; D-pad directions follow
  the visual order, including nested tab-close controls.

- Validate layout attributes against Android resources, not Kotlin property names;
  `TextView.compoundDrawablePadding` is `android:drawablePadding` in XML.

- A background click listener can make a ViewGroup keyboard-focusable through
  FOCUSABLE_AUTO. Mark touch-only containers non-focusable explicitly, and test
  returning from touch/dialog interaction to D-pad navigation, including rows
  outside the initially visible grid.

## Compose TV modernization and release gates

- Consult current official Android Developers guidance for Compose for TV, focus, Back handling, interoperability, Baseline Profiles and Macrobenchmark before adopting a platform pattern. Record the consulted version/date in the PR; never assume the latest release is compatible with AGP, Kotlin, minSdk or the TV Material stack.
- Use the newest compatible **stable** dependency versions after checking release notes and transitive compatibility. Update the version catalog and lockstep integrations together; experimental versions require a demonstrated need and an explicit risk assessment.
- Keep one owner of Back navigation in MainActivity/OnBackPressedDispatcher. Compose actions dispatch to the existing browser commands; do not create a competing WebView navigation or fullscreen controller.
- For the top capsule, preserve the current ordering of address, Back/Forward/Reload/Home, shield (including long press), menu, suggestions and tabs. Focusing an address must not open the IME; OK activates editing, Back dismisses the innermost layer, and focus is restored predictably.
- During a bounded migration, retain a View control only until Compose passes equivalent state, accessibility, pointer/D-pad and callback tests. Document its removal condition; never ship a permanent parallel UI. Never recreate WebView due to Compose recomposition; avoid observing high-frequency page state in the entire toolbar.
- Check API 24/28/34/37 CI, emulator D-pad routes, fullscreen exit, tab switching, shield toggling and text suggestions. Baseline Profiles are only coverage; claim a speed or memory improvement after comparative measurements on a representative low-RAM TV.
- For each UI migration, document verified behavioral improvements, the exact tests run and unverified device-specific behavior; avoid cosmetic-only commits that leave the new UI disconnected.

## Verified migration regression: 2026-11-05

- The hybrid Compose toolbar compiled but CI run 42033562413 failed 19 of 533 unit tests, including BrowserNavigationTest, BrowserUiSnapshotTest and ToolbarStateTest. Treat this as a functional regression, not a transient CI failure; diagnose test assertions and restore behavior before merging.
- Do not hide legacy buttons and redirect Compose clicks through `performClick()` without first updating the focus, navigation, editing, accessibility and test contracts. A hidden View cannot remain the authoritative focus target; expose commands through explicit activity callbacks and one coherent state owner.
- During View/Compose interoperability, verify D-pad movement across the address field, Compose buttons, tab strip and suggestions in both directions; verify Back/IME/fullscreen and the enabled/disabled history state. Existing navigation and snapshot tests are acceptance gates, not tests to delete to make CI green.
- Before declaring a migration integrated, require passing targeted tests, the complete CI suite and an emulator D-pad path. A source-level mount is not equivalent to a verified TV user interaction.

## Single-architecture migration rule (owner decision, 2026-11-06)

- Read `docs/NATIVE_UI_MODERNIZATION_POLICY.md` before changes. Target one Compose UI, not legacy Views controlled indirectly by Compose.
- Iconography: choose a verified compatible Material Symbols Rounded source, one typed catalog and one renderer. Do not call a third-party Compose font wrapper official, and do not accept `@DrawableRes`-to-ligature mapping or API-split XML/font rendering as the final design.
- An official vector resource can be a valid rendering asset; deleting XML by extension is not the objective. Remove obsolete assets only when no supported code path references them.
- Before replacing UI, inventory all callers, callbacks, focus transitions and supported API levels. Replace the implementation and remove its obsolete counterpart in the same completed migration, with tests; do not layer new technology indefinitely.
- Evaluate stable AndroidX Compose/TV libraries against the current Gradle, Kotlin, minSdk and release notes. Preserve WebView, adblock, private state, downloads, bookmarks and fullscreen behavior.
- Measure Baseline Profile coverage and Macrobenchmark frame timing independently; neither CI green nor profiles alone proves latency or memory gains.

## Compose TV stack and pointer input (verified 2026-11-07)

- TV Material 3 (`androidx.tv:tv-material`) is the only Material library: `VireoTvTheme`, `Text`, `Icon` and typography come from `androidx.tv.material3`. Do not reintroduce `androidx.compose.material3`; remove a Compose artifact when no import uses it.
- Interactive controls use Foundation `clickable`/`combinedClickable` with an interaction source and `TvControlColors`: it handles D-pad OK, touch, mouse and pointer clicks and reports hover. TV Material 1.1.0 clickable `Surface`/`ListItem` react only to D-pad Enter and accessibility (see `SurfaceClickableUtils.tvClickable`), which fails pointer support; a Robolectric `performClick` test exposes it.
- User-visible Compose text and content descriptions come from `stringResource`; a literal English string in a composable is a localization regression.
- Pointer remotes and mice (Google TV, I/O 2026): native controls handle hover, scroll and clicks without prior D-pad focus. Declare `android.software.leanback.supports_touch` as application `<meta-data>`, as in the [official guide](https://developer.android.com/blog/posts/increasing-app-discovery-and-engagement-on-google-tv). Test mouse/touch injection and rendered hover separately from semantic `performClick`.
- A unified Compose host contains multiple focus regions: its View-level `hasFocus()` cannot identify the tab row. Report that row's focus boundary explicitly before intercepting Down at window level. Tab activation must consume the clicked index independently of any preceding focus event; preview focus must not change the live tab.
- Each migrated screen deletes its XML layouts, adapters and obsolete resources in the same change, marks them `ELIMINADO` in `docs/audit/SOURCE_INVENTORY.tsv`, and adds Compose tests on Robolectric (`ui-test-junit4`, `setInTouchMode(false)`) for order, initial focus, D-pad movement, single activation and Back.
- A long-lived `pointerInput` handler must read changing callbacks/state through `rememberUpdatedState` or restart with the relevant keys. Prime pointer input before changing state in regression tests: handlers start lazily, so a first tap after a D-pad change can miss a stale capture.
