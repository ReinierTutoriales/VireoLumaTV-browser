# TV Bro fork — Roadmap de estabilización WebView-only

Documento de trabajo de la rama `fixes/audit`.

Este roadmap reemplaza el plan anterior centrado en Gecko. El fork actual está orientado a
un APK WebView-only, con Gecko eliminado del build: módulo, flavor y dependencia. La prioridad
es estabilizar, medir en el onn y reducir deuda técnica sin mezclar problemas en commits grandes.

## Estado de referencia

Rama principal de trabajo:

| Rama | Estado |
|---|---|
| `fixes/audit` | Rama de integración actual |
| HEAD actual verificado | `c928c3e` |
| Línea base W0 | `f2ed4ecb3656759e41b6813b939d4ee2aacd369d` |
| W0 tag recomendado | `w0-baseline` pendiente de crear |
| APK objetivo | `genericRelease` firmado para instalación en onn |
| `applicationId` | `com.phlox.tvwebbrowser` |

Historial reciente relevante:

| Commit / PR | Estado | Descripción |
|---|---:|---|
| `f2ed4ec` | ✅ Integrado | PR #1, rebase de `01cb1bf`; incluye PR #2. Línea base W0 |
| PR #2 | ✅ Mergeado | CI valida release con `assembleGenericRelease`; workaround temporal de cadena Gecko |
| PR #3 | ✅ Mergeado | Timeouts y límites en red de favicons; dos commits preservados por rebase |
| PR #4 | ⏳ Abierto | Logs calientes en release; falta borrar dos `Log.d` de `WebViewEx.kt` |

La línea base W0 debe medirse desde `f2ed4ecb3656759e41b6813b939d4ee2aacd369d`.
No es imprescindible que exista el tag para medir, pero el tag evita errores manuales.

Crear tag cuando haya PC disponible:

```bash
git tag w0-baseline f2ed4ecb3656759e41b6813b939d4ee2aacd369d
git push origin w0-baseline
```

## Reglas de trabajo

1. Un problema → un commit o PR pequeño → CI → revisión → decisión.
2. No mezclar refactors, comportamiento, traducciones y CI en el mismo cambio salvo que sea
   estrictamente necesario para desbloquear la validación.
3. CI verde demuestra compilación, tests y release validation; no demuestra comportamiento en
   el onn.
4. Los cambios que afecten memoria, renderizado, fullscreen, WebView lifecycle, bloqueo de
   anuncios o descargas requieren prueba física cuando estén en el camino de ejecución.
5. Si una edición requiere hunk sobre archivo grande, hacerla desde PC o IDE. No sustituir
   archivos grandes completos por API si la herramienta no puede garantizar contenido íntegro.
6. No tocar `gecko:` en `WebTabState`; es compatibilidad de estado anterior y no forma parte
   de la retirada de Gecko.
7. Mantener `fixes/audit` como integración; no reescribir historial remoto hasta decidir la
   limpieza final.

## Estado técnico actual

### WebView-only

Gecko fue eliminado del build en `43c10f3`: módulo `:app:gecko`, flavor `webengine` y
dependencia de Mozilla. El objetivo actual es un APK WebView-only.

La limpieza de UI y cadenas relacionadas con el selector de motor queda pendiente porque
requiere cambios en varios archivos y traducciones.

No hacer todavía por API remota:

| Pieza | Motivo |
|---|---|
| `MainSettingsView.kt` | Selector en mitad de archivo; requiere edición por hunk |
| `view_settings_main.xml` | Layout grande; requiere edición por hunk |
| `Config.kt` | Limpieza de constantes y funciones; `isWebEngineNotSet()` no tiene usos |
| `WebEngineFactory` | Todavía tiene referencias en estado/versiones/factory; es refactor |
| Strings localizadas | Borrar cadena Gecko/WebView implica tocar múltiples XML localizados |

### CI

PR #2 añadió validación de release:

```bash
./gradlew assembleGenericRelease --stacktrace
```

Esto atrapó el fallo release-only de `lintVitalGenericRelease` causado por traducciones
stale de `settings_engine_change_gecko_msg`.

Workaround actual:

| Archivo | Estado |
|---|---|
| `app/src/main/res/values/strings.xml` | Contiene `settings_engine_change_gecko_msg` con `translatable="false"` |
| 9 locales | Siguen teniendo traducciones stale de esa cadena |
| Release lint | Verde con AGP 9.4.1 |

Ese workaround es temporal. Se debe retirar junto con la limpieza real del selector de motor
y las traducciones asociadas.

### Favicons

PR #1 y PR #3 dejaron el flujo de favicons más seguro:

| Área | Estado |
|---|---|
| Manifest parsing | Endurecido |
| HTML favicon discovery | Con timeouts |
| Web manifest fetch | Con timeouts |
| Icon download | Con timeouts |
| Doble conexión por icono | Eliminada |
| Streams | Cerrados con `use {}` |
| `inSampleSize` | Mínimo 1 |
| Tamaño máximo de icono | Limitado |
| CI | Verde |

