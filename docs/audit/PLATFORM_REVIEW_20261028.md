# Revisión de plataforma y eficiencia — 28 de octubre de 2026

Base: `master` `7e8995a0` (2.0.1). Esta revisión contrasta requisitos seleccionados con código y documentación oficial; no certifica todos los requisitos de Android TV ni mide ganancias en dispositivo. Los cambios de esta rama conservan funcionalidades, identificador de aplicación, firma y esquema Room.

## Resultado concreto

El proyecto ya aplica varias medidas de eficiencia y usa dependencias recientes. No hay evidencia para llamarlo óptimo ni para afirmar fluidez máxima. Se encontraron y corrigieron trabajo duplicado y una selección de sugerencias innecesariamente costosa. También quedan mejoras de arquitectura concretas, descritas abajo, que se pueden desarrollar sin esperar a una TV.

| Recomendación o requisito | Evidencia en el código / cambio | Estado verificable |
|---|---|---|
| Evitar fondos redundantes ([Android: overdraw](https://developer.android.com/topic/performance/rendering/overdraw)) | `AppTheme` y `SettingsScreen` ya tienen `windowBackground`; se retira el mismo fondo de las raíces de `browser_window`, `history_screen`, `downloads_screen`, `settings_screen`. Los overlays conservan su fondo. | `WindowBackgroundTest` compara todos los píxeles compuestos con/sin el antiguo fondo. No cuantifica ahorro de GPU: Android puede descartar capas opacas. |
| Evitar descargas de favicons innecesarias ([WebSettingsCompat](https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#setDownloadFaviconsEnabled(android.webkit.WebSettings,boolean))) | `BrowserWebView` desactiva la descarga automática solo si el proveedor anuncia `DOWNLOAD_FAVICONS_ENABLED`. La interfaz carga sus iconos por `ViewFaviconLoader`/`Favicons`; `TabWindow` no consume el bitmap del callback de Chromium. | Prueba HTTP del cargador propio: descarga, decodificación y reutilización de caché. El cambio de URL también notifica a la tira de pestañas aunque no llegue un nuevo título o callback de icono, incluidos cambios de historial SPA; una URL repetida no vuelve a notificar. Proveedores antiguos conservan su comportamiento. |
| Acotar trabajo y asignaciones de interfaz ([renderizado](https://developer.android.com/topic/performance/rendering/slow-ui)) | `AddressSuggestions` elige hasta seis filas mediante tres pasadas estables; elimina la lista secundaria de todos los candidatos y su ordenación. | Pruebas con 10 000 favoritos, prioridades, duplicados y orden. Todavía se cargan todos los favoritos como entrada: no se afirma memoria total constante. |
| Sanear intents de origen no confiable ([seguridad de intents](https://developer.android.com/privacy-and-security/risks/intent-redirection)) | `ExternalWebNavigation` elimina `component`, `selector`, `clipData` y permisos de URI; añade `BROWSABLE` antes de resolver. Se mantienen la confirmación y los deep links por paquete. | Pruebas de limpieza y de conservación de datos/extras legítimos. Es una corrección delimitada; no equivale a certificar todos los esquemas o extras posibles. |
| Limitar bitmaps y cachés ([memoria en TV](https://developer.android.com/training/tv/playback/memory)) | `BrowserTab`: previews de 480 px RGB565 y límite de lectura de 2 MiB. `Favicons`: LRU de 2 MiB, lado máximo de 256 px, entrada máxima de 2 MiB y candidatos acotados. `AdBlocker`: cachés acotadas y vaciado con presión de memoria. | Límites presentes en código y pruebas existentes. Falta presupuesto conjunto por escenario. |
| Liberar WebView y medir sus procesos ([memoria WebView](https://developer.android.com/topic/performance/memory/guide/webview-memory)) | Desconexión/destrucción al cerrar; tratamiento de muerte del renderer. El WebView activo se reutiliza, con excepción del flujo de ventanas emergentes. | Cobertura de ciclo de vida existente. La memoria de la aplicación no representa por sí sola la del renderer. |
| No mantener animaciones activas en reposo | El puntero usa `postOnAnimation`, termina al cesar el movimiento y reutiliza objetos de dibujo. | Inspección del controlador; fluidez con mando todavía sin traza física. |
| Mantener herramientas estables compatibles ([AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)) | AGP 9.4.1 → 9.5.0; wrapper 9.8.2 y JDK 21 de CI cumplen los mínimos de Gradle 9.6 y JDK 17. Se conserva NDK r30 fijado. | Compatibilidad final sujeta al build de esta rama, no solo a la tabla de versiones. |

## Versiones comprobadas

Consulta del 2026-10-28 de metadatos de publicación primarios: Google Maven para AndroidX/AGP, Maven Central para Kotlin/KSP/coroutines/Robolectric/JUnit y `services.gradle.org/versions/current` para Gradle. Se excluyen alpha, beta y RC; “estable” no significa que una migración de versión mayor sea necesaria.

| Componente | Proyecto tras estos cambios | Resultado de la consulta |
|---|---|---|
| AGP | 9.5.0 | Estable más reciente; se actualiza en esta rama |
| Gradle | 9.8.2 | Estable actual |
| Kotlin / KSP | 2.4.21 / 2.3.12 | Estables actuales |
| WebKit / AppCompat | 1.18.0 / 1.8.0 | Estables actuales |
| RecyclerView / Lifecycle | 1.4.0 / 2.11.0 | Estables actuales |
| Room | 2.8.5 | Estable de la línea 2; no se fuerza migración mayor |
| Coroutines / Robolectric | 1.11.0 / 4.17 | Estables actuales |
| JUnit | 4.13.2 | Última de la línea 4 |
| adblock | 0.13.5 tras integrar #83 | El PR #83 prepara 0.13.5 con regresiones y recuperación de caché; integrado en `master` mediante el PR #83 antes de actualizar esta rama |

El motor no se sustituye por uno propio solo por procedencia: perder corrección, mantenimiento o compatibilidad de listas no demuestra eficiencia. La revisión de licencias y procedencia sigue siendo independiente de esta auditoría de plataforma.

## Trabajo pendiente con criterios de aceptación

1. **Arranque WebView:** evaluar [`WebViewCompat.startUpWebView`](https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup). Requiere una puerta de inicialización: ninguna llamada prematura debe forzar el arranque síncrono; configurar el directorio del proceso incógnito antes de tocar el proveedor; cubrir fallo y proveedores sin soporte. Medir inicio frío y caliente antes/después. No basta mover una llamada a otro hilo.
2. **Baseline/Startup Profiles y Macrobenchmark:** no hay módulo ni perfiles propios. Incorporar recorridos de arranque, dirección, pestañas e historial, generar perfiles y comparar release con y sin ellos. [Calidad TV](https://developer.android.com/docs/quality-guidelines/tv-app-quality) y [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) ofrecen criterios concretos; no se declara un nivel completo por cumplir uno de ellos.
3. **Presupuesto de memoria:** medir aplicación, proceso incógnito cuando exista y renderers atribuidos; diferenciar memoria gráfica y Java/native. `largeHeap=true` sigue sin un presupuesto demostrado ([manifest](https://developer.android.com/guide/topics/manifest/application-element#largeHeap)). Comparar activado/desactivado con cargas iguales antes de retirarlo. Diseñar límites por capacidad real, sin listas de marcas/modelos. No activar prefetch ni más WebViews sin comprobar su coste.
4. **Cola de descargas:** la revisión adicional de esta rama limita la admisión y cierra payloads rechazados (ver abajo). Falta presupuesto agregado de bytes y comprobar la experiencia de saturación en dispositivo; nunca ejecutar transferencias en el hilo de UI como mecanismo de rechazo.
5. **Consulta de sugerencias:** la nueva selección reduce trabajo intermedio; todavía falta acotar la consulta de favoritos sin alterar prioridades, carpetas, duplicados o resultados. Comparar con conjuntos grandes y preservar orden determinista.
6. **Compatibilidad heredada:** la asignación de `applicationInfo.targetSdkVersion=32` en API ≤28 para oscurecimiento es una solución heredada. Sustituirla exige comprobar capacidad del proveedor, oscurecimiento y navegación en esas versiones. No se elimina a ciegas ni se presenta como práctica moderna.

Las cifras de adblock obtenidas en ordenador con listas sin el preprocesado de la app no constituyen presupuesto de RAM, arranque o bloqueo real de Android TV. Vídeo, fullscreen, anuncios dentro del vídeo y mando necesitan recorridos de aplicación, además de tests del motor.

## Captura reproducible disponible

`scripts/measure-tv.py` guarda JSON con APK instalado, proveedor WebView, sistema, procesos, conexiones, meminfo y framestats. No borra datos ni cambia ajustes del sistema. Reinicia los contadores gfxinfo; `--cold-start` además fuerza cierre y arranque, solo si se solicita. Los informes pueden contener URLs o datos del dispositivo: revisarlos antes de compartirlos.

```sh
python scripts/measure-tv.py --serial SERIAL --source-sha SHA_DEL_APK --phase baseline-one-tab --output measurements
python scripts/measure-tv.py --serial SERIAL --source-sha SHA_DEL_APK --phase candidate-cold --cold-start --output measurements
python scripts/measure-tv.py --serial SERIAL --source-sha SHA_DEL_APK --phase candidate-four-tabs --renderer-pid PID_VERIFICADO --output measurements
```

Preparar la escena antes de cada captura; el intervalo predeterminado es de 30 segundos. Repetir 3–5 veces con los mismos sitios, datos, resolución, proveedor y condiciones térmicas: una pestaña, cuatro pestañas con cambio entre ellas, historial/descargas y vídeo fullscreen. Comparar APK release base y candidato. Guardar el hash del APK y comprobar manualmente que corresponde al SHA suministrado: el script no deduce esa relación. Atribuir renderers a la app mediante las conexiones guardadas, no sumar todos los procesos de WebView del dispositivo. `ro.config.low_ram` se registra como propiedad, no como resultado de `ActivityManager.isLowRamDevice()`.

`gfxinfo` describe ventanas nativas; no mide todos los frames de Chromium ni del vídeo. Para latencias y composición usar trazas Perfetto y recorridos Macrobenchmark, y verificar pérdida de frames del reproductor por separado. Definir umbrales tras obtener la base, sin inventar porcentajes de mejora.

Validación local del capturador: ayuda CLI y compilación sintáctica de Python. No ejecutado sobre una TV. Las pruebas Android, lint y APK minificados de los cambios deben comprobarse en CI sobre el SHA publicado.

## Auditoría de recorridos de aplicación sobre `af790f39`

Revisión adicional del actualizador, publicación, permisos, descargas, interceptación y ciclo de vida; no es una certificación de todas las funciones ni de fluidez. Los cambios de esta rama requieren su propia CI antes de integrar.

| Área | Hallazgo contrastado en código | Corrección y evidencia preparada |
|---|---|---|
| APK interrumpido | `ReleaseUpdater.download` borraba por hash incorrecto, pero no si fallaba la copia o se superaba el límite; las conexiones tampoco se desconectaban expresamente en todos los errores. | `saveVerified` cierra la entrada y elimina todo archivo no verificado en `finally`; desconexión HTTP también al fallar la negociación. Tests de lectura interrumpida, hash válido e inválido. |
| Instalación | `AboutPage` ejecutaba `install` dentro de `Result.onSuccess`: una excepción del instalador escapaba del manejador de error. Al volver de permitir orígenes desconocidos, la pantalla volvía a descargar. | Manejo de errores alrededor del recorrido completo, propagación de cancelación y botón restaurado. Conserva el APK ya verificado para reintento explícito mientras existe esa pantalla. Tests de instalador ausente y reintento por el botón sin red. No hay instalación silenciosa ni reanudación automática tras muerte del proceso. |
| Admisión de descargas | Dos trabajadores con cola sin límite; cola previa al enlace del servicio también sin límite. Se anunciaba «iniciada» aunque no hubiera admisión confirmada. | Servicio: dos trabajadores y cuatro tareas esperando, rechazo explícito con aviso; cola previa hasta seis solicitudes; solo anuncia inicio si el servicio acepta. Tests de saturación y cierre de payload rechazado. Son límites de cantidad, no un presupuesto medido de bytes ni de toda la cola de mensajes del hilo principal. |
| Permisos de almacenamiento | Al llegar el enlace del servicio se vaciaba la cola aunque el diálogo de permiso siguiera abierto. Denegar almacenamiento o abandonar la ventana descartaba objetos sin cerrar sus streams. | El enlace respeta el permiso pendiente; rechazo, denegación y abandono cierran streams y eliminan referencias al payload. Tests del enlace antes de la respuesta, denegación y abandono. |
| Capacidades de WebView | `approve` podía conceder recursos desconocidos al usar la lista completa de la petición. Android advierte expresamente contra este patrón. | Solo cámara/micrófono con permiso Android y DRM conocido soportado; se excluyen capacidades desconocidas y recursos no pedidos. Tests de peticiones mixtas y respuestas del sistema con recursos adicionales. |
| Propietario del permiso | Una respuesta Android antigua podía conceder a una petición posterior ya aprobada; ubicación también sustituía callback sin resolver el anterior. | La generación viaja junto al código de permiso en `WebViewPageEngine`; respuestas antiguas se ignoran. Sustituir o destruir resuelve/deniega al propietario anterior. Tests de dos solicitudes aprobadas, ubicación sustituida y destrucción. |

Otro fallo de descargas anterior a Android 11: elegir el primer nombre libre no lo reservaba hasta que el worker abría el archivo. Dos tareas con el mismo nombre podían recibir la misma ruta. La reserva usa ahora `createNewFile()` atómico, conserva archivos existentes y elimina la reserva si el executor rechaza la tarea. `DownloadServiceTest` cubre dos tareas esperando antes de cualquier escritura.

Se comprobó también la escritura del historial de descargas: `Transfer.run` insertaba el registro antes del `try/finally`, por lo que una excepción de base de datos dejaba el stream sin liberar y no notificaba fin. La inserción ahora entra en la ruta de fallo y cierre; un fallo de actualización de metadatos se registra sin impedir el cierre y notificación de la transferencia. Tests con triggers SQLite fuerzan ambas excepciones. Esto evita abortar el recorrido; no garantiza que los metadatos se persistan si el almacenamiento sigue fallando.

### ¿Se puede actualizar como una aplicación Android?

El flujo `generic` consulta manualmente desde Acerca de, elige ABI, verifica la firma ECDSA de los checksums y el SHA-256 del APK, y entrega un URI de solo lectura al instalador del sistema. `google` desactiva este actualizador; `foss` tiene identificador propio. La publicación usa el keystore de release y exige el certificado SHA-256 `7485dc06a3e9b12528679ff8c5f8d06b308300ff454fce8f9f0193e66adf051f`. El workflow indica que esa clave rige desde 1.2.1: no demuestra compatibilidad con instalaciones anteriores firmadas con otra clave ni con APK debug.

Android exige identificador y certificado compatibles y código de versión no inferior. La base actual tiene `versionCode=95`, `versionName=2.0.1`. La firma de checksums y la firma de instalación del APK son claves y comprobaciones distintas. Las pruebas no sustituyen instalar una actualización sobre una copia real con datos. El pipeline comprueba versión nominal y certificado, pero aún debe añadir una comparación del código de versión respecto a la última release antes de publicar; no se ha publicado otra versión en esta auditoría.

### ¿El bloqueador cumple su función completa?

Se revisaron `AdBlocker`, preprocesado de listas, JNI, clasificación, `BrowserWebViewClient`, scripts de documentos y controlador de YouTube. Hay reglas de red, `$popup` separado, ocultación/scriptlets, cachés acotadas, actualización con validadores y conservación de reglas tras errores. Las pruebas del motor y recuperación son necesarias y ya existen.

**Cobertura incompleta identificada:** no hay registro de `ServiceWorkerClient`/`ServiceWorkerClientCompat`. Android ofrece esa interceptación por separado; no afirmar que `WebViewClient.shouldInterceptRequest` cubre peticiones de service workers. Integrarla requiere atribuir origen y respetar excepciones por sitio, sin reutilizar ciegamente la pestaña visible. También faltan fixtures de proveedor real que recorran descarga → preprocesado → JNI → petición/ocultación. La muestra de URLs del motor no sustituye ese recorrido ni demuestra bloqueo de publicidad dentro del vídeo.

### UI y novedades de plataforma

Se contrastaron cápsula/pestañas, fila de pestaña, paleta, foco de ajustes, permisos, N01–N10 y las suites existentes de navegación, renderer, puntero y fullscreen. El manifest declara launcher TV y capacidades opcionales; no obliga a touchscreen. Las pestañas truncan títulos largos a una línea. La paleta opaca da contraste calculado de 14,47:1 (texto/surface), 7,97:1 (secundario/surface) y 16,16:1 (texto/foco); esto no verifica overlays, imágenes, estados disabled ni todas las combinaciones de recursos.

No se certifica que toda la UI esté pulida: faltan inspección visual por escenario, textos largos/escala de fuente, accesibilidad con lector y mando/proveedor reales. Las suites verifican recorridos concretos, no todos los sitios web. Las APIs modernas pendientes siguen siendo startup asíncrono, perfiles propios y presupuesto agregado; incluir novedades sin soporte/coste contrastado no demuestra optimización. La admisión acotada de esta rama resuelve cantidad de trabajos, no el presupuesto de RAM de WebView, renderers y vídeo ni el de todos los payloads.

Fuentes oficiales contrastadas:

- [Actualizaciones Android: paquete, certificado y versión](https://developer.android.com/google/play/app-updates)
- [PermissionRequest: no conceder recursos desconocidos](https://developer.android.com/reference/android/webkit/PermissionRequest)
- [ThreadPoolExecutor: colas acotadas y rechazo](https://developer.android.com/reference/java/util/concurrent/ThreadPoolExecutor)
- [ServiceWorkerController: interceptación separada](https://developer.android.com/reference/android/webkit/ServiceWorkerController)
- [Criterios de calidad TV](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Startup de WebView](https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup)

### Verificación directa de los APK publicados 2.0.1

Se descargaron los tres APK y `SHA256SUMS.txt`/`.sig`. La firma ECDSA del manifiesto se verificó con la clave pública fijada en `ReleaseUpdater`; cada APK coincidió con su hash firmado. `aapt2 dump badging` confirmó en los tres `com.reiniertutoriales.vireolumatv`, `versionCode=95`, `versionName=2.0.1`, target 37 y su ABI. El certificado extraído del bloque APK v2 dio en los tres el fingerprint fijado por el workflow (`7485dc06…df051f`). Esta extracción no es una prueba de instalación Android ni reemplaza verificar su firma en el dispositivo.

| ABI | SHA-256 del APK publicado |
|---|---|
| arm64-v8a | `0c80f59162b183f94fb4f747a6966bb6f5e505c3d0e732496a5f26bb079b50d3` |
| armeabi-v7a | `f33d463a535d194325dab89940f6415e775945454d19fdc9abd4f75264aa1cb3` |
| x86_64 | `5b01fc667afeefe4d7f828e186beab1ec5fd03af2514b97a170e850dc302b06b` |

`RELEASE_SOURCE.txt` y `SIGNING.txt` también coincidieron con sus hashes firmados. El primero vincula la release a `19da33fb585b3f89bee76ccd083a5b230af42eac`; el segundo registra la verificación con apksigner del workflow. Por tanto, **el APK 2.0.1 publicado no es el `master` auditado `af790f39`**: los commits posteriores requieren otra versión/código de versión y una publicación antes de llegar al actualizador. Mantener ambos como 2.0.1 no permite que `isNewer` ofrezca esos cambios a quien ya tiene 2.0.1. Esta auditoría no ha modificado versiones ni publicado una release.


## Recuperación del bloqueador tras actualizar el motor

Correcciones de código: si la caché binaria es incompatible, compilar e instalar
las listas guardadas antes de esperar respuestas de red. Una actualización a la
que le faltan listas conserva el motor activo y programa el reintento.
El puente distingue motor pendiente de un resultado válido sin filtros; el
script espera con un solo temporizador por documento y hasta doce reintentos
(aproximadamente 36 segundos). Se conserva el seguimiento del reproductor
mientras espera. No se recarga ni se reinicia el vídeo automáticamente.

Las pruebas cubren caché DAT6 con motor real, servidor detenido antes de responder,
lista ausente durante actualización y aplicación diferida de filtros. El reintento
no puede deshacer scripts que la web ya haya ejecutado; tampoco garantiza filtros
al primer arranque sin listas guardadas si la red tarda más que el plazo acotado.
La causa concreta del sitio observado en TV sigue sin reproducirse.


## Inicio y revisión dirigida — 2026-10-30

Inicio centra el buscador y la fila de iconos, conserva YouTube y añade +.
Los accesos nuevos se guardan en el almacenamiento del origen local de Inicio
(máximo 24), separado del antiguo menú de favoritos/Room. Solo admite HTTP(S),
sin credenciales ni HTML generado desde entradas del usuario. Los iconos se leen
de la caché existente; si faltan, se muestra un globo. No se contacta a los sitios
al abrir Inicio ni al añadir un acceso. El usuario puede borrar estos accesos al
borrar los datos web; incógnito usa el directorio WebView separado existente.
El formulario conserva envío GET nativo a DuckDuckGo, valida texto no vacío y
recorta espacios. No incorpora framework, fuentes externas, polling o animaciones.

Contraste de versiones oficial: AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.12, Gradle
9.8.2, WebKit 1.18.0, AppCompat 1.8.0, coroutines 1.11.0 y Room 2.8.5 coinciden
con las releases consultadas de sus proyectos; no se justifica actualizarlas en
este cambio visual. Esto no certifica todos los transitivos ni completa una
auditoría de todo el navegador.

Revisión de implementaciones: Inicio ya sirve recursos locales bajo HTTPS y no
requiere red para dibujarse. No se encontró uso de setAppCache/setRenderPriority,
LOAD_NO_CACHE ni acceso universal a archivos en el código inspeccionado.
largeHeap sigue activo y requiere presupuesto medido antes de retirarlo. El
workaround targetSdkVersion=32 solo en API<=28 para ForceDark sigue presente:
una API deprecada no debe eliminarse sin preservar esos dispositivos y su tema.
El arranque asíncrono de WebView, perfiles de arranque y medición de app+renderer
siguen pendientes concretos; no hay medición física nueva en este trabajo.

Referencias: https://developer.android.com/develop/ui/views/layout/webapps/load-local-content ;
https://developer.android.com/training/tv/get-started/navigation ;
https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup ;
https://developer.android.com/jetpack/androidx/versions ;
https://developer.android.com/build/releases/agp-9-5-0-release-notes ;
https://kotlinlang.org/docs/releases.html ; https://github.com/google/ksp/releases ;
https://github.com/Kotlin/kotlinx.coroutines/releases ; https://gradle.org/releases/ .
