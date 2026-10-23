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
- Preserve license/attribution requirements (distinct name, app ID, icon, About screen credit to original TV Bro; see LICENSE.md, NOTICE.md, docs/audit/archive-2026-10-01/LICENSE_REBRANDING_AUDIT.md).
- Prefer native Android / WebView APIs. Avoid unnecessary dependencies.
- Do not invent APIs, behaviors, or engine features not present in the codebase.

## Working style
1. Brief analysis + risks (especially memory, focus, and license).
2. Propose the smallest grounded solution.
3. Implement cleanly, matching existing style.
4. Verify against existing code and tests.
5. Note any need for real Android TV device validation.

## Hard Rules for All Agents (mandatory)
- **Branches**: Never create or push branches whose name contains an AI name, model name, tool name, or any non-owner identifier (examples forbidden: `claude-*`, `gpt-*`, `grok-*`, `ai-*`, `agent-*`, `copilot-*`). Use only descriptive, owner-neutral names.
- **Authorship / signing**: Never commit, sign, or set author/committer to any name, email, or identity that is not the repository owner (`ReinierTutoriales`). Do not use AI names, tool names, or third-party identities in commit messages, trailers, or metadata.
- **Write only what is verified**: Read the relevant files, tests, contracts, and documentation before proposing or writing any change. Do not write code, comments, or docs for the sake of writing. Every addition must be the minimal change that solves a demonstrated need and is traceable to existing code or a clearly labeled assumption.
- **Documentation**: Update or create documentation only when it reflects verified current behavior. Do not invent APIs, policies, or guarantees. Prefer updating existing docs over creating new ones. Keep docs short, precise, and consistent with the code.
- **Optimal code**: Prefer the simplest correct solution that matches existing style and patterns. Avoid speculative refactors, unused helpers, extra abstractions, or "improvements" without measured benefit. Verify against tests and contracts before considering the change complete.

## Rules
- Match existing Kotlin/Android style and patterns.
- Keep changes focused; do not touch unrelated files.
- Prefer the simplest correct fix that preserves remote UX and low-memory behavior.
- Respect ProGuard/R8 rules and native library integration.

## Skills
Use skills from `.agents/skills/` when relevant, especially `precise-coding` and `agent-governance` for any code or documentation change.

## Reglas para modificar este repositorio

Antes de editar código, leer `docs/REGRESSION_POLICY.md` y `docs/ROADMAP.md`.
La política define contratos de producto y evidencias; el roadmap define el orden.
Leer también `docs/IMPLEMENTATION_PLAN.md` para tareas y aceptación detalladas.
Una auditoría o propuesta antigua no autoriza aplicar automáticamente sus parches.

## Contratos que debe conservar cada cambio

- La barra inferior sigue permitiendo Inicio, Atrás, Adelante, Recargar, cerrar
  pestaña y controles de bloqueo. La superior conserva dirección/búsqueda,
  voz cuando esté disponible, ajustes y salida. No eliminar una barra para
  resolver un problema de Atrás ni reinterpretar una acción existente en silencio.
- En fullscreen, Atrás debe cerrar el vídeo y mostrar ambas barras en la misma
  acción, con foco inferior y ruta a la superior; conservar el callback existente.
- Separar Atrás del sistema, historial de la página, cierre de fullscreen,
  cierre de IME/diálogo y apertura/cierre del menú. Probar cada transición.
- Foco visible y controles alcanzables con D-pad. Foco no ejecuta acciones ni
  oculta otra fila; los comandos se ejecutan sobre la pestaña que se ve seleccionada.
- Conservar historial, URLs, pestañas normales, favoritos, descargas e aislamiento
  privado. No cambiar Room, estados legacy, firma o applicationId como efecto
  secundario. No publicar versión/APK como parte de una refactorización.
- Optimizar recursos sin convertir «pestaña suspendida» en «pestaña vacía» ni
  impedir reanudación. El modo actual de una WebView viva es la base temporal;
  cambiarlo exige pruebas del nuevo contrato y presupuesto agregado medido.
- No desactivar Safe Browsing/TLS para reproducir vídeo ni permitir popups o
  descargas automáticas como solución de compatibilidad.

## Método y verificación

1. Registrar SHA base, fallo reproducible, comportamiento esperado y consumidores
   del código que se cambia. Leer pruebas actuales y contratos antes de editar.
2. Corregir una causa por commit. Separar lógica, rediseño visual, dependencias y
   build salvo dependencia técnica inseparable explicada en el PR.
3. Para cambios funcionales, añadir una prueba que detecte el fallo anterior y
   pruebe el resultado; ejecutar también las suites vecinas relevantes. No
   eliminar/debilitar aserciones para volver verde una regresión. Si un contrato
   cambia por petición del usuario, documentar el cambio y comprobar sus vecinos.
4. Exigir CI verde para el SHA exacto antes de integrar. Para renderer, vídeo,
   memoria y mando real, registrar también prueba de dispositivo; si falta,
   informar «pendiente de validación física», nunca «probado en TV».
5. Mantener un revert concreto sin reescribir historial; revisar migraciones y
   compatibilidad antes de revertir cambios persistentes.
6. No prometer cero regresiones. Distinguir cobertura automática, medición y
   riesgo residual. No introducir modificaciones especulativas de rendimiento.

Las listas de pruebas, escenarios críticos y fuentes oficiales están en la
política. Los cambios meramente documentales requieren revisión de enlaces,
coherencia y `git diff --check`; no necesitan pruebas nuevas que solo reflejen texto.
