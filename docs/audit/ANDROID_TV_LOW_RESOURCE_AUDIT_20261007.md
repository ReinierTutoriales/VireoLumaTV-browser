# Auditoría Android TV y recursos limitados — 7 de octubre de 2026

## Alcance y conclusión

Código examinado: `569cdda469d4def2c16dab3b25401e84eb7f938d`, rama
`fixes/consolidated-audit-20261007`, PR #76. La auditoría anterior y sus correcciones
siguen vigentes; este documento añade el contraste con documentación oficial
consultada el 7 de octubre. No modifica el comportamiento del navegador.

Se identifican **9 defectos verificables por flujo de código** y **8 riesgos o
brechas de validación**. No equivalen a 17 fallos reproducidos en una TV.
No hay dispositivo Android conectado ni `adb` en este entorno. No se pueden
certificar ausencia de ANR, memoria máxima, decodificación, DRM, sitios concretos,
foco real del mando ni limpieza privada después de muerte abrupta del proceso.
Una auditoría estática tampoco garantiza encontrar todos los fallos posibles.

La CI del código exacto terminó correctamente:
[run 37605080801](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/37605080801).
Incluye pruebas Android/Javascript, APK debug, comparación del esquema Room v19
y compilación release con R8. Eso demuestra compilación y las regresiones
cubiertas, no certificación física Android TV.

## Criterio oficial aplicado correctamente

- **2 GB no equivale automáticamente a `isLowRamDevice() == true`.** Se debe
  consultar `ActivityManager.isLowRamDevice()` y registrar proveedor WebView,
  versión Android, resolución UI y arquitectura reales [S1].
- Los objetivos publicados para TV de 1 GB no se trasladan como límites
  universales al onn de 2 GB. Deben interpretarse con sus supuestos [S1].
- Medir la aplicación y su renderer WebView por separado: el PSS del paquete
  por sí solo omite memoria del renderer [S2].
- Este navegador usa WebView: los parámetros de buffers de Media3/ExoPlayer
  no son controles directamente disponibles aquí.
- Los criterios TV-DP, TV-DB, TV-NP y TV-ME son pertinentes. Live Tab, PiP,
  Cast y niveles superiores solo aplican cuando se implementa esa función [S3].

Prioridad **alta**: navegación bloqueada, permisos incorrectos, pantalla vacía,
crash condicionado o presión de memoria sin cota. **Media**: configuración,
callbacks o borrado incorrectos en escenarios específicos. «Verificable»
significa que la ruta defectuosa se ve en código; la frecuencia real requiere
la prueba indicada.

## Defectos verificables

