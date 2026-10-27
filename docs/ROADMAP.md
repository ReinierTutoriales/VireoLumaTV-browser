# VireoLumaTV — trabajo unificado

Base comprobada: `7a95715` (1.2.0, `master`).
Rama de trabajo: `fixes/integrated-audit`.

El [registro de auditoría actual](audit/CONSOLIDATED_AUDIT_20261007.md)
reúne los defectos comprobados, cambios, decisiones sobre ramas y verificaciones.
El [roadmap anterior](audit/archive-2026-10-07/ROADMAP_PRE_CONSOLIDATION.md)
queda conservado como referencia histórica; sus pendientes requieren contraste
con el código actual antes de aplicarlos.

## Estado

| Área | Estado de esta rama |
| --- | --- |
| Tema guardado fuera de rango | Corrección y pruebas de regresión pasan en CI. |
| User-Agent personalizado | Corrección y pruebas del selector pasan en CI. |
| Room v19 | Esquema generado, inspeccionado y versionado; CI comprueba cambios inesperados. |
| JavaScript de inicio, controles y YouTube | Tres suites locales pasan. |
| Pruebas Android y APK debug/release | Primera CI completa en verde; consultar los checks del PR para el tip actual. |
| Publicación de rama y CI remoto | Rama publicada y CI ejecutada en el PR #76. |
| Limpieza remota de ramas | Ocho ramas verificadas eliminadas; seis referencias históricas conservadas. |
| Restauración rechazada y UA Default efectivo (F03/F07) | Arreglos publicados con pruebas que fallaron antes; aceptación mediante CI del tip actual. Ver [lote sin TV](audit/OFF_DEVICE_FIXES_20261007.md). |
| Actualización adblock: solicitudes y disparadores | Correcciones publicadas con HTTP local/Robolectric; aceptación mediante CI del tip. Salud por fuente y sitios reales pendientes. Ver [lote adblock](audit/ADBLOCK_OFF_DEVICE_20261007.md). |
| Renovación visual TV | Recursos de barras, pestañas y paleta; capturas/contraste/geometría automáticos. Validación de mando y rendimiento real pendiente. Ver [lote UI](audit/TV_UI_REFRESH_20261007.md). |
| Rendimiento, reproducción y mando en TV | Requieren dispositivo físico y medición. |

## Revisión de dependencias — 2026-10-26