Observaciones no bloqueantes:

- `readTimeout` no es timeout global de descarga; es por operación de lectura.
- Si hiciera falta límite total, usar `withTimeout` alrededor de la operación completa.
- La descarga a `ByteArray` puede tener pico transitorio de memoria; asumible por ahora, pero
  medible en W0/W1 si aparecen síntomas.

### Logs calientes / adblock

PR #4 está abierto y no está listo para merge.

Hecho en PR #4:

| Commit | Estado | Descripción |
|---|---:|---|
| `23d0201` | ⚠️ Parcial | Añadió reglas R8 para quitar logs no críticos |
| `761ed8d` | ✅ Correcto | Quitó log por request en `HomePageHelper` |
| `7906a72` | ✅ Correcto | Preservó `Log.i` en release; solo se eliminan `Log.v` y `Log.d` |

Pendiente en PR #4:

Borrar exactamente estas dos líneas en `WebViewEx.kt`:

```kotlin
Log.d(TAG, "shouldOverrideUrlLoading url: ${request.url}")
Log.d(TAG, "shouldInterceptRequest url: ${request.url}")
```

Motivo:

- `HomePageHelper.shouldInterceptRequest` solo corre en la página de inicio.
- El hot path real de páginas normales es `WebViewEx.shouldInterceptRequest`.
- R8 puede eliminar la llamada a `Log.d`, pero no es una garantía suficiente para evitar toda
  evaluación de argumentos si hay llamadas con posible efecto o coste.
- `Log.i` debe sobrevivir en release para W0, porque reporta estado de listas de adblock.

Parche:

```text
0001-Remove-per-request-debug-logs-from-WebView-client.patch
```

Ese parche está fuera del repo; es regenerable borrando esas dos líneas sobre `7906a72`.

Verificación esperada:

```text
Blob antes:   f3cb7a4216bed350da904a1b65f29a63a439072e
Blob después: c9b937ee4322ecea83abc9d0fdf0f5ef2afa634a
```

Aplicación local recomendada:

```bash
git switch perf/adblock-hotpath-logs
git pull --ff-only
git rev-parse HEAD:app/src/main/java/com/phlox/tvwebbrowser/webengine/webview/WebViewEx.kt
git am 0001-Remove-per-request-debug-logs-from-WebView-client.patch
git rev-parse HEAD:app/src/main/java/com/phlox/tvwebbrowser/webengine/webview/WebViewEx.kt
git push
```

## W0 — Línea base física en onn

W0 es obligatorio antes de atribuir mejoras de memoria o fluidez.

### Base

Medir primero desde:

```text
f2ed4ecb3656759e41b6813b939d4ee2aacd369d
```

Opcional, si existe tag:

```text
w0-baseline
```

### Firma

El debug y release comparten `applicationId`. Si el onn tiene instalado un debug firmado con
el debug keystore compartido, un release firmado con otra clave fallará con:

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

Para W0 se recomienda firmar el release con el mismo debug keystore que usa la CI. La firma no
afecta al rendimiento y permite `adb install -r` conservando datos.

Comprobar huella local:

```powershell
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android | findstr SHA256
```

Huella CI:

```text
Copiar del log del paso "Restore shared debug keystore" de cualquier run reciente.
```

No asumir una huella no verificada.

### Arranque

No usar `pm clear` para W0. Borrar datos fuerza redescarga de listas de adblock y contamina
la medición.

Usar arranque frío:

```bash
adb shell am force-stop com.phlox.tvwebbrowser
```

Abrir manualmente la app o con `monkey` si procede.

### Procesos WebView

No medir solo:

```bash
adb shell dumpsys meminfo com.phlox.tvwebbrowser
```

Desde Android 8, WebView usa renderer/sandbox process. Hay que medir app + renderer.

Confirmar procesos reales:

```powershell
adb shell ps -A -o PID,RSS,NAME | findstr /i "tvwebbrowser sandboxed webview"
```

Capturar memoria por PID:

```bash
adb shell dumpsys meminfo <PID>
```

Guardar una captura por cada PID relevante que devuelva `ps`.

### Logcat

Filtro recomendado:

```powershell
adb logcat -c
adb logcat | findstr /i "tvbro webview chromium render favicon adblock crash exception lowmemorykiller lmkd"
```

`lowmemorykiller`/`lmkd` es obligatorio; en el onn fue el síntoma principal con Gecko.

### Escenarios W0

Mantener orden y espera fija. Esperar aproximadamente 30 s antes de cada medición.

