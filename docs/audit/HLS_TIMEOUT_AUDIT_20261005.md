# HLS timeout and 2 GB device audit — 2026-10-05

Screenshot: `Could not play video`, `hls:networkError_levelLoadTimeOut`; user confirms this occurs during playback. The HLS.js official ErrorDetails enum identifies `LEVEL_LOAD_TIMEOUT`, and official playlist-loader source maps a timed-out level playlist load to that error. This is a playlist request timeout, not evidence by itself of insufficient RAM, codec failure, renderer termination, or a broken APK. RAM/CPU contention can contribute, but needs measurement. No page URL, player instance/version, failed request or device connection was supplied.

## Browser changes

1. Cache deterministic adblock decisions for exact URL + resource type + page host. Fixed 64 KiB estimated entry budget, maximum retained URL length 2048, no persistent cache. Preserve token/query differences, type-specific rules and first/third-party context. Clear on native client replacement/model disposal. Do not cache native matcher failures or decisions made without a client. This removes repeat native matcher work for an unchanged playlist URL; it does not cache the playlist or skip fetching live updates. Unique segment URLs still need matching.
2. Ordinary web console info/debug messages no longer generate application logs unless WebView debugging is explicitly enabled. Warning/error forwarding uses eight messages per ten seconds and at most 2048 characters per message. This limits app-side formatting/log I/O on the UI thread; it does not silence or modify the website JavaScript runtime.
3. Native HLS/DASH/media transport and HTTP errors receive separate rate-limited diagnostics: resource kind + host + error/status code, without paths, query tokens or response bodies. This helps distinguish a request failure from player/renderer failures. It observes WebView error callbacks only, so cannot expose every JS-aborted request, extensionless endpoint or iframe player error. No request interception/retry/reload policy is changed.

Keep the existing single-live-renderer policy, small previews, bounded favicon requests/failure cooldown and HTTP cache validation. Retain hardware acceleration and the default renderer priority: lowering priority just because the main WebView is GONE would endanger fullscreen playback. Never destroy the selected renderer or clear Chromium caches as a memory-pressure optimization while it is playing.

## Why no injected HLS timeout/buffer settings

HLS.js config belongs to the site's player, which may use a bundled older version, custom loader, closure-scoped instance, iframe, DRM or a different library. Official current policies are `playlistLoadPolicy`, `manifestLoadPolicy`, `fragLoadPolicy` with separate first-byte/load deadlines and bounded timeout/error retries. Legacy `levelLoadingTimeOut` and similar fields are deprecated in current HLS.js. WebView has no public generic setting that overrides these JS deadlines. Mutating globals/prototypes or adding page reload/retry polling could create competing retries, break signed links, reset playback or increase traffic.

A player's buffer/quality tuning must balance network and RAM. A larger buffer can mask brief jitter but increases native/MSE memory and startup delay; reducing it arbitrarily can increase stalls. Current HLS.js exposes worker transmuxing, adaptive bandwidth selection, FPS-based quality capping, media-capability checks and finite backBufferLength. These are appropriate to review for the actual player only after its identity/version/config are known; do not claim that browser code applied them to arbitrary pages. Total system RAM of 2 GB is not a per-WebView memory allowance or a guarantee of any resolution/framerate.

## Evidence and acceptance

Tests added: repeated playlist matching; URL token/type/page-host isolation; client invalidation; estimated-size eviction; oversized URL bypass; exceptions not cached; media-path classification; log rate/window bounds. Existing live/DVR control, native pointer and network suites remain in CI along with debug/minified release builds. Node suites passed locally; Android execution is validated on GitHub Actions because local Gradle downloads are blocked.

On the actual device, compare identical streams and quality before/after for at least a sustained session and several repetitions. Record time-to-first-frame, rebuffer count/duration, playlist HTTP status/latency, player HLS version/error `fatal` flag, frame drops, main/renderer memory, System WebView provider/version, fullscreen transitions and Wi-Fi loss/reconnection. No target-device benchmark has run; lower native match/log work is verified independently, not a measured reduction in the screenshot's timeout frequency.

Device commands for an authorized local diagnostic session:

```sh
adb shell dumpsys webviewupdate
adb shell dumpsys meminfo com.reiniertutoriales.vireolumatv
adb shell dumpsys activity processes com.reiniertutoriales.vireolumatv
adb logcat -v time -s 'VireoLumaTV WebView' WebViewEx chromium
```

Use the isolated renderer PID shown by activity processes with `adb shell dumpsys meminfo <PID>`; app-only Java heap measurements miss native DOM/MSE/graphics and isolated-renderer memory. Enable the app's existing WebView debug option only for diagnosis, then inspect the real player's Network/Console panels with Chrome DevTools. A playlist timeout may require the site's CDN/player fix, a connection fix, or lower player quality; the screenshot cannot establish which. Disabling adblock once on that tab for an A/B test can identify a filtering conflict, but this patch does not blanket-allow streaming hosts or disable blocking.

## Official sources consulted

- https://hlsjs.video-dev.org/api-docs/hls.js.errordetails
- https://github.com/video-dev/hls.js/blob/master/src/loader/playlist-loader.ts
- https://github.com/video-dev/hls.js/blob/master/src/config.ts
- https://hlsjs.video-dev.org/api-docs/hls.js.hlsloadpolicies
- https://hlsjs.video-dev.org/api-docs/hls.js.loaderconfig
- https://hlsjs.video-dev.org/api-docs/hls.js.buffercontrollerconfig
- https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory
- https://developer.android.com/develop/ui/views/layout/webapps/debugging
- https://developer.android.com/reference/android/webkit/WebViewClient

Checked 2026-10-05. Guarantees of interruption-free playback are unsupported without control of the stream server, connection, player and decoder, even with more RAM.
