---
name: code-quality
description: Use when optimizing, cleaning, or reviewing code. Prioritizes simplicity, low-memory behavior, maintainability, and TV remote usability.
---

# Code Quality

- Analyze for real improvements in clarity, performance, memory use, and remote navigation.
- Prefer the simplest correct solution that matches existing style and keeps the app small/predictable.
- If a superior alternative exists, choose the most production-solid one for Android TV; state what improved.
- Do not reintroduce removed complexity (Gecko, multi-engine, heavy deps).
- Keep changes focused and measurable against low-memory TV constraints.
- Verify against existing tests and code.
- Note any remaining need for real Android TV device validation.
