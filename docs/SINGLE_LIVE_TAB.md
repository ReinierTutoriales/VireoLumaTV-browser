# Single live tab on low-memory devices

The visible tab is the only tab retained with a WebView. Switching captures the old
navigation state, detaches and destroys its WebView, then creates/restores the new
one. Open tab entries remain available, but background pages, timers, audio and
video stop. Restoring navigation history does not guarantee preservation of a
page's JavaScript variables or unsent forms (Android WebView saveState limitation).

Normal new tabs allocate only after the old renderer is released. Initial
navigation remains with the caller so it is not requested twice. Chromium popups
require a new WebView for window transport: the source is released on the next UI
queue turn after that callback completes. This is a brief transport overlap, not a
second retained background tab. Stale popup requests are closed instead of taking
focus back after the user switches tabs.

Missing favicons use the existing placeholder after HTTP/manifest/default-icon
lookup; favicon lookup no longer launches a hidden renderer or executes the page.
HTTP icon size bounds, cache and private-mode persistence rules remain intact.

Automated regression coverage: allocation/destruction ordering, captured history,
restoration without URL reload, normal/private tabs, same-tab selection and
caller-owned initial navigation. Existing privacy, atomic-save and renderer-loss
tests remain relevant. W2_MEASUREMENT.md describes the older two-WebView baseline;
it is not a measurement of this change.

Physical validation on the 2 GB, 32-bit onn device is still required: open four
heavy tabs, alternate among them, use back/forward, open a JavaScript popup,
switch during a popup, play video/fullscreen, confirm background playback stops,
and record browser/renderer PSS before comparing against W2. Do not infer measured
RAM savings from the automated lifetime checks.
