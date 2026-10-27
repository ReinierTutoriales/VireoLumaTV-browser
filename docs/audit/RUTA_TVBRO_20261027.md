# Ruta de trabajo: sustitución del código heredado de TV Bro

Rama: `rewrite/independent-core`. Estado verificado en `0a0109c`: 193 tests unitarios
(`testGenericDebugUnitTest`), 0 fallos. Avisos del compilador: 34 → 26. Registro de referencia: [backlog](TVBRO_REMOVAL_BACKLOG_20261027.md)
e [inventario por archivo](UPSTREAM_TVBRO_INVENTORY_REWRITE_20261027.tsv).

## Hecho

| Commit | Qué | Verificación |
|---|---|---|
| `1a8fd79` | Utilidades heredadas reemplazadas (`deleteDirectory` sin `rm -rf`, código muerto eliminado) | 164 tests, prueba de `FileUtils` |
| `333d802`, `6aa8722`, `8008ece` | Menú de aplicación, iconos propios y barra de control única (cápsula) | Capturas revisadas; geometría y foco en pruebas |
| `f26f088`, `71e600b` | Recursos sin uso eliminados; observables limpiados con pruebas | 176 tests |
| `9c0758f`, `a290be3` | Superficie unificada y acento verde de la paleta del icono | Contraste WCAG comprobado |
| `326ea46`, `80d0478` | `HomePageHelper` simplificado; puente JavaScript reducido a 11 métodos con llamante | Prueba de superficie |
| `70e50b8`, `82d3f05` | Historial: pruebas de caracterización y `RecyclerView` + `ListAdapter` | 7 pruebas; corrige offset de paginación tras borrar |
| `2208c30` | Descargas: `RecyclerView` + `ListAdapter`; eliminada `pinned-section-listview` | 5 pruebas; dependencia y NOTICE retirados |
| `eaf7a8f` | Diff de pestañas compara título, dirección y posición | 3 pruebas; defecto confirmado antes del cambio |
| `cd140ff` | Resultado del historial por la API de ActivityResult | Compilación y suite; falta validar en Android TV |
| `01e4d9e` | Trabajo por recurso en `WebViewEx` reducido; `printStackTrace` sustituido | Compilación y suite; sin medición de tiempo |
| `add5d79` | `MainActivity`: lanzador de apps externas único; trazas con contexto; sin `Log.d` por evento | Suite; falta validar en Android TV |
| `e9328b5` | Búsqueda por voz eliminada (menú, atajo, historial, permisos, cadenas, documentos) | Suite; `RECORD_AUDIO` conservado para web |
| `0a0109c` | Selector de archivos y permisos de instalación por ActivityResult; `ApkInstaller` único; `STOP_FOREGROUND_REMOVE` | Suite; falta validar descargas e instalación en Android TV |

Decisiones fijadas: `DPADNavigationEventsAdapter` conserva la reflexión sobre `BUTTON_DPAD_*` (no es API
pública); `Scripts.kt` queda como propio; el documento de contrato de navegación se actualizó para la cápsula.

## Pendiente, en orden

1. **Validación en Android TV** (emulador o dispositivo con D-pad): recorridos N01–N10 con la cápsula, foco de «Abajo»
   desde pestañas, detalle del escudo, selección múltiple e historial, actualizaciones de progreso
   en descargas, popups de descargas, colores en claro y oscuro.
2. **Avisos del compilador restantes (26)**: pantalla completa (`systemUiVisibility`), oscurecimiento
   forzado (`setForceDark`), zoom (`canZoomIn/Out`), `MODE_MULTI_PROCESS`, `SHOW_IMPLICIT` y constantes del
   instalador. Cada uno cambia comportamiento visible; se migran con dispositivo.
3. **`MainActivity`** (~1690 líneas, ~80 % heredado): fullscreen y barras, con N05 como red.
4. **`WebViewEx`** (818 líneas, ~93 % heredado): fullscreen, ciclo de vida del renderer, SSL y descargas.
   Solo con pruebas de N05 y N10 y validación física.
