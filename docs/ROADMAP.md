# VireoLumaTV — trabajo unificado

Base comprobada: `521c214`, build 80 de `fixes/audit`.
Rama de trabajo: `fixes/consolidated-audit-20261007`.

El [registro de auditoría actual](audit/CONSOLIDATED_AUDIT_20261007.md)
reúne los defectos comprobados, cambios, decisiones sobre ramas y verificaciones.
El [roadmap anterior](audit/archive-2026-10-07/ROADMAP_PRE_CONSOLIDATION.md)
queda conservado como referencia histórica; sus pendientes requieren contraste
con el código actual antes de aplicarlos.

## Estado

| Área | Estado de esta rama |
| --- | --- |
| Tema guardado fuera de rango | Corrección y pruebas de regresión preparadas. |
| User-Agent personalizado | Corrección y pruebas del selector preparadas. |
| Room v19 | Configuración KSP corregida; generación e inspección del JSON pendientes de CI. |
| JavaScript de inicio, controles y YouTube | Tres suites locales pasan. |
| Pruebas Android y APK debug/release | Pendientes; no equivalen a las pruebas JavaScript. |
| Publicación de rama y CI remoto | Bloqueadas por revisión automática de autorización. |
| Limpieza remota de ramas | Pendiente; las ramas remotas conservan su contenido. |
| Rendimiento, reproducción y mando en TV | Requieren dispositivo físico y medición. |

## Siguiente trabajo en la misma rama

1. Publicar la rama y abrir un único PR contra `fixes/audit`.
2. Ejecutar CI, revisar resultados y resolver solo fallos comprobados.
3. Recuperar el esquema generado de Room, inspeccionarlo, versionarlo y comprobar
   cambios de esquema en CI. Sustituir el PR antiguo de Room después de verificarlo.
4. Verificar ajustes en el TV y medir antes de cambiar memoria, WebView o reproducción.
5. Retirar ramas integradas después de comprobar su contenido y conservar cualquier
   trabajo único o prueba histórica útil.

## Reglas de integración

- Continuar sobre una rama de trabajo; commits pequeños con un propósito comprobable.
- Mantener código, pruebas y documentación coherentes dentro del mismo trabajo.
- No aplicar propuestas antiguas por su nombre ni por diferir sus SHA.
- Exigir CI verde antes de integrar código y prueba física para afirmar cambios
  de comportamiento de WebView, vídeo, memoria, fullscreen o bloqueo.
- Conservar las migraciones y la compatibilidad de estados `gecko:` existentes.
- No reescribir el historial publicado para limpiar la lista de ramas.
- No cambiar firma, versión ni publicar APK como efecto secundario de una limpieza.
