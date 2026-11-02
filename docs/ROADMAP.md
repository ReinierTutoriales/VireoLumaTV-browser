# VireoLumaTV — mantenimiento actual

El desarrollo parte de `master`, con Android System WebView como único motor.
Las versiones de bibliotecas y herramientas se fijan en `gradle/libs.versions.toml`
y `native/adblock-jni/Cargo.lock`. No se conserva una cola de cambios del fork.

## Estado comprobado

- GitHub identifica este repositorio como independiente.
- La versión publicada que se conserva es 2.0.14. El código de `master` puede
  incluir correcciones posteriores que todavía no se han publicado.
- CI comprueba inventario, pruebas, esquemas Room, compilaciones debug/release,
  lint y correspondencia entre los assets del APK y sus fuentes.
- Las licencias y el icono de Inicio se copian durante el build desde sus fuentes
  únicas; no se mantienen duplicados generados dentro de `src/main/assets`.
- Los informes fechados de `docs/audit/` son evidencia histórica, no instrucciones
  ni una cola de parches para aplicar.

## Prioridades

1. Mantener el inventario y los avisos de componentes coherentes con el árbol actual.
2. Corregir únicamente defectos reproducibles, con pruebas sobre el SHA que se integra.
3. Revisar las dependencias por grupos compatibles cuando exista un motivo concreto.
4. Mantener los contratos de navegación, privacidad, descargas, actualización firmada
   y restauración definidos en [la política de regresión](REGRESSION_POLICY.md).

No hay mediciones nuevas que permitan afirmar mejoras de memoria o rendimiento.
La comprobación en dispositivo queda aplazada por decisión del propietario.

## Integración

Trabajar en una rama descriptiva desde el `master` remoto actual. Revisar los cambios,
ejecutar las comprobaciones pertinentes y exigir CI verde sobre el SHA exacto antes
de integrar. Eliminar la rama al terminar. No reescribir el historial como parte de
una limpieza y no publicar versiones sin petición expresa.

[AGENTS.md](../AGENTS.md) y la política de regresión contienen las reglas vigentes.
El [plan de implementación](IMPLEMENTATION_PLAN.md) describe contratos y escenarios;
sus referencias fechadas deben contrastarse con el código actual antes de actuar.
