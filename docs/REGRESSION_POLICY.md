# Política de estabilidad, navegación y modernización

Vigente desde 2026-10-07. Objetivo: mejorar el navegador TV preservando sus
recorridos críticos y detectar fallos antes de entregar versiones. No existe
una garantía técnica de cero regresiones; esta política exige pruebas y
mediciones adecuadas al riesgo. Aplica a colaboradores y agentes mediante
`AGENTS.md`, al trabajo mediante `ROADMAP.md` y a revisión mediante la plantilla PR.

## 1. Estado actual y restricciones reales

Base de esta política: `660c0db`. Las pruebas actuales cubren partes del
contrato, pero no toda la aplicación con WebView real. CI construye debug/release,
ejecuta pruebas Android/Javascript y controla el esquema Room; no ejecuta
actualmente la matriz completa en una TV física ni benchmarks de release.
La plantilla PR y AGENTS son reglas de trabajo: **no configuran por sí solos
protección de rama ni impiden un merge en GitHub**. No se ha modificado esa
configuración administrativa.

El repositorio usa WebView del sistema con AndroidX WebKit. La biblioteca
`androidx.webkit:webkit:1.17.1` coincide con la estable publicada oficialmente
al consultar el 7 de octubre [O4]. Actualizarla no sustituye el proveedor Blink
instalado por Android/OEM, ni garantiza soporte de todas sus funciones.

Hay una WebView viva por política de recursos; otras pestañas guardan estado y
se reconstruyen al seleccionarlas. Es una implementación deliberada, no prueba
por sí misma de un motor defectuoso. Puede causar recargas o perder estado que
WebView no serializa; debe verificarse la restauración y nunca aceptar pantalla
vacía como efecto permitido de ahorro.

El bloqueador conserva reglas funcionales si falla la red, distingue resultados
UPDATED/PARTIAL/CACHED/ERROR y programa timestamps de actualización (7 días) y
reintento parcial (1 día). Un timestamp no ejecuta un trabajo por sí mismo:
revisar disparadores de reintento en sesiones largas y retorno a primer plano.
No concluir que cada resultado CACHED significa fallo del bloqueador.

## 2. Contrato de navegación protegido

Estos recorridos deben seguir funcionando después de cada cambio que toque
MainActivity, ActionBar, pestañas, cursor, shortcuts o fullscreen.

| ID | Recorrido | Resultado obligatorio |
| --- | --- | --- |
| N01 | Página → abrir menú | Ambas barras disponibles; foco visible en control accionable. |
| N02 | Mover foco por barra inferior → Arriba → pestañas → búsqueda | Controles alcanzables, sin ocultar filas por recibir foco; buscar no abre IME hasta activar edición. |
| N03 | Barra inferior Atrás/Adelante/Inicio/Recargar | Ejecutar una sola acción sobre la pestaña seleccionada; botones coherentes con historial. |
| N04 | Editar dirección → Atrás/cancelar | Cerrar IME/edición antes de alterar historial; no enfocar accidentalmente Salir. |
| N05 | Vídeo fullscreen → Atrás/control de salida → navegador | Cerrar custom view primero, restaurar barra/cursor/foco y no duplicar acciones ni navegar otra URL. |
| N06 | Pestaña A → preview B → comando inferior | Aplicar al contenido seleccionado B, sin quedarse apuntando a A. |
| N07 | Diálogo/selector/permiso → cancelar o volver | Resolver callback una vez con su propietario; foco vuelve al lugar correcto. |
| N08 | Atrás desde raíz | Salida al launcher alcanzable y sin bucle, conservando acceso al menú y comandos inferiores. La secuencia exacta debe especificarse antes de implementarla. |
| N09 | Mando OK, D-pad, pulsación larga, gamepad y puntero | Un gesto físico produce una acción; se conserva selección, scroll y clic en enlaces. |
| N10 | Incógnito, cambio de modo, muerte/recreación de renderer | Mantener separación de datos y navegación recuperable sin abrir pestañas normales como privadas o viceversa. |

La auditoría F01 señala el bucle de Atrás del sistema. **No es una instrucción
para quitar el menú, los botones inferiores o hacer que Atrás navegue siempre**.
Antes del arreglo, definir estados y transición de salida; conservar N01–N10.
La recomendación oficial de TV exige foco comprensible y salida por Atrás [O2].

## 3. Evidencia exigida y control de regresiones

