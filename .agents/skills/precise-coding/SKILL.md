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
  requirements. Resolve conflicting dates or channels before calling a version
  latest; never infer an installed engine's capabilities from a spoofed user agent.
