---
name: tv-ui
description: Use for UI, focus, navigation, or layout work. Ensures polished Android TV remote (D-pad) experience and low-memory friendliness.
---

# TV UI & Remote

- Optimize for Android TV remote: proper focus handling, D-pad navigation, clear focus indicators, and accessibility.
- Prefer native Android / WebView UI patterns. Keep layouts simple and lightweight.
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
- During hybrid migration, retain the stable View control until Compose has equivalent state, accessibility, pointer/D-pad and callback behavior. Never recreate a WebView due to Compose recomposition; avoid observing high-frequency page state in the entire toolbar.
- Check API 24/28/34/37 CI, emulator D-pad routes, fullscreen exit, tab switching, shield toggling and text suggestions. Baseline Profiles are only coverage; claim a speed or memory improvement after comparative measurements on a representative low-RAM TV.
- For each UI migration, document verified behavioral improvements, the exact tests run and unverified device-specific behavior; avoid cosmetic-only commits that leave the new UI disconnected.

## Verified migration regression: 2026-11-05

- The hybrid Compose toolbar compiled but CI run 42033562413 failed 19 of 533 unit tests, including BrowserNavigationTest, BrowserUiSnapshotTest and ToolbarStateTest. Treat this as a functional regression, not a transient CI failure; diagnose test assertions and restore behavior before merging.
- Do not hide legacy buttons and redirect Compose clicks through `performClick()` without first updating the focus, navigation, editing, accessibility and test contracts. A hidden View cannot remain the authoritative focus target; expose commands through explicit activity callbacks and one coherent state owner.
- During View/Compose interoperability, verify D-pad movement across the address field, Compose buttons, tab strip and suggestions in both directions; verify Back/IME/fullscreen and the enabled/disabled history state. Existing navigation and snapshot tests are acceptance gates, not tests to delete to make CI green.
- Before declaring a migration integrated, require passing targeted tests, the complete CI suite and an emulator D-pad path. A source-level mount is not equivalent to a verified TV user interaction.
