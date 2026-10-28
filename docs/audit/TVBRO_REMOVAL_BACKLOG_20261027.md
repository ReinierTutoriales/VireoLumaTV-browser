# Backlog de eliminación de código heredado de TV Bro — 2026-10-27

Base: `rewrite/independent-core` @ `6bac762`. Referencia: `truefedex/tv-bro` @ `6cb4b7c`.
Inventario por archivo: [UPSTREAM_TVBRO_INVENTORY_REWRITE_20261027.tsv](UPSTREAM_TVBRO_INVENTORY_REWRITE_20261027.tsv).
Línea base: 162 tests unitarios, todos pasan (`testGenericDebugUnitTest`).

## Estado medido

| Clase | Archivos | Acción |
|---|---|---|
| Kotlin/Java derivados de TV Bro | 79 | Reescribir desde el contrato de comportamiento, no editar el texto original |
| Recursos y textos idénticos a TV Bro | 85 (64 texto + 21 binarios) | Sustituir o regenerar con procedencia propia |
| Archivos de TV Bro sin equivalente | 30 | Ya ausentes del árbol; verificar que no quedan referencias |

Retención de líneas en los 79 derivados: 84 % (9 029 de 10 736). 64 archivos tienen ≥ 80 %.

## Método por archivo

1. Anotar el contrato observable (entradas, salidas, efectos, estados) con los tests existentes; añadir tests que fijen lo que no está cubierto.
2. Escribir la implementación nueva desde ese contrato, sin copiar el texto original.
3. Verificar: tests unitarios, `assembleGenericRelease`, y que el archivo no aparezca en el inventario como derivado.
4. Actualizar el inventario y registrar el archivo como sustituido.

## Criterio de cierre

- Inventario sin entradas `MODIFICADO`, `IDENTICO` ni `IDENTICO_BLOB` para código fuente ni recursos.
- Comprobación del APK: ninguna clase, recurso ni cadena de TV Bro distribuida.
- La licencia de TV Bro (`LICENSE.md`, `NOTICE.md`, crédito en «Acerca de») se retira solo después de esa comprobación y con revisión legal; el historial Git conserva el origen.
- Pruebas en el onn: recorridos N01–N10 y reproducción. No se declara optimización sin medición.

## Limitaciones

- Este backlog no prueba que una reescritura esté libre de expresión heredada. La medición por líneas es una aproximación.
- No se ha validado comportamiento en TV física.

## Estado medido — 80d0478

| Medida | Valor |
|---|---|
| Kotlin/Java modificados a partir de TV Bro | 77 (antes: 79) |
| Líneas de TV Bro retenidas en esos archivos | 82 % (8 742 de 10 707) |
| Archivos con ≥ 80 % retenido | 57 |
| Archivos de TV Bro sin equivalente | 39 |

Reemplazos hechos, con pruebas:

- `utils/Extensions.kt`, `FileUtils.kt`, `BaseAnimationListener` y `StringUtils` (eliminados o reescritos).
- `utils/observable/*`: eliminados `EventSource` y `ParameterizedEventSource` sin consumidores; pruebas de comportamiento añadidas.
- `webengine/webview/HomePageHelper.kt`: sin parámetro `view` sin uso; pruebas de paso y de 404.
- `webengine/webview/AndroidJSInterface.kt`: eliminados cuatro métodos expuestos al JavaScript de cualquier página sin llamantes, y su cadena de callbacks. Prueba de superficie.

## Recuento de rutas presentes en `5471650` (2026-10-27)

El TSV se generó para una base anterior. Al contrastar sus rutas con el árbol de
`rewrite/independent-core` en `5471650`, siguen presentes 136 rutas etiquetadas
`MODIFICADO`, 56 `IDENTICO` y 21 `IDENTICO_BLOB`. Otras 14 rutas de esas
clases ya no existen. Entre las presentes, 48 son Kotlin/Java, 71 son recursos
y 15 son assets. La presencia de una ruta no demuestra que su contenido siga
igual: **este recuento no es una nueva medición de líneas ni un porcentaje
de código heredado**. Regenerar el inventario por contenido antes de declarar
un bloque sustituido.

La barra inferior y la búsqueda por voz ya no están en esta rama. La cápsula
superior integra los controles de navegación y el escudo; Favoritos sigue activo.
El callback de descargas de `MainActivity` tiene una prueba de cuatro casos
de activación en `5471650`, pero WebView real y la descarga final siguen fuera
de esa prueba. Los avisos de licencia de TV Bro permanecen mientras haya
fuentes derivadas distribuidas.

Decisiones:

