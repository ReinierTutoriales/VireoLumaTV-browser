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
| Tema guardado fuera de rango | Corrección y pruebas de regresión pasan en CI. |
| User-Agent personalizado | Corrección y pruebas del selector pasan en CI. |
| Room v19 | Esquema generado, inspeccionado y versionado; CI comprueba cambios inesperados. |
| JavaScript de inicio, controles y YouTube | Tres suites locales pasan. |
| Pruebas Android y APK debug/release | Primera CI completa en verde; consultar los checks del PR para el tip actual. |
| Publicación de rama y CI remoto | Rama publicada y CI ejecutada en el PR #76. |
| Limpieza remota de ramas | Ocho ramas verificadas eliminadas; seis referencias históricas conservadas. |
| Rendimiento, reproducción y mando en TV | Requieren dispositivo físico y medición. |

## Siguiente trabajo en la misma rama

1. Exigir checks verdes para el tip actual del [PR #76](https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/76) antes de integrar.
2. Verificar ajustes en el TV y medir antes de cambiar memoria, WebView o reproducción.
3. Revisar el trabajo único de las ramas históricas conservadas antes de eliminarlas.

## Reglas de integración

- Continuar sobre una rama de trabajo; commits pequeños con un propósito comprobable.
- Mantener código, pruebas y documentación coherentes dentro del mismo trabajo.
- No aplicar propuestas antiguas por su nombre ni por diferir sus SHA.
- Exigir CI verde antes de integrar código y prueba física para afirmar cambios
  de comportamiento de WebView, vídeo, memoria, fullscreen o bloqueo.
- Conservar las migraciones y la compatibilidad de estados `gecko:` existentes.
- No reescribir el historial publicado para limpiar la lista de ramas.
- No cambiar firma, versión ni publicar APK como efecto secundario de una limpieza.