| ID | Prioridad | Fallo y condición | Evidencia en el código | Corrección mínima y prueba de aceptación |
| --- | --- | --- | --- | --- |
| F01 | Alta | Atrás no lleva al launcher: en el estado normal alterna página y menú. | `MainActivity.handleBackNavigation()` cierra el overlay si está visible; el `else` vuelve a abrirlo. El callback de sistema siempre está habilitado. `CursorDrawerDelegate` solo consume Atrás en grab mode. Ninguna ruta normal delega salida al dispatcher. | Conservar el desenredo de IME/fullscreen/cursor; después navegar hacia atrás y permitir salida en la raíz. Prueba: desde inicio, pulsar Atrás repetidamente y llegar al launcher sin botón Exit ni bucle. Android lo prohíbe expresamente [S3, S4]. |
| F02 | Alta | Un recurso web desconocido se puede autorizar al aceptar el diálogo. | `WebViewEx.onPermissionRequest()`: el `else` incorpora recursos desconocidos; si no faltan permisos Android se llama `grant(request.resources)`. También se agregan al resultado del permiso Android. | Lista explícita de recursos admitidos y autorizados, denegar el resto. Incluir origen solicitante en el diálogo. Prueba con PermissionRequest falso que contenga un recurso desconocido, solo audio, solo vídeo y combinaciones. La documentación advierte específicamente contra conceder toda la lista [S5]. La ruta especial de Widevine de un solo recurso no demuestra este defecto por sí sola. |
| F03 | Alta | Restauración fallida puede dejar una pestaña vacía sin cargar su URL. | `WebViewWebEngine.restoreState()` descarta el resultado nullable de WebView; `WebTabState.restoreWebView()` devuelve `true` siempre que haya Bundle decodificable. `TabsModel.changeTab()` omite `loadUrl` cuando recibe `true`. | Propagar éxito real de restauración; si falla, descartar estado inválido y cargar URL guardada. Prueba con Bundle vacío/incompatible y estado válido. `restoreState()` puede devolver null [S6]. |
| F04 | Media | Geolocalización y permisos multimedia comparten un diálogo y estado mutable sin identidad por solicitud. | `WebViewEx.onGeolocationPermissionsShowPrompt()` sustituye `permRequestDialog` sin resolver el anterior; los botones usan `geoPermissionsCallback!!` y el origen actual. `onGeolocationPermissionsHidePrompt()` puede cerrar el diálogo multimedia. El resultado Android geo tampoco identifica la solicitud original. | Separar solicitudes, capturar callback/origen inmutables y resolver o cancelar el anterior. Prueba: geo pendiente + micrófono, cancelación y reemplazo durante el diálogo Android; ningún resultado puede conceder otra solicitud ni provocar NPE. |
| F05 | Media | Selección de archivo pierde su callback o entrega el resultado a la pestaña equivocada. | `WebViewEx.onShowFileChooser()` sobrescribe `pickFileCallback`. `destroy()` no cancela ese callback. `MainActivity.onActivityResult()` lo envía a `currentTab`, no al iniciador. | Registrar propietario y resolver cada callback una vez, con null al cancelar/reemplazar/destruir. Prueba: dos solicitudes, recreación de actividad y enlace externo que cambia pestaña mientras el selector está abierto. El contrato obliga a resolver el callback cuando se devuelve true [S7]. |
| F06 | Media | Desactivar depuración WebView deja activa la inspección en el proceso. | `MainSettingsView.initWebEngineDebugUI()` guarda false; `WebViewEx` solo llama `setWebContentsDebuggingEnabled(true)`, nunca false. Crear otra WebView tampoco revoca el valor global. | Aplicar explícitamente el valor vigente al proceso, también al apagar el ajuste. Prueba en release: activar, reiniciar como indica la UI, desactivar y comprobar que desaparece de chrome://inspect sin reiniciar de nuevo [S6]. |
| F07 | Media | Al elegir User-Agent Default, la pestaña viva conserva el UA personalizado/anterior. | El setter `WebViewWebEngine.userAgentString` solo actualiza settings cuando el valor no es null; la suscripción de MainActivity sí entrega null. La corrección previa del editor no cubre este setter. | Asignar también null: WebSettings lo interpreta como UA predeterminado. Prueba Custom → Default en la misma WebView y verificar cabecera y navigator.userAgent [S8]. |
| F08 | Alta, Android 15+ | Descarga dataSync larga en segundo plano puede terminar en excepción fatal al agotarse el tiempo del sistema. No es un defecto específico de Android 14. | Ambos servicios declaran foregroundServiceType=dataSync; targetSdk=36; `DownloadService` no implementa `onTimeout(int,int)` ni limita tiempo agregado de segundo plano. | Implementar parada dentro del plazo del callback, cancelar/persistir tareas y manejar denegación de nuevo arranque. Prueba en Android 15+ con timeout reducido mediante herramientas oficiales. El presupuesto es agregado por tipo y se restablece al volver a primer plano [S9]. |
| F09 | Media | Un borrado fallido elimina igualmente el registro y oculta el archivo al usuario; además ejecuta el borrado físico en Main. | `ActiveDownloadsModel.deleteItem()` captura excepción o rowsDeleted<1 pero siempre borra en Room. La rama File.delete ignora false. `DownloadsActivity.deleteItem()` se ejecuta con Dispatchers.Main y después elimina la fila visible. | Borrar archivo en IO, tratar archivo ya inexistente por separado y conservar registro/mostrar error cuando falle. Prueba con proveedor lento, excepción, cero filas y File.delete=false. `suspend` no desplaza por sí mismo el código síncrono a IO [S10]. |

## Riesgos y brechas que necesitan validación

