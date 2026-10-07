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
| Restauración rechazada y UA Default efectivo (F03/F07) | Arreglos publicados con pruebas que fallaron antes; aceptación mediante CI del tip actual. Ver [lote sin TV](audit/OFF_DEVICE_FIXES_20261007.md). |
| Actualización adblock: solicitudes y disparadores | Correcciones publicadas con HTTP local/Robolectric; aceptación mediante CI del tip. Salud por fuente y sitios reales pendientes. Ver [lote adblock](audit/ADBLOCK_OFF_DEVICE_20261007.md). |
| Renovación visual TV | Recursos de barras, pestañas y paleta; capturas/contraste/geometría automáticos. Validación de mando y rendimiento real pendiente. Ver [lote UI](audit/TV_UI_REFRESH_20261007.md). |
| Rendimiento, reproducción y mando en TV | Requieren dispositivo físico y medición. |

## Línea de trabajo vigente

Seguir el [plan detallado de ejecución](IMPLEMENTATION_PLAN.md): tareas por
etapa, contrato fullscreen→barras→puntero, bloqueo, recursos, actualización
de aplicación y condiciones de release. Este es el desarrollo del roadmap,
no una cola adicional de cambios.

La [política de estabilidad y modernización](REGRESSION_POLICY.md) y `AGENTS.md`
son el contrato para este trabajo. La auditoría identifica evidencia; no impone
parches que eliminen las barras ni cambia por sí sola el diseño de navegación.

| Etapa | Trabajo | Condición de cierre |
| --- | --- | --- |
| 0 — Protección | Política, contrato N01–N10, plantilla PR y mapa de cobertura. | Documentado en esta rama; ampliar pruebas de eventos reales y TV sigue pendiente. |
| 1 — Navegación | Estados separados para menú, barras, historial, IME, fullscreen, dialogs y salida; corregir restauración y callbacks. | Conservar controles inferiores/superiores y su foco; sin bucle ni pestaña vacía; regresiones cubiertas y validación TV. |
| 2 — Experiencia protegida | Reintento observable de adblock, reglas válidas, política de popups/redirecciones y descargas explícitas con límites. | Sitios de prueba legítimos/abusivos; login, target=_blank y descargas voluntarias preservados; sin ráfagas de diálogos. |
| 3 — Pestañas y recursos | Ahorro y evaluación de Equilibrado, precarga acotada, estado restaurable y presupuestos agregados. | Base y mediciones en onn 2 GB y dispositivo low-RAM; timers globales coordinados; pestaña activa y vídeo prioritarios. |
| 4 — Motor WebView | Evaluar startup/navegación/cache/favicons de APIs estables existentes y recuperación. | Feature checks por proveedor, fallback, release y medición antes/después. |
| 5 — UI TV | Renovar barra superior, estilos, tipografía/iconos/espaciado/foco y transiciones conservando acciones y barra inferior. | Propuesta visual, capturas comparables y N01–N10 intactos; mando, contraste y legibilidad comprobados. |
| 6 — Actualizaciones y release | Revisar librerías/toolchain por grupos compatibles, pruebas minificadas, perfiles y documentación. | Changelog oficial actual, SHA con CI verde y evidencia pertinente; no actualización masiva ni release implícito. |

Correcciones concretas F01–F09 y riesgos R01–R08 están en la
[auditoría Android TV](audit/ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md).
F01 requiere acordar la secuencia de salida respetando N01–N10; no quitar el
menú ni reinterpretar el botón inferior Atrás. Estas etapas son pendientes de
implementación, no mejoras ya entregadas. Comenzar cada etapa con los fallos
reproducibles y sus pruebas, sin reescritura general del motor.

Las ramas históricas conservadas se revisan por contenido único antes de
eliminarlas; no se usan como cola automática de parches.

## Reglas de integración

- Continuar sobre una rama de trabajo; commits pequeños con un propósito comprobable.
- Mantener código, pruebas y documentación coherentes dentro del mismo trabajo.
- No aplicar propuestas antiguas por su nombre ni por diferir sus SHA.
- Exigir CI verde antes de integrar código y prueba física para afirmar cambios
  de comportamiento de WebView, vídeo, memoria, fullscreen o bloqueo.
- Conservar las migraciones y la compatibilidad de estados `gecko:` existentes.
- No reescribir el historial publicado para limpiar la lista de ramas.
- No cambiar firma, versión ni publicar APK como efecto secundario de una limpieza.
