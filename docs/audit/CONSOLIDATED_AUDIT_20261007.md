# Consolidated work branch — October 7, 2026

Base: `521c214` (build 80), current `origin/fixes/audit` when this review started.
Work branch: `fixes/consolidated-audit-20261007`.

This is the current work record for this branch. Older audits describe their own
base commits; their proposals must be checked against current code before use.

## Verified defects and bounded corrections

| Finding | Trigger and existing behavior | Correction | Regression coverage |
| --- | --- | --- | --- |
| Unchecked theme ordinal | A stored negative or out-of-range integer indexes `Theme.entries` during `Config` construction and throws. | Use SYSTEM for invalid ordinals; retain every valid ordinal and other preferences. | `ConfigThemeTest`: both bounds, extreme integers, valid themes and subsequent settings writes. |
| Custom User-Agent erased by settings | The initial Custom selection callback writes the empty custom entry from `uaStrings`, replacing the saved editor value. Saving then removes the custom preference. | Preserve the editor on initial/repeated Custom selection; show the saved value when switching back to Custom; keep preset selection working. | `MainSettingsLifecycleTest`: opening, editing, saving, presets and reopening, through the actual selection listener. |
| Room export options configured for the wrong compiler | The Kotlin database is processed by KSP, but schema arguments were configured only for javac. | Pass options to KSP, explicitly export schema v19, remove the redundant app javac Room compiler, require a generated schema CI artifact. | CI must run all Android unit tests, debug build and minified release build and produce `room-schemas`. |

Room reference: https://developer.android.com/training/data-storage/room/migrating-db-versions
The schema JSON must come from Room compilation, never be handwritten.
Export configuration does not change entities, database version or migration SQL.
The v19 JSON was retrieved from CI artifact `11474177793`, inspected and committed.
Its identity is `a0e95140d37989600ef637430ae467ae`; its five tables, indexes and setup
SQL were validated with SQLite. CI now rejects untracked or changed schema JSON.
This does not establish coverage of historical migrations.

## Consolidation decisions

The work branch includes the complete current main-branch tree and its integrated
fixes. No historical branch was blindly merged or cherry-picked.

| Source | Decision and evidence |
| --- | --- |
| Credits, engine/adblock audit, minimal home, TV menu focus, TV tab focus branches | `git cherry origin/fixes/audit <branch>` reports equivalent patches and no unique patches; do not reapply. |
| Home/search/preview branch | Current `CursorLayout.kt` matches its cursor implementation; later focus fixes are already on the base. Keep the current tree. |
| Older input, navigation, security and privacy branches | Different commit hashes and patch IDs are not evidence that changes are missing after integration. Reviewed current settings observers, navigation/cursor tests, bridge context/token checks, private-tab cleanup and serialized tab writes; do not replace them with old snapshots. |
| `perf/onn-2gb` | Main already releases background WebViews before creating the replacement. The old manifest also lacks later private downloads components. Do not merge that old tree. `largeHeap` remains a separate measurement question. |
| Draft PR #41 | Its database source path predates package migration. Port only its KSP/schema-export intent to the current package; do not apply the old patch. The old PR is superseded by PR #76; its branch is retained. |
| Old validation documents | Physical-device scenarios remain useful references; old defect lists and performance expectations are not proof about build 80. |

## Validation status

Executed locally and passed:

- `node scripts/test-home-page-security.cjs`
- `node scripts/test-webview-controls.cjs`
- `node scripts/test-youtube-adblock.cjs`
- `git diff --check`
- Checked that no entity/DAO/migration SQL, application ID, version code, video
  scripts, renderer lifecycle, cursor dispatch or adblock implementation changed.

Android validation executed successfully on published commit `20cc6bfb`:
[CI run 37604054177](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37604054177).
It ran the JavaScript suites, all Android unit tests including the added settings
regressions, the debug APK build and the minified release build. Room generated
schema v19 successfully. Local Gradle downloads remain unavailable; remote CI
provides the Android build evidence.

PR #76 contains the complete work on one branch. Its current checks are the
source of truth for subsequent commits, including the schema-diff safeguard:
https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/76

Physical TV/onn playback, memory and remote-control testing remain pending.
No APK release was published and no performance improvement is attributed to
these settings/build-configuration corrections.

## Remaining work on this same branch

1. Require green checks for the current PR tip before integration.
2. Check the settings flow on the TV; changes to WebView memory, video, fullscreen
   or blocking require separate physical evidence before implementation.
3. Historical branches without verified integration remain preserved; review their
   unique work before any further deletion.

Further debt to measure rather than change speculatively: main-thread Room access
enabled by `allowMainThreadQueries`, the effect of `largeHeap` on the actual TV,
and blocking filter-list I/O cancellation. These are not fixed by this branch.

## Local cleanup completed in this branch

- Replaced the old roadmap at its stable path with the current work plan and
  preserved the complete old document under `archive-2026-10-07/`.
- Added a current audit index and repaired README's broken license-audit link.
- Removed the one-time `cleanup-once.yml` workflow: its hard-coded TV UI branch is
  absent from the current GitHub branch list. Regular CI and release workflows
  are retained.
- Removed the unused `cleanup-repository-once.py` script, which mixed old run/branch
  deletion with a release dispatch. No workflow references it. Its previous source
  remains in Git history; the historical cleanup manifest remains as evidence.
- After explicit authorization, published PR #76 and executed remote CI.
- Deleted the eight branches below only after proving their entire trees identical
  to ancestor commits of `fixes/audit`, checking their current SHA, excluding open-PR
  and protected branches, and using a deletion lease. The default branch stayed at
  `521c214`. No release or previous successful run was deleted.
- Cleanup succeeded in [run 37604824836](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37604824836).
  Its temporary workflow was removed after completion; regular CI/release workflows
  remain. The exact cleanup code is retained in commit `12c72f86`.

| Deleted branch | Identical integrated commit |
| --- | --- |
| `fixes/credits-20261006` | `29047977` |
| `fixes/engine-adblock-audit-20261006` | `dfa75b7e` |
| `fixes/home-search-preview-20261006` | `90deabd3` |
| `fixes/input-adblock-recovery-20261005` | `20239306` |
| `fixes/minimal-home-20261006` | `58598d88` |
| `fixes/tv-menu-focus-20261006` | `49e91288` |
| `fixes/tv-navigation-20261005` | `1928ffa1` |
| `fixes/tv-tab-focus-20261006` | `521c214a` |

Retained historical branches: `docs/onn-validation-checkpoint`,
`fixes/ci-js-bridge-context-guards`, `infra/room-v19-schema-export`, `perf/onn-2gb`,
`review/privacy-security-stability`, and `security/js-bridge-context-guards`.
Their complete integration was not established by the identical-tree check.
They are references or pending review, not additional active work branches.
