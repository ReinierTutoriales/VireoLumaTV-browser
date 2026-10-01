# Vireo PC + onn physical validation runbook — 2026-10-01

This is the single operational checklist for the next physical session. Follow it in order. Do not improvise build order: release tests must finish before replacing the app with debug.

## Fixed baseline

- Repository: `ReinierTutoriales/tv-bro`
- Code under test: `fixes/audit@c339fcfb848593491a683f8aeec6efb50d73dadb` (`c339fcf`)
- CI: run #193, ID `36809338873`
- Artifacts: `vireo-release` first; `vireo-debug` only at the final database-inspection stage.
- #193 artifacts expire 2026-10-08. If expired, rerun CI with workflow_dispatch on exact `c339fcf`; do not change code.
- Package: `com.reiniertutoriales.vireobrowser`
- Main Activity component: `com.reiniertutoriales.vireobrowser/com.phlox.tvwebbrowser.activity.main.MainActivity`
- Both #193 artifacts use the same application ID and shared debug signing key. Installing debug with `adb install -r` replaces release and preserves data.

## Before touching the onn

On the PC:

1. Confirm ADB exists:
   `C:\Users\Reinier\Downloads\platform-tools\adb.exe`
2. Create one evidence directory:
   `C:\Users\Reinier\Downloads\Vireo-P0-20261001`
3. Download `vireo-release` and `vireo-debug` from CI #193. Keep them clearly named.
4. Extract each artifact ZIP if GitHub downloaded it as a ZIP. Identify the actual APK files.
5. Do not install debug yet.
6. Do not uninstall old TV Bro. Its application ID differs and it may coexist with Vireo.

## Stage 1 — Device connection and release installation

Open **Command Prompt (cmd.exe)** for the session. Use PowerShell only when specifically requested; database binary redirection later must use cmd-compatible redirection.

Run:

```
C:\Users\Reinier\Downloads\platform-tools\adb.exe devices -l
```

Expected: the onn appears as `device`, not `unauthorized` or `offline`.

Install the release APK, replacing an existing #193 Vireo installation if present:

```
C:\Users\Reinier\Downloads\platform-tools\adb.exe install -r "FULL_PATH_TO_VIREO_RELEASE_APK"
```

Expected: `Success`.

Verify package/build:

```
C:\Users\Reinier\Downloads\platform-tools\adb.exe shell dumpsys package com.reiniertutoriales.vireobrowser | findstr "versionName versionCode pkgFlags"
```

Save the output. The tested release must not report `DEBUGGABLE`.

If installation fails because of a signature mismatch, stop. Do not uninstall immediately unless we have confirmed that the installed Vireo is expendable; uninstalling destroys Vireo test data.

## Stage 2 — Release smoke test

Do these manually before privacy/security reproductions:

- cold launch Vireo;
- launcher name is **Vireo Browser**;
- launcher icon is correct;
- Android TV app-row banner is correct;
- Settings / Version shows the TV Bro source-code attribution;
- open a normal HTTPS page;
- navigate between at least two pages;
- open/switch/close tabs;
- perform one ordinary download and confirm it behaves normally.

If any basic function fails, stop the P0 sequence and preserve the exact symptom/logs. Do not continue and mix baseline failures with privacy tests.

## Stage 3 — Prepare controlled test identifiers

Use test accounts/sites only. Avoid using a sensitive personal login for privacy reproductions.

Before the incognito tests choose:

- one site where login/session state is easy to recognize;
- one unique hostname/domain to visit only in incognito for the host/favicon test;
- one uniquely named downloadable file for the incognito download-history test.

Write these identifiers into a small text file in the evidence directory so later DB inspection cannot confuse them with normal browsing.

## Stage 4 — Incognito suffix/session-isolation reproduction on RELEASE

1. In normal mode, sign in to the chosen test site.
2. Confirm the authenticated state is visible in normal mode.
3. Enter incognito.
4. Confirm the normal authenticated state is **not** visible initially.
5. While incognito, also visit the unique hostname reserved for the later favicon/hosts DB test.
6. Press Home **without using Vireo's normal Exit Incognito action**.
7. From cmd:
   `adb shell am force-stop com.reiniertutoriales.vireobrowser`
8. Clear logcat immediately before reopening if we want a clean reproduction log:
   `adb logcat -c`
9. Reopen Vireo from the launcher.
10. Check processes:
    `adb shell ps -A | findstr vireobrowser`
11. Run that process command several times if the app appears to restart/bounce.
12. Search for the known release-visible branch:
    `adb logcat -d -v time | findstr "Looks like we already in incognito"`
13. Check whether Vireo reopened in normal or incognito mode.
14. If it is incognito, check whether the normal authenticated session became visible.
15. Save full logcat:
    `adb logcat -d -v time > "C:\Users\Reinier\Downloads\Vireo-P0-20261001\incognito-restart-logcat.txt"`
16. Save process output:
    `adb shell ps -A | findstr vireobrowser > "C:\Users\Reinier\Downloads\Vireo-P0-20261001\incognito-processes.txt"`

Record four facts exactly:
- reopened mode: normal/incognito;
- whether `Looks like we already in incognito mode` appeared;
- whether normal authenticated state was visible in incognito;
- whether PIDs/processes appeared to bounce.

Do not interpret an unexpected normal-mode reopen as proof that suffix isolation is safe; it may instead expose the separate cross-process preference issue.

## Stage 5 — Incognito download-history reproduction on RELEASE

Start from a known incognito session.

