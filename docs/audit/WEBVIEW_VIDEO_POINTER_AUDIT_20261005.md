# WebView video and pointer audit — 2026-10-05

Base: `4e18f979e5ffb272481e8bf9e1012cbde724be11`, current default `fixes/audit`; merged PR #63 included.

## Findings and changes

- Virtual cursor clicks were finger events with an unknown source and no idle hover. This does not represent a precise mouse and can involve Chromium touch target adjustment/highlights. The supplied screenshot shows one large highlighted wrapper enclosing ESPN/OP2/OP3/Disney+, but cannot establish the website DOM or actual link destinations. WebView now opts into native mouse coordinates/tool/source/button state and hover; other surfaces retain touch behavior. Scroll and pinch remain touch. End the scroll finger stream before clicking. Never inject `element.click()` or bypass the page's trusted event/default navigation, popup or blob authorization.
- Cursor updates previously reposted immediately on the UI Handler. Pace to display frames and avoid logging every mouse movement to reduce competition with video rendering. Send hover exit when the cursor hides.
- Long press recognized only a direct `<a>`/image target; nested spans/images lost their enclosing link. Track trusted mouse/touch presses, find the nearest anchor, decode the JSON WebView result, and ignore callbacks after detach/replacement.
- Playback toggle required `currentTime > 0` and `readyState > 2`. A starting/stalled video was mistaken for paused and received another play instead of pause. Use paused/ended state and prefer an already active media element. Handle rejected play promises without a retry loop. Rewind/forward/stop respect seekable ranges, including sliding live windows and gaps; streams without seeking remain unchanged.
- Renderer loss did not immediately cancel homepage favicon jobs. Cancel them before disposing the dead view.

## Network/video policy retained

The two favicon permits, bounded candidates/attempts, failure cooldown, asynchronous cache-only homepage interception and download backoff in #63 remain. These limits apply to auxiliary work, not Chromium's media requests. One live tab renderer, `LOAD_DEFAULT`, DOM storage, hardware acceleration and existing renderer recreation stay intact. Normal pages/segments are not proxied or throttled. No blanket cache-else-network, forced codec/quality, synthetic autoplay, changed user agent, periodic reload or stream retry script is added. Site JavaScript/MSE, browser engine and device codecs still determine playback behavior.

Background `onPause` and global `pauseTimers` are paired with resume on active engine attach/resume. Fullscreen merely hides the WebView and shows its custom view; it does not call the engine pause hook. Do not lower renderer importance when the WebView is hidden: that could kill an actively playing fullscreen custom view. Global timer pause is appropriate only while the one-live-renderer invariant remains valid; it is not a guarantee of zero background network traffic.

## Verification and release limits

- `node scripts/test-home-page-security.cjs`: passed.
- `node scripts/test-webview-controls.cjs`: passed (buffering at time zero, rejected play, active media choice, live/no-seek/gapped/changing ranges, nested individual links, trusted input, reinjection).
- Android regression `VirtualCursorPointerTest`: verifies source, tool, exact coordinates, down/up/cancel button and stream timing, hover routing, and non-WebView finger fallback. Robolectric does not run Chromium DOM hit testing or decode video.
- Full unit tests/debug/minified release are required in CI. Local Gradle cannot download its distribution in this restricted-network environment; do not report local Android builds as passed.
- Device acceptance remains required. The supplied screenshot does not include the website URL/DOM, and no TV/device is connected. Mouse input cannot fix a site's actual CSS `parent:hover`/`parent:active` styling, full-area overlays or invalid page markup. Native clicking improvements are implemented; the exact screenshot behavior is not yet proven resolved on its original site.

Before publishing a release, install the CI APK on the target TV with its actual Android System WebView version recorded. Test each of the four links independently (including nested icon/text, after scrolling and zoom); normal click, held click/context menu, blob download and video play. Play a long live and on-demand stream, enter/exit fullscreen repeatedly, background/foreground, switch tabs, interrupt/reconnect Wi-Fi, and verify renderer recovery. Compare the old and new APK on the same stream/quality/network for startup delay, buffering count/duration, UI response and crash logs. Server availability, Wi-Fi loss, codec/DRM support and bitrate are not measured here; no zero-crash/no-buffering guarantee is justified.

## Primary documentation checked on 2026-10-05

- https://developer.android.com/reference/android/webkit/WebView (`onPause`, `pauseTimers`, `resumeTimers`, termination handling)
- https://developer.android.com/reference/android/webkit/WebSettings (`LOAD_DEFAULT`, cache validation, media gesture policy)
- https://developer.android.com/develop/ui/views/layout/webapps/managing-webview (updated 2026-08-25; renderer termination/importance)
- https://developer.android.com/reference/android/view/MotionEvent (mouse source vs finger tool, button state and hover)
- https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/play (promise rejection)
- https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/seekable (valid seek ranges)
