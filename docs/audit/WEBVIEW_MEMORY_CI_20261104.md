# Memoria de WebView en CI — 2026-11-04

Evidencia: [CI del PR #196, SHA `41102d3d`](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/41806987618), integrada en `master` como `04da3a8f`. Los artefactos `release-startup-api-*` contienen `normal-home-memory.json`, `private-home-memory.json`, los volcados originales de procesos y `dumpsys meminfo`. GitHub conserva esos artefactos siete días; esta tabla retiene los resultados después de su caducidad.

El APK minificado se abrió en los emuladores de API 24, 28, 34 y 37. Tras la prueba de arranque en frío de Inicio normal, y después de entrar en incógnito desde el menú, `scripts/capture-webview-memory.py` identificó el proceso de la app y buscó solo renderers vinculados a su `SandboxedProcessService` en `dumpsys activity processes`. Registró el PSS de cada PID con `dumpsys meminfo` y la RAM declarada por `/proc/meminfo`. Son **instantáneas de Inicio**, no una serie durante carga, vídeo o cambio de pestaña.

| API | RAM del emulador | App normal | Renderer normal | App incógnito | Renderer incógnito |
| --- | ---: | ---: | ---: | ---: | ---: |
| 24 | 2002 MiB | 85,3 MiB | No observado por separado | 85,9 MiB | No observado por separado |
| 28 | 1999 MiB | 95,7 MiB | 58,9 MiB | 103,4 MiB | 58,1 MiB |
| 34 | 2475 MiB | 78,2 MiB | PSS no disponible | 91,2 MiB | PSS no disponible |
| 37 | 3913 MiB | 87,1 MiB | 66,6 MiB | 101,6 MiB | 67,6 MiB |

En API 34 se identificaron los PID del renderer, pero `dumpsys meminfo` devolvió solo la cabecera. **PSS no disponible no significa cero.** En API 24 no se observó un renderer separado en esas capturas. Las cifras normales y privadas proceden de procesos e instantes distintos; su diferencia no demuestra el coste propio de incógnito. Las versiones de Android, proveedor WebView y RAM también difieren entre filas. No se midieron FPS, latencia, picos, GC ni consumo de un vídeo, y no existe comparación antes/después de una optimización ni medición en TV física.

## Decisión sobre recursos

`MainActivity.onTrimMemory()` libera vistas ocultas, miniaturas de pestañas no seleccionadas y cachés de iconos y de decisiones/filtros del bloqueador; las reglas instaladas permanecen. Los topes configurados son 2 MiB para iconos, 256 KiB para decisiones y 512 KiB para filtros de página (estos dos últimos usan estimadores propios de tamaño). La aplicación mantiene una WebView activa por política, con el estado de las otras pestañas guardado. Estas capturas no justifican cambiar esos topes, retirar `largeHeap`, destruir la vista visible ni añadir un límite fijo de pestañas.

Para decidir un cambio de política: usar la misma API, proveedor, página y APK de referencia; repetir la navegación/carga, cambio de pestaña, vídeo y retorno; capturar app **y** renderer durante el pico y tras el retorno, además de Swap/Graphics/GC y respuesta del mando. Contrastar el candidato en las mismas condiciones y conservar historial, vídeo y estado privado. La [guía oficial de WebView y memoria](https://developer.android.com/topic/performance/memory/guide/webview-memory) explica por qué `dumpsys meminfo <paquete>` no incluye por sí solo al renderer.
