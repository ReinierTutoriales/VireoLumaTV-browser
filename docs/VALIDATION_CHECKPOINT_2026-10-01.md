# Vireo validation checkpoint — 2026-10-01

This document freezes the state and next validation steps before any further functional changes.

## Frozen baseline

- Repository: `ReinierTutoriales/tv-bro`
- Integration branch under test: `fixes/audit`
- Exact commit: `c339fcfb848593491a683f8aeec6efb50d73dadb` (`c339fcf`)
- Commit: `Merge pull request #39 from ReinierTutoriales/cleanup/vireo-internal-names`
- CI run: #193, run ID `36809338873`, successful.
- Test artifact: `vireo-release`, artifact ID `11139605653`.
- Artifact expiry: **2026-10-08**.
- The #193 release artifact is release/minified but signed with the shared debug key. It is for physical release/R8 validation, not the definitive production release.

If the artifact expires before physical validation, rerun CI with `workflow_dispatch` on this exact baseline and use the regenerated `vireo-release`. Do not change code just to regenerate the artifact.

## Rule before physical validation

Do not modify `fixes/audit` until the physical Vireo APK smoke test and incognito reproduction have been completed. Documentation/preparation may live on separate branches.

## onn validation order

### 1. Vireo smoke test

Install the #193 `vireo-release` APK and verify:

- package: `com.reiniertutoriales.vireobrowser`
- release build is not debuggable
- launcher name: Vireo Browser
- launcher icon
- TV app-row banner
- Settings / Version attribution still says it is based on TV Bro source code
- normal navigation
- tabs
- one download

Old TV Bro and Vireo may coexist because their application IDs differ.

### 2. Incognito isolation reproduction

Potential release-blocking privacy issue.

Static finding in `MainActivityViewModel.prepareSwitchToIncognito()`:

```kotlin
if (incognitoWebViewData.exists()) {
    Log.i(TAG, "Looks like we already in incognito mode")
    return
}
WebView.setDataDirectorySuffix("incognito")
```

Directory existence is not equivalent to "the suffix has already been configured in this process". A stale `app_webview_incognito` directory from a previous incognito process can therefore cause a newly created `:incognito` process to skip `WebView.setDataDirectorySuffix("incognito")`.

Initialization audit on `c339fcf` found no WebView initialization before `prepareSwitchToIncognito()`. `TVBro.initWebEngineStuff()` registers the provider but does not create a WebView. Its CookieManager is `java.net.CookieManager`, not `android.webkit.CookieManager`.

#### Physical reproduction

1. In normal mode, sign in to a test website.
2. Enter incognito and first verify that the normal authenticated session is not visible.
3. Press Home without leaving incognito.
4. Terminate the app/process for the reproduction, e.g.:
   `adb shell am force-stop com.reiniertutoriales.vireobrowser`
5. Reopen Vireo.
6. Check which process is running:
   `adb shell ps -A | findstr vireobrowser`
7. Repeat the process check if necessary. PIDs changing without user action can indicate process bouncing.
8. Search release logcat by message text, not tag:
   `adb logcat -d -v time | findstr "Looks like we already in incognito"`
9. Check whether the normal authenticated session is visible in the reopened incognito browser.
10. Preserve logcat. System `ActivityTaskManager` process-start messages can help identify process bouncing.

Important: release R8 removes the relevant `Log.d` calls in MainActivity, and `MainActivityViewModel::class.java.simpleName` may be obfuscated. Therefore do not depend on the MainActivity/ViewModel log tags or on the removed debug messages.

#### Interpretation

- A new `:incognito` PID plus `Looks like we already in incognito mode` confirms execution of the defective branch.
- If the normal authenticated session is also visible, the privacy impact is physically demonstrated.
- If Vireo reopens in normal mode, this may instead expose the separate `MODE_MULTI_PROCESS` SharedPreferences problem; it does not disprove the suffix bug.
- Repeated process starts/PID changes without intervention may indicate the `onCreate` XOR guard is bouncing between processes because of stale `incognitoMode` reads.

`Config.incognitoMode` uses synchronous `commit()` before `switchProcess()`, so there is no apply/write race in the writing process. Cross-process visibility remains the concern because `MODE_MULTI_PROCESS` is obsolete/unreliable.

#### Patch design only if confirmed

Keep this separate from any `MODE_MULTI_PROCESS` fix.

