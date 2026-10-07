# Plan de ejecución: navegador TV estable, eficiente y actualizado

Fecha: 2026-10-07. Base examinada: `f614e51`, CI
[37609889851](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37609889851)
completada correctamente. Trabajo en `fixes/consolidated-audit-20261007`, PR #76.

Este documento desarrolla el roadmap vigente; no es otra cola de parches.
El avance F03/F07 y su evidencia previa están en el
[lote verificable sin TV](audit/OFF_DEVICE_FIXES_20261007.md). El avance parcial ADS de solicitudes/disparadores está en el
[lote adblock sin TV](audit/ADBLOCK_OFF_DEVICE_20261007.md). El resto de tareas
siguientes es **pendiente de implementación/validación**.
La política y las correcciones ya verificadas se conservan. «Inmejorable» se
traduce aquí en criterios comprobables de funcionamiento y mejoras medidas;
no en una garantía de perfección para todos los sitios/proveedores/hardware.

## Resultado de producto obligatorio

- Salir de fullscreen con una pulsación de Atrás y mostrar ambas barras,
  con foco accionable en la inferior; poder ir a pestañas y barra superior.
- Volver al contenido y al puntero sin clics duplicados, pérdida de posición,
  controles invisibles o eventos que atraviesen un menú/diálogo.
- Pestañas recuperables y carga de fondo acotada; vídeo y página activa tienen
  prioridad sobre descargas, reglas, imágenes y precarga.
- Bloqueo útil y actualizado, con excepciones reversibles y sin romper login,
  vídeo ni descargas que el usuario pidió.
- UI TV coherente, legible y moderna; dependencias estables y actualizaciones
  de aplicación verificables, con firma y datos del usuario preservados.

## Contrato de entrada y navegación

Separar estado de interfaz, estado de pestaña y propietario de entrada.
El enfoque de estado explícito es una propuesta de implementación, no una
orden de sustituir todo MainActivity o añadir un framework de navegación.

| Situación / evento | Resultado esperado | Entrada y protección |
| --- | --- | --- |
| Fullscreen / Atrás del mando | Cerrar custom view una vez; mostrar ambas barras en la misma acción; foco en Atrás inferior habilitado o fallback accionable. | No ir atrás en historial ni salir de la app por esa misma pulsación. |
| Destrucción/pérdida de renderer/cierre de pestaña fullscreen | Liberar custom view y callbacks sin abrir un menú de una pestaña obsoleta. | Distinguir cierre del usuario de teardown; comprobar propietario. |
| Barra inferior / Arriba | Llegar a pestañas; desde pestañas subir a Buscar en la barra superior. | Mantener ambas filas visibles; foco no abre IME ni ejecuta comando. |
| Barra superior / bajar hacia contenido | Ruta explícita hacia pestañas y barra inferior; regreso al contenido con acción existente de cierre/bajar. | Restaurar foco/puntero en página; no enfocar automáticamente Salir. |
| Barras / Atrás del sistema | Cerrar overlay y devolver entrada al contenido, como cubre la prueba actual. | La salida al launcher se resuelve como recorrido de raíz separado; no cambiar silenciosamente este comportamiento. |
| Botón inferior Atrás/Adelante/Inicio/Recargar | Ejecutar una sola acción sobre la pestaña realmente seleccionada y devolver foco según contrato actual. | Conservar historial y estado enabled; seleccionar preview antes del comando. |
| Edición/IME/diálogo / Atrás | Cancelar primero la interacción superior, resolver callback y restaurar foco. | No navegar historial ni cerrar fullscreen subyacente en el mismo evento. |
| Contenido / D-pad y OK en modo puntero | Movimiento/scroll coherentes y exactamente un DOWN/UP por activación. | Barras/IME/diálogo no reciben simultáneamente una acción de la página. |
| Puntero físico / barras o página | Hover/clic al control correcto con sus coordenadas y su propietario. | No reenviar clic de barra a WebView; distinguir ratón, cursor virtual y foco D-pad. |
| Raíz / Atrás repetido | Alcanzar launcher en recorrido finito sin alternar indefinidamente menú y página. | Definir y probar secuencia explícita antes del commit; preservar apertura del menú, cierre por Atrás y salida manual existentes. |

