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

Counts refer to the filter snapshot fetched on 2026-10-30. The bundled snapshot
is tracked in `assets/adblock/baseline/manifest.json`.

Applied by the bridge with adblock-rust 0.13.6:

- `$to=` and `$denyallow=` run together in `native/adblock-jni/src/scoped.rs` to preserve
  hostname matching and `$important` precedence. The upstream `$to=` implementation
  has different destination matching and its own precedence. A `*` rule without
  a `domain=`/`from=` page is ignored, as uBO requires.
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

## DOM regression coverage

`scripts/test-page-filters.cjs` covers frames moved out of a consumed `DocumentFragment`,
preserving legitimate same-origin frames and skipping inaccessible cross-origin frames.
Clipboard copy/cut checks the selected substring of a focused input or textarea, with
document selection as fallback; partial selections and `excludeMatches` remain covered.
These are deterministic fixtures, not physical WebView validation.

DOM behavior: https://dom.spec.whatwg.org/#concept-node-insert
Text selections: https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#textFieldSelection

## WebView and uBlock Origin review, 2026-11-28

In uBlock Origin 1.77.4 the DOM surveyor queues a scan when work is pending,
deduplicates nodes and queries new names in batches. VireoLumaTV already splits
subtree walks into bounded slices and caches native request verdicts per rule
generation. The local surveyor now reads each changed element once per observer
delivery and schedules a timer only for queued roots or previously unseen names.
Duplicate notifications cannot exhaust the 256-root queue.

In the synthetic regression, 100 attribute records for one element require one
class read instead of 100. The bridge still receives one lookup for the new class
and ID. Initialization and a later delivery containing only known names each go
from one queued timer to zero. The fixture also checks that a duplicate at 256
queued roots avoids a document walk while a 257th distinct root still triggers
the bounded fallback. These counts describe work in the fixture.

uBlock Origin can stop surveying after five minutes and 256 consecutive misses.
VireoLumaTV keeps its observer active so advertisements added later in long-lived
pages can still be found. The extension captures requests through
`webRequest`. Android WebView exposes interception callbacks with different
coverage, including callback delivery only for the initial URL of a redirect.
The extension's network path is therefore outside this port.

The existing async WebView startup, single live tab, renderer recovery and
memory-pressure cache eviction remain the baseline. Measure cold and warm
navigation, renderer and host PSS, and DOM activity on the same WebView provider
before attributing any end-to-end speed or memory change. Speculative loading
would also need an origin policy and a joint app-plus-renderer memory budget.

- [uBlock Origin 1.77.4 DOM surveyor](https://github.com/gorhill/uBlock/blob/1.77.4/src/js/contentscript.js)
- [uBlock Origin 1.77.4 network interception](https://github.com/gorhill/uBlock/blob/1.77.4/src/js/traffic.js)
- [Android WebView interception contract](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest))
- [Android WebView startup](https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup)
- [Android WebView memory](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory)
- [Android WebView speculative loading](https://developer.android.com/develop/ui/views/layout/webapps/speculative-loading)
