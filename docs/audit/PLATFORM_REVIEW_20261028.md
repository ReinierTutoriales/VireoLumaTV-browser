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
4. **Cola de descargas:** los dos workers de `DownloadService` limitan transferencias simultáneas, pero `newFixedThreadPool(2)` mantiene una cola pendiente sin límite explícito. Añadir admisión acotada con estado visible, cierre de streams y pruebas de saturación, cancelación y reanudación; nunca ejecutar una transferencia en el hilo de UI como mecanismo de rechazo.
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
