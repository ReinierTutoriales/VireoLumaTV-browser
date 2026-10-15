---
name: testing-discipline
description: Use when adding features or changing logic. Enforces running relevant tests and not relaxing existing coverage.
---

# Testing Discipline

- Prefer running or reasoning about:
  ```bash
  ./gradlew test
  ./gradlew assembleDebug
  ./gradlew assembleRelease
  ```
- Do not relax or weaken existing tests.
- Prefer focused tests for changed behavior (especially adblock, navigation, downloads, or WebView integration).
- A change is incomplete until consistent with the test baseline and release build expectations (R8).
- Explicitly call out gaps that need real Android TV device validation.
