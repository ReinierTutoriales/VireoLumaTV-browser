# VireoLumaTV — Agent Instructions

## Project
Simple WebView-only Android TV browser optimized for remote control and low-memory devices (based on TV Bro, Gecko removed).

## Stack
- Kotlin / Android (WebView)
- Gradle Kotlin DSL
- Native adblock-rust (Brave) integrated for arm64/armv7/x86_64
- Modules: `:app`, `:app:common`

## Commands
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
```
Prefer the wrapper. Release builds use R8.

## Priorities
- Keep the app small, simple, and predictable on low-memory Android TV hardware.
- WebView only — do not reintroduce GeckoView or multi-engine complexity.
- Optimize for TV remote navigation, focus, and D-pad usability.
- Preserve license/attribution requirements (distinct name, app ID, icon, About screen credit to original TV Bro; see LICENSE.md, NOTICE.md, docs/LICENSE_REBRANDING_AUDIT.md).
- Prefer native Android / WebView APIs. Avoid unnecessary dependencies.
- Do not invent APIs, behaviors, or engine features not present in the codebase.

## Working style
1. Brief analysis + risks (especially memory, focus, and license).
2. Propose the smallest grounded solution.
3. Implement cleanly, matching existing style.
4. Verify against existing code and tests.
5. Note any need for real Android TV device validation.

## Rules
- Match existing Kotlin/Android style and patterns.
- Keep changes focused; do not touch unrelated files.
- Prefer the simplest correct fix that preserves remote UX and low-memory behavior.
- Respect ProGuard/R8 rules and native library integration.

## Skills
Use skills from `.agents/skills/` when relevant, especially `precise-coding` for any code change.