| Tipo de cambio | Evidencia antes de integrar/entregar |
| --- | --- |
| Lógica funcional | Reproducción antes, prueba que detecta el defecto, después correcto y suites vecinas. |
| Navegación/barras/fullscreen | N01–N10 según impacto, rutas reales de eventos; pruebas con dobles no bastan para afirmar fullscreen de WebView real. |
| Adblock/popups/descargas | Fixtures deterministas de página legítima y abusiva, actualización fallida, descarga explícita y límites de memoria; además sitios reales para compatibilidad. |
| Persistencia | Estados válidos/inválidos, migración, cierre abrupto, concurrencia e incógnito; preservar datos del usuario. |
| UI visual | Comparación de capturas con estados normal/foco/pressed/disabled, overscan, texto largo y contraste; mando real cuando cambia geometría/foco. |
| Rendimiento/renderer | Release, mismo dispositivo/proveedor/página, varias repeticiones, app y renderer separados; trazas y métricas antes/después. |
| Dependencias/toolchain | Changelog oficial, matriz mínima/proveedor, suites y APK minificado; comprobar integración real, no solo resolución de Gradle. |
| Documentación | Coherencia, enlaces locales, diff limpio y separación entre implementado y propuesto. |

Suites de partida ya existentes, ampliar según fallo:

- Navegación: BrowserNavigationTest, CursorMenuFocusTest, CursorLayoutRecoveryTest,
  VirtualCursorPointerTest, TabRowsTest y scripts/test-webview-controls.cjs.
- Pestañas/estado: SingleLiveTabTest, RendererRecoveryTest, AtomicTabSaveTest,
  ExitPersistenceTest e IncognitoSessionTabsTest.
- Bloqueo: AdblockUpdateTest, AdblockCacheTest, AdblockFilterListValidatorTest,
  AdblockListDownloaderTest, AdblockRequestClassifierTest y YouTubeAdblockControllerTest.
- Descargas/bridge: DownloadResourceTest, DownloadBackoffCancellationTest,
  DownloadUtilsDataUrlTest, BridgePagePolicyTest, UserActivationTrackerTest,
  IncognitoDownloadTest y scripts/test-home-page-security.cjs.

La estrategia usa pruebas pequeñas para feedback rápido y pruebas de integración
para recorridos reales, con una validación de release antes de distribuir [O1].
No fijar porcentajes de cobertura como sustituto de probar rutas críticas.

Cada PR funcional registra: SHA base, causa, alcance, contratos afectados,
comandos/resultados, SHA de CI, dispositivo/proveedor si procede, riesgos
pendientes y commit(s) a revertir. No marcar un pendiente como ejecutado.
Si aparece una regresión, detener la entrega afectada, conservar evidencia y
revertir el cambio causal o repararlo con prueba reproducible; no superponer
ajustes sin conocer la causa. Las migraciones deben revisarse antes de revertir.

## 4. Política de pestañas y recursos

Separar **seleccionada**, **cargando**, **lista**, **suspendida**, **restaurando** y
**fallida**. El usuario debe entender qué ocurrió y poder reanudar/reintentar.
Conservar URL/historial antes de suspender; si restoreState falla, cargar la URL
con fallback y sin anunciar éxito falso.

Objetivo de diseño: modos Ahorro y Equilibrado con carga en segundo plano
**acotada**, priorizando siempre la pestaña visible. Ahorro parte del modo
actual. Equilibrado debe evaluar una precarga adicional y concederla solo
bajo presupuesto medido, sin dar por seguro un número de WebViews por tener
2 GB. Si hay presión, suspender trabajo de fondo y conservar su estado. Los
umbrales y el modo predeterminado se decidirán con mediciones y validación;
no están implementados por este documento.

Condiciones para cambiar la política actual:

- Consultar isLowRamDevice y medir app + renderer; incluir vídeo/fullscreen.
- Coordinar pauseTimers/resumeTimers a nivel de proceso: son globales y la
  lógica actual depende de una sola vista viva. No copiarla por pestaña al
  añadir concurrencia.
- Limitar estados, bitmaps, tareas, bytes pendientes, red, precargas y caché
  de forma agregada; limitar trabajadores no limita la cola.
- Cancelar navegación/precarga cerrada o sustituida, mantener prioridad de
  interacción y evitar vídeo/audio de fondo no autorizado.
