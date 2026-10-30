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