Ya existe `onRestoreBrowserControlsAfterFullscreen()` y una prueba
`fullscreenExitRestoresOneCoherentMenuAndNextBackReturnsToPage`. No reimplementar
ese comportamiento como si faltara. Verificar qué rutas/sitios/proveedores no
lo cumplen. La secuencia de raíz es una decisión todavía pendiente: antes de
programarla se debe describir completa y demostrar N01–N10; no usar un timeout
arbitrario de doble Atrás como parche al bucle.

## Etapas y entregables

| Orden / ID | Trabajo concreto | Prueba y condición para aceptar |
| --- | --- | --- |
| 0 / BASE | Capturar baseline de release, dispositivo/proveedor, recorridos N01–N10 y webs de prueba; mapear listeners y propietarios de eventos. Registrar cambios ya resueltos. | Reproducción distinguible de fallo nuevo, limitación de sitio y comportamiento intencional. Capturas/trazas con SHA y condiciones. |
| 1 / NAV | Consolidar transiciones de barras, fullscreen, IME, cursor, preview e historial. Resolver bucle de raíz con especificación explícita, sin sustituir las barras. | Contrato anterior y N01–N10, eventos reales y pruebas negativas: un evento no realiza dos acciones. Fullscreen con custom view real además de dobles. |
| 1 / PTR | Corregir únicamente fallos reproducidos de puntero, hit testing, escala/zoom, aceleración, scroll, pulsación larga y restauración de foco/posición. | Enlaces, campos, vídeo, overlays, borde de pantalla, zoom, ratón y gamepad; 20 ciclos de abrir/cerrar menú/fullscreen sin perder clic. Sin clic de página al activar una barra. |
| 2 / STATE | F03 fallback de restoreState; F04–F05 identidad/cancelación de callbacks; F07 UA predeterminado; F06 depuración revocable. Aislar snapshots del hilo de bridge. | Estado válido/vacío/incompatible, cambio de pestaña/recreación, petición reemplazada/cancelada, Custom→Default y debugging off efectivos. Ninguna pantalla vacía por restauración fallida. |
| 2 / DATA | F02 lista explícita de permisos; F09 borrado consistente en IO; evaluar R05/R08 coordinación de procesos y datos privados fuera de backup. | Recurso desconocido denegado, origen visible, borrado que falla conserva fila, datos normales conservados e incógnito aislado después de cierre abrupto y restore. |
| 3 / ADS | Hacer observable la salud de cada fuente: última comprobación/actualización válida, reglas activas, parcial/error y próximo reintento. Disparar reintento debido al volver a primer plano/recuperar red, más actualización manual. | Red caída, timeout, texto inválido/vacío, caché corrupta, cambio de fuente durante carga, sesión larga y actualización manual concurrente. Una generación válida sigue activa; no descargar/compilar en Main ni duplicar tareas. |
| 3 / RULES | Auditar sintaxis realmente soportada por com.github.truefedex:ad-block:0.0.4, reglas de excepción/cosméticas/scriptlets y límites nativos; elegir listas compatibles. Sustituir componente solo si la brecha funcional y licencia justifican la migración. | Corpus de reglas admitidas/no admitidas y recursos legítimos; corpus de webs de reproducción/login. No declarar soporte universal uBlock/AdGuard ni copiar scriptlets incompatibles. |
| 3 / ABUSE | Política común por origen/sesión para ventanas nuevas, redirecciones abusivas, intent externos y descargas; conservar puntos de decisión específicos. Consumir activación nativa cuando corresponda. | Popups automáticos y ráfagas, anuncios abiertos por clic real, redirects, iframe, blob/data/HTTP/stream y APK. Acción Descargar explícita funciona; login/OAuth/target=_blank legítimos no se bloquean en silencio. |
| 4 / TASKS | Cotas de tareas y bytes pendientes de descargas, cancelación/limpieza de cola, progreso agrupado y parada correcta; F08 timeout dataSync Android 15+. | Saturación no produce OOM ni cientos de diálogos; cola cancelada no continúa trabajando; disco lleno, red lenta, cancelación y timeout muestran resultado coherente. |
| 4 / TABS | Estado seleccionado/cargando/listo/suspendido/restaurando/fallido. Modo Ahorro conserva base actual; probar Equilibrado con una precarga candidata y presupuesto agregado. Coordinar timers globales antes de introducir varias vistas. | A→B→A, navegación larga, formularios, scroll/sesión, renderer perdido y presión; todas las pestañas reanudables. Precarga no mantiene vídeo/audio no autorizado ni degrada visible. Si no hay recursos, suspender de forma comprensible. |
| 4 / MEMORY | Acotar snapshots/lectura de estado y pasar disco a IO; liberar estados persistidos con revisión correcta. Evaluar largeHeap, imágenes, trims UI_HIDDEN/BACKGROUND y recursos al salir a Home. | PSS/Swap/Graphics/GC de app y renderer antes/después, sin pérdida de estado. No quitar largeHeap ni destruir WebView visible por intuición. Medir pico durante transición, no solo al final. |
| 5 / WEB | Evaluar APIs estables de startup asíncrono, navegación identificable, cuotas de caché, favicon duplicado y observación de renderer bloqueado. Feature checks y fallback por proveedor. | Arranque/carga/cambio de pestaña repetidos en release; rollback por API aislada si regresiona. Mantener vídeo fullscreen, TLS, cookies, autorización y pérdida de renderer. |
| 5 / VIDEO | Medir HTML5/YouTube/HLS/DRM: codec, resolución, frames, buffering, audio, seek/subtítulos y Home→volver. Revisar scripts inyectados y logging por costo. | Ningún bucle de recarga, salto/seek artificial ni inyección intensiva como «optimización». No prometer evitar fallos de red/decoder/DRM del OEM. Liberación de fondo y retorno verificados. |
| 6 / UI | Proponer capturas de barra superior/inferior; ajustar tipografía, iconos, espaciado, contraste y estados de foco/pressed/disabled; animaciones cortas sin bloquear entrada. | Misma funcionalidad y recorrido de foco antes/después; 720p/1080p/4K UI, texto largo, overscan y lectura a distancia. Separar commit de estilos de lógica. |
| 7 / DEPS | Inventario de versiones/fuentes/licencias y APIs usadas; revisar AndroidX, Room, coroutines, bloqueo y UI por separado de AGP/Kotlin/KSP. Priorizar correcciones estables pertinentes. | Changelog oficial vigente, compilación/suites/release minificado, ABI 32/64 y requisitos de distribución si aplica. WebKit ya está en 1.17.1 estable al corte: no actualizar por aumentar número. |
| 7 / UPDATE | Diseñar canal de actualización propio con release firmado, fuente verificable, metadatos/versionCode y APK compatible; indicar versión disponible, notas, estado/error y acción del usuario. | Interrupción/offline, APK corrupto/incompatible, firma distinta, migración y actualización instalada sin pérdida de datos. No reactivar BUILT_IN_AUTO_UPDATE, ni reutilizar canal del proyecto original, hasta cerrar estas pruebas. Respetar política de cada flavor/tienda. |
| 8 / RELEASE | Candidato minificado, matriz física, comparación de resultados, notas de cambios y plan de reversión por commit/datos. Publicación de versión como acción separada. | CI del SHA exacto; evidencia de recorridos, recursos y privacidad. Fallos críticos bloquean entrega; limitaciones restantes documentadas, sin etiquetar como verificadas. |

