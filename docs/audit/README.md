# Repository checks

Commit SHAs in these audit documents refer to the history before the current root
commit (`927249bb`, followed by `212bcdab` on `master`). Those commits are not in
`master`; they are kept only in the private backup taken before the rewrite.
CI run IDs, artifact IDs and dated branch names are not commit SHAs.
Historical artifacts may expire; their recorded identifiers do not guarantee availability.

The current application identity and license are documented in the repository's
`README.md`, `LICENSE.md`, `NOTICE.md` and `docs/BRAND_STANDARD.md`.
`SOURCE_INVENTORY.tsv` covers the files tracked in this tree. Its classifications
record the existing inventory and do not establish exclusive authorship.

## Current verification

The current CI definition is `.github/workflows/ci.yml`. It checks the source
inventory, unit tests, debug and minified release builds, lint, Room schemas and
APK contents. Use the successful run for the exact source SHA being reviewed.
Generated APK licenses are compared with the canonical root license files.
Physical validation is deferred by the owner; CI does not establish device performance.

## Historical source migration record

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

## Interface icon sources (2026-11-03)

The current interface symbols come from the pinned Material Symbols Rounded
snapshot in `NOTICE.md`, converted from SVG paths to Android VectorDrawable.
The resource comments identify each upstream symbol; the inventory marks these
adapted resources as MODIFICADO. This is an
asset import under Apache 2.0, not a claim of independent icon authorship.
No icon library, icon font or network font loader is added.
