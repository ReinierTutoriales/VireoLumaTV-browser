# Repository checks

The current application identity and license are documented in the repository's
`README.md`, `LICENSE.md`, `NOTICE.md` and `docs/BRAND_STANDARD.md`.
`SOURCE_INVENTORY.tsv` covers the files tracked in this tree. Its classifications
record the existing inventory and do not establish exclusive authorship.

## Current verification

For 2.0.1, PR #82 head `0021a43e` passed CI run `40754020489`: unit tests, debug
and minified release builds, lint, Room schema verification and APK content audits.
The application license bundled in the APK must match the root MIT license.
Physical validation of the current APK remains a separate check.

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