| ID | Prioridad | Evidencia y límite de la conclusión | Acción y prueba |
| --- | --- | --- | --- |
| R01 | Alta en poca RAM | `DownloadService` usa `newFixedThreadPool(2)`: limita trabajadores, **no tareas pendientes**. `activeDownloads` también crece sin cota. Cada blob puede retener 44.739.244 caracteres base64 para 32 MiB originales, además de copias en renderer/bridge. No se ha medido OOM real. | Cota por tareas y bytes agregados, rechazo visible y limpieza al cancelar. Experimento JVM ejecutado: 2 trabajadores ocupados admitieron otras 100 tareas; quedaron 100 en cola tras shutdown. El carácter no implica un tamaño de heap fijo: depende de la representación JVM. Android documenta la cola ilimitada [S11]. |
| R02 | Alta en poca RAM | La única WebView viva no limita todos los estados: `WebTabState.savedState` sigue referenciado tras persistirlo; trimMemory lo conserva; no hay presupuesto agregado de pestañas/Bundle. `restoreWebView()` lee todo el fichero con readBytes desde la ruta síncrona de cambio de pestaña. Tamaño, latencia y crecimiento reales no medidos. | Limitar tamaño antes de leer y captura de historial; lectura/deserialización IO, liberación de snapshots ya persistidos con control de revisiones. Probar 1/10/50 pestañas con historial largo, corrupción y fichero excesivo. No afirmar TransactionTooLarge por esta persistencia a disco: no se demostró que estos Bundles viajen en savedInstanceState [S12]. |
| R03 | Alta para vídeo | `onPause()` pausa WebView y timers, lo cual es útil. Pero `MainActivity.onStop()` solo desliga descargas; no libera la WebView activa ni el custom view de vídeo. `onTrimMemory()` solo recorre pestañas no seleccionadas. No se ha demostrado que el proveedor siga reproduciendo o retenga el decoder: depende de WebView y sitio. | Medir salida a Home durante vídeo normal/fullscreen, audio, buffers y decoder. Diseñar liberación al quedar realmente oculto sin romper selector de archivos ni retorno del usuario. La guía TV recomienda liberar recursos multimedia al parar [S1]; TV-NP exige vídeo pausado al salir [S3]. En Android 14 no se debe depender de las antiguas notificaciones de trim: revisar UI_HIDDEN y BACKGROUND [S17]. |
| R04 | Media | `largeHeap=true` está aplicado a todos los procesos y no hay comprobación `isLowRamDevice` ni presupuesto documentado. No demuestra fuga ni que quitarlo mejore el rendimiento. | Medir heap normal/grande, app + renderer, GC y presión del sistema en release; definir presupuesto antes de cambiarlo. Android indica que largeHeap no garantiza memoria adicional y aconseja reducir consumo [S13]. |
| R05 | Alta para coherencia entre procesos | `VireoLumaTVApp` abre preferencias con MODE_MULTI_PROCESS; modo normal e incógnito usan procesos distintos. `incognitoMode` usa commit síncrono, lo que mejora durabilidad, pero no ofrece reconciliación entre procesos. No se reprodujo pérdida de ajustes. | Probar cambios con descargas todavía vivas en el otro proceso; usar propietario explícito y mecanismo IPC/almacenamiento multiproceso compatible. No basta sustituir por MODE_PRIVATE sin diseñar coordinación. La API está deprecada y Android dice que no es fiable [S14]. |
| R06 | Alta, divergencia de seguridad | `onReceivedSslError()` cancela por defecto, pero permite proceed tras acción explícita y comparación de certificado. Está restringido y no es un bypass silencioso; aun así diverge del contrato recomendado oficialmente. | Revisar si se conserva la excepción como política de navegador. Para cumplimiento estricto cancelar siempre. Probar cambio de certificado, host, subrecurso, navegación y consumo de la autorización. No afirmar explotación demostrada [S15]. |
| R07 | Alta, frontera del bridge | `addJavascriptInterface` está disponible en todas las páginas. El token y activación nativa limitan blobs; comprobar URL principal no autentica el iframe llamante. Hay lecturas de estado mutable de vista desde el hilo del bridge. No se ha ejecutado una PoC que eluda token/activación ni se afirma acceso arbitrario a archivos. | Prueba con iframe de otro origen compitiendo por una activación y navegación concurrente. Para operaciones privilegiadas evaluar WebMessageListener con orígenes explícitos/isMainFrame y obtener snapshots de estado en Main de forma segura. Android documenta todos los frames e hilo de bridge separado [S6]. |
| R08 | Alta para privacidad, condicionado al backup | allowBackup=true y sin fullBackupContent/dataExtractionRules. Las pestañas privadas se persisten en Room y sus estados en filesDir; limpiarlas al salir o al siguiente arranque no es una exclusión de copia de seguridad. CacheDir tiene exclusión propia; no se afirma que previews de cache entren en backup. No se comprobó una copia real ni política OEM. | Definir qué historial/preferencias se conserva y excluir datos privados. Como una DB mezcla filas normales/privadas, una regla por fichero no puede excluir solo ciertas filas: revisar almacenamiento privado separado o exclusivamente en memoria. Probar backup/restore durante sesión y tras cierre abrupto [S16]. |

