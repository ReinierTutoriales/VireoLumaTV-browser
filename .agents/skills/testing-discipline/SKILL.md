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

## Convert failures into regression coverage

- Turn a reproducible error into the smallest fixture that exercises the failing
  behavior, then check the correction and adjacent contracts. Where practical,
  demonstrate failure against the previous code; state when that comparison was
  not run or cannot detect the original failure.
- Classify a failed check before acting: product logic, test fixture, build,
  inventory or environment. Read its evidence; do not weaken an assertion or
  change unrelated product code merely to make the run green.
- Before sending a tree to CI, inspect tracked paths and the diff, verify the
  source inventory, and exclude generated caches or unrelated work. After any
  code change, require results for the new SHA rather than reusing an older green.
- Report expected behavior, automatic checks, device measurements and delivery
  state separately. A draft, merged source and an installed official APK are
  different states; never imply that an unbuilt change is already on the TV.

## Security and release gates

- Classify each security report as proven defect, exposure with stated preconditions, or unverified suspicion. Do not call a code inspection a penetration test.
- Pair security fixes with adversarial cases: cross-origin iframe calls, untrusted certs, path traversal and encoded separators, intent extras, failed downloads, and privacy-mode state; preserve normal playback and browsing paths.
- Before a version bump, check CI on every merged change and again on the exact combined release candidate; do not use an earlier passing run as proof. Verify versionCode, versionName, signing configuration and unchanged user data contracts.
- Distinguish a merged PR, successful CI, triggered release workflow and published signed APKs. Confirm the published tag, workflow conclusion and APK/checksum/signature assets before saying a version has shipped.
- Release CI does not establish better loading speed, lower RAM usage or success on physical TV; require before/after device measurements for those claims.
