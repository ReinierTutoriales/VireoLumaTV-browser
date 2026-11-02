# Network audit — 2026-10-05

Baseline: e3b71c3012b4030ecdcedbcb1f51845a1fbefd83. Scope: network work initiated by the browser, its homepage, icons, downloads and filter updates.

## Existing protections retained

One live renderer; Chromium/WebView HTTP caching with LOAD_DEFAULT; two file-download workers; serialized adblock refresh, finite timeouts and bounded rule-list input. Do not replace normal HTTP cache validation with forced stale responses, strip page resources indiscriminately, or throttle video streams to an arbitrary rate.

## Corrections

- Pause JavaScript/layout timers with the WebView when the app is paused or a renderer detached, and resume them before the active renderer resumes. Android onPause alone does not pause JavaScript; the existing single-live-renderer invariant makes the process-wide timer API appropriate.

- Homepage favicon interception now reads only the memory cache and returns immediately when absent. A separate lifecycle-bound job loads at most eight visible tile icons, with up to two global favicon requests. Navigation, detaching the renderer and pausing cancel the batch; resuming the internal homepage restarts it. Results require the same live view and internal homepage. Known icon URLs bypass downloading the site's HTML. Failed images can be replaced asynchronously without applying stale results to another tile.
- All native homepage icon URLs use the bounded pool instead of uncontrolled external image requests. Ordinary browser previews retain their existing behavior.
- Failed favicon lookups have a 60-second cooldown, held in a 128-entry LRU. Low-memory trimming retains cooldowns. Same-host waits no longer consume a global network permit. Cancellation does not become a cached failure.
- Discovery retains at most 32 icon candidates. Download attempts are distinct, HTTP(S), non-SVG, sorted by size with overflow-safe arithmetic, and limited to three. HTML that already supplies a bitmap candidate does not trigger an extra manifest download. Existing byte and bitmap limits remain.
- Icon, discovery and adblock HTTP connections are disconnected in finally blocks. Download retry connections close before waiting, with exponential delays of 3/6/12/24 seconds, respect for Retry-After seconds/HTTP dates, and no automatic retry earlier than a server-requested delay exceeding 60 seconds. Cancellation is checked before connecting and every 250 ms during backoff. The existing maximum of five attempts and two download workers remains; 429 is not automatically retried.

## Validation and limits

Regression tests use a local HTTP server to verify that 16 concurrent plus 10 repeated missing-icon lookups produce one HTML request and three distinct icon requests, without a manifest fetch or retry after memory trimming. Tests cover candidate bounds, homepage interception without lookup, asynchronous tile replacement/stale-result rejection, Retry-After and cancellation without a second connection. CI also runs existing incognito, renderer, navigation, state, adblock and UI tests and builds debug/minified release APKs.

There is no arbitrary bandwidth cap on the active website. Chromium owns page connections, streaming, cache validation and resource scheduling. Browser auxiliary work is bounded; this does not guarantee the availability of remote websites or a slow/unstable connection. Blocking socket operations may take their finite timeout to return after coroutine cancellation. Real-link bandwidth, RTT, packet loss, video quality and device RAM remain unmeasured.

Primary sources:
- https://developer.android.com/reference/android/webkit/WebView
- https://developer.android.com/reference/android/webkit/WebSettings
- https://developer.android.com/reference/java/net/HttpURLConnection
- https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after
