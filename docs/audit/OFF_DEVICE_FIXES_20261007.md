# Correcciones verificables sin TV — 2026-10-07

Base: `b8d3e60`. Mismo PR #76 y rama de trabajo. Este lote aborda F03 y F07;
no modifica barras, foco, cursor, fullscreen, política de una WebView viva,
listas de anuncios, dependencias, Room v19, versión o firma.

## Evidencia previa: pruebas que detectan los fallos

Commit `77a013d` añadió pruebas sin cambiar el código funcional. La
[CI 37615956279](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37615956279)
ejecutó 112 pruebas Android: cinco fallaron y una quedó omitida.
Los cinco fallos corresponden a las nuevas regresiones:

| Prueba | Resultado observado con código anterior |
| --- | --- |
| rejectedMemoryStateLoadsSavedUrlOnceInBothModes | No cargó la URL guardada al rechazar el estado. |
| rejectedDiskStateDoesNotReportSuccessfulRestoration | Anunció restauración válida al rechazarse el Bundle leído de disco. |
| invalidMemoryStateFallsBackInsteadOfThrowing | Lanzó IllegalArgumentException al recibir un payload inválido. |
| engineWithoutLiveViewDoesNotManufactureSavedState | Devolvió un Bundle vacío sin vista viva. |
| defaultSelectionResetsTheLiveWebViewWithoutReplacingIt | Conservó CustomBrowser/2.0 después de seleccionar Default. |

Esto es una reproducción automatizada con Robolectric y dobles de contrato,
no una reproducción de Blink o de una TV física. Las pruebas anteriores se
conservan; los dobles de SingleLiveTabTest y RendererRecoveryTest ahora devuelven
el resultado Boolean del contrato de restauración, manteniendo sus aserciones.

## F03 — resultado real de restauración

Commit `3d4462e`: WebEngine.restoreState devuelve éxito/fallo; el adaptador
WebView considera éxito solo un resultado no nulo de la API Android.
WebTabState propaga el resultado y captura excepciones de payload inválido,
tanto en memoria como desde disco. TabsModel ya tenía el fallback de cargar
la URL; ahora recibe false cuando corresponde y lo ejecuta.

saveState devuelve null cuando no existe una vista o la API no pudo guardar.
No se borran archivos de estado rechazados: los reemplaza el guardado atómico
normal. Los prefijos legacy gecko siguen usando su fallback existente.
No se cambió lectura de disco síncrona, límite de snapshots ni coordinación
multiproceso: R02/R05 siguen pendientes.

Cobertura: URL cargada una sola vez en normal/incógnito, estado rechazado de
disco sin borrar archivo, payload inválido sin crash, ausencia de vista,
historial válido preservado y rechazo de Bundle vacío. La prueba adicional
usa el adaptador WebView y ShadowWebView, con entradas de historial explícitas
para simular navegación completada; no ejecuta Chromium.

## F07 — UA predeterminado efectivo

Commit `b910981`: el setter envía también null a WebSettings, que representa
la selección del proveedor predeterminado. La prueba verifica Custom→Default→
otro UA y que cambiarlo conserva la misma vista. No se modificó el selector
ni se eliminó la cobertura de MainSettingsLifecycleTest.

Contrato oficial: [WebSettings.setUserAgentString](https://developer.android.com/reference/android/webkit/WebSettings#setUserAgentString(java.lang.String)).
Restauración: [WebView.restoreState](https://developer.android.com/reference/android/webkit/WebView#restoreState(android.os.Bundle)).

## Validación posterior y límite de alcance

El estado posterior se comprueba en los checks del
[PR #76](https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/76)
para el SHA exacto que contiene los dos arreglos. Exigir las suites completas,
APK debug, comparación del esquema Room y release minificado antes de integrar.
La CI roja anterior es evidencia deliberada del fallo, no el estado final.

Sin dispositivo, este lote se valida en CI; mando físico, vídeo/DRM, pérdida de
renderer real, memoria y proveedor OEM siguen pendientes. No equivale a aprobar
una nueva release ni a cerrar la etapa completa de navegación.

Reversión de código: revertir b910981 y 3d4462e en ese orden; ajustar las pruebas
al revert solo si se revierte también su contrato, conservando registro del
fallo. No requiere migración de base de datos. Si se revierte un arreglo, las
pruebas nuevas deben volver a detectar el defecto: no deshabilitarlas para
presentar el código anterior como corregido.
