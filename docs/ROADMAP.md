# VireoLumaTV — Roadmap WebView-only

Documento de trabajo de la rama de integración `fixes/audit`.

VireoLumaTV es WebView-only: Gecko está eliminado del build (módulo `:app:gecko`, flavor
`webengine` y dependencia de Mozilla) y ya no queda selector de motor en la app. El
dispositivo objetivo es el onn 4K Streaming Device (Realtek RTD1325, 2 GB de RAM,
sistema solo 32 bits `armeabi-v7a`, Android 14).

## Estado de referencia

| Dato | Valor |
|---|---|
| Rama de integración | `fixes/audit` |
| Base real de este documento | `f7c216d` |
| Línea base W0 | `f2ed4ecb3656759e41b6813b939d4ee2aacd369d` |
| Tag `w0-baseline` | Pendiente; el SHA completo es suficiente para medir |
| `applicationId` | `com.reiniertutoriales.vireolumatv` (debug y release comparten id) |
| APK de medición | `genericRelease` firmado con el debug keystore compartido |

## Cerrado

| Bloque | PR | Commits en `fixes/audit` | Resultado |
|---|---|---|---|
| Release CI | #2 | `ee63b43` | La CI ejecuta `assembleGenericRelease` además de tests y debug |
| Favicons | #1, #3 | `f2ed4ec`, `b592e4a`, `c928c3e` | Parser de manifest endurecido; timeouts, descarga única, límite de 2 MB, `use {}` |
| Patch queue | #6 | `c1bee2c` | Workflow `Apply patch queue` para aplicar parches desde móvil |
| Logs en release | #4 | `c8886dc`, `017e3fb`, `2ffad09`, `6534645` | R8 elimina `Log.v`/`Log.d`; `Log.i` se conserva; eliminados en el código fuente los `Log.d` por petición de `WebViewEx` |
| F1 `ContentBlocker` | #7 | `ab7b79b` | Interfaz `ContentBlocker`/`ContentBlockerEngine`; `ad-block 0.0.4` aislado en `BraveAdBlockEngine` |
| F2 pipeline de listas | #8 | `de4fb10`, `b8939a1`, `71a8689`, `eb01295` | Validación de contenido, caché de texto por lista, escritura atómica, BOM aceptado, reintento a 24 h, reutilización del serializado |
| F3 coalescing | #9 | `abd443e` | `onBlockedAds(count)` como máximo cada 250 ms; un `Log.i` de resumen por ráfaga |
| Selector de motor | #10 | `c945a88`, `996cbbb`, `29dbbee`, `1ed20c4` | UI, layout, preferencia y lectura en `WebEngineFactory` eliminados; cada commit compila |
| Cadenas huérfanas | #11 | `f7c216d` | 4 cadenas eliminadas en 11 `strings.xml`; retirado el workaround `translatable="false"` |
| Roadmap | #5 | `908d92b` | Versión anterior de este documento |

## Reglas de trabajo

1. Un problema → un commit (o serie pequeña) → CI verde → revisión → merge con rebase.
2. Cada commit debe compilar por sí solo: primero se eliminan los usos y después las
   definiciones.
3. No mezclar código, documentación, traducciones y CI en el mismo PR.
4. CI verde demuestra compilación, tests y validación de release; no demuestra
   comportamiento en el onn.
5. Todo cambio en memoria, renderizado, fullscreen, ciclo de vida de WebView, bloqueo o
   descargas necesita prueba física antes de darse por bueno.
6. Merge con rebase, no squash: se conservan los commits y su patch-id.
7. No tocar el prefijo `gecko:` de `WebTabState`: limpia estados de pestañas de
   instalaciones anteriores.
8. No reescribir el historial remoto de `fixes/audit` sin una decisión explícita.

## Método estándar: patch queue

Para cualquier cambio de código desde el móvil, sin edición por hunk en el conector.

### Reglas

1. Los parches se generan con `git format-patch`. Nunca se escriben ni se reescriben a
   mano; un contador de hunk incorrecto produce `corrupt patch`.
2. La rama destino debe existir antes que la cola.
3. La rama `patchq/<rama-destino>` se crea **siempre desde `fixes/audit`**, nunca desde la
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

Fases posteriores, condicionadas a F4: F5 cambio de motor para red; F6 filtrado cosmético
y scriptlets (inyección con `WebViewCompat.addDocumentStartJavaScript`, previa
comprobación con `WebViewFeature.isFeatureSupported`).

## Pendientes remotos

### W6 — actualizador

`latest_version.json` contiene canales y changelog vacíos: todavía no hay una versión
propia publicada para actualización automática. El actualizador está desactivado en
todos los builds y no consulta la red cuando está desactivado. No se ofrecen APK del
proyecto original como actualización de VireoLumaTV.

Pendiente:

- decidir el canal de actualización propio del fork;
- apuntar a APK reales publicados por este repositorio;
- definir la clave de firma de release;
- no publicar metadatos de actualización hasta tener una release firmada y probada.

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

## Deuda menor

| Área | Detalle |
|---|---|
| Favicons | `readTimeout` es por lectura, no un límite total; pico transitorio de hasta ~2× el icono |
| Adblock | Una caché de lista personalizada queda huérfana si cambia la URL |
| Adblock | `onBlockedAds` comprueba `config.adBlockEnabled`, no la configuración por host |
| Escritura atómica | Sin `fsync`: protege frente a `lmkd`, no frente a un corte de luz |

## No tocar sin evidencia

| Área | Motivo |
|---|---|
| Ciclo de vida de WebView | Ya se destruye en detach, trim y muerte del renderer |
| Fullscreen | Ya se rechaza una segunda custom view; falta prueba física, no código |
| `WebTabState` `gecko:` | Compatibilidad con estados antiguos |
| Historial remoto | Decisión pendiente |
