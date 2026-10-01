# W2 measurement protocol

W2 measures the first memory optimization after W1: keeping at most two live tab WebViews.

## Build under test

Use the post-merge CI release artifact for this exact commit:

- Branch: `fixes/audit`
- Commit: `94b32643525070ebc05ffbce3a7e66b68d7066ff`
- Parent: `0b8277f688fac76673b70ec8c3dfe949e7cde236`
- PR: #31, `Keep at most two live tab WebViews`
- Post-merge workflow run: `36782928588`
- Release artifact: `vireo-release`
- Artifact ID: `11128174762`
- Artifact digest: `sha256:36bcf90bd4867070b043e2dedf931443a3a1536bfd0c38d79e2fc26b47147d7b`

Install over the existing app with `adb install -r`. Do not clear app data.

## Fixed URLs

Use the same fixed tab set as W1 unless explicitly noted in `notas.txt`:

1. `https://es.wikipedia.org`
2. `https://www.bbc.com/mundo`
3. `https://m.youtube.com`
4. `https://example.com`

YouTube must use the default/mobile WebView user agent. Do not use the desktop user agent for W2.

## Capture rule

Run `-Begin` before every measured scenario. This resets `gfxinfo` and clears `logcat`, so each capture is scenario-local.

Recommended order:

1. `-Meta`
2. `-Begin 02_one_tab`, then `-Capture 02_one_tab`
3. `-Begin 03_four_tabs`, then `-Capture 03_four_tabs`
4. `-Begin 04_after_close`, then `-Capture 04_after_close`
5. `-Begin 09_restore_tab`, then `-Capture 09_restore_tab`
6. `-Begin 05_youtube_default_ua`, then `-Capture 05_youtube_default_ua`
7. `-Cold 00_cold_start` last

Cold start is last so it does not reset or alter the tab-session behavior before the restoration test.

## Scenarios

### 02_one_tab

Open only:

- `https://es.wikipedia.org`

Wait for load to settle, then capture.

### 03_four_tabs

Open the four fixed URLs above. Switch through the tabs so PR #31 has a chance to release background WebViews.

Expected log evidence:

- `TabsModel: released 2 background WebView(s), kept current and previous tab`

Primary W2 success metric:

- renderer PSS should be clearly below W1's `03_four_tabs` baseline of about `301 MB`
- target range: roughly `200-240 MB`, allowing for Chromium cache variability

### 04_after_close

Close tabs until only Wikipedia remains. Wait 30 seconds before capture.

Expected:

- only one visible tab remains
- no crash
- renderer memory should not exceed W1's after-close result in a meaningful way

### 09_restore_tab

This validates that suspended tabs restore correctly.

Procedure:

1. With four tabs open, navigate inside the first tab to create history. Example:
   - open `https://es.wikipedia.org`
   - follow one internal link
2. Switch through enough other tabs for the first tab to be released.
3. Return to the first tab.
4. Confirm the same URL/page restores.
5. Press Back.
6. Confirm Back returns to the previous Wikipedia page.

Record in `notas.txt`:

- restored URL: yes/no
- Back history restored: yes/no
- any visible reload delay or broken state

### 05_youtube_default_ua

Use `m.youtube.com` with the default WebView/mobile user agent.

Procedure:

1. Open the same YouTube video used in W1 if known.
2. Start playback.
3. Enter fullscreen.
4. Wait 10-15 seconds.
5. Capture.

Record in `notas.txt`:

- video URL
- fullscreen entered: yes/no
- responsiveness with remote
- pointer/control problems
- ads shown/skipped/not observed

### 00_cold_start

Run cold start last using the script's `-Cold` mode.

Record launch time and any startup anomaly.

## notas.txt template

```text
APK: vireo-release from post-merge run 36782928588
SHA: 94b32643525070ebc05ffbce3a7e66b68d7066ff
Artifact ID: 11128174762
Artifact digest: sha256:36bcf90bd4867070b043e2dedf931443a3a1536bfd0c38d79e2fc26b47147d7b
Install: adb install -r, no pm clear
Device: onn
Android: 14
WebView: com.google.android.webview: 153.0.8010.36

W2 URLs:
02_one_tab = https://es.wikipedia.org
03_four_tabs =
  1. https://es.wikipedia.org
  2. https://www.bbc.com/mundo
  3. https://m.youtube.com
  4. https://example.com
05 YouTube video =

09_restore_tab:
- restored same URL/page:
- Back restored previous page:
- reload delay or broken state:

Functional observations:
- four-tab responsiveness:
- YouTube fullscreen:
- YouTube controls/pointer:
- ads observed:

Notes:
- ran -Begin before every scenario
- waited 30s before 04_after_close capture
```

## Comparison against W1

Use W1 as the baseline:

| Scenario | W1 key result |
| --- | --- |
| `02_one_tab` | renderer PSS about `149 MB` |
| `03_four_tabs` | renderer PSS about `301 MB` |
| `04_after_close` | renderer PSS about `202 MB` |
| `05_youtube_default_ua` | renderer PSS about `351 MB`, jank about `23.8%` |
| desktop UA | discarded: renderer PSS about `612 MB` and heavy swap |

The core PR #31 validation is `03_four_tabs`: memory must move toward two live WebViews, not four live WebViews.