# Adblock verification

The application uses the Rust engine pinned in `native/adblock-jni/Cargo.lock`
through `RustAdblock` and the JNI bridge in `native/adblock-jni/src/lib.rs`.
The engine and bridge retain their MPL 2.0 terms; see `NOTICE.md`.

Request classification, filter downloads, cache publication, per-tab settings,
redirect resources and page injections must be checked against the current source
and tests. Dated reports under `docs/audit/` describe their recorded source revisions;
they are not a list of changes to apply again.

CI runs unit tests, lint, debug and minified release builds, and APK content audits.
The package audit checks retired resources, namespace residue and agreement between
reviewed source assets and packaged files. It does not measure blocking performance
or determine authorship.

Validate the released APK across Android TV, Google TV and compatible TV boxes: legitimate playback and downloads,
blocking on/off, per-site exceptions, offline startup after list updates, D-pad
focus and renderer recovery. Record device, WebView provider, APK/source SHA,
page conditions, load times and app/renderer memory before comparing results.
Previous device measurements remain evidence only for their measured builds and conditions.

## Filter syntax coverage

Counts refer to the bundled lists in `assets/adblock/baseline` (fetched 2026-10-30).

Applied by the bridge in addition to adblock-rust 0.13.5:

- `$to=` and `$denyallow=` (adblock-rust drops or rejects them): `native/adblock-jni/src/scoped.rs`.
  A `*` rule without a `domain=`/`from=` page is ignored, as uBO requires.
- `$method=`: the request's HTTP method reaches the engine (`nativeVerdict`).
- `$popup`/`$popunder`: rewritten to `$document` in a separate engine for new windows
  (`FilterListPreprocessor`).
- `trusted-*` scriptlets: only from uBlock Origin's own lists (permission bit 1).
- Scriptlets: `page_filters.js` implements every name the bundled lists invoke except
  `mpegdash-prune` (1 rule); the library is built only in frames that receive a call.

Not applied (no WebView mechanism without rewriting network responses, or not supported by
adblock-rust): `$replace` (77 rules), `##^` HTML filters (397), `$header=` (42), `$ipaddress=`,
`$strict1p`/`$strict3p`, `$cname`, `$urlskip`, `$uritransform`, some `$removeparam` forms,
`$csp` combined with a content type, and generic `#@#` exceptions.

Known limits: a `$redirect-rule` among the `$to=`/`$denyallow=` rules only replaces requests
those rules block, not requests blocked by the main lists. On YouTube, list scriptlets are
skipped; `youtube_adblock.js` handles that site.
