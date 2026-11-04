# Security and privacy review — 2026-11-04

Scope: current `master` plus `security/privacy-completion`. This is a source-code review, **not** a penetration test or hardware attestation.

## Official platform constraints

- Android: `addJavascriptInterface` exposes annotated methods to **all frames**, with no authenticated caller origin. https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- AndroidX: `WebViewCompat.addWebMessageListener` supplies `sourceOrigin` and `isMainFrame`; listener origin rules must be selected for each use. https://developer.android.com/reference/androidx/webkit/WebViewCompat
- Android: `loadDataWithBaseURL` with a non-HTTP(S) base has **opaque/null origin**, which must not be trusted as unique. https://developer.android.com/reference/android/webkit/WebView
- Android: `WebView.setDataDirectorySuffix` must precede WebView initialization and separates process-specific data. https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String)

## Reviewed implementation

| Area | Evidence in source | Result / remaining limitation |
| --- | --- | --- |
| JavaScript bridge | `BrowserWebView.kt`, `PageBridge.kt` | Legacy privileged methods removed in PR #215. Synchronous `FilterBridge` is accessible to all frames; supplied URL is syntax-checked, **not caller-authenticated**. This must not be used to grant permissions, trust certificates, or read secrets. |
| Blob messages | `BrowserWebView.kt`, `DownloadMessages.kt` | Listener checks main-frame/source origin and live WebView; lifecycle invalidation implemented. |
| Certificate warning | `BrowserWebView.kt`, `CertificateWarningPage.kt` | Main-frame, active warning, and user activation checked; legacy approval disabled. **Caution:** warning loaded with `file:///android_asset/` base has an opaque origin; do not add a check that treats `null` as a trusted origin. Moving approval to a native dialog or authenticated HTTPS app-asset origin is a future hardening option requiring regression testing. |
| Private WebView | `VireoLumaTVApp.kt`, `IncognitoWebViewData.kt`, manifest | Separate `:incognito` process, API 28+ data suffix, previous directory cleared on private startup. Process termination on private activity finish. **No physical forced-kill verification performed.** |
| Cookies | `CookiePersistence.kt`, `CookieJar.kt` | Normal cookies flushed asynchronously on leaving screen; private flush skipped. This change makes removal + flush non-cancellable once begun; regression test covers cancellation. |
| Permissions | `PagePermissions.kt` | Origin shown; pending requests invalidated; location prompt dismissal denies. Origin displayed to user is not a universal frame authentication primitive. |
| Backup | `AndroidManifest.xml` | `allowBackup=false`; `dataExtractionRules` declared. Device-specific backup/transfer behavior still needs integration verification. |

## Release gates

1. Unit tests, source inventory, Rust/JS checks, Android lint, API 24/28/34/37 startup checks must pass.
2. Confirm private session restart, crash/restart, cookies, storage and permissions on actual Android TV provider versions.
3. Do not claim that filter-bridge caller provenance is solved by validating caller-supplied URLs.
4. Do not remove `largeHeap` or claim memory improvements without before/after measurements.