5. **Recalcular inventario** tras cada bloque y actualizar el backlog con las cifras medidas.

## Límites conocidos

- Ninguna mejora de rendimiento está medida en dispositivo.
- Las pruebas no cubren el mando real, el renderer ni la reproducción de vídeo.
- Las cifras de retención de TV Bro son por líneas, una aproximación.

## Avisos del compilador pendientes de dispositivo (`0a0109c`)

Sin cambiar: cada uno altera comportamiento visible y necesita comprobación en un Android TV antes de migrarlo.

| Archivo | Línea | Aviso | Qué validar en Android TV |
|---|---|---|---|
| Utils.kt | 154 | 'fun getInstallerPackageName(p0: String): String?' is deprecated. Deprecated in Java. | Detección de origen de instalación |
| Utils.kt | 40 | 'val defaultDisplay: Display!' is deprecated. Deprecated in Java. | Tamaño de pantalla en TV y cursor |
| Utils.kt | 42 | 'fun getSize(p0: Point!): Unit' is deprecated. Deprecated in Java. | Tamaño de pantalla en TV y cursor |
| CursorDrawerDelegate.kt | 93 | 'val defaultDisplay: Display!' is deprecated. Deprecated in Java. | Tamaño de pantalla en TV y cursor |
| CursorDrawerDelegate.kt | 95 | 'fun getSize(p0: Point!): Unit' is deprecated. Deprecated in Java. | Tamaño de pantalla en TV y cursor |
| VireoLumaTVApp.kt | 60 | 'static field MODE_MULTI_PROCESS: Int' is deprecated. Deprecated in Java. | Arranque y renderer en proceso separado (N10) |
| DownloadsActivity.kt | 183 | 'static field INSTALL_NON_MARKET_APPS: String' is deprecated. Deprecated in Java. | Instalación de APK desde descargas |
| MainActivity.kt | 1468 | 'var systemUiVisibility: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1468 | 'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1469 | 'static field SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1470 | 'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1471 | 'static field SYSTEM_UI_FLAG_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1472 | 'static field SYSTEM_UI_FLAG_FULLSCREEN: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1473 | 'static field SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1482 | 'var systemUiVisibility: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1483 | 'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| MainActivity.kt | 1483 | 'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java. | Fullscreen: barras, inmersión y retorno (N05) |
| ActionBar.kt | 71 | 'static field SHOW_IMPLICIT: Int' is deprecated. Deprecated in Java. | Abrir el teclado de dirección en TV |
| DownloadService.kt | 137 | 'static field INSTALL_NON_MARKET_APPS: String' is deprecated. Deprecated in Java. | Instalación de APK desde descargas |
| ApkInstaller.kt | 19 | 'static field ACTION_INSTALL_PACKAGE: String' is deprecated. Deprecated in Java. | Instalación de APK desde descargas |
| WebViewEx.kt | 213 | 'static fun setForceDark(p0: WebSettings, p1: Int): Unit' is deprecated. Deprecated in Java. | Oscurecimiento de páginas con y sin tema oscuro |
| WebViewEx.kt | 213 | 'static field FORCE_DARK_ON: Int' is deprecated. Deprecated in Java. | Oscurecimiento de páginas con y sin tema oscuro |
| WebViewEx.kt | 215 | 'static fun setForceDark(p0: WebSettings, p1: Int): Unit' is deprecated. Deprecated in Java. | Oscurecimiento de páginas con y sin tema oscuro |
| WebViewEx.kt | 215 | 'static field FORCE_DARK_OFF: Int' is deprecated. Deprecated in Java. | Oscurecimiento de páginas con y sin tema oscuro |
| WebViewWebEngine.kt | 80 | 'fun canZoomIn(): Boolean' is deprecated. Deprecated in Java. | Zoom por mando y por puntero |
| WebViewWebEngine.kt | 88 | 'fun canZoomOut(): Boolean' is deprecated. Deprecated in Java. | Zoom por mando y por puntero |
