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

## Additional confirmed app-layer incognito leaks

These are independent of the WebView data-directory suffix problem and can occur even after a normal incognito exit.

### Favicon/host persistence

Local audit of `c339fcf` found that `FaviconsPool.saveFavicon` does not gate persistence on incognito mode. Visiting a site in incognito can therefore:

- write its favicon under `cacheDir/favicons/<hash>.png`;
- insert/update the site's hostname in the shared `hosts` table in `main.db`.

The normal incognito cleanup does not remove the `hosts` rows or favicon cache. This exposes visited incognito domains beyond the incognito session.

Physical verification should use the #193 `vireo-debug` build because release cannot use `run-as`. **Do this only after all release/R8 tests are complete.** The #193 debug and release artifacts use the same application ID and shared debug signing key, so `adb install -r` of debug replaces release while preserving its app data.

Do not redirect binary database output with PowerShell `>`; PowerShell can text-encode/corrupt binary output. Room uses WAL, so copy the database together with its WAL/SHM companions. First stop the app to avoid copying while it is writing, then use `adb exec-out` with `cmd /c` redirection:

```
adb shell am force-stop com.reiniertutoriales.vireobrowser
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db > main.db"
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-wal > main.db-wal"
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-shm > main.db-shm"
```

Keep all three files together when opening/inspecting the database so SQLite can account for WAL content. A `main.db`-only copy can miss recent `hosts` rows and produce a false negative.

After visiting a unique test domain only in incognito and exiting normally, inspect the exported database set for that hostname. Also inspect favicon persistence if needed.

Future fix invariant: while incognito is active, favicons may remain in the in-memory FaviconsPool cache for the live session but must not be persisted to disk or written to the shared `hosts` table.

### Download-history persistence

Local audit found that `FileDownloadTask` writes download records through `downloadDao` without an incognito-mode guard. Consequently an incognito download can remain visible in Vireo's normal Downloads list after returning to normal mode.

Physical verification:

1. Enter incognito.
2. Download a uniquely named test file.
3. Exit incognito normally.
4. Open Downloads in normal mode.
5. Record whether the incognito download appears.

The product decision is **still pending**. The downloaded file is expected to remain on storage, but there are two implementation models for Vireo's internal Downloads UI:

- **(a) Never persist the incognito download.** This maximizes privacy, but current download IDs originate from the database insert and are used by active-download/progress UI. Implementing this correctly would require a separate/synthetic ID strategy and changes to list/progress tracking.
- **(b) Candidate design: persist the download only for the live incognito session, mark it as incognito, show normal progress/list behavior, then delete its database metadata on normal incognito exit and on startup sanitation of a new incognito process.** This aligns with the P0-4 session-cleanup boundary but requires a Room schema migration.

Do not choose between (a) and (b) before the P0 implementation review. Option (b) is the current candidate, not an approved implementation.

#### Room prerequisite if option (b) is selected

The current repository does not version exported Room schema JSON. `AppDatabase` has schema export disabled/commented, while the project compiles Room with KSP; a classic annotation-processor `room.schemaLocation` argument is not sufficient for KSP schema export.

Before implementing database version 20, create a **separate prerequisite commit** on the then-current version-19 database that:

- enables Room schema export;
- configures the Room KSP processor with `ksp { arg("room.schemaLocation", ...) }` (using the project's actual Gradle/KSP syntax);
- generates the exact version-19 schema from the unchanged v19 database;
- versions that v19 JSON in the repository;
- makes no schema change in that prerequisite commit.

Then the P0 database change may add an `incognito` marker to downloads, bump Room 19 -> 20, and include a `MigrationTestHelper` test using the versioned schemas. Also physically test installing the v20 APK over a v19 installation that already contains data.

There is **no automatic downgrade path**. The current Room builder registers forward migrations and does not opt into destructive downgrade fallback. After a test APK upgrades the database to v20, installing/running an older v19 APK against that data can fail at database open. To return to a v19 build during testing, uninstall/clear app data first. Treat downgrade-by-install as unsupported; do not add destructive downgrade behavior merely for test convenience.

### Incognito tab/state remnants after abnormal termination

If the incognito process dies without the normal exit path, incognito tab rows and their persisted `wvstates/` and `tabthumbs/` data can remain and be restored by a later incognito process.

Privacy policy for Vireo: an incognito session is process-lifetime scoped. A new incognito process must not restore private tabs/state/thumbnails left by a previous dead incognito process.

The future startup cleanup for the suffix defect should therefore be designed as one pre-WebView incognito-session sanitation step covering:

- stale incognito WebView data/cache paths;
- stale incognito tab database rows;
- their WebView state files;
- their thumbnails.

This must happen before the new incognito WebView session begins. Avoid deleting shared normal-mode state.

### Incognito patch acceptance criteria

Before W3, the eventual privacy PR should demonstrate all of these:

- normal authenticated WebView state is not visible in incognito;
- a new incognito process always configures the incognito WebView suffix exactly once per process;
- Activity/ViewModel recreation in the same process does not reconfigure the suffix;
- incognito-only hostnames are not persisted in `hosts`;
- incognito favicons are not persisted to disk;
- downloaded files remain, but incognito downloads do not enter Vireo's persistent Downloads history;
- abnormal incognito process death does not cause private tabs, state files, or thumbnails to be restored in the next incognito process;
- returning to normal mode does not expose incognito browsing metadata;
- normal-mode favicon, download-history, tab persistence, and WebView behavior remain unchanged.

Do not combine a speculative `MODE_MULTI_PROCESS` redesign into this PR unless the physical test independently reproduces that problem.

## Security audit findings before W3

### Explicit Intent can inject non-web schemes into the current tab

Local audit of `c339fcf` found that exported `MainActivity` accepts external VIEW intents and `handleIntent()` can pass `intent.data.toString()` to navigation when the public legacy `EXTRA_OPEN_IN_SAME_TAB` extra is true. The manifest's HTTP/HTTPS intent filter constrains implicit matching; it is not a substitute for validating data received by an explicitly addressed exported Activity.

Physical reproduction on an already-open ordinary web page:

```
adb shell am start -n com.reiniertutoriales.vireobrowser/com.phlox.tvwebbrowser.activity.main.MainActivity -a android.intent.action.VIEW -d "javascript:alert(document.domain)" --ez com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB true
```

If the current page executes the JavaScript and displays its domain, treat this as confirmed external-script injection.

Future fix invariant: every externally supplied navigation URI handled through this exported entry point must be allowlisted to the intended web schemes (`http` and `https`) before it reaches WebView navigation. Reject `javascript:`, `data:`, `file:`, `content:`, `intent:`, and any unknown/non-web scheme. Preserve the existing public extra name for compatibility; do not rename it as part of the fix.

Regression tests should cover at least:

- explicit HTTP URL accepted;
- explicit HTTPS URL accepted;
- `javascript:` rejected;
- `data:` rejected;
- `file:` rejected;
- `content:` rejected;
- `intent:` rejected;
- malformed/no-scheme input cannot reach WebView as an external navigation;
- same-tab behavior still works for valid HTTP/HTTPS callers.

### Global JavaScript interface exposure

`WebViewEx` exposes the `TVBro` JavaScript interface to web content. Local audit identified two methods needing tighter origin/context authorization:

- `takeBlobDownloadData` can accept page-provided base64 data and initiate a download path without an equivalent internal-page guard. Besides unsolicited downloads, unbounded base64 transfer into Java creates a memory-exhaustion risk on constrained TV hardware.
- `lastSSLError(true)` exposes the last certificate-error details without restricting the caller to the internal SSL-error page.

Do not implement the blob fix as a simple blanket disable: legitimate `blob:` downloads must continue working.

Future design requirements for blob downloads:

- bind acceptance to a browser-controlled pending blob-download request or equivalent short-lived capability created by the legitimate download flow;
- make that capability single-use and short-lived;
- reject unsolicited calls from arbitrary pages/iframes;
- enforce a defensible decoded-size limit before allocating/decoding an arbitrarily large payload;
- clear pending authorization on navigation/tab destruction as appropriate;
- preserve legitimate blob downloads after the change.

Future invariant for `lastSSLError`: certificate details are exposed only in the browser-controlled SSL error-page context that needs them.

### Build-order rule for physical validation

Run **all release/R8 behavior tests first** using `vireo-release`: smoke test, incognito suffix/session isolation, download-history leak, explicit-Intent injection, and legitimate blob-download capture. Only after those are complete, install `vireo-debug` over release with `adb install -r` to perform `run-as` database inspection. Because both #193 artifacts share application ID and signing key, this replacement preserves the accumulated app data but changes the tested build to debug.

Do not switch to debug early and then treat subsequent behavior as release/R8 validation.

### Security test order on onn

After the incognito tests and before W3:

1. Run the explicit-Intent `javascript:` reproduction above.
2. If confirmed, include URI scheme validation in the pre-W3 security/privacy patch.
3. Exercise a legitimate normal HTTP/HTTPS external VIEW intent after the fix.
4. Test at least one legitimate blob download before designing/finalizing the JS-interface restriction, so the existing required flow is captured.
5. Do not merge speculative JS-interface changes until that legitimate flow has a regression test.

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
