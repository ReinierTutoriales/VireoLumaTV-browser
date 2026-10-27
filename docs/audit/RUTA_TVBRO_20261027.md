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
| `44f630f` | Ajustes principales construidos con filas propias (`SettingsRows`); el botón Actualizar del bloqueador pasa a ser alcanzable con D-pad; interruptores como fila completa. Heredado en la página: 761/856 → 128/631 líneas (`git blame -w -C`; lo restante son llaves, imports y llamadas a API) | 205 tests; `MainSettingsFocusTest` falla en `15638e1`; falta validar foco, IME y desplazamiento en Android TV |
| `f6be67f`, `2919791` | Puntero y mando propios: rueda y hover de ratón en la página, stick proporcional, modo agarrar operativo, `RemoteInput` sustituye a `DPADNavigationEventsAdapter` (eventos de gamepad consumidos, `isFromSource`, sin reflexión de `BUTTON_DPAD_*`). Heredado en el bloque: 1 253/1 386 → 161/667 líneas | 216 tests; `RemoteInputContractTest` falla 5/7 con la entrada anterior; falta validar en Android TV scroll de WebView, hover, gamepads reales y zona muerta |

Decisiones fijadas: `DPADNavigationEventsAdapter` fue sustituido por `RemoteInput`;
`Scripts.kt` queda como propio; el contrato de navegación contempla la cápsula.

## Pendiente, en orden

1. **Validación en Android TV** (emulador o dispositivo con D-pad): recorridos N01–N10 con la cápsula, foco de «Abajo»
   desde pestañas, detalle del escudo, selección múltiple e historial, actualizaciones de progreso
   en descargas, popups de descargas, colores en claro y oscuro.
2. **Avisos del compilador medidos en `0a0109c` (26; recuento actual pendiente)**: pantalla completa (`systemUiVisibility`), oscurecimiento
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

## Referencias medidas ya existentes (no repetir sin motivo)

Fuente: [W2_MEASUREMENT.md](../W2_MEASUREMENT.md), commit medido `94b3264` (PR #31, «Keep at most two live tab WebViews»).
Dispositivo: onn 2 GB, 32 bits. Mismas URLs del protocolo W2.

| Escenario | PSS del renderer |
|---|---|
| `02_one_tab` | ~149 MB |
| `03_four_tabs` | ~301 MB (línea base W1; W2 apunta a 200–240 MB) |
| `04_after_close` | ~202 MB |
| `05_youtube_default_ua` | ~351 MB, jank ~23,8 % |
| UA de escritorio | descartado: ~612 MB y swap intenso |

Medición de bloqueo en x86 (no en TV): compilación de listas y coste por petición,
en [ROADMAP](ROADMAP.md), sección del motor adblock-rust.

**Límite:** `94b3264` no es ancestro de `rewrite/independent-core`; las ramas divergen desde bases distintas.
Por eso estas cifras no certifican el código actual. Para compararlas hay que generar el APK de esta
rama y ejecutar el protocolo W2 completo sobre ambos builds, en el mismo dispositivo y sesión.

## APK release generado (no validado en dispositivo)

Commit: `f820459` (rama `rewrite/independent-core`). Variante `generic`, `assembleGenericRelease`, con R8.
Firma: clave de depuración local (`CI_SIGN_RELEASE_WITH_DEBUG_KEY=true`), certificado SHA-256
`D110AD58E9C2E8D08F872B981EF2170E1FA4DAB1BA6ABD747C75CC0AF0B9F831`. No es la clave de CI del protocolo W2.

| Archivo | SHA-256 |
|---|---|
| `app-generic-armeabi-v7a-release.apk` (32 bits, como el onn) | `2f452d1882946ce882c430f39df2add2946fd5a3d001eab3f19e0ee67db91b21` |
| `app-generic-arm64-v8a-release.apk` | `8c40a5ddf4887cc80c3dd9cde2e609c238bc292b5714c3110ff97254b282d838` |
| `app-generic-x86_64-release.apk` | `8e4ee3a8e15b27717646e280ec29265c7850c81d31461736109d9335eed37c3e` |

Instalación: `adb install -r` sobre una app firmada con otra clave falla (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
Para medir sobre la instalación existente hace falta la clave de CI o desinstalar, lo que borra los datos del usuario.
