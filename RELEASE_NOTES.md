# VireoLumaTV 2.0.10

**Android versionName:** `2.0.10`  
**Android versionCode:** `104`

This release consolidates security hardening, browser navigation fixes, and targeted efficiency improvements since 2.0.9.

## Security and privacy
- Hardened list transport, certificate and backup handling (#114–#119).
- Replaced the privileged blob-download JavaScript bridge with origin-aware WebMessage transport and bounded 512 KiB chunks (#121).
- Camera, microphone and location prompts identify the requesting origin, including third-party frames. Location permission is no longer retained; previously stored geolocation grants are cleared (#126).
- One-time certificate-error approval is bound to the approved host and certificate; unused approvals are cleared on navigation or completed page loads (#127). WebView may still remember an accepted SSL decision internally for the same host during the session.

## Navigation and playback
- Fixed fullscreen cleanup when a tab is detached (#125).
- Remote-control navigation now distinguishes exiting fullscreen with Back/Menu from exiting via the player, exits D-pad mode before YouTube history navigation, previews tabs without committing until OK, and keeps focus in the tab strip when closing the active tab (#129).

## Performance and reliability
- Added Gradle wrapper download retries (#122).
- Reused a single adblock verdict per request and cached bounded verdicts (#123).
- Avoided repeated missing-site-settings database lookups while keeping cache invalidation correct (#124).
- Corrected singular/plural wording for blocked items and active downloads in 12 languages (#128).

## Compatibility and known trade-offs
- Blob downloads are intentionally unavailable on older WebView implementations without `WEB_MESSAGE_LISTENER`, where the requesting frame cannot be reliably verified.
- Sites requesting location will prompt again rather than relying on a persistent permission.
- The existing hidden-WebView memory-management policy was not changed. Chunked blob downloads reduce peak transfer-buffer requirements; no overall RAM, CPU, latency or playback improvement percentage is claimed.
- Automated tests and CI do not substitute for validation on physical Android TV devices. Media codec, DRM, HLS and DASH support remain dependent on Android, the WebView provider and device capabilities.

## Distribution and verification
Release APKs are built separately for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. Verify the release signature and SHA-256 checksums using the accompanying `SIGNING.txt`, `SHA256SUMS.txt` and signature file before installing.
