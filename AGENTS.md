# VireoLumaTV: reglas para modificar este repositorio

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
