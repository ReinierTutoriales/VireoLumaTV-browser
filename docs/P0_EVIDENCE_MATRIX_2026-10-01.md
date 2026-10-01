# Vireo P0 evidence matrix — baseline c339fcf

Use this matrix during the physical onn session. It maps each pre-W3 privacy/security finding to evidence and the exact decision that follows. Do not modify `fixes/audit` until the applicable physical evidence is collected.

| ID | Finding | Static status | Physical evidence | Decision |
|---|---|---|---|---|
| P0-1 | Incognito WebView suffix skipped when stale incognito directory exists | Static defect identified | Release: new `:incognito` process plus `Looks like we already in incognito mode`; strongest impact evidence is normal authenticated state becoming visible | If defective branch reproduces, fix per-process suffix state and synchronous stale-data sanitation before WebView |
| P0-2 | Incognito hostname/favicon persisted | Static leak identified | After release session, install debug over it; binary/WAL-safe DB extraction shows unique incognito hostname in `hosts`; inspect favicon disk cache if needed | Prevent incognito disk favicon and shared `hosts` persistence; keep live in-memory favicon only |
| P0-3 | Incognito download appears in persistent Downloads history | Static leak identified | Release: uniquely named incognito download remains visible after normal incognito exit | Keep downloaded file; prevent persistent internal download-history record |
| P0-4 | Dead incognito process leaves restorable tabs/state/thumbnails | Static persistence issue identified | Release: terminate incognito abnormally, restart, observe private tabs/state restored | Treat incognito as process-lifetime session; sanitize stale private tab rows/state/thumbnails before new session |
| P0-5 | Explicit Intent can navigate current WebView to non-web scheme | Static entry-point defect identified | Release: explicit `javascript:alert(document.domain)` with same-tab extra executes in already-open web page | Allowlist externally supplied navigation to HTTP/HTTPS before WebView; preserve legacy extra name |
| P0-6a | Blob JS bridge accepts page-provided data too broadly | Static exposure identified; legitimate flow must be preserved | Release: capture a legitimate blob download path before changing behavior; later regression must prove unsolicited bridge calls are rejected | Add short-lived/single-use browser-controlled authorization plus bounded payload size; do not blanket-disable blob downloads |
| P0-6b | JS bridge exposes last SSL error too broadly | Static exposure identified | Verify internal SSL error page still needs/accesses this method; test ordinary page cannot obtain it after fix | Restrict SSL error details to browser-controlled SSL error-page context |
| P0-7 | Cross-process `MODE_MULTI_PROCESS` preference visibility | Risk only; not yet established as an independent defect | Release: wrong-mode restart or repeated process bouncing/PID churn after mode switch/restart | Patch separately only if independently reproduced; do not fold speculative redesign into incognito suffix fix |

## Build order

All behavior evidence must be collected on `vireo-release` first. Only after those tests are complete install `vireo-debug` over it with `adb install -r` for database inspection. The #193 artifacts share application ID/signature, so data is preserved but the installed build changes to debug.

## SQLite extraction rule

Force-stop before copying. Use binary-safe `adb exec-out` and `cmd /c` redirection. Copy all three files when present:

```
adb shell am force-stop com.reiniertutoriales.vireobrowser
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db > main.db"
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-wal > main.db-wal"
cmd /c "adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-shm > main.db-shm"
```

Never use PowerShell `>` to capture these binary files.

## Exit criteria for P0

W3 remains blocked until confirmed P0 defects have fixes with regression coverage and the release build passes the relevant physical retests. A finding that does not reproduce physically must be recorded with the exact test conditions; static defects should not be silently marked resolved merely because one runtime scenario did not demonstrate impact.