- `DPADNavigationEventsAdapter` fue sustituido por `RemoteInput` en `2919791`; comprobar en TV real la entrada de gamepad y el cursor.
- `Scripts.kt`: 33 % heredado, una constante; sin reemplazo.
- `WebViewEx.kt`: pendiente, con pruebas de fullscreen y renderer antes de tocarlo.

## Estado medido — `c0161b0`

Método: por archivo, líneas actuales con contenido (sin vacías, llaves sueltas, `package`/`import`,
comentarios ni declaraciones XML) que aparecen idénticas, tras normalizar espacios y el paquete, en el
archivo correspondiente de `truefedex/tv-bro` @ `6cb4b7c`. Es una aproximación por líneas, no una prueba de
independencia; en XML cuenta como coincidencia cualquier atributo genérico (`layout_width`…), por eso los
layouts reescritos siguen como `MODIFICADO`.

| Archivo | Retenido | Clase en el inventario |
|---|---|---|
| `CursorDrawerDelegate.kt` | 49/276 (18 %) | `REESCRITO` |
| `CursorLayout.kt` | 9/55 (16 %) | `REESCRITO` |
| `MainSettingsView.kt` | 24/172 (14 %) | `REESCRITO` |
| `VersionSettingsView.kt` | 10/57 (18 %) | `REESCRITO` |
| `ShortcutsSettingsView.kt` | 3/16 (19 %) | `REESCRITO` |
| `SettingsDialog.kt` | 6/19 (32 %) | `REESCRITO` |
| `ShortcutMgr.kt` | 9/71 (13 %) | `REESCRITO` |
| `Shortcut.kt` | 6/28 (21 %) | `REESCRITO` |
| `ShortcutDialog.kt` | 12/45 (27 %) | `REESCRITO` |
| `DPADNavigationEventsAdapter.kt`, `view_settings_version.xml`, `view_shortcut.xml`, `text_link_background_selector.xml`, `values-night/colors.xml` | — | `ELIMINADO` |

Las líneas retenidas en los `REESCRITO` son firmas públicas conservadas para no cambiar a sus consumidores
(`goToGrabMode`, `onLongPress`…), overrides obligatorios del framework (`onKeyDown`, `setContentView`) y
llamadas sueltas (`surface.postInvalidate()`); no hay lógica ni estructura del original. Revisión manual
por archivo. Archivos nuevos (`RemoteInput.kt`, `SettingsRows.kt`, filas `view_setting_*`) añadidos como
`PROPIO`. `REESCRITO` no sustituye la comprobación del APK ni la revisión legal del criterio de cierre.

## Contraste puntual del árbol `d572b70`

Tres etiquetas `MODIFICADO` seguían describiendo versiones anteriores. Se compararon
manualmente las implementaciones actuales con las rutas originales del TSV:

- `BackNavigationEventsAdapter`: estado propio por canal/pulsación y eliminación de
  eventos duplicados. Coinciden 18 de 92 líneas de contenido normalizadas (20 %),
  principalmente firmas y llamadas de Android; tiene `BackNavigationEventsAdapterTest`.
- `FavoritesListAdapter`: sustituyó `BaseAdapter` por `ListAdapter` con diff y acciones
  para mando. Coinciden 0 de 35 líneas de contenido; tiene `FavoritesListAdapterTest`.
- `FileUtils`: usa `File.deleteRecursively()` en vez de ejecutar `rm -rf`; coinciden
  0 de 1 líneas de contenido y existen pruebas de árbol anidado y ruta ausente.
- `AdblockModel`: sustituye el cliente Brave antiguo y una lista única por el
  motor `ContentBlockerEngine`/Rust, siete fuentes, cachés y estados de actualización.
  Coinciden 41 de 449 líneas de contenido (9 %), principalmente firmas y
  llamadas generales; tiene pruebas de actualización, caché y validación de listas.

Las cuatro rutas pasan a `REESCRITO` en el inventario. La comparación normaliza espacios
y paquete, excluye imports, comentarios y llaves; no determina derechos de autor.
Las cifras históricas de este archivo conservan el SHA al que corresponden.

Otras dos rutas contrastadas con `d572b70` y el mismo origen: `HomePageHelper`
(2/9 líneas de contenido coincidentes) ahora consulta solo el favicon cacheado y
devuelve 404 sin bloquear el hilo de WebView; `HomePageNetworkTest` cubre ese
camino. `Scripts` (2/10 líneas coincidentes) cambió el JavaScript de pulsación
larga para resolver enlaces y descartar elementos desconectados. Ambas pasan
a `REESCRITO` por implementación distinta; esto no equivale a validación de
WebView real ni a revisión legal.