[PR #78](https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/78):
revisión de Google Maven, Maven Central y crates.io; se conservan las versiones
estables actuales de Android/Gradle/Kotlin. NDK actualizado de r28c a
[r30 LTS](https://developer.android.com/ndk/downloads) (30.0.16248370), con
compilación nativa de las tres ABI como control. Cambios compatibles: smallvec
1.16.4, syn 3.0.9 y acciones de CI/release actualizadas; inventario de licencias
sincronizado con los paquetes comprobados. La CI incluye ahora lint de release.
Room 3 requiere migrar al paquete `androidx.room3`; se mantiene Room 2.8.5.
Las versiones mayores de transitivos se conservan según los requisitos de sus
consumidores. El PR registra los resultados de CI; validación física pendiente.

## Línea de trabajo vigente

Seguir el [plan detallado de ejecución](IMPLEMENTATION_PLAN.md): tareas por
etapa, contrato fullscreen→barras→puntero, bloqueo, recursos, actualización
de aplicación y condiciones de release. Este es el desarrollo del roadmap,
no una cola adicional de cambios.

La [política de estabilidad y modernización](REGRESSION_POLICY.md) y `AGENTS.md`
son el contrato para este trabajo. La auditoría identifica evidencia; no impone
parches que eliminen las barras ni cambia por sí sola el diseño de navegación.

| Etapa | Trabajo | Condición de cierre |
| --- | --- | --- |
| 0 — Protección | Política, contrato N01–N10, plantilla PR y mapa de cobertura. | Documentado en esta rama; ampliar pruebas de eventos reales y TV sigue pendiente. |
| 1 — Navegación | Estados separados para menú, barras, historial, IME, fullscreen, dialogs y salida; corregir restauración y callbacks. | Conservar controles inferiores/superiores y su foco; sin bucle ni pestaña vacía; regresiones cubiertas y validación TV. |
| 2 — Experiencia protegida | Reintento observable de adblock, reglas válidas, política de popups/redirecciones y descargas explícitas con límites. | Sitios de prueba legítimos/abusivos; login, target=_blank y descargas voluntarias preservados; sin ráfagas de diálogos. |
| 3 — Pestañas y recursos | Ahorro y evaluación de Equilibrado, precarga acotada, estado restaurable y presupuestos agregados. | Base y mediciones en onn 2 GB y dispositivo low-RAM; timers globales coordinados; pestaña activa y vídeo prioritarios. |
| 4 — Motor WebView | Evaluar startup/navegación/cache/favicons de APIs estables existentes y recuperación. | Feature checks por proveedor, fallback, release y medición antes/después. |
| 5 — UI TV | Renovar barra superior, estilos, tipografía/iconos/espaciado/foco y transiciones conservando acciones y barra inferior. | Propuesta visual, capturas comparables y N01–N10 intactos; mando, contraste y legibilidad comprobados. |
| 6 — Actualizaciones y release | Revisar librerías/toolchain por grupos compatibles, pruebas minificadas, perfiles y documentación. | Changelog oficial actual, SHA con CI verde y evidencia pertinente; no actualización masiva ni release implícito. |

Correcciones concretas F01–F09 y riesgos R01–R08 están en la
[auditoría Android TV](audit/ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md).
F01 requiere acordar la secuencia de salida respetando N01–N10; no quitar el
menú ni reinterpretar el botón inferior Atrás. Estas etapas son pendientes de
implementación, no mejoras ya entregadas. Comenzar cada etapa con los fallos
reproducibles y sus pruebas, sin reescritura general del motor.

Las ramas históricas conservadas se revisan por contenido único antes de
eliminarlas; no se usan como cola automática de parches.

## Reglas de integración

1. Los parches se generan con `git format-patch`. Nunca se escriben ni se reescriben a
   mano; un contador de hunk incorrecto produce `corrupt patch`.
2. La rama destino debe existir antes que la cola.
3. La rama `patchq/<rama-destino>` se crea **siempre desde `master`**, nunca desde la
   rama destino: en un `push`, GitHub solo ejecuta los workflows presentes en el árbol
   del commit empujado.
4. `.patchq/expect` es obligatorio cuando el cambio toca XML, localizaciones o archivos
   grandes, y recomendable siempre.
5. Antes de crear una cola, confirmar el SHA real de la rama destino con `git ls-remote`;
   el estado cacheado del conector puede estar desfasado.
6. Los parches se aplican en orden alfabético: usar prefijos `0001-`, `0002-`…

### Estructura

```text
patchq/<rama-destino>
└── .patchq/
    ├── 0001-<asunto>.patch
    └── expect
```

Formato de `expect`:

```text
pre  <ruta> <blob-sha-antes>
post <ruta> <blob-sha-después>
```

### Comportamiento del workflow

| Paso | Efecto |
|---|---|
| Verify pre blobs | Se detiene si la rama destino no está en el estado esperado |
| Apply patches | `git am --keep-cr` |
| Verify post blobs | Se detiene antes del push si el resultado no coincide |
| Push target and clean queue | Actualiza la rama destino y borra la cola |
| Dispatch CI on target | Lanza CI con `workflow_dispatch` sobre el nuevo head |

Notas:

- El push lo hace `GITHUB_TOKEN`; el run `pull_request` asociado puede quedar en
  `action_required`. El run válido es el de evento `workflow_dispatch`.
- Si la cola falla, no se borra: hay que eliminarla a mano desde `/branches` antes de
  crear otra.
- El workflow solo se ejecuta si el push lo hace el propietario del repositorio.

## Puertas

### W0 / F0 — medición física (requiere onn + ADB)

Obligatoria antes de atribuir mejoras de memoria o bloqueo, y antes de F4.

Base: compilar desde `f2ed4ecb3656759e41b6813b939d4ee2aacd369d` y, en la misma sesión,
desde el `fixes/audit` del momento.

Firma: el release debe firmarse con el mismo debug keystore que usa la CI; con otra
clave, `adb install -r` falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Comparar huellas:

```bat
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android | findstr SHA256
```

La huella de la CI se copia del log del paso "Restore shared debug keystore". No usar
huellas no verificadas.

Arranque en frío, sin `pm clear`:

```bat
adb shell am force-stop com.reiniertutoriales.vireolumatv
```

Memoria: WebView renderiza en un proceso aparte; hay que medir app y renderer.

```bat
adb shell ps -A -o PID,RSS,NAME | findstr /i "vireolumatv sandboxed webview"
adb shell dumpsys meminfo <PID>
```

Logs:

```bat
adb logcat -c
adb logcat | findstr /i "adblock onBlockedAds lowmemorykiller lmkd crash exception"
```

Fluidez:

```bat
adb shell dumpsys gfxinfo com.reiniertutoriales.vireolumatv reset
adb shell dumpsys gfxinfo com.reiniertutoriales.vireolumatv
```

Escenarios, con espera fija de unos 30 s antes de cada medición:

| Escenario | Qué registrar |
|---|---|
| Reposo | `meminfo` de app y renderer |
| 1 pestaña ligera | `meminfo` |
| 4 pestañas (lista fija) | `meminfo`, `lmkd` |
| Tras cerrar | `meminfo` |
| Bloqueo (d3ward) | Contador en pantalla frente al último `page total` de `onBlockedAds` |
| Fullscreen ×3 | logcat y comportamiento |

Lista fija: `youtube.com`, `google.com`, `wikipedia.org` y un sitio pesado reproducible.
No cambiarla entre mediciones que se vayan a comparar.

Preguntas que W0 debe responder:

- ¿`matches()` bloquea de verdad en el onn?
- ¿Cuánta memoria ocupa el motor actual y cuánto el renderer?
- ¿Hay jank atribuible al bloqueo?

### F4 — prueba de concepto `adblock-rust` (requiere datos de W0)

`adblock-rust` es el motor actual de Brave: bloqueo de red, filtrado cosmético,
scriptlets y redirecciones con sintaxis de uBlock Origin. No tiene bindings oficiales
para Android.

Alcance de la prueba, en una rama aislada:

1. Capa JNI propia y compilación `armeabi-v7a` con `cargo-ndk` en la CI.
2. Implementación de `ContentBlockerEngine` sobre el nuevo motor.
3. Medir en el onn: tamaño del `.so`, memoria tras cargar las listas, tiempo de carga y
   bloqueo en d3ward frente a W0.

Solo se adopta si mejora de forma medible a `ad-block 0.0.4` sin superar el presupuesto
de memoria del onn. Licencia MPL-2.0.

Estado: adoptado en la versión 1.1.0 (F4, F5 y F6 integradas en `fixes/audit`). Capa JNI en `native/adblock-jni` (adblock 0.13.3, jni 0.22),
compilada por Gradle con el NDK 28.2 para `arm64-v8a`, `armeabi-v7a` y `x86_64`; los tests de
JVM cargan una compilación de host del mismo motor. Medido en x86_64 con las mismas listas y
URLs (pendiente repetir en el onn). En Chromium con sitios reales y el mismo motor: 98/100 en
adblock-tester.com.

| | ad-block 0.0.4 | adblock-rust 0.13.3 |
|---|---|---|
| Compilar EasyList + EasyPrivacy + Spanish | 10 000 ms, +28,6 MB | 170 ms, +10,5 MB |
| Coste por petición | 138 µs | 4,6 µs |
| Consultas desde 4 hilos | resultados erróneos (estado compartido en `BloomFilter`) | correctas |
| + listas uBlock Origin (7 listas, 6,7 M caracteres) | 13 400 ms, +34 MB | 267 ms, +14,6 MB |
| `.so` arm64-v8a | 1,57 MB | 3,25 MB |

F6 incluida en la misma rama: filtrado cosmético por sitio y scriptlets propios
(`assets/adblock/page_filters.js`, sin `eval`), y redirecciones (`assets/adblock`, recursos propios).

Fases posteriores, condicionadas a F4: F5 cambio de motor para red; F6 filtrado cosmético
y scriptlets (inyección con `WebViewCompat.addDocumentStartJavaScript`, previa
comprobación con `WebViewFeature.isFeatureSupported`).

## Pendientes remotos

### W6 — actualizador

Activo solo en la variante `generic`. Consulta `releases/latest` de este repositorio, elige el APK
del ABI del dispositivo, verifica su SHA-256 contra `SHA256SUMS.txt` de la misma release e
instala con el instalador del sistema. Las variantes `google` y `foss` no se actualizan desde la app.
Android solo acepta una actualización firmada con el mismo certificado que la instalada; las
releases publicadas (1.0.0 a 1.2.0) usan el certificado compartido de CI.

Pendiente:

- verificar en el onn la descarga, la instalación y la actualización sobre 1.2.0 con datos conservados;
- la firma de checksums usa ECDSA P-256: `RELEASE_SIGNING_KEY` en GitHub y la pública
  fijada en `ReleaseUpdater.RELEASE_PUBLIC_KEY_PEM`. CI comprueba su correspondencia;
  las actualizaciones rechazan manifiestos sin firma válida. Las releases anteriores sin
  `SHA256SUMS.txt.sig` no son aceptadas por este verificador.
- el APK sigue firmado con el certificado compartido de CI; cambiarlo rompe la actualización de las
  instalaciones existentes y requiere rotación (API 28+) o reinstalación.

### Limpieza de historial

Pendiente de decisión antes de integrar en `master`. Reescribir `fixes/audit` invalidaría
todos los SHA citados en este documento y en los PR.

| Punto | Detalle |
|---|---|
| `43c10f3` | Commit grande y no atómico: eliminación de Gecko más cambios del bloqueador |
| `ee63b43` | Squash de PR #2: cambio de CI más el workaround de cadena ya retirado |
| `b27067d` | Commit vacío |
| `fixup!` | `4ab4ed3`, `7b8a4cc`, `279a8d7`, `d28f5bc` sin compactar |
| Reverts emparejados | `b438427`/`ef1d903` → `639370e`; `90e9211`/`8b4e988` → `208e0dd`; `156fdb3`/`3ebb562` → `02b3e1d` |
| Commits solo de Gecko | Cambios sobre código que `43c10f3` elimina |

Opciones: conservar el historial por trazabilidad, o construir una rama limpia para
`master` dejando `fixes/audit` como registro.

## Hechos técnicos confirmados

| Hecho | Consecuencia |
|---|---|
| `AdBlockClient::parse()` siempre devuelve `true` | La validez de una lista se comprueba por su contenido, no con `parse()` |
| El motor nativo ignora `$popup` y las reglas cosméticas | Las reglas `$popup` se compilan en un cliente aparte y la ocultación por sitio (`dominio##selector`) se inyecta al inicio del documento; las reglas cosméticas genéricas y procedurales siguen sin aplicarse |
| `onDetachFromWindow` pone `callback = null` | Las pestañas en segundo plano no actualizan el contador de bloqueos |
| `-assumenosideeffects` no garantiza eliminar la evaluación de argumentos | Los logs del camino caliente se eliminan en el código fuente |
| `isAd()` no tiene contador de invocaciones | W0 no puede medir la proporción bloqueadas/totales sin instrumentación |
| `AppDatabase.kt` es idéntico en `v1.0.0`, `v1.1.0` y `f1146b5`, con `version = 19` | Las versiones publicadas no usan las versiones 2, 3 ni 7; los huecos de migración 2→3, 3→4 y 6→7 no son alcanzables desde instalaciones publicadas |

## Deuda menor

| Área | Detalle |
|---|---|
| Favicons | `readTimeout` es por lectura, no un límite total; pico transitorio de hasta ~2× el icono |
| Adblock | Al cambiar la URL personalizada, la caché anterior se elimina tras guardar una nueva generación compilada; si esa escritura falla, la caché antigua puede permanecer hasta el próximo éxito. |
| Escritura atómica | Sin `fsync`: protege frente a `lmkd`, no frente a un corte de luz |
| `intent:` de una página | Se abren sin confirmación, a diferencia de otros esquemas externos (`askUserAndOpenInExternalApp`); decidir si pedir confirmación |
| `androidx.webkit` | 1.18.0 adoptada en la auditoría (compila y pasa tests); validar el WebView en el onn |
| Robolectric | 4.17 con targetSdk 37 en JDK 21. Los tests de `FaviconExtractorTest` fijan SDK 28 porque no necesitan APIs nuevas |
| targetSdk 37 | Subido en 1.1.1. Lint sin errores; el comportamiento en ejecución (bordes de pantalla, permisos) solo se valida en el onn |
| Crates Rust transitivos | `psl` y `icu_*` actualizados tras comparar 48 120 decisiones del motor (listas integradas) antes y después: sin diferencias. `url` ya estaba en su última versión compatible. El corpus sale de las propias listas y no es tráfico real |

## No tocar sin evidencia

| Área | Motivo |
|---|---|
| Ciclo de vida de WebView | Ya se destruye en detach, trim y muerte del renderer |
| Fullscreen | Ya se rechaza una segunda custom view; falta prueba física, no código |
| `WebTabState` `gecko:` | Compatibilidad con estados antiguos |
| Historial remoto | Decisión pendiente |
