# Vireo audit master index — 2026-10-01

This is the compact source-of-truth index for work audited against baseline `c339fcfb848593491a683f8aeec6efb50d73dadb` (`c339fcf`). Detailed procedures and acceptance criteria live in `VALIDATION_CHECKPOINT_2026-10-01.md`.

## Baseline and branch discipline

- Physical-test baseline: `fixes/audit@c339fcf`.
- CI #193 passed on this exact commit.
- #193 `vireo-release` expires 2026-10-08; regenerate with workflow_dispatch on the same commit if necessary.
- Do not modify `fixes/audit` before physical smoke/privacy/security validation.
- Documentation work is isolated on `docs/onn-validation-checkpoint`.
- Future merges should use Rebase and merge unless there is a specific reason not to.

## Priority gates

### P0 — privacy/security before W3

1. Incognito WebView suffix can be skipped in a new process when stale `app_webview_incognito` exists.
2. Incognito favicon/hostname persistence leaks visited domains to shared app storage/database.
3. Incognito downloads persist in Vireo's normal download-history list.
4. Abnormally terminated incognito sessions can leave tab rows, WebView state files, and thumbnails that a later session restores.
5. Exported MainActivity can pass an explicitly supplied non-web URI such as `javascript:` to current-tab navigation unless scheme validation is added.
6. Global `TVBro` JavaScript interface exposes blob-download ingestion and SSL-error details more broadly than intended. Blob protection requires preserving legitimate blob downloads and adding bounded, browser-authorized acceptance.

### P1 — W3 measured performance/behavior

- `renderThumbnail`: measure before changing; View drawing must not simply be moved off Main.
- `onPause/saveTab`: measure total Main-thread blocking. Median <30 ms => keep; 30–50 ms => inspect; consistently >50 ms => redesign serialized application-level persistence.
- fullscreen: validate existing duplicate protection and behavior before modifying.

### P2 — confirmed logical consistency/recovery issues

- Renderer death on the active tab can restore stale `savedState` instead of the latest current URL.
- `saveTab` selection persistence has a non-atomic `unselectAll -> state file -> update/insert` window; process death can leave no selected tab.

## Audited as currently acceptable / no action without new evidence

- History is suppressed in incognito by existing guards.
- Incognito tab rows are separated by the `incognito` column during normal operation.
- Normal incognito exit clears WebView data/cookies/cache and closes incognito tabs in an ordered path.
- MainActivity is the intentionally exported browser entry point; incognito/download/service/FileProvider components are not exported.
- WebView file access defaults are not currently an identified issue; content access is disabled.
- Mixed content defaults to never allow.
- Automatic JavaScript windows are disabled.
- `reloadWithSslTrust` is restricted to the local error-page flow.
- Current fullscreen implementation already has duplicate-view/callback protection.
- Current lifecycle/FaviconsPool performance work is not reopened without evidence. The favicon item above is a privacy-persistence issue, not a performance finding.
- Release workflow and signing-variable wiring were audited as correct.
- Manifest/FileProvider authority follows `${applicationId}.provider`.
- Changing applicationId to Vireo gives it a separate Android sandbox; no automatic migration of TV Bro data is expected.

## Decisions already made

- Vireo application ID: `com.reiniertutoriales.vireobrowser`.
- Internal namespace/packages stay `com.phlox.tvwebbrowser`; package migration was abandoned because benefit is cosmetic and it creates reflection/R8/upstream risk.
- Preserve public legacy Intent key `com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB`.
- Preserve TV Bro attribution/compatibility constants and legacy UA checks.
- Incognito download policy: keep the downloaded file, but do not persist an incognito download in Vireo's internal download-history list.
- Incognito favicon policy: in-memory use is allowed during the live session; no incognito hostname/database or favicon-disk persistence.
- Incognito recovery policy: private session state is process-lifetime scoped; a new incognito process must not restore stale private tabs/state/thumbnails from a previous dead process.
- Do not redesign `MODE_MULTI_PROCESS` unless physical testing independently demonstrates the cross-process preference problem.
- Do not weaken/disable legitimate blob downloads merely to close the JS-interface exposure.

## Release/signing facts

- #193 `vireo-release` is minified/release but signed with the shared debug key.
- It is valid for release/R8 behavior testing, not official distribution.
- Definitive release requires a safely backed-up Vireo keystore and GitHub secrets:
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`
- A #193 test build must be uninstalled before installing an APK signed by the definitive key because Android signatures will differ.

## Physical validation sequence

The #193 debug and release artifacts have the same application ID and shared debug signature. Installing debug with `adb install -r` replaces release and preserves its data. Therefore all release/R8 behavior tests must run before switching to debug.

1. On `vireo-release`: Vireo release smoke test.
2. On `vireo-release`: incognito suffix/process/session isolation test.
3. On `vireo-release`: incognito download-history leak test.
4. On `vireo-release`: explicit-Intent `javascript:` injection reproduction.
5. On `vireo-release`: capture at least one legitimate blob-download flow before changing the JS bridge.
6. Only now install `vireo-debug` over release with `adb install -r`; accumulated app data is preserved.
7. On `vireo-debug`: inspect incognito host/favicon persistence with `run-as`.
8. Database extraction must be binary-safe and WAL-aware: force-stop first and copy `main.db`, `main.db-wal`, and `main.db-shm` with `adb exec-out` plus `cmd /c` redirection. Never use PowerShell `>` for these binary files.
9. Implement/verify the privacy-security block based on evidence.
10. Only then run W3.
11. Address renderer recovery and tab-selection atomicity after the higher-priority block unless new evidence raises their severity.

## Do-not-touch compatibility/legal list

Do not casually rename/remove:

- `LICENSE.md`
- TV Bro source attribution
- `URL_TV_BRO_SOURCES`
- `based_on_tv_bro_sources`
- `TV_BRO_UA_PREFIX`
- legacy `"TV Bro/1.0 "` checks
- `com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB`

## Detailed companion document

See `docs/VALIDATION_CHECKPOINT_2026-10-01.md` for exact adb commands, interpretations, patch invariants, and W3 thresholds.