- Use a **static/per-process** in-memory indicator (companion object or file-level state), not a MainActivityViewModel instance field.
- On the first incognito initialization in each new process, synchronously remove stale incognito WebView data/cache directories using the same paths covered by current incognito cleanup, including the lowercase cache-name variant.
- Perform that cleanup before any WebView initialization and before setting the suffix.
- Call `WebView.setDataDirectorySuffix("incognito")`.
- Mark the static flag configured only after successful setup.
- Subsequent Activity/ViewModel recreation in the same process must not call `setDataDirectorySuffix()` again; doing so after WebView initialization can throw `IllegalStateException`.
- Do not reuse asynchronous `clearIncognitoData()` directly for startup cleanup because it can race WebView opening the directory.

Normal exit from incognito is already ordered defensively: WebStorage/cookies/cache are cleared, tabs are closed, `clearIncognitoData().join()` completes, then `incognitoMode=false` is committed and the process is switched.

If the release behavior is ambiguous, only then add diagnostic `Log.i` messages with literal stable tags. Do not instrument preemptively.

## 3. W3 performance/behavior validation

Run only after the Vireo smoke test and, if reproduced, after the incognito privacy fix/regression test.

Scope remains:

- `renderThumbnail`
- `onPause/saveTab`
- fullscreen

Do not reopen lifecycle/FaviconsPool optimization without new evidence.

The prepared W3 script must use:

- package: `com.reiniertutoriales.vireobrowser`
- Activity component:
  `com.reiniertutoriales.vireobrowser/com.phlox.tvwebbrowser.activity.main.MainActivity`

The namespace remains `com.phlox.tvwebbrowser`; the package/namespace migration was intentionally abandoned.

For `onPause/saveTab`, measure repeated samples. Decision criteria:

- median <30 ms: leave current `runBlocking` unchanged
- 30–50 ms: inspect distribution/pattern; no automatic change
- consistently >50 ms: redesign persistence asynchronously and serialize it at process/application level
- an isolated spike is not sufficient evidence

Room DAO methods are suspend functions; do not describe their database computation as running on Main. `runBlocking` blocks Main while waiting for the sequence to complete.

`renderThumbnail` ultimately calls synchronous View drawing. Do not blindly move `super.draw(canvas)` to a background dispatcher. If measurement proves it costly, investigate dimensions, frequency, caching, or scheduling.

Fullscreen currently has duplicate protection in both WebViewEx and WebViewWebEngine. Validate behavior before changing it.

## Other confirmed logical issues — after privacy/W3 priority

### Current-tab renderer recovery can restore stale state

If the renderer dies while the current tab has navigated since its last pause, recovery can prefer an old `savedState` over the current `tab.url`. This can restore an older page/history instead of the page visible immediately before renderer death.

Background tabs are less affected because their state is captured when leaving them.

Do not patch as part of W3. A future recovery-specific design should prioritize the current recovery URL deterministically and avoid callbacks mutating the comparison target during restore.

### Tab selection persistence is not atomic

`TabsModel.saveTab()` performs:

1. `unselectAll()`
2. WebView-state file persistence
3. tab update/insert

If the process dies between 1 and 3, the database can contain no selected tab. Startup then falls back to the first tab. This is a consistency issue, not a crash/data-loss catastrophe.

A future persistence redesign should consider making the Room selection update transactional, separately from W3 timing work.

## Release signing

The #193 artifact is not the definitive production-signed APK.

Before an official release, create and safely back up the definitive Vireo keystore and configure:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Never commit the keystore.

When moving from the #193 debug-key-signed release test APK to the definitive production key, uninstall the test Vireo build first. Android will otherwise reject the update because the signatures differ, and uninstalling removes that test build's local data.

## Compatibility/attribution constraints

Do not rename/remove without a concrete compatibility/legal reason:

- `LICENSE.md`
- TV Bro source attribution
- `URL_TV_BRO_SOURCES`
- `based_on_tv_bro_sources`
- `TV_BRO_UA_PREFIX`
- legacy `"TV Bro/1.0 "` checks
- public Intent key `com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB`

The internal namespace/package remains `com.phlox.tvwebbrowser`. Vireo identity is provided by the application ID, visible branding, assets, metadata, and release artifacts.

## Next action

Resume on the onn with **Step 1: Vireo smoke test**. Do not merge speculative fixes before obtaining those physical results.
