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
6. Write only the precise code needed. Avoid speculative refactors or unused code.

Every line must be traceable to existing code or a clearly labeled minimal assumption.
