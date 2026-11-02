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