| Escenario | Acción | Archivo sugerido |
|---|---|---|
| W0-idle | App recién abierta, sin navegar | `meminfo-w0-idle.txt` |
| W0-1tab | Abrir sitio ligero | `meminfo-w0-1tab.txt` |
| W0-4tabs | Abrir lista fija de 4 sitios | `meminfo-w0-4tabs.txt` |
| W0-after-close | Cerrar pestañas o volver a reposo | `meminfo-w0-after-close.txt` |
| W0-adblock | Verificar lista cargada y bloqueo básico | `logcat-w0-adblock.txt` |
| W0-fullscreen | Entrar/salir fullscreen 3 veces | `logcat-w0-fullscreen.txt` |

Lista fija inicial:

1. `youtube.com`
2. `google.com`
3. `wikipedia.org`
4. sitio pesado de noticias o streaming reproducible

No cambiar lista entre ramas si se quiere comparar.

## PRs abiertos

### PR #4 — Strip hot-path debug logs in release

Estado: abierto, no listo.

Requisitos antes de merge:

- aplicar parche de `WebViewEx.kt`;
- CI verde;
- revisar que `Log.i` sigue visible en release;
- merge con rebase, no squash, para conservar commits de corrección.

### PRs ya mergeados

| PR | Estado | Notas |
|---|---:|---|
| #1 | ✅ Mergeado | Rebase merge; GitHub reescribió hash pero patch-id preservado |
| #2 | ✅ Mergeado | Squash; mezcla CI + workaround string Gecko |
| #3 | ✅ Mergeado | Rebase merge; dos commits preservados y patch-id idéntico |

## Siguiente orden operativo

### 1. Completar PR #4

Pendiente solo por edición local/hunk:

- borrar dos `Log.d` de `WebViewEx.kt`;
- esperar CI;
- mergear por rebase.

No intentar sustituir `WebViewEx.kt` completo por API.

### 2. Ejecutar W0

Desde `f2ed4ec` o `w0-baseline`.

Objetivo: línea base real antes de comparar ramas actuales.

### 3. Medir rama actual

En la misma sesión física del onn, medir también `fixes/audit` actual para comparar contra W0.

### 4. Adblock runtime

Separar en commits:

| Commit | Cambio |
|---|---|
| A | Aislar fallo por lista: una lista rota no debe descartar todas |
| B | Validación física con logcat y página de prueba |

No declarar solucionado el bloqueo hasta verificar en onn. El motor `ad-block 0.0.4` puede seguir
fallando aunque las listas carguen.

### 5. Limpieza selector motor y strings

Solo desde PC/IDE.

Debe incluir en una rama propia:

- retirar UI del selector si ya no hay alternativas visibles;
- retirar cadena Gecko default añadida como workaround;
- retirar las 9 traducciones stale de `settings_engine_change_gecko_msg`;
- evaluar si `settings_engine_change_webview_msg` queda muerto y, si se elimina, borrar también
  sus traducciones;
- no tocar `gecko:` en `WebTabState`.

### 6. Revisión de updater

`latest_version.json` apunta a APK `geckoIncluded` de `truefedex`, variante que ya no existe
en este fork y que además estaría firmada con otra clave. Esa actualización fallaría al instalarse.

Pendiente:

- definir canal de actualización propio del fork;
- apuntar a APK real generado por este repo;
- revisar firma esperada;
- no publicar update metadata hasta tener release firmada y probada.

### 7. Revisión de historial

Pendiente para integración final.

Problemas conocidos:

| Punto | Estado |
|---|---|
| `43c10f3` | Commit grande/no atómico |
| `ee63b43` | Squash con CI + workaround string Gecko |
| `b27067d` | Commit vacío |
| `fixup!` commits | Revisar si quedan en la historia final |
| reverts pareados | Decidir si se conservan por trazabilidad o se reescribe rama limpia |

No reescribir `fixes/audit` remoto sin decisión explícita.

## Deuda conocida

| Área | Deuda |
|---|---|
| W0 | Falta medición física en onn |
| Release signing | Falta confirmar keystore local vs CI |
| PR #4 | Falta hunk en `WebViewEx.kt` |
| Selector motor | UI/string cleanup pendiente |
| Strings Gecko | Workaround temporal en default + traducciones stale |
| Adblock | Falta aislar fallo por lista y comprobar bloqueo real |
| Logs | `Log.d` hot-path de `WebViewEx.kt` pendiente |
| Historial | Rebase/limpieza final pendiente |
| Updater | `latest_version.json` apunta a `geckoIncluded` de `truefedex`, variante inexistente en el fork y con firma incompatible |
| Config | `isWebEngineNotSet()` no tiene uso conocido |

## No tocar por ahora

| Área | Motivo |
|---|---|
| WebView lifecycle | Ya hay destrucción en detach/trim/renderGone; no duplicar sin evidencia |
| Fullscreen | Ya hay rechazo de segunda custom view; falta prueba física, no cambio de código |
| `WebTabState` `gecko:` | Compatibilidad de estados antiguos |
| Selector motor por API | Requiere edición por hunk en archivos grandes |
| Traducciones por API | Riesgo de codificación y cambios masivos |
| Historial remoto | Decisión final pendiente |
