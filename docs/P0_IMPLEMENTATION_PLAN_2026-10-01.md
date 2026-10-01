# Vireo P0 implementation plan — baseline c339fcf

This document defines the intended decomposition of the pre-W3 privacy/security work. It is a plan, not authorization to modify `fixes/audit`. Physical evidence remains the gate.

## Principle

Do not implement all P0 findings as one large patch. Keep independently reviewable changes separate, preserve a green CI state between them, and rerun the physical regression relevant to each change.

## Proposed sequence

### P0-A — External navigation scheme validation

Scope:
- Validate externally supplied navigation data at the exported MainActivity entry point.
- Allow only HTTP/HTTPS into the browser navigation path.
- Preserve `com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB` for compatibility.
- Do not alter internal browser navigation semantics unnecessarily.

Tests:
- HTTP accepted.
- HTTPS accepted.
- Same-tab HTTP/HTTPS still works.
- `javascript:`, `data:`, `file:`, `content:`, `intent:`, malformed and scheme-less external inputs do not reach WebView navigation.
- Repeat the physical explicit-Intent reproduction after the patch.

This is the smallest P0 fix and should remain independent of incognito/Room work.

### P0-B — Incognito favicon/hostname persistence

Scope:
- In incognito, allow favicon use in live memory only.
- Do not persist incognito favicon files.
- Do not insert/update incognito hostnames in the shared `hosts` table.
- Normal-mode behavior remains unchanged.

Tests:
- Unique incognito hostname absent from persisted `hosts`.
- No corresponding incognito favicon disk persistence.
- Normal hostname/favicon persistence still works.

Database inspection must follow the binary/WAL-safe procedure in the checkpoint.

### P0-C — Incognito process/session sanitation and WebView suffix

Scope, only if physical evidence confirms the defective path:
- Replace directory-existence-as-process-state with a static/per-process configuration state.
- Before first incognito WebView initialization in a new process, synchronously sanitize stale incognito WebView data/cache.
- Configure `WebView.setDataDirectorySuffix("incognito")` exactly once per process.
- Sanitize stale incognito tab rows and their state/thumbnails according to the process-lifetime privacy policy.
- Do not redesign `MODE_MULTI_PROCESS` unless separately reproduced.

Tests:
- Normal authenticated state absent from incognito.
- New incognito process cannot skip suffix setup because a stale directory exists.
- Activity/ViewModel recreation in the same process does not call suffix setup again.
- Abnormal incognito termination does not restore stale private tabs/state/thumbnails into the next incognito process.
- Normal-mode tab/state data remains intact.

### P0-D0 — Room v19 schema-export prerequisite

Create this only if download design (b) is selected.

This commit must make **no database schema change**.

Scope:
- Enable Room schema export.
- Configure schema location for KSP, not only the classic annotation processor.
- Generate and version the exact v19 schema from the unchanged database.
- Verify generated schema corresponds to database version 19.

Purpose:
- Establish the historical JSON required for official Room migration testing before v20 exists.

This prerequisite should be reviewed/merged separately from the actual migration.

### P0-D1 — Incognito download session metadata (candidate design b)

Only after P0-D0 and final product decision.

Scope:
- Add an incognito marker to persisted download metadata.
- Bump Room 19 -> 20.
- Add explicit 19 -> 20 migration.
- Add `MigrationTestHelper` coverage using versioned schemas.
- Preserve existing download IDs/progress/list behavior during the live incognito session.
- Remove incognito download metadata on normal incognito exit.
- Remove stale incognito download metadata during new-incognito-process sanitation.
- The downloaded external file itself remains.

Tests:
- Automated migration test from v19 schema/data to v20.
- Physical install of v20 over a v19 onn installation containing representative existing data.
- Existing normal download history survives migration.
- Incognito download appears/progresses during live session if that is the final chosen behavior.
- Incognito download metadata disappears after normal exit and after abnormal-session startup sanitation.
- Normal downloads remain persisted.

Downgrade:
- Unsupported by current Room configuration.
- After a test database reaches v20, uninstall/clear data before running a v19 APK.
- Do not add destructive downgrade fallback for test convenience.

### P0-E — JavaScript bridge hardening

Do not implement until a legitimate blob-download flow has been captured and can be regression-tested.

Blob path:
- Introduce browser-controlled authorization for blob data transfer.
- Authorization should be short-lived and single-use or equivalently scoped.
- Reject unsolicited bridge calls.
- Bound accepted payload size before expensive allocation/decoding.
- Clear authorization when its navigation/tab context is no longer valid.
- Preserve legitimate blob downloads.

SSL-error path:
- Restrict `lastSSLError` details to the browser-controlled SSL error-page context.

Tests:
- Legitimate blob download still succeeds.
- Unsolicited page/iframe bridge call cannot initiate a download.
- Oversized input is rejected without destabilizing the app.
- Authorization cannot be reused outside its intended context.
- Ordinary pages cannot read the last SSL error.
- Internal SSL error page still functions.

## Dependency graph

- P0-A: independent.
- P0-B: independent.
- P0-C: depends on physical incognito reproduction; may later provide the sanitation hook reused by P0-D1.
- P0-D0: depends only on choosing download design (b); must precede D1.
- P0-D1: depends on D0 and should integrate with the incognito sanitation boundary established by C.
- P0-E: depends on capturing the legitimate blob flow first.

## Merge discipline

For each accepted P0 unit:
1. branch from the current integration head;
2. make one focused change;
3. CI/unit tests green;
4. inspect diff for unrelated edits;
5. run the relevant physical regression when required;
6. use Rebase and merge unless there is a concrete reason not to;
7. record the resulting integration SHA and evidence in the audit documents.

Do not merge a later dependent unit while an earlier prerequisite is only present on an unmerged side branch.

## W3 gate

W3 starts only after the confirmed release-blocking P0 findings have been resolved and retested. P0 findings that remain unconfirmed must have their exact physical test result recorded rather than being silently dropped.