## Aspectos ya correctos que conviene conservar

- Una sola pestaña con WebView viva; destrucción explícita de vistas de fondo.
- Manejo de pérdida de renderer que destruye la vista afectada y prepara recuperación.
- onPause + pauseTimers; no añadir otra pausa global indiscriminada.
- Previews de hasta 480 px, RGB_565, favicons con caché de 2 MiB, lectura y decode
  acotados, dos búsquedas concurrentes, deduplicación y cooldown.
- Safe Browsing independiente del ajuste adblock, feature checks de darkening,
  retry limitado y cancelable para descargas, blobs decodificados por streaming.
- Estado de pestañas con AtomicFile y control de revisiones, esquema Room v19
  generado por compilador y verificado en CI, reducción con R8.
- Manifest leanback y hardware táctil no obligatorio; minSdk 24.

No se prescribe bajar globalmente la prioridad del renderer. El código de
fullscreen oculta la WebView con GONE mientras muestra el custom view: copiar
`setRendererPriorityPolicy(..., true)` sin evaluar esta transición podría hacer
reclamable el renderer durante vídeo [S12]. Tampoco se recomienda vaciar cachés
en cada navegación o simular un UA antiguo como optimización.

## Cobertura adicional y límites

| Área | Resultado de la revisión |
| --- | --- |
| Manifest/instalación | Leanback, landscape y touchscreen no requerido están declarados. Icono/banner, overscan y legibilidad requieren launcher/pantalla reales; no se certifican solo por XML. |
| Arquitecturas y distribución | Configuración de splits incluye armeabi-v7a, arm64-v8a y x86_64. No se inspeccionó alineación ELF/ZIP de un AAB ni se ejecutó validación Play/16 KB. Compatibilidad de instalación en onn 32 bits no prueba conformidad de distribución completa. |
| Input/voz/accesibilidad | Hay cobertura de cursor, foco y activación en pruebas unitarias; faltan mando real, TalkBack, teclado del fabricante, permisos de voz, subtítulos y tamaños de texto. No se convierte esa ausencia de medición en un bug demostrado. |
| Descargas >2 GiB | DownloadTask consulta contentLength (Int) antes de convertir a Long; puede perder la longitud conocida y mostrar progreso indeterminado. Al terminar usa bytesReceived. Revisar contentLengthLong; no se afirma corrupción del archivo por ese detalle. |
| Descargas y UI legacy | startDownload realiza existencia/mkdir síncronos en Android anterior a 11. Medir y mover a IO si se mantiene soporte; onn Android 14 no usa esa rama. |
| Prioridad/renderer bloqueado | Falta evaluación de WebViewRenderProcessClient y prioridad al estar oculto. Son opciones de recuperación/observabilidad; su ausencia no es por sí misma incumplimiento ni prueba de ANR. |
| SDK antiguo | Application modifica applicationInfo.targetSdkVersion=32 en API <=28 para darkening. No cambia el target declarado ni afecta onn 14. Revisar y aislar esa solución heredada con proveedor/API real antes de mantenerla. |
| Analizadores y red | No se observó un runBlocking en rutas revisadas. No se ejecutó fuzzing de listas adblock, certificados, MIME, URL, contenido hostil o parser HTML; ni análisis de dependencias/licencias nuevo. |

## Orden de trabajo en esta misma rama

1. F01–F03: salida por Atrás, lista de permisos y fallback de restauración,
   en commits pequeños con pruebas específicas.
2. F04–F07 y F09: propietario/cancelación de callbacks, ajustes efectivos y
   borrado consistente fuera de Main.
3. F08: compatibilidad de descargas con Android 15+, sin confundirla con onn 14.
4. R01–R02: presupuesto agregado de tareas/bytes/estados; conservar integridad
   de estados y migraciones, sin eliminar datos existentes.
5. R03–R08: medición de vídeo/memoria y decisiones explícitas de privacidad,
   TLS, bridge y coordinación de procesos.

## Matriz de aceptación física pendiente

