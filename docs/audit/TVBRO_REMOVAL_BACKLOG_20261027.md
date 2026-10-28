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

- Inventario sin entradas pendientes para implementaciones y recursos de la aplicación; los avisos obligatorios y herramientas de terceros se identifican por separado y no se atribuyen al proyecto.
- Comprobación del APK: ninguna implementación o recurso heredado pendiente de sustitución distribuido. Las menciones de origen de los avisos y créditos se conservan; buscar solo nombres de paquete no demuestra independencia.
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

## Recursos de inicio sin consumidores

`index.html` usa SVG en línea y solo carga imágenes `data:`. Ningún archivo,
prueba ni script referencia las siete imágenes `pages/home/favicons/*` ni
`ic_not_available.svg`, `mic.svg`, `more.svg` y `search.svg`. Se eliminaron
esos once assets y se marcaron `ELIMINADO` en el inventario.

La página de certificado reemplazó su imagen heredada por una marca CSS y texto
decorativo (`aria-hidden`); el título sigue anunciando el aviso. La imagen
`pages/ic_warning.svg` también se eliminó y se marcó `ELIMINADO`.

## Sustitución del lote completo — base `7711f8e`

Trabajo autorizado por el propietario: sustituir las superficies heredadas pendientes,
conservando datos y contratos de TV. Las cifras anteriores conservan sus bases históricas.

- Barra: implementación nueva de edición/cancelación, entrega de sugerencias y comandos;
  indicador estático de descargas en lugar de la animación infinita. `ToolbarStateTest`,
  `AddressSuggestionsFocusTest` y suites de navegación comprueban sus contratos.
- Historial y descargas: páginas por frontera `(time, id)`, sin OFFSET. Insertar o borrar
  filas anteriores no desplaza la frontera; `StablePaginationTest` reproduce ambos casos.
  Una búsqueda reemplaza una petición pendiente; una página completada antes de resume se
  entrega una vez a la pantalla. Pruebas `HistoryRequestTest` y `HistorySelectionTest`.
- Favoritos: el editor trabaja en una copia, incluida la información legacy; el diálogo
  relee la base tras cada escritura y cancela sus operaciones al cerrarse. Pruebas del
  editor y del esquema instalado. `VALID_UNTIL` conserva milisegundos INTEGER nullable:
  se elimina el conversor Date, sin cambiar el esquema 19.
- Aviso privado: DialogFragment restaurable y preferencia de supresión explícita, con prueba.
- Menú de enlaces: acciones nativas tipadas; eliminado `menu_link.xml`, con pruebas de
  enlaces web, otros esquemas y ausencia de enlace.
- JavaScript: implementación en cierre propio para medios y blobs, con cancelación y entrega
  únicas. Ocho suites JS, incluida `test-blob-transfer.cjs`; no se certifica WebView real.
- Proveedor: solo `Download/` externo y `cache/updates/`. Se prueban archivos admitidos y rechazo
  de `Documents/` y caché ajena al actualizador. Las URI content existentes no se alteran.
- Textos: nueva redacción inglesa y explicaciones en los once idiomas adicionales existentes;
  se mantienen etiquetas breves traducidas. Se corrigen nombres de Inicio mal traducidos.
  Es una sustitución parcial de los archivos de traducción, que siguen `MODIFICADO`.
- Inicio: contrastado contra `6cb4b7c`; el formulario estático con CSP ya sustituía la tabla
  dinámica del origen. Se actualiza su etiqueta, sin adjudicar una reescritura nueva a este lote.
- Se eliminan ocho changelogs del producto original y el manifest release redundante. Metadatos,
  README y plantillas de incidencias pasan a describir el producto actual.

### Sustitución de los contratos de persistencia

Después del primer contraste se reemplazan las operaciones de los cinco DAO desde sus
consumidores y el esquema instalado, con `StorageWriteContractTest`:

| DAO | Implementación del lote |
|---|---|
| DownloadDao | Lectura por frontera y actualización de campos de transferencia; URL/fecha de origen inmutables |
| FavoritesDao | Escritura exclusiva de título/URL; una copia antigua no sobrescribe las demás columnas |
| HistoryDao | Fronteras de página, búsqueda literal y decisión acotada de umbral sin contar toda la tabla |
| HostsDao | Creación idempotente con IGNORE; transacciones separadas de favicon y política de ventanas, sin REPLACE |
| TabsDao | Copia de `(id, posición)` antes de suspender; guardado y selección transaccionales |

Antes de estas sustituciones coincidían 10/12, 8/10, 15/20, 8/8 y 18/25 líneas
respectivamente, normalizando espacios y paquete y excluyendo imports, comentarios y llaves.
No se usa ese porcentaje como criterio de autoría. Las rutas pasan a `REESCRITO` por los
contratos y algoritmos nuevos, no por sus nombres. Permanecen anotaciones de Room, firmas
necesarias, nombres de columnas y SQL convencional. No se atribuyen AndroidX ni las APIs del
framework al proyecto. La clasificación técnica no acredita independencia jurídica.

Los archivos de traducción continúan `MODIFICADO`: se sustituyen las explicaciones extensas
pero se conservan etiquetas breves y otras traducciones del origen. Mantener los avisos;
**el objetivo de independencia total no se declara cerrado**.

`Extensions` conserva firmas de extensiones y el adaptador de contexto Android; su resolución
por tipos y listener de animación sustituyen la implementación original, y los dos helpers sin
consumidores se eliminan. Las otras fuentes reclasificadas conservan firmas, callbacks y
llamadas obligatorias del framework, no la estructura de las implementaciones originales.

### Verificación de paquete

`scripts/audit-apk-provenance.py` inspecciona los APK debug y release minificado en CI:
clases definidas en DEX, namespace original, doce assets retirados, recursos retirados,
applicationId, coincidencia exacta de assets revisados y presencia de licencia/crédito.
Escribe un JSON con SHA-256 por APK. Tres pruebas con paquetes sintéticos comprueban el
lector DEX y la detección de restos. Esta comprobación mecánica no detecta todo código
copiado o renombrado ni certifica autoría; las traducciones y la revisión de procedencia continúan
pendientes aunque el paquete pase. Mantener LICENSE, NOTICE y Acerca de.

La CI `543` detectó una pérdida de entrega inicial de historial y dos problemas de preparación
asíncrona/almacenamiento de las pruebas nuevas. `3f5b642` corrige la entrega. La prueba del
proveedor necesita además adjuntar una instancia nueva a las raíces de almacenamiento de cada
caso Robolectric; se conservan las aserciones y se comprueba la raíz `downloaded_files`. Consultar la CI de la cabeza exacta del PR para el resultado del conjunto vigente.
No se ha ejecutado Gradle local: la descarga del wrapper falla por red y no hay SDK Android.
Siguen pendientes mando, reproducción, WebView real y medición física; no se atribuye una
mejora de rendimiento sin esa medición. No se publica una versión.
