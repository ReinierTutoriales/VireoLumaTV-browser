# Repository checks

Commit SHAs in these audit documents refer to the history before the current root
commit (`927249bb`, followed by `212bcdab` on `master`). Those commits are not in
`master`; they are kept only in the private backup taken before the rewrite.
CI run IDs, artifact IDs and dated branch names in these files are not commits and
remain valid references to GitHub records.

The current application identity and license are documented in the repository's
`README.md`, `LICENSE.md`, `NOTICE.md` and `docs/BRAND_STANDARD.md`.
`SOURCE_INVENTORY.tsv` covers the files tracked in this tree. Its classifications
record the existing inventory and do not establish exclusive authorship.

## Current verification

For 2.0.1, PR #82 head `0021a43e` passed CI run `40754020489`: unit tests, debug
and minified release builds, lint, Room schema verification and APK content audits.
The application license bundled in the APK must match the root MIT license.
Physical validation of the current APK remains a separate check.

## Source migration status

Closed on 2026-11-01 at `66459a60`, measured with
`python3 scripts/measure-inherited-lines.py --rev 66459a60`: 878 of 14 310 counted
lines in `app/src/main` are still attributed by `git blame -w -C` to commits made
before this project. Breakdown: layouts 362, Kotlin 309, manifest and other XML 133,
values 56, HTML 18, JavaScript 0.

Those lines are the forms the Android platform requires (`super.onCreate`,
`setContentView`, Room annotations, `<intent-filter>`, `android:exported`,
`layout_width`, document boilerplate). They carry no upstream design, text or
identity. Upstream translations, names, credits, notices, data layout and the
pointer, tab, download, history and database designs have been replaced.
Rewriting the remaining lines would only alter `git blame`, so it is not pursued.

## Device and regression evidence

Dated reports describe their own recorded builds. Preserve measurements and traces;
compare their source SHA and conditions before using them as a baseline.

- [Android TV and low-memory audit](ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md)
- [Restore and User-Agent fixes](OFF_DEVICE_FIXES_20261007.md)
- [Filter update checks](ADBLOCK_OFF_DEVICE_20261007.md)
- [TV interface checks](TV_UI_REFRESH_20261007.md)
- [Regression policy](../REGRESSION_POLICY.md)
- [Roadmap](../ROADMAP.md)

Obsolete identity and migration reports are retained in Git history and the
repository backups, rather than presented as current project instructions.
