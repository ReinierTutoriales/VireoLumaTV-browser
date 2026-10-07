# Actualización adblock verificable sin TV

Fecha: 2026-10-07. Base: `f1ad0b4`. Rama `fixes/consolidated-audit-20261007`,
PR #76. Este lote aborda disparadores y concurrencia de actualización;
no certifica compatibilidad con sitios reales ni cierra toda la etapa ADS.

## Fallos y evidencia previa

1. Una solicitud manual durante restauración de caché reciente se descartaba
   por `clientLoading`; la carga automática terminaba sin descargar.
   Prueba publicada antes del arreglo: `76169ca`.
   [CI 37618863779](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37618863779):
   114 pruebas, una fallida (`manualRefreshDuringCacheRestoreIsNotLost`), una
   omitida. La aserción exige una petición HTTP al servidor local después de
   liberar la restauración; el código anterior no la realizaba.
2. Inspección de la base: actualización automática solo al crear el modelo y
   cambiar fuente. `MainActivity.onResume` y su receptor de conectividad no
   solicitaban actualización; no había comprobación periódica de fechas.
   Guardar el plazo de reintento no ejecutaba trabajo en una sesión larga.

## Correcciones

- `0641da9`: un Job compartido para actualización en curso y posible solicitud
  manual pendiente. Una petición manual durante carga exclusiva de caché se
  atiende después; peticiones mientras ya se descarga comparten esa descarga.
  Esperar ese Job incluye el trabajo pendiente. Cambio de fuente se procesa
  después y no publica la generación anterior como fuente actual.
- `7176ed8`: comprobar al volver a RESUMED, cada 15 minutos de sesión activa y
  al recibir conectividad disponible. Son comprobaciones de fechas, no
  descargas forzadas: conservan intervalo de 7 días y reintento de 1 día.
  Una sola comprobación periódica; se cancela en `onPause` y al limpiar modelo.
  Una descarga ya iniciada puede terminar al pausar; no se añade servicio ni
  trabajo periódico del sistema en segundo plano. Red/compilación siguen en IO.

## Cobertura y aceptación

`AdblockUpdateTest` usa HTTP local, motor doble y Robolectric SDK 28:

- Caché válida bloquea mientras llega la respuesta; descarga válida la sustituye.
- HTML, respuesta vacía y HTTP 503 conservan reglas/fecha de último éxito;
  no recompilan ni restauran otra instancia activa y programan reintento.
- Solicitudes concurrentes comparten Job; solicitud manual durante caché no se pierde.
- Cambio de fuente durante descarga termina con reglas de la fuente actual.
- Reloj del looper avanzado: reintento futuro no descarga, plazo vencido se
  atiende en sesión larga, pausa cancela el bucle y reanudación comprueba enseguida.
  No se llama manualmente al actualizador para provocar la comprobación vencida.

Aceptación: CI completa del tip del PR, incluyendo suites Android/JavaScript,
APK debug, ausencia de deriva Room y APK release minificado. Consultar checks
para su resultado y SHA exacto; este documento no anticipa su éxito.

## Alcance pendiente

La integración de eventos se revisó en código; el receptor Android real, WebView,
matcher nativo, consumo y compatibilidad de sitios requieren dispositivo/proveedor.
Las pruebas de modelo no prueban el sistema de red Android ni anuncios reales.
La salud individual de fuentes, UI de última comprobación/reintento, actualización
parcial multi-fuente y protección frente a popups/descargas siguen pendientes.
No se cambiaron navegación, barras, puntero, fullscreen, política de pestañas,
Room, firma, versión o canal de actualización de la aplicación.

Reversión: revertir `7176ed8` y `0641da9` (más pruebas correspondientes si se
revierte la API); no hay migración de datos. La prueba previa de `76169ca`
permite conservar la evidencia del fallo.

Referencia consultada: [coroutines con ciclo de vida](https://developer.android.com/topic/libraries/architecture/views/coroutines-views).
Se limita la comprobación periódica al primer plano mediante callbacks actuales
sin añadir una dependencia ni mantener un bucle suspendido en segundo plano.
