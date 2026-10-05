# YouTube blocking and publication follow-up — 2026-10-05

## Findings
Default subscriptions remain EasyList, EasyPrivacy and EasyList Spanish. Custom subscriptions remain a single user-selected URL.
The existing Brave ad-block 0.0.4 integration evaluates network requests, without a cosmetic or scriptlet layer. The inspected optimization history does not establish a specific removed YouTube script or an exact causal regression. Passing mocked matcher tests does not establish that YouTube ads are blocked on a physical TV.

HaGeZi PRO++ is a domain list, including its Adblock-format distribution. It cannot separate ads served by the same host as the wanted video. Replacing URL/path/context lists with PRO++ would lose browser-specific rules and would not supply YouTube scriptlets. It therefore is not the default for this resource-constrained browser.

## Changes
- Keep the three complementary browser lists, refresh every 7 days rather than 30; partial-refresh retry remains one day, serialized matching cache still activates before downloads.
- Recognize unambiguous JSON Accept headers as XHR; mixed HTML/JSON remains unknown.
- Add a small HTTPS YouTube/youtube-nocookie origin-scoped content layer before page JavaScript using WebViewCompat.addDocumentStartJavaScript when supported.
- Scrub playerAds, adPlacements and adSlots only from recognizable player response objects and bounded known bootstrap envelopes. Preserve streamingData, videoDetails, authentication, signatures and media URLs.
- Handle inline ytInitialPlayerResponse/ytplayer assignment, JSON.parse, player endpoint Response.json/text and XHR response/responseText. Keep original parsing errors/revivers, response types, headers and non-player endpoints.
- Native CSS hides known ad containers. No polling, extra HTTP requests, document-wide mutation observation, forced playback/seeks or bitrate changes. XHR text rewriting is capped at 2 MiB with weak per-request memoization.
- Respect the requesting tab's adblock switch, including construction before attach; disable the content layer/CSS and remove future document-start registration. Release the script registration when destroying the WebView.
- Old providers get best-effort injection at page start/end; it cannot guarantee intercepting the earliest player bootstrap. A current Android System WebView is necessary for early injection.
- Release certificate verification supports both numbered and SDK-range signer labels, still demands one distinct identical signer in both verified APKs. versionName remains 1.0.0, versionCode increases to 72.

## Verification
Node fixtures cover player fields versus retained signed media URLs, bootstrap assignment, nested envelopes, JSON revivers/errors, fetch/XHR scoping, disable/enable, reinjection, HTTPS and host isolation.
Robolectric tests cover origin policy; classifier tests cover extensionless JSON and mixed Accept. Full unit/debug/release checks run in CI and again before release upload, including signature equality.

## Limits and device checks
This narrowly maintained YouTube layer is not a complete uBlock Origin/AdGuard scriptlet implementation. YouTube experiments, anti-adblock changes and server-stitched ads can evade it. Test logged-in/logged-out playback, pre-roll/mid-roll, mobile/desktop/embedded player, skip behavior, tab switches and blocker toggle on the user's TV. Do not claim zero ads or that the reported real-device regression is reproduced/resolved solely from fixture tests.
Published release files must be checked for the new source commit and APK digest after successful upload.

## Primary references
- https://github.com/hagezi/dns-blocklists/blob/main/FAQ.md — domain filtering limits and tier tradeoffs.
- https://github.com/uBlockOrigin/uAssetsCDN/blob/main/filters/filters.txt — current YouTube player data keys (reference, not copied implementation).
- https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E) — feature gate, origins and pre-load timing.
- https://developer.android.com/reference/android/webkit/WebViewClient — request interception limits.
- https://android.googlesource.com/platform/tools/apksig/+/master/src/apksigner/java/com/android/apksigner/ApkSignerTool.java — numbered and SDK-range certificate labels.