- Probar A→B→A, fallo de restauración, formularios, sesión, scroll, popups,
  pérdida del renderer, cambio de modo y presión de memoria.

## 5. Política contra navegación y descargas abusivas

Adblock de recursos, creación de ventanas, navegación/redirecciones y descargas
son cuatro puntos distintos. Una lista de anuncios no cubre todos ellos.
No confiar solamente en isUserGesture: un clic en un anuncio puede ser un gesto
real y aun así abrir una cadena abusiva.

Diseñar decisiones con origen, pestaña/sesión, acción explícita, destino,
recursos solicitados y política por sitio. No convertir cada redirección HTTP
en un popup ni romper login/OAuth, pagos o enlaces target=_blank legítimos.

| Superficie | Comportamiento objetivo y aceptación |
| --- | --- |
| Reglas adblock | Actualización atómica; conservar última válida; resultados y fecha por fuente claros; reintento al recuperar conexión/primer plano con backoff y sin trabajos duplicados. Probar sesión larga y actualización manual durante carga. |
| Ventanas emergentes | Bloquear aperturas automáticas y ráfagas; controlar ventanas originadas por clic según política del sitio. Contador comprensible y excepción deliberada; ninguna pestaña/vista huérfana. |
| Descargas HTTP/blob/data/stream | Acción nativa Descargar o confirmación con origen, nombre y tipo; política decide antes de encolar. No generar un diálogo por cada anuncio: agrupar/rechazar ráfagas. Tokens de blob no sustituyen la política de todos los tipos de descarga. |
| Instaladores/enlaces externos | No instalar ni abrir aplicaciones por anuncio automáticamente; preservar descarga/acción explícita del usuario y flujo Android de permisos. |
| Excepciones por sitio | Alcance claro y reversible; no desactivar TLS ni Safe Browsing; preservar login, archivos y reproducción legítimos. |

El bloqueo de una petición debe dejar UI operativa y mensajes limitados; nunca
crear un bucle de recargas, diálogos o pruebas JS que consuman CPU para «ganar»
a la publicidad. Compatibilidad perfecta con todas las webs no es garantizable.

## 6. Modernización del motor, UI y dependencias

Evaluar APIs estables de AndroidX WebKit ya disponible: startup asíncrono,
seguimiento de navegación, control de favicons/duplicación y caché de perfil.
Usar feature checks y fallback para cada proveedor. Las notas oficiales de
1.17 describen navegación y control de caché/favicons; no prueban una mejora
para este repositorio sin medir [O4]. No sustituir el motor como primera medida.

La barra superior se renovará con tipografía legible a distancia, iconos
consistentes, superficies y espaciado coherentes, contraste y foco/pressed
visibles, tamaños adecuados al mando y transiciones breves. Conservar todas
sus acciones y relaciones con la barra inferior. Primero capturas de propuesta
y contrato de foco; después estilos; finalmente comportamiento si está
justificado. No migrar todo a Compose ni cambiar librería solo por apariencia.
Las guías TV centran la interacción en el foco [O3].

Antes de cada cambio de dependencias y cada release, consultar fuentes
oficiales actuales. Registrar versión instalada/candidata, canal, cambio útil,
compatibilidad, validación y revert. Preferir estable; alpha/beta requieren
motivo concreto y aislamiento. Separar librerías de UI, WebKit, Room y
AGP/Kotlin/KSP en cambios distintos salvo versiones técnicamente acopladas.
No usar rangos dinámicos ni actualizar todo en lote. Una revisión periódica es
una tarea de mantenimiento propuesta; este documento no crea una automatización.

Medir arranque, respuesta del mando, cambio de pestaña, carga web, memoria y
vídeo. Evaluar Macrobenchmark/Baseline Profiles para rutas nativas; no asumir
que optimizan Blink/JavaScript/decoder. Los benchmarks de rendimiento real se
validan en dispositivo físico [O5]. No inventar mejoras porcentuales ni umbrales
antes de obtener una base repetible.

## Fuentes oficiales consultadas el 2026-10-07

- O1: https://developer.android.com/training/testing/fundamentals/strategies
- O2: https://developer.android.com/training/tv/get-started/navigation
- O3: https://developer.android.com/design/ui/tv/guides/styles/focus-system
- O4: https://developer.android.com/jetpack/androidx/releases/webkit
- O5: https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile
- Memoria/renderer/permisos: fuentes y hallazgos en
  [auditoría oficial Android TV](audit/ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md).
