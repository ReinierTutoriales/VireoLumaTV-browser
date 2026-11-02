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
