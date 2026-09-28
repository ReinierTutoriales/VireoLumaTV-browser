# TV Bro (fork) — Hoja de ruta: Gecko + bloqueo de anuncios

Documento de trabajo de la rama `fixes/audit`. Recoge qué está hecho, qué falta, en qué
orden, cómo se valida cada fase y qué no se debe tocar antes de tiempo.

Estado de referencia: HEAD `ef0eeb5`, 48 commits por delante de `master`, 0 por detrás.

## Leyenda

| Marca | Significado |
|---|---|
| ✅ Cerrado | Aplicado y con CI verde |
| ⏳ Pendiente | Aún no aplicado |
| 📺 Requiere dispositivo | Solo se valida en el onn 4K Pro (2026); la CI no lo cubre |
| 🔍 Solo auditoría | Lectura de código o documentación, sin commit |

## Reglas de trabajo

1. **Un problema → un commit → CI → informe → parada.** No se encadena el siguiente
   cambio hasta que el anterior tenga la CI en verde y se haya informado.
2. Cada afirmación técnica se valida contra documentación oficial, código fuente o el
   estado real del dispositivo antes de actuar.
3. CI verde demuestra compilación y tests, **no** el comportamiento en ejecución. Los
   cambios de ciclo de vida, UI, permisos, multimedia o red requieren prueba en el onn.