Los números son orden, no plazos prometidos. Dentro de cada etapa: un defecto
causal por commit. Un upgrade acoplado se documenta como excepción técnica;
no mezclar UI, lógica de navegación y toolchain para «ahorrar una CI».

## Bloqueador y defensa: límites y decisiones

La política de descarga debe ejecutarse **antes de encolar**, cubriendo todos
los entrypoints. El permiso Android para almacenamiento/notificaciones no
prueba intención de descargar. Si se requiere confirmación, agrupar intentos
abusivos y ofrecer rechazo; no obligar al usuario a cerrar muchos diálogos.

Mantener última lista válida; validar y publicar generación nueva de forma
atómica; no confundir reglas activas con actualización exitosa. Si una lista
contiene características no soportadas, mostrar alcance real o elegir otra.
La actualización temporal requiere un disparador; escoger mecanismos de
lifecycle/conectividad apropiados antes de añadir trabajo periódico de fondo.
No implementar un servicio permanente solo para actualizar listas.

Permitir excepciones por sitio con alcance visible/reversible. Mantener Safe
Browsing separado del bloqueo publicitario. TLS no se desactiva para evitar
errores de vídeo. El contenido publicitario del mismo origen y anuncios dentro
del vídeo pueden exigir mecanismos distintos; no se garantiza bloqueo completo.

