# VireoLumaTV — Agent Instructions

## Project
Simple WebView-only Android TV browser optimized for remote control and low-memory devices using Android System WebView.

## Stack
- Kotlin / Android (WebView)
- Gradle Kotlin DSL
- Native adblock-rust (Brave) integrated for arm64/armv7/x86_64
- Module: `:app` (the `:app:common` split for GeckoView was removed on 2026-10-28)

## Verification commands
Use the Gradle wrapper. For a functional change, run the affected unit tests first;
use `testGenericDebugUnitTest`, `lintGenericDebug` and `assembleGenericRelease`
before integration when the environment supports Android/Rust builds. CI on the
exact head is the integration gate; report local tests and CI separately.

## Priorities
- Keep the app small, simple, and predictable on low-memory Android TV hardware.
- WebView only — do not reintroduce GeckoView or multi-engine complexity.
- Optimize for TV remote navigation, focus, and D-pad usability.
- Apply MIT to the original application code as selected by the owner. Preserve separately licensed component notices and the native bridge MPL 2.0 terms; see LICENSE.md and NOTICE.md.
- Prefer native Android / WebView APIs. Avoid unnecessary dependencies.
- Do not invent APIs, behaviors, or engine features not present in the codebase.

## Target and validation (mandatory)
- Target Android TV, Google TV and compatible TV boxes on API 24+, with D-pad and gamepad input.
  Keep low-memory devices usable without limiting support to one RAM size or manufacturer.
  Select behavior through Android API levels, WebView feature checks and reported capabilities; never special-case a model.
  Validate the shipped armv7, arm64 and x86_64 variants and include low-memory and newer-device configurations.
- Verify in this order, and report only what was run: (1) build and unit tests, (2) Android TV emulator or
  device with D-pad input for focus and navigation changes, (3) a reference low-RAM device for memory and
  rendering claims. Do not ask the owner for a specific device unless the change cannot be checked otherwise.
- Never describe a change as performance-improved without a measurement; never call a flow validated without
  a run.

## Hard Rules for All Agents (mandatory)
- **Branches**: Never create or push branches whose name contains an AI name, model name, tool name, or any non-owner identifier (examples forbidden: `claude-*`, `gpt-*`, `grok-*`, `ai-*`, `agent-*`, `copilot-*`). Use only descriptive, owner-neutral names.
- **Authorship / signing**: Never commit, sign, or set author/committer to any name, email, or identity that is not the repository owner (`ReinierTutoriales`). Do not use AI names, tool names, or third-party identities in commit messages, trailers, or metadata.
- **Write only what is verified**: Read the relevant files, tests, contracts, and documentation before proposing or writing any change. Do not write code, comments, or docs for the sake of writing. Every addition must be the minimal change that solves a demonstrated need and is traceable to existing code or a clearly labeled assumption.
- **Documentation**: Update or create documentation only when it reflects verified current behavior. Do not invent APIs, policies, or guarantees. Prefer updating existing docs over creating new ones. Keep docs short, precise, and consistent with the code.
- **Optimal code**: Prefer the simplest correct solution that matches existing style and patterns. Avoid speculative refactors, unused helpers, extra abstractions, or "improvements" without measured benefit. Verify against tests and contracts before considering the change complete.

## Skills
Read only the relevant `.agents/skills/` instructions: `precise-coding` for
implementation, `testing-discipline` for functional changes, `tv-ui` for
focus/layout, `code-quality` for refactors, and `agent-governance` before Git
mutations. Use `source-migration` when replacing inherited code or resources.
After a verified regression or owner correction, apply the feedback loop in
`agent-governance`: fix and test the cause, retain only a reusable lesson in the
relevant existing skill, and consult it before the next related change.
The contracts and acceptance criteria live in
`docs/REGRESSION_POLICY.md`; do not duplicate them in skills.

## Reglas para modificar este repositorio

Antes de editar, consultar la sección afectada de `docs/REGRESSION_POLICY.md`.
Consultar `docs/ROADMAP.md` para prioridad y `docs/IMPLEMENTATION_PLAN.md`
cuando haga falta el criterio detallado; contrastar cualquier estado histórico
con el SHA actual.
Una auditoría o propuesta antigua no autoriza aplicar automáticamente sus parches.

## Contratos que debe conservar cada cambio

- La cápsula superior contiene dirección/búsqueda, Atrás, Adelante, Recargar,
  Inicio, escudo del bloqueador y menú. Cerrar pestaña está en el menú y en la X de cada pestaña.
  La barra inferior y la búsqueda por voz se eliminaron por petición del propietario
  (2026-10-27). No reinterpretar una acción existente en silencio.
- En fullscreen, Atrás debe cerrar el vídeo y restaurar la cápsula y las pestañas
  en la misma acción, con foco accionable; conservar el callback existente.
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

## Continuidad en sesiones con herramientas limitadas

- Al reanudar una tarea, consultar el estado remoto real: rama predeterminada, SHA, PR abiertos, CI y último release. La conversación anterior es contexto, no prueba del estado actual.
- Determinar qué acciones permite la conexión disponible (lectura, edición, PR, merge y workflows). No asumir terminal, compilador local, navegador interactivo, sesión persistente ni ejecución en segundo plano. Si una herramienta falta, utilizar las comprobaciones CI existentes sin atribuirse ejecuciones locales.
- Antes de editar: leer `AGENTS.md`, la habilidad pertinente, contratos afectados y archivos fuente. Trabajar en una rama descriptiva y hacer cambios mínimos verificables; no modificar `master` directamente para tareas ordinarias.
- Exigir CI satisfactoria en el SHA exacto del PR antes de integrarlo; tras el merge verificar el SHA de `master`. No confundir CI aprobada con pruebas físicas de TV.
- Publicar únicamente con petición expresa del propietario: comprobar versión, tag y release existentes, utilizar el flujo `.github/workflows/release.yml` y verificar el resultado de GitHub Actions y los artefactos. Nunca declarar una publicación completa solo porque se inició el workflow.
- Si la sesión termina con trabajo pendiente, registrar en la respuesta: repositorio, rama, SHA, PR, pruebas ejecutadas y no ejecutadas, bloqueo, siguiente acción exacta y estado del release. En la siguiente sesión volver a consultar esos datos; nunca confiar ciegamente en el informe previo.
- No incluir credenciales, claves de firma ni valores de secretos en informes, commits, registros o solicitudes.