4. Nada de squash durante la estabilización. La limpieza del historial se hace al final
   (ver [Integración final](#integración-final)).
5. No se añade deuda técnica a este documento sin respaldo en código, CI o pruebas.

## Estado actual

Documenta el estado de la rama con sus commits relevantes. No es un changelog exhaustivo:
los `fixup!`, los intentos revertidos y el commit vacío se tratan en sus propias secciones.

### Correcciones funcionales y de seguridad (✅ CI verde, 📺 pendiente de prueba física salvo indicación)

| Commit | Cambio |
|---|---|
| `e16ac99`, `a452204` (+ fixup `4ab4ed3`) | Permisos de cámara/micrófono en WebView: resolución y cancelación |
| `2aa5cfa`, `ee8950f` | Selector de archivos WebView: resultado y cancelación |
| `ff9b7ba` | Descargas en Android 13+ aunque se deniegue POST_NOTIFICATIONS |
| `a29c672` | `destroy()` de WebViews descartados |
| `a5e92aa` | Saneo de nombres de archivo de descargas (path traversal en Android < 11) |
| `55738a1` | Escapado de valores del buscador inyectados en la página de inicio |
| `7473542` | Comparación real del certificado al confiar en un error SSL |
| `a487f39` | Decodificación de descargas blob con `Blob.type` vacío |
| `c79bd82` | Manejador de clics de descargas blob en `generic_injects.js` |
| `22eb9d4` | `FaviconsPool`: WebView temporal siempre destruido, con timeout, sin `runBlocking` |
| `e91a079` | WebView temporal de `clearCache()` destruido |
| `5182cbd` | Acceso nullable al GeckoView tras pulsación larga |
| `2b2ad62` (+ fixup `7b8a4cc`) | Peticiones de pantalla completa duplicadas. 📺 Pendiente: entrar/salir 3 veces en la misma pestaña |
| `b7fa247` | Conservar el estado de la pestaña al recortar memoria |
| `bfdf8a5` | Avisos repetidos de script lento en Gecko |
| `7b5b996` | Resultados del selector de archivos en Gecko sin `!!` |
| `76fd3a4` | Recuperación tras la muerte del proceso de renderizado de WebView |
| `de8946a` | Descargas pendientes hasta que el servicio conecte (CI verde; sin revisión de código detallada) |
| `ab5e278` | Cola de descargas mientras hay permisos pendientes |
| `8444c1a` | Peticiones de permisos web solapadas (parcial; completado en `50331af`) |
| `a993c10` | Liberar el indicador de permiso si la petición se interrumpe |
| `6a4ff07` | Descargas blob procesadas en el hilo principal |
| `50331af` | Resultado del permiso de Android ligado a la petición web que lo pidió. 📺 Pendiente: prueba de micrófono |
| `e233283` | A.1: conservar la última lista de bloqueo válida si falla la actualización |

### Toolchain y dependencias (✅ CI verde)

| Commit | Cambio |
|---|---|
| `e5043cb` | Workflow de CI: tests unitarios + APK debug de ambos motores |
| `935a943` | AndroidX estables (AppCompat 1.8.0, WebKit 1.17.1, ConstraintLayout 2.2.2, Lifecycle 2.11.0, Room 2.8.5) |
| `9739695` | KSP 2.3.12 |
| `bf01e17` | AGP 9.4.1 + Gradle 9.6.0 |
| `c2e9268` | Kotlin 2.4.20 |
| `62ad09c` (+ fixups `279a8d7`, `d28f5bc`) | Nulabilidad de `SlowScriptResponse` en Gecko exigida por Kotlin 2.4 |
| `36d99aa` | kotlinx-coroutines 1.11.0 |
| `d359ab9` | `distributionSha256Sum` de Gradle 9.6.0 (comprobado con descarga real en la CI) |
| `df8c216` | G0: acciones de `ci.yml` en Node 24 (`setup-gradle` v6 con `cache-provider: basic`) |
| `d9e5ac8` | Acciones de `release.yml` en Node 24 (ver [limitaciones](#limitaciones-de-validación-de-releaseyml)) |
| `ef0eeb5` | Eliminado el alias de plugin `kotlin-android` sin uso |

### Intentos revertidos (documentados para no repetirlos igual)

| Commits | Intento | Por qué falló |
|---|---|---|
| `90e9211`, `8b4e988` → revert `208e0dd` | Eliminar el flavor `geckoExcluded` | `:app:gecko` exige `minSdk 26`; `VersionSettingsView` depende de `BuildConfig.FLAVOR_appstore` y `BuildConfig.FLAVOR_webengine` |
| `156fdb3`, `3ebb562` → revert `02b3e1d` | GeckoView 155 | Callbacks de cookie banner eliminados; GeckoView 155 exige compilar contra API 37.1 (y arrastra androidx.core 1.19.0); `buildSrc` trata `compileSdk` como entero (`For input string: "37.1"`) |

## Hechos confirmados, hipótesis y decisiones pendientes

**Hechos confirmados**
- GeckoView sigue en `147.0.20260212191108`; `compileSdk 36`, `minSdk 24`.
- El proyecto ya usa el Kotlin integrado de AGP 9 (no aplica `org.jetbrains.kotlin.android`).
- `ContextElement.textContent` ya no existe en el código fuente actual de GeckoView (existe
  `linkText`); TV Bro lo usa en `MyContentDelegate.kt`. Pendiente de contrastar con la
  etiqueta exacta de la 155 en G3.
- El motor de bloqueo (`com.github.truefedex:ad-block:0.0.4`, clase `com.brave.adblock.AdBlockClient`)
  solo se usa para bloqueo de red: `parse`, `serialize`, `deserialize`, `matches`.
  Lista por defecto: EasyList únicamente.
- La CI solo ejecuta los tests unitarios de `GeckoExcluded`.
- La CI no configura una clave debug persistente; los runners efímeros pueden generar claves
  distintas entre runs, y en ese caso instalar un APK de otro run obliga a desinstalar y se
  pierden los datos.

**Hipótesis (sin confirmar)**
- **A.2:** el puerto `tvbro_bg` de la extensión Gecko queda ligado al `GeckoWebEngine` que
  lo registró; al cambiar de pestaña, `onDetachFromWindow()` pone `callback = null` y las
  peticiones de todas las pestañas esperarían el timeout de 1,5 s sin bloquearse. 📺
- Arquitectura del onn: se asume sistema de 32 bits por reseñas; **no** se decide nada de
  ABI hasta tener `adb shell getprop ro.product.cpu.abilist`.

**Decisiones pendientes**
- Motor de bloqueo en Gecko: reparar el puente actual o WebExtension/uBlock Origin (G7).
- Eliminar WebView como fallback (solo con datos de G5).
- ABI del APK (solo con la salida real de ADB).

## Plan por fases (orden operativo)

### G0 — CI en Node 24 ✅
`df8c216` (`ci.yml`) y `d9e5ac8` (`release.yml`). Limpieza del alias: `ef0eeb5`.

### Documentación ⏳
Este archivo, como commit solo de documentación.

### G1 — `compileSdk` con versión menor ⏳ 🔍
Antes del commit, verificar contra la documentación oficial de AGP 9.4.1:
1. La sintaxis oficial para declarar `compileSdk` 37.1 (sin parser improvisado).
2. Que AGP 9.4.1 soporta API 37/37.1 como `compileSdk`. Si no, G2 requiere antes otra
   actualización de AGP.

Commit: solo `buildSrc`. `android-compileSdk` sigue en `36`. CI y parada.

### G2 — `compileSdk` 37.1 con GeckoView 147 ⏳
Separar las dos variables que se mezclaron en el intento revertido. No se tocan
`targetSdk` ni `minSdk`. Salida: ambos motores compilan con CI verde.

### G3 — Auditoría GeckoView 148 → 155 🔍
Sin commit. Cruzar los changelogs/API oficiales con `MyContentDelegate`, `MyPromptDelegate`,
`MyPermissionDelegate`, `MyNavigationDelegate`, `MyMediaDelegate`, `MyProgressDelegate`,
WebExtensions, descargas, selector de archivos, pantalla completa, estado de sesión,
runtime settings, DRM y menú contextual. Producto: matriz API → cambio → afectado → acción.
Incompatibilidades ya conocidas: `ContextElement.textContent` y
`onCookieBannerDetected`/`onCookieBannerHandled`. No se asume que sean las únicas.

### G4 — GeckoView 155 ⏳
Solo la versión y las adaptaciones demostradas en G3. Fuera de este commit: optimizaciones,
preferencias experimentales, uBlock Origin, eliminar WebView, ABI, refactors de delegados.

### Clave debug fija en CI ⏳
Antes de G5: firmar los APK debug de la CI con una clave fija (secreto del repositorio) para
poder instalar builds sucesivos con `adb install -r` sin perder datos. Necesario para probar
la restauración de estado entre builds.

### G5 — Certificación física de Gecko 155 📺
En el onn: navegación y redirecciones; varias pestañas; restauración tras reiniciar;
vídeo HTML5 (reproducir, pausa, seek); pantalla completa repetida y con Atrás; DRM
(Widevine) real; permisos (micrófono, cámara, geolocalización: aceptar, rechazar, cancelar);
selector de archivos (único, múltiple, cancelar); descargas (normales, blob, cancelación);
estabilidad con varias pestañas y presión de memoria.
**WebView se mantiene como fallback como mínimo hasta superar G5.**

### G6 — Endurecimiento de Gecko ⏳
- Diálogo de script lento: al cerrar con Atrás, completar con `CONTINUE`
  (`setOnCancelListener`); hoy queda `activeAlert` en `true` y los avisos siguientes se
  aceptan solos.
- Auditoría de `MyPromptDelegate` y `MyPermissionDelegate` (aún no auditados): cada callback
  asíncrono debe completarse exactamente una vez.
Un commit por problema.

### G7 — Motor de bloqueo ⏳
**Entrada obligatoria:** resultado de la prueba A.2 en el onn (porcentaje en
`d3ward.github.io/toolz/adblock.html` en pasos A/B/C/D y log de
`AppWebExtensionBackgroundPortDelegate`/`onBlockedAd`).

Alternativas a evaluar (ninguna decidida):
- **A — Mejorar el motor actual:** corregir A.2, clasificación de recursos, ciclo de vida del
  puerto, varias listas. Mantiene el puente JS↔Kotlin por petición.
- **B — WebExtension (p. ej. uBlock Origin):** bloqueo dentro de Gecko sin ida y vuelta a
  Kotlin, con filtros cosméticos y scriptlets. Requiere verificar en la documentación de
  GeckoView 155 qué admite para extensiones embebidas y medir la memoria.

Medición obligatoria con `adb shell dumpsys meminfo com.phlox.tvwebbrowser`: Gecko sin
bloqueo avanzado, con él, y con varias pestañas. No se declara viable ninguna opción en un
box de 3 GB sin medirla. La UI de ajustes se decide después de demostrar la arquitectura.

### G8 — Gecko como arquitectura principal ⏳
Solo tras G5 y G7. Migración coordinada: primero el código que depende de
`BuildConfig.FLAVOR_webengine`, después `minSdk 26`, después simplificar flavors.
ABI: primero `ro.product.cpu.abilist` del onn, después build (`armeabi-v7a` solo si procede).

### G9 — Rendimiento de Gecko 📺
Medir antes de optimizar: arranque, primera y segunda navegación, cambio de pestaña, memoria
con 1/3/5 pestañas, vídeo, bloqueo activado/desactivado. Una optimización sin mejora medible
no entra.

## Trabajo independiente (cuando no interfiera con una migración)

Respaldado por la auditoría del código:
- **S1** ⏳ Quitar `Log.d`/`Log.i` por petición en el camino del bloqueo (WebView y Gecko).
- **S2** ⏳ Timeouts de conexión/lectura en la descarga de la lista de bloqueo y publicación
  segura entre hilos del `AdBlockClient` (hoy no es `@Volatile`).
- **U1b** ⏳ Regenerar `gradle-wrapper.jar` y los scripts `gradlew` desde la distribución
  oficial (ejecutando `gradle wrapper` en un runner), sin fabricarlos a mano.

WebView queda como fallback; sus mejoras de bloqueo (no evaluar el documento principal,
ajuste de la pestaña que origina la petición, tipos de recurso, `baseHost`, contador con el
ajuste por pestaña) solo se abordan si Gecko no resulta ser la plataforma definitiva.

## Deuda conocida (con respaldo en código)

- Consultas Room en el hilo principal (`allowMainThreadQueries()`).
- Safe Browsing ligado al ajuste del bloqueador.
- Migraciones Room 2→3→4 ausentes (solo afecta a instalaciones muy antiguas).
- Descargas fallidas en Android 11+: `cancelDownloadIfNeeded()` pone `IS_PENDING=0` al archivo
  parcial si no fue cancelado, publicándolo como si estuviera completo.
- Si el renderer muere en pantalla completa, la vista de pantalla completa no se retira.
- Selector de archivos Gecko en modo único: si el resultado solo trae `clipData`, se descarta.
- Descargas blob iniciadas con `a.click()` sobre un enlace fuera del DOM no se interceptan.
- `release.yml` no declara `permissions: contents: write`.
- Auto-actualizador del flavor `generic` apunta al repositorio original; `applicationId`
  igual al de TV Bro original (identidad del fork, bloque propio).

## Limitaciones de validación de `release.yml`

Solo se ejecuta manualmente y crea una release real firmada con el keystore. Hoy está
validado por estructura (actionlint) y por no romper la CI. Su ejecución real queda
pendiente de la primera release verdadera del fork.

## Calidad (después de estabilizar Gecko)

Android Lint en CI; StrictMode y LeakCanary en debug; tests unitarios también para
`GeckoIncluded`.

## Integración final

Al terminar la estabilización: actualizar la referencia de `origin/master` y hacer el rebase
interactivo con `--autosquash` contra el HEAD de `origin/master` vigente en ese momento.
- `--autosquash` integra los `fixup!`: `4ab4ed3` → `a452204`, `7b8a4cc` → `2b2ad62`,
  `279a8d7` y `d28f5bc` → `62ad09c`.
- `ee8950f` no es un `fixup!`: hay que marcarlo a mano como `fixup` de `2aa5cfa`
  (misma corrección del selector de archivos partida en dos commits).
- **`b27067d` es un commit vacío** y debe eliminarse a mano (git conserva por defecto los
  commits que nacen vacíos).
- Los intentos revertidos pueden eliminarse junto con sus reverts una vez demostrado el árbol
  final.