## Medición reproducible y criterio de no regresión

Usar onn Android 14/2 GB/32 bits como dispositivo principal informado, verificar
sus datos reales; incluir dispositivo marcado low-RAM y Android 15+ para F08.
Registrar proveedor/versión WebView: la biblioteca AndroidX y Blink instalado
son componentes distintos. Sin TV física, el trabajo puede avanzar con pruebas,
pero no se certifica rendimiento o interacción reales.

| Métrica / recorrido | Protocolo de comparación |
| --- | --- |
| Arranque frío/caliente y barras/puntero | Mismo release/perfil de compilación; al menos 5 repeticiones comparables; registrar distribución y trazas, no solo el mejor resultado. |
| Fullscreen y foco | 20 ciclos entrar→Atrás→barra inferior→superior→contenido; cero acciones dobles/controles inaccesibles en la matriz observada. |
| Pestañas | 1/10/50 registros de pestaña, con vistas vivas limitadas según modo; A→B→A y snapshots largos/corruptos. No confundir 50 pestañas guardadas con 50 renderers. |
| Memoria/CPU/frames | Series durante carga, pico de cambio de pestaña/vídeo, Home 30 s y retorno; medir app + renderer, gráficos y swaps. |
| Red/bloqueo/descargas | Fixture estable legítimo/abusivo, offline/lento/errores, muchas solicitudes y cambio de fuente; controlar bytes, tareas, sockets y diálogos. |
| Persistencia/upgrade | Misma firma en build autorizado; favoritos/normal/incógnito, cierre abrupto, restore fallido y migraciones; comprobar datos y versión final. |

Antes de cada optimización fijar métrica, muestra y tolerancia por ruido a partir
de baseline; después exigir beneficio repetible en la métrica objetivo y
recorridos vecinos sin deterioro significativo fuera de esa tolerancia.
No hay umbrales universales inventados de RAM/FPS/latencia para todos los sitios.
Si ahorro de memoria empeora reanudación o vídeo, explicitar la compensación
y mantener Ahorro/Equilibrado diferenciados; no declarar ambos «más rápidos».

## Primer lote ejecutable

1. Añadir fixtures y ampliar pruebas de navegación real sin cambiar UI.
2. Reproducir el recorrido fullscreen→barras→puntero; conservar el que ya pasa
   y reparar solo las rutas que fallen. Especificar aparte salida en raíz.
3. Corregir fallback de restauración y propietarios de callbacks (F03–F05).
4. Instrumentar estado de actualización adblock y construir fixtures de
   popup/redirect/descarga antes de modificar la política de bloqueo.
5. Obtener baseline físico y decidir presupuestos de carga/memoria; después
   introducir precarga y APIs WebKit una por una.

No se necesita escoger ahora una biblioteca alternativa o reescribir el motor
para ejecutar este lote. Las etapas de modernización usan la evidencia recogida
para decidir qué cambiar y qué conservar.

## Fuentes y trazabilidad

- [Política de estabilidad](REGRESSION_POLICY.md) y contratos N01–N10.
- [Auditoría actual Android TV](audit/ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md): F01–F09/R01–R08.
- [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation): controles alcanzables, foco y salida sin bucle.
- [Android TV memory](https://developer.android.com/training/tv/playback/memory): clasificación low-RAM y recursos multimedia.
- [WebView memory](https://developer.android.com/topic/performance/memory/guide/webview-memory): app y renderer se miden separadamente.
- [WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient): creación/cierre de ventanas, custom view y callbacks.
- [AndroidX WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit): estable 1.17.1, capacidades y limitaciones por proveedor.
- [Testing strategies](https://developer.android.com/training/testing/fundamentals/strategies): cobertura por capas y candidato release.

Consultadas el 2026-10-07. Los criterios de producto y prioridades son decisiones
de este proyecto; no se presentan como mandatos oficiales de cada API.
