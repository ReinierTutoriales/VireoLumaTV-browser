---
name: source-migration
description: Use when replacing existing code, layouts, assets or text in VireoLumaTV and recording whether a module can be considered independently implemented. Tracks provenance and behavior without mistaking Git blame or renamed files for proof of ownership.
---

# Source migration

1. Start from the current branch SHA and the affected rows in
   `docs/audit/SOURCE_INVENTORY.tsv`. Its labels are
   historical: confirm each file still exists and compare actual contents.
2. Define the observable contract from product requirements, call sites and
   focused tests: inputs, outputs, persisted data, Android API levels, focus
   and error paths. Read the N01–N10 cases in `docs/REGRESSION_POLICY.md` when
   relevant. Keep a test that detects a behavior regression.
3. Implement the replacement from that contract. Preserve Room schema and
   user data unless an explicit tested migration is part of the task. Do not
   treat formatting, renaming, moving files or changing `git blame` as a rewrite.
4. Compare old and new behavior with affected unit tests, neighboring suites,
   lint and minified release CI on the exact SHA. List any WebView, remote,
   renderer or memory behavior requiring a physical TV check.
5. Measure with `python3 scripts/measure-inherited-lines.py --rev <SHA>`
   (per-line `git blame -w -C`; blank, bracket, import and comment lines are
   not counted) and report its numbers with the SHA, never an estimate. Lines
   Android requires as written (`super.onCreate`, `setContentView`,
   `android:exported`, `layout_width`) are not rewrite targets.
   Update the affected inventory rows and `docs/audit/README.md`
   with the measured method and SHA. Separate still present routes from actual
   retained expression; never extrapolate a whole-project percentage from a
   single module.
6. The migration is closed (see `docs/audit/README.md`): upstream notices were
   removed by the owner's decision once only platform-required lines remained,
   and the repository is no longer a GitHub fork. Do not reintroduce upstream
   code, text or notices; new code follows the replacement criteria in
   `code-quality`.

Use `precise-coding` for implementation, `testing-discipline` for evidence,
and `agent-governance` for commits; this skill only covers migration provenance.
