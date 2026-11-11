---
name: precise-coding
description: Use when writing or modifying any code. Forces grounded output by verifying against the existing codebase. Reduces hallucinations and token waste.
---

# Precise Coding (Anti-Hallucination)

1. Read the relevant existing Kotlin/Java/native files, layouts, and tests before writing anything.
2. Never invent Android APIs, WebView behaviors, adblock interfaces, focus handling, or features not already present or clearly documented.
3. If unclear, state the assumption explicitly instead of inventing.
4. Prefer the smallest change that matches existing patterns and preserves TV remote UX and low-memory behavior.
5. Do not claim correctness or test passage unless reasoned from the actual project files.
6. Write only the precise code needed. Do not write for the sake of writing. Avoid speculative refactors or unused code.
7. Update documentation only when it reflects verified current behavior. Prefer editing existing docs over creating new ones.
8. Follow agent-governance rules: no AI-named branches, no authorship other than the repository owner.

Every line must be traceable to existing code or a clearly labeled minimal assumption.

## Apply lessons before proposing a replacement

- State the expected user behavior and trace the existing consumers before adding
  a library or replacing a component. Confirm that the proposal preserves the
  owner's current constraints; API availability alone does not establish fit.
- Distinguish the system WebView, the site's own player, an embedded player API
  and a native player. Do not present them as interchangeable or promise that
  filters, sessions, quality controls or DRM transfer automatically between them.
- Verify current versions in primary release documentation and compatibility
  requirements (Google Maven and Maven Central `maven-metadata.xml`, Gradle's
  `versions/current`), and read a library's sources jar before using an API you
  have not seen in this repository. Resolve conflicting dates or channels before calling a version
  latest; never infer an installed engine's capabilities from a spoofed user agent.
- When combining rule engines, test precedence across their boundaries: a first
  ordinary match or exception must not hide a later higher-priority rule.
- JSON edits must target own data properties; array removals must preserve the
  original selected indices even when a query lists them out of order.
- When wrapping DOM mutations, capture references that the mutation consumes
  before calling it; access attachment-dependent state after it returns.
- Read focused text-control selections through their selection APIs; document
  selection alone does not describe text selected inside an input or textarea.

## Delivery efficiency for repository tasks

- Work toward the user's acceptance criterion, not a sequence of preparation-only commits. Before editing, map the minimum complete vertical slice (UI, activity callbacks, resources, tests) and implement the related changes together.
- Prefer one cohesive multi-file commit over many trivial commits. Avoid repeated status-only reports and documentation-only changes while the requested feature remains disconnected.
- Check the exact current CI head once, inspect failing logs to identify a reproducible cause, and distinguish transient infrastructure errors from code regressions. Do not rerun unaffected jobs or repeatedly poll without a decision to make.
- After changes, verify the exact head; report implemented behavior, validation, and remaining gaps precisely. Do not call a component integrated merely because it compiles or exists in the source tree.

## Single-pass edits (lessons from verified failures)

- Before editing, list every consumer of what changes: grep the symbol, its members
  (including interface default methods), its resource IDs, and its key or event in
  `app/src`, `scripts/`, `baselineprofile/` and `.github/`. Deleting a type or changing a
  key's meaning breaks callers the compiler of one module does not see.
- Scripted edits must anchor on unique surrounding context and assert one match; a
  first-match replace once landed in the wrong `when` branch. Read the resulting hunk
  before committing.
- A changed Kotlin or Compose signature changes its JVM descriptor: update the
  matching lines in `app/src/main/generated/baselineProfiles/*.txt` (Compose appends
  `Composer`, `$changed` ints per 10 parameters and `$default` ints per 31), or the
  method loses ahead-of-time compilation until the profile is regenerated.
- Chain shell steps with `&&` or `set -e`; a failed `git add` followed by `;` commits
  the wrong set. Keep a file's trailing newline when rewriting it.
- Run every check the session can run before pushing (`cargo test --locked`,
  `python3 -m py_compile`, `git diff --check`, inventory script); leave only Android
  builds to CI; do not wait for it (see `agent-governance`, Long-running jobs).
- Query the remote (releases, branches, merged PRs) before stating repository facts;
  the previous turn is not evidence.

## Professional code, not filler

- Match the surrounding naming, structure and comment density. Comments state why
  (contract, platform fact, measured cost), never what the next line does.
- No speculative helpers, wrappers, logging or defensive branches the callers do not
  need; delete code made dead by the change in the same commit.
- Put measured numbers and the method in the commit or PR, not in the code.
