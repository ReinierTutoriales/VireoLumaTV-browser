---
name: code-quality
description: Use when optimizing, cleaning, or reviewing code. Prioritizes simplicity, low-memory behavior, maintainability, and TV remote usability.
---

# Code Quality

- Analyze for real improvements in clarity, performance, memory use, and remote navigation.
- Prefer the simplest correct solution that matches existing style and keeps the app small/predictable.
- If a superior alternative exists, choose the most production-solid one for Android TV; state what improved.
- Do not reintroduce removed complexity (Gecko, multi-engine, heavy deps).
- Keep changes focused and measurable against low-memory TV constraints. Do not write for the sake of writing.
- Verify against existing tests and code before considering the change complete.
- Update documentation only when it reflects verified current behavior.
- Note any remaining need for real Android TV device validation.
- Follow agent-governance rules on branches and authorship.

## Replacing older code

Replace an implementation only when the alternative improves security,
correctness, performance, maintainability or compatibility from minSdk to
targetSdk, and say which. Check the current official Android/AndroidX
reference first, then keep the replacement inside the project's dependencies:

- Foreground services: `ServiceCompat.startForeground` with the declared
  type, before the work starts; handle refusal (Android 12+ background start,
  Android 14+ type permission, Android 15+ dataSync time limit and `onTimeout`).
- Notifications: monochrome small icon, `NotificationCompat`, a category.
- Activity results: an `ActivityResultContract`, not request codes or raw extras.
- Callbacks: a Kotlin function type or a sealed command type instead of
  multi-method listener interfaces that callers implement with empty bodies.
- Main-thread drawing: `invalidate()`; `postInvalidate()` only off the main thread.

Renaming, reformatting or moving code to change `git blame` is not a replacement.

## Avoid repeating failed optimization choices

- Trace network requests, script injection, service workers and per-site choices
  before changing a playback or loading path. Reject a proposed path that bypasses
  the required blocking policy; retain the working path until equivalent coverage
  is implemented and checked.
- Evaluate codec capabilities independently. Do not infer VP8 support from VP9,
  or efficiency from hardware availability alone. Keep capability enumeration
  shared and bounded instead of adding repeated scans on interaction paths.
- Separate a correctness/resource fix from a measured performance improvement.
  Name the affected resource and tradeoff; preserve restoration and functionality.
  Smaller APK size, newer APIs and fewer lines alone do not prove faster playback.
- Validate compressed assets through Android's loader and the built APK. Source
  hashes alone do not prove readability; implicit filename/codec handling belongs
  to the format contract, and an already compressed payload needs no second layer.

## Security-sensitive WebView reviews

- Treat WebView documents, subframes, external intents, local assets, downloads and update URLs as separate trust boundaries. Review Android's current official guidance and the actual provider capabilities before changing any of them.
- An `addJavascriptInterface` caller has no trustworthy frame-origin identity. Do not infer it from the top-level URL or a URL passed by JavaScript. Scope privileged operations using origin-verifiable mechanisms where available, and retain working media, filtering and download paths until tested replacements exist.
- For one-time TLS exceptions, compare actual certificate bytes; missing cryptographic identity must fail closed. Preserve normal certificate validation and Safe Browsing.
- Resolve packaged asset paths against an explicitly bounded origin and directory; reject traversal, encoded path separators and ambiguous URLs. Verify file access, backup, intent extras and FileProvider grants independently.
- Preserve signed release checksum verification and check update redirects, dependency provenance and workflow privileges. Treat any security finding as confirmed only when supported by code and tests; otherwise state the preconditions and remaining validation.
