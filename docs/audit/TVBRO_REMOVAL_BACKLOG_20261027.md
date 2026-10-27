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

- `DPADNavigationEventsAdapter`: la reflexión sobre `BUTTON_DPAD_*` se conserva. Esas constantes no son API pública.
- `Scripts.kt`: 33 % heredado, una constante; sin reemplazo.
- `WebViewEx.kt`: pendiente, con pruebas de fullscreen y renderer antes de tocarlo.
