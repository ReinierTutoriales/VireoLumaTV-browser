# Android TV UI audit — 2026-11-04

Status: source-code audit; **not** a hardware UI acceptance test. Baseline: master at 18e8e935. The privacy PR #217 is separate.

## Official standards used

- [Android TV quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality): five-way D-pad navigation, no clipped controls, landscape.
- [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation): reachable controls, predictable directional navigation, visible focus.
- [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system): distinguish focused/selected/pressed states.
- [TV layout](https://developer.android.com/training/tv/playback/compose/layouts): overscan-safe controls and scalable dp/sp sizing.
- [TV accessibility](https://developer.android.com/training/tv/accessibility): TalkBack labels, text scaling and keyboard navigation.

These guidelines are used as acceptance criteria, not as proof that a device passed them. The browser uses Android Views and WebView; adopting Compose solely for novelty is not a demonstrated optimization.

## Code-level inspection

| Surface | Files inspected | Findings and status |
| --- | --- | --- |
| Address bar | `BrowserControls.kt`, `browser_controls.xml` | Focusing address does not open IME; click explicitly enters edit mode. Directional focus is declared. Device-level IME and suggestion transitions remain to test. |
| Suggestions | `AddressSuggestionsView.kt` | Click activates suggestion, focused URL is preserved across updates, separate native pointer path exists. Needs remote + IME integration test for list updates and dismissal. |
| Tabs | `TabChips.kt`, `TabStrip.kt`, `tab_chip.xml` | 48dp close hit target, D-pad left/right to close, long-press actions preserved, diff updates and recycling. **Fix in this PR:** tab accessibility label now updates on title changes and clears on recycle. |
| Native Home | `NativeHome.kt`, `native_home.xml` | Native search and tiles, dynamic columns, focus return and keyboard handling, lifecycle cleanup. Verify with varied tile counts and display scaling. |
| Pointer | `AddressSuggestionsView.kt`, `BrowserWebView.kt` | Pointer handling exists in WebView and suggestion list. Cross-surface focus/selection and diagonals require device input tests. |
| Video | `BrowserChromeClient.kt`, `BrowserWebView.kt` | Fullscreen and WebView lifecycle have dedicated paths; YouTube/non-YouTube playback quality and fullscreen controls cannot be certified by static review. |
| Visual sizing | `dimens.xml`, `tab_chip.xml` | 48dp control size, 48/27dp TV-safe margins on 960x540dp design canvas. Need tests at different font scales, density and overscan. |
| Accessibility | `tab_chip.xml`, `TabChips.kt` | Close control has a string label. Tab rows previously had no explicit dynamic label; addressed in this PR. TalkBack traversal not yet verified. |

## Acceptance matrix for release

1. On API 24, 28, 34 and 37, navigate all visible controls by D-pad, with no dead ends or unexpected IME.
2. With 1, 4, 12 and 30 tabs: focus, preview, activate, close via dedicated X, long-press menu, RTL behavior.
3. From address input: type, select bookmark/history suggestion with OK and pointer, dismiss IME, return focus to content.
4. On Home: zero, one and many tiles; search, edit/move/remove shortcuts, scroll, return to capsule, preserve focus.
5. With video on YouTube and at least one non-YouTube site: enter/exit fullscreen, remote Back, controls, available resolution.
6. At 1.0 and 1.3 font scale and differing display density: no clipping, focus visible, accessible descriptions.
7. Compare startup, frame times, CPU and RSS/PSS on the same Android TV hardware before claiming improved performance.

## Release decision

Source review found a specific tab accessibility-state gap, fixed here. The full UI is **not certified** until the device acceptance matrix and CI are green. No claims of visual screenshots or benchmark results are made.
