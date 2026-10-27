# Ruta de trabajo: sustitución del código heredado de TV Bro

Rama: `rewrite/independent-core`. Estado verificado en `01e4d9e`: 194 tests unitarios
(`testGenericDebugUnitTest`), 0 fallos. Registro de referencia: [backlog](TVBRO_REMOVAL_BACKLOG_20261027.md)
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
| `cd140ff` | Resultado del historial por la API de ActivityResult | Compilación y suite; falta validar en onn |
| `01e4d9e` | Trabajo por recurso en `WebViewEx` reducido; `printStackTrace` sustituido | Compilación y suite; sin medición de tiempo |

Decisiones fijadas: `DPADNavigationEventsAdapter` conserva la reflexión sobre `BUTTON_DPAD_*` (no es API
pública); `Scripts.kt` queda como propio; el documento de contrato de navegación se actualizó para la cápsula.

## Pendiente, en orden

1. **Validación en onn** (requiere dispositivo): recorridos N01–N10 con la cápsula, foco de «Abajo»
   desde pestañas, detalle del escudo, selección múltiple e historial, actualizaciones de progreso
   en descargas, popups de descargas, colores en claro y oscuro.
2. **`MainActivity`** (1692 líneas, ~80 % heredado): llamadas a `Log` y `printStackTrace` en el camino
   de pestañas, con las pruebas de `BrowserNavigationTest` como red.
3. **Voz y selector de archivos** a la API de ActivityResult (`VoiceSearchHelper`, `WebEngine.onFilePicked`).
4. **`WebViewEx`** (818 líneas, ~93 % heredado): fullscreen, ciclo de vida del renderer, SSL y descargas.
   Solo con pruebas de N05 y N10 y validación física.
5. **Recalcular inventario** tras cada bloque y actualizar el backlog con las cifras medidas.

## Límites conocidos

- Ninguna mejora de rendimiento está medida en dispositivo.
- Las pruebas no cubren el mando real, el renderer ni la reproducción de vídeo.
- Las cifras de retención de TV Bro son por líneas, una aproximación.
