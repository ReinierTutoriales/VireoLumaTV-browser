---
name: testing-discipline
description: Use when changing product logic, adding features, or fixing regressions in VireoLumaTV. Selects relevant tests and records what CI and device checks actually covered.
---

# Testing Discipline

- Identify the changed behavior and run its focused test first; require an assertion
  that detects the old defect for functional fixes.
- Run affected neighboring suites. Do not weaken existing assertions.
- Before integration, check the exact SHA in CI for unit tests, lint and a minified
  release build. Record any unavailable check rather than treating it as passed.
- For WebView, gamepad, fullscreen, video or memory, name the physical TV check
  still needed; tests using doubles do not validate the device path.
- Use `docs/REGRESSION_POLICY.md` for the N01–N10 contracts and evidence matrix.