1. Download the uniquely named test file while incognito.
2. Confirm whether Vireo's Downloads UI shows/progresses it during that live session.
3. Let it finish.
4. Exit incognito using Vireo's normal UI.
5. In normal mode open Vireo Downloads.
6. Record whether the uniquely named incognito download remains visible.
7. Do **not** delete the downloaded file or its Vireo history yet; preserve evidence until the debug database stage.

This test informs the later product decision between never-persist and session-only persistence.

## Stage 6 — Explicit Intent JavaScript reproduction on RELEASE

Open an ordinary non-sensitive web page in the current Vireo tab.

From cmd run exactly:

```
adb shell am start -n com.reiniertutoriales.vireobrowser/com.phlox.tvwebbrowser.activity.main.MainActivity -a android.intent.action.VIEW -d "javascript:alert(document.domain)" --ez com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB true
```

Record:
- whether an alert appeared;
- the domain shown, if any;
- whether the current page changed/crashed.

Then verify a legitimate external HTTPS VIEW still works:

```
adb shell am start -n com.reiniertutoriales.vireobrowser/com.phlox.tvwebbrowser.activity.main.MainActivity -a android.intent.action.VIEW -d "https://example.com"
```

Do not test arbitrary destructive JavaScript. The alert is sufficient evidence.

## Stage 7 — Capture one legitimate blob download on RELEASE

Before any future JS-bridge change, obtain a real positive-control blob download.

Use a trusted/simple site or page that genuinely initiates a `blob:` download through Vireo's existing mechanism.

Record:
- source page;
- filename;
- whether Vireo prompts/notifies;
- whether download completes;
- approximate file size;
- whether it appears in Downloads.

If we cannot find a reliable legitimate blob source during the session, record that and stop this subtest. Do not invent a JS-bridge fix without a positive-control flow.

## Stage 8 — Finish ALL release observations before installing debug

At this checkpoint confirm that release testing is complete:

- smoke test;
- incognito restart/suffix observation;
- incognito download-history observation;
- explicit Intent test;
- legitimate HTTPS external VIEW;
- legitimate blob positive control, if available.

Once debug is installed, later behavior must not be described as release/R8 validation.

## Stage 9 — Replace RELEASE with DEBUG, preserving accumulated data

Install the #193 debug APK over release:

```
adb install -r "FULL_PATH_TO_VIREO_DEBUG_APK"
```

Expected: `Success`.

Do not use `-d`. Do not uninstall between release and debug; preserving accumulated data is intentional here.

## Stage 10 — Binary/WAL-safe Room database capture

Force-stop first:

```
adb shell am force-stop com.reiniertutoriales.vireobrowser
```

Change cmd working directory to the evidence directory:

```
cd /d C:\Users\Reinier\Downloads\Vireo-P0-20261001
```

Copy the database:

```
adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db > main.db
adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-wal > main.db-wal
adb exec-out run-as com.reiniertutoriales.vireobrowser cat databases/main.db-shm > main.db-shm
```

Important:
- these commands must run through cmd-style binary redirection, not PowerShell `>`;
- keep `main.db`, `main.db-wal`, and `main.db-shm` together;
- if WAL/SHM do not exist after force-stop, record that fact rather than fabricating empty files;
- do not reopen Vireo between force-stop and completing the copy.

Also inspect/capture the favicon cache if needed for the unique incognito hostname. Do not delete it before evidence is collected.

## Stage 11 — Preserve and send evidence

Keep/upload together:

- `incognito-restart-logcat.txt`
- `incognito-processes.txt`
- `main.db`
- `main.db-wal` if present
- `main.db-shm` if present
- the text file containing the unique test hostname/download filename
- screenshots/photos only where they clarify visible behavior
- a short note containing:
  - smoke PASS/FAIL;
  - suffix branch message seen YES/NO;
  - reopened mode;
  - normal login visible in incognito YES/NO;
  - process bounce YES/NO;
  - incognito download persisted in normal Downloads YES/NO;
  - Intent JS alert executed YES/NO;
  - legitimate HTTPS VIEW PASS/FAIL;
  - blob positive control PASS/FAIL/NOT FOUND.

Do not modify or vacuum the copied database before analysis.

## Stop conditions

Stop and report before continuing if:
- ADB says unauthorized/offline;
- release APK installation fails;
- installed release is debuggable;
- baseline smoke test fails;
- Vireo enters a repeated process/crash loop;
- a command reports permission/run-as failure unexpectedly;
- database extraction produces an obvious error instead of binary data.

Do not solve these by uninstalling, clearing data, changing branches, rebuilding locally, or editing source unless we first identify the cause.

## Things NOT to do during this session

- Do not modify `fixes/audit`.
- Do not locally build a different commit and mix it with #193 evidence.
- Do not install debug before all release behavior tests finish.
- Do not uninstall Vireo merely to fix a transient ADB/test issue.
- Do not clear Vireo data between P0 tests unless specifically directed.
- Do not use PowerShell `>` for SQLite files.
- Do not delete the incognito download/history/favicon evidence before DB capture.
- Do not generate the definitive production keystore during this evidence session.
- Do not start W3 yet.

## What happens after this runbook

After evidence is reviewed:
1. record which P0 findings are physically confirmed and any ambiguous/non-reproduced cases;
2. implement only the justified P0 units, with automated tests where possible;
3. build them in CI;
4. install/retest the resulting release APK(s);
5. establish Room v19 schema-export infrastructure before any future Room schema migration;
6. make the final P0-3 product decision before a possible Room 19 -> 20 change;
7. complete P0 regression validation;
8. run W3;
9. address lower-priority renderer/tab-selection findings;
10. create/back up the definitive Vireo signing key and configure release secrets before official distribution.

The production keystore is deliberately postponed so the physical evidence session cannot accidentally mix signing changes with behavior validation.
