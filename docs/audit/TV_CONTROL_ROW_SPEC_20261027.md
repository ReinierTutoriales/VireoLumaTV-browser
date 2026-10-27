# Barra de control única para TV — auditoría y especificación — 2026-10-27

Base: `rewrite/independent-core` @ `1a8fd79`. Estado: **propuesta**, pendiente de implementación y de prueba en el onn.

## Auditoría de la lógica actual

| Área | Evidencia | Problema |
|---|---|---|
| Barra flotante inferior | `activity_main.xml:92` (`llBottomPanel`): cerrar, Atrás, Adelante, Recargar, escudo, texto de estado, popups, Inicio, dos contadores | Segunda superficie de control separada de la URL. Dos rutas de foco para la misma acción. |
| Estado de la barra | `MainActivity.kt:1032–1151`: `showBottomPanel`, `hideBottomPanel`, `animate().cancel()`, `hideBottomButtons` | Máquina de estados de visibilidad, animación y foco repartida en varios métodos. Fuente de fallos de foco ya documentados (N05, F01). |
| Barra superior | `view_actionbar.xml`: URL + voz, historial, favoritos, descargas, incógnito, ajustes, menú | Siete botones ya en la barra. Acciones de navegación duplicadas: Atrás del sistema y Atrás de la barra. |
| Estado del bloqueador | `BlockerIndicator.kt` (función pura, testeada), `MainActivity.kt:601–631` | Lógica correcta y reutilizable: se conserva. El texto ocupa espacio en la barra inferior. |
| Contadores de bloqueo | `tvBlockedAdCounter`, `tvBlockedPopupCounter` en la barra inferior | Ocupan la misma fila de mando que las acciones. |
| Pestañas | `vTabs` 52 dp, fila propia | Correcto como fila separada; se conserva. |

## Diseño objetivo

Una sola fila de control, arriba, debajo de la línea de pestañas o encima, según medición en el onn:

`[ URL ······························ ] [Atrás] [Adelante] [Recargar] [Inicio] [Escudo] [Pestañas] [Menú]`

- **URL:** ocupa el espacio restante. OK activa la edición (IME); no abre el IME al recibir foco.
- **Atrás / Adelante / Recargar / Inicio:** una acción por pulsación, sobre la pestaña seleccionada (N03).
- **Escudo del bloqueador:** un botón. OK alterna bloqueo; mantener OK o Menú abre el detalle. Su descripción usa `BlockerIndicator` (ya existente). El contador de anuncios bloqueados aparece como subtítulo accesible, no como segundo control.
- **Ventanas emergentes:** conmutador dentro del detalle del escudo, no botón propio.
- **Cerrar pestaña:** acción en la fila de pestañas (botón de cierre del elemento enfocado) y en el menú. Se retira de la barra inferior.
- **Menú:** historial, favoritos, descargas, incógnito, ajustes y voz. Reemplaza los botones sueltos de la barra superior, que dejan de ocupar la fila.

## Contrato de navegación con mando

| Desde | Acción | Destino |
|---|---|---|
| Contenido | Arriba | Último control enfocado de la fila de control (por defecto, URL) |
| Fila de control | Izquierda/Derecha | Control anterior/siguiente; no envuelve |
| Fila de control | Abajo | Fila de pestañas; desde pestañas, Abajo vuelve al contenido |
| Fila de control | OK en URL | Edición; Atrás cancela edición y devuelve foco a URL |
| Fila de control | Atrás | Vuelve al contenido; no navega en el historial |
| Contenido | Atrás (sistema) | Sin cambios: N08 |
| Fullscreen | Atrás | Sale una vez y deja foco en la fila de control (sustituye al foco inferior de N05) |

Los controles deshabilitados (p. ej. Adelante sin historial) se saltan en el foco, no se ocultan.

## Cambios a N01–N10 (requiere aprobación explícita)

- N01, N02, N03, N05: el recorrido inferior desaparece; sus pruebas se reescriben sobre la fila única.
- N04 y N07 no cambian de resultado; cambian el foco inicial.
- N06 (preview → comando) y N08 (raíz) no cambian.
- N09 y N10 no cambian.

## Orden de implementación

1. Mover los controles de `llBottomPanel` a `view_actionbar.xml` sin cambiar comportamiento. Tests de foco y comandos existentes deben pasar.
2. Retirar la máquina de estados de la barra inferior y sus métodos.
3. Escudo con detalle (ventaja: sitio, contador, excepción, ventanas emergentes).
4. Reubicar cerrar pestaña y menú.
5. Actualizar capturas (`BrowserUiSnapshotTest`) y N01–N10.

## Criterios de aceptación

- Ningún control duplicado en la pantalla ni en el orden de foco.
- Cada control alcanzable con solo mando, sin trampas de foco, en 720p/1080p.
- Estado del bloqueador visible sin recorrer más de un control.
- Pruebas unitarias en verde y capturas revisadas antes/después.
- Validación física en el onn: pendiente. No se declara mejora de rendimiento sin medición.