Usar APK release y registrar modelo, Android, 32/64 bits, proveedor WebView y
`isLowRamDevice`. Cubrir onn Android 14/2 GB y Android 15+ para F08; incluir
un dispositivo que realmente reporte low RAM. Repetir arranque frío/caliente,
10 cambios de pestaña, navegación larga y retorno desde Home.

| Escenario | Evidencia necesaria |
| --- | --- |
| Inicio, menú, IME, fullscreen, selector, mando D-pad y gamepad | Todos los controles accesibles; foco visible; Atrás sin bucle; callbacks resueltos una vez. |
| YouTube y vídeo HTML5/HLS/DRM | Sitio y URL de prueba, calidad/codec, errores de red/decoder, pausa, seek, audio/subtítulos y retorno de fullscreen. No deducir compatibilidad DRM de MediaDrm.isCryptoSchemeSupported. |
| Memoria con 1/10/50 pestañas y 0/2/muchas descargas | PSS/Private Dirty/Swap/Graphics de app y renderer, tamaño de estados, heap, GC, CPU, frames lentos y LMK. Comparar puntos equivalentes con la misma página y WebView. |
| Home durante vídeo y después de 30 segundos | Confirmar pausa real, liberación de decoder/buffers, memoria retenida y recuperación al volver. |
| Incógnito, cookies, cambio de proceso, cierre abrupto y restauración | Aislamiento de sesión, sin estados privados restaurados, sin pérdida de normales; evidencia de backup conforme a política definida. |
| Descargas grandes, disco lleno, cancelación, borrado y timeout 15+ | Integridad, progreso, errores visibles, cola/bytes acotados y servicio detenido dentro del plazo. |

Comandos de captura para un equipo con adb; no se ejecutaron en esta auditoría:

```sh
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell dumpsys webviewupdate
adb shell dumpsys meminfo com.reiniertutoriales.vireolumatv
adb shell dumpsys activity processes com.reiniertutoriales.vireolumatv
# Identificar PID de SandboxedProcessService asociado a la app, y medirlo aparte:
adb shell dumpsys meminfo RENDERER_PID
adb shell dumpsys gfxinfo com.reiniertutoriales.vireolumatv framestats
adb logcat -d -v threadtime
```

`ro.config.low_ram` puede ser una pista OEM, pero no sustituye una consulta a
ActivityManager.isLowRamDevice en la app. Un muestreo de meminfo no es un pico
máximo garantizado; guardar series y trazas durante las transiciones.

## Fuentes oficiales

Todas las conclusiones sobre APIs se contrastaron con Android Developers.
Las rutas defectuosas y prioridades son el análisis de este repositorio.

- S1: [Optimize memory usage — Android TV](https://developer.android.com/training/tv/playback/memory).
- S2: [WebView and memory](https://developer.android.com/topic/performance/memory/guide/webview-memory).
- S3: [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality).
- S4: [TV navigation](https://developer.android.com/training/tv/get-started/navigation).
- S5: [PermissionRequest](https://developer.android.com/reference/android/webkit/PermissionRequest).
- S6: [WebView — restoreState, debugging y addJavascriptInterface](https://developer.android.com/reference/android/webkit/WebView).
- S7: [WebChromeClient.onShowFileChooser](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser(android.webkit.WebView,%20android.webkit.ValueCallback%3Candroid.net.Uri[]%3E,%20android.webkit.WebChromeClient.FileChooserParams)).
- S8: [WebSettings.setUserAgentString](https://developer.android.com/reference/android/webkit/WebSettings#setUserAgentString(java.lang.String)).
- S9: [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).
- S10: [Better performance through threading](https://developer.android.com/topic/performance/threads).
- S11: [Executors.newFixedThreadPool](https://developer.android.com/reference/java/util/concurrent/Executors).
- S12: [Manage WebView objects](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview).
- S13: [Application largeHeap](https://developer.android.com/guide/topics/manifest/application-element#largeHeap).
- S14: [Context.MODE_MULTI_PROCESS](https://developer.android.com/reference/android/content/Context#MODE_MULTI_PROCESS).
- S15: [WebViewClient.onReceivedSslError](https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,%20android.webkit.SslErrorHandler,%20android.net.http.SslError)).
- S16: [Auto Backup](https://developer.android.com/identity/data/autobackup).

- S17: [Manage your app’s memory — trim callbacks](https://developer.android.com/topic/performance/memory/manage-app-memory).
