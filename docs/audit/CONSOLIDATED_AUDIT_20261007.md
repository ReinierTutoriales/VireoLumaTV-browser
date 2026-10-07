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
The generated v19 JSON still needs retrieval, inspection and version control after
CI is authorized and succeeds; schema-export configuration alone does not establish
coverage of historical migrations.

## Consolidation decisions

The work branch includes the complete current main-branch tree and its integrated
fixes. No historical branch was blindly merged or cherry-picked.

| Source | Decision and evidence |
| --- | --- |
| Credits, engine/adblock audit, minimal home, TV menu focus, TV tab focus branches | `git cherry origin/fixes/audit <branch>` reports equivalent patches and no unique patches; do not reapply. |
| Home/search/preview branch | Current `CursorLayout.kt` matches its cursor implementation; later focus fixes are already on the base. Keep the current tree. |
| Older input, navigation, security and privacy branches | Different commit hashes and patch IDs are not evidence that changes are missing after integration. Reviewed current settings observers, navigation/cursor tests, bridge context/token checks, private-tab cleanup and serialized tab writes; do not replace them with old snapshots. |
| `perf/onn-2gb` | Main already releases background WebViews before creating the replacement. The old manifest also lacks later private downloads components. Do not merge that old tree. `largeHeap` remains a separate measurement question. |
| Draft PR #41 | Its database source path predates package migration. Port only its KSP/schema-export intent to the current package; do not apply the old patch. The old PR has not been closed. |
| Old validation documents | Physical-device scenarios remain useful references; old defect lists and performance expectations are not proof about build 80. |

## Validation status

Executed locally and passed:

- `node scripts/test-home-page-security.cjs`
- `node scripts/test-webview-controls.cjs`
- `node scripts/test-youtube-adblock.cjs`
- `git diff --check`
- Checked that no entity/DAO/migration SQL, application ID, version code, video
  scripts, renderer lifecycle, cursor dispatch or adblock implementation changed.

Not executed for this branch:

- Android/Kotlin tests and APK builds: local Gradle download fails with
  `Network is unreachable`; the environment has Java 17, while this project
  requires Java 21 for the Gradle daemon.
- GitHub Actions: publishing the branch was blocked by automatic approval review
  pending explicit authorization to upload the local changes.
- TV/onn playback, memory and remote-control testing: no physical device attached.

The branch is a reviewable local candidate, not a tested release. Do not publish an
APK, claim lower RAM use or uninterrupted playback, or mark Android regressions as
passed until the relevant checks run.

## Next actions on this same branch

1. Publish this branch and open one draft PR against `fixes/audit`.
2. Run existing CI: all JS and Android unit tests, debug APK and minified release.
3. Retrieve the generated Room v19 schema, inspect its identity and entities,
   commit the generated JSON, and add a CI check for unexpected schema changes.
4. Re-run CI after the generated-schema commit; replace draft PR #41 only after
   its infrastructure is verified in this branch.
5. Check the settings flow on the TV. Changes to WebView memory, video, fullscreen
   or blocking require separate physical evidence before implementation.

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
- No remote branch, PR, run or release was changed. Remote publication remains
  blocked by automatic approval review after the second attempt.
