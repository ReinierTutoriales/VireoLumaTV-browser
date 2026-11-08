# VireoLumaTV — mantenimiento actual

El desarrollo parte de `master`, con Android System WebView como único motor.
Las versiones de bibliotecas y herramientas se fijan en `gradle/libs.versions.toml`
y `native/adblock-jni/Cargo.lock`. No se conserva una cola de cambios del fork.

## Estado comprobado

- GitHub identifica este repositorio como independiente.
- La versión publicada y sus APK firmados se consultan en
  [Releases](https://github.com/ReinierTutoriales/VireoLumaTV-browser/releases).
  El código de `master` puede incluir correcciones todavía no publicadas.
- CI comprueba inventario, pruebas, esquemas Room, compilaciones debug/release,
  lint y correspondencia entre los assets del APK y sus fuentes.
- Las licencias y el icono de Inicio se copian durante el build desde sus fuentes
  únicas; no se mantienen duplicados generados dentro de `src/main/assets`.
- Los informes fechados de `docs/audit/` son evidencia histórica, no instrucciones
  ni una cola de parches para aplicar.

## Prioridades

1. Mantener el inventario y los avisos de componentes coherentes con el árbol actual.
2. Corregir únicamente defectos reproducibles, con pruebas sobre el SHA que se integra.
3. Revisar las dependencias por grupos compatibles cuando exista un motivo concreto.
4. Mantener los contratos de navegación, privacidad, descargas, actualización firmada
   y restauración definidos en [la política de regresión](REGRESSION_POLICY.md).

La [captura de memoria de Inicio en CI](audit/WEBVIEW_MEMORY_CI_20261104.md)
separa app y renderer en emuladores; no compara rendimiento antes/después ni
justifica cambios de caché o de pestañas. La comprobación en dispositivo queda
aplazada por decisión del propietario. El informe define la comparación con
Perfetto, Macrobenchmark y DevTools cuando haya acceso a equipos reales.

El workflow `Benchmark`, manual y automático en PRs que afectan app/build/mediciones, ejecuta en emulador el arranque en frío y el tiempo
de fotograma de los recorridos del mando (cápsula, menú, pestañas y Ajustes), sin
compilar y con el perfil incluido. Compara variantes del mismo commit; no mide un TV.
El workflow `Baseline profile`, manual y automático para cambios de app/recorridos
en PRs del propio repositorio, genera el perfil de arranque solo desde el arranque y
el perfil general desde esos mismos recorridos. Los recorridos exigen foco,
activación con OK, destino y retorno; el cierre comprueba la identidad superviviente.
El generador conserva ART entre iteraciones y usa el launcher sin añadir pestañas
por intents VIEW repetidos. Tras subir perfiles solicita CI y Benchmark sobre el nuevo SHA.
Los eventos del bot no vuelven a generar perfiles.

La revisión del 7 de noviembre contrasta la declaración de puntero con la
[guía oficial Google TV](https://developer.android.com/blog/posts/increasing-app-discovery-and-engagement-on-google-tv),
las pruebas de entrada con las [API Compose](https://developer.android.com/develop/ui/compose/testing/apis)
y los recorridos con la [generación de perfiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).
El arranque asíncrono WebView ya existe; no se añaden precargas ni prerender sin
mediciones y revisión de privacidad/bloqueo. La [guía de arranque](https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup)
no demuestra por sí sola una mejora en esta app.

## Dependencias contrastadas el 8 de noviembre de 2026

Las versiones directas consultadas se contrastan con las publicaciones estables de
los proveedores. Core pasa a 1.19.1 por la corrección de compatibilidad de lint con
AGP 9.5; se declara `core` directamente, porque desde 1.19 las extensiones Kotlin
están integradas y `core-ktx` es un artefacto vacío de compatibilidad
([notas oficiales](https://developer.android.com/jetpack/androidx/releases/core#1.19.1)).

| Grupo | Versiones fijadas y contrastadas | Fuente |
| --- | --- | --- |
| AndroidX | Activity (activity-compose) 1.13.0; Core 1.19.1; WebKit 1.18.0; Lifecycle 2.11.0; Room3 3.0.3; SQLite 2.7.1. AppCompat, Fragment y RecyclerView retirados (8 nov.) | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Compose para TV | Compose BOM 2026.09.00 (ui, foundation); TV Material 1.1.0 | [Compose](https://developer.android.com/develop/ui/compose/bom), [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv) |
| Perfiles y métricas | ProfileInstaller 1.4.1; Metrics 1.0.0; Benchmark 1.5.0 | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Kotlin | Kotlin 2.4.21; Coroutines 1.11.0; KSP 2.3.13 | [Kotlin](https://kotlinlang.org/docs/releases.html), [Coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases), [KSP](https://github.com/google/ksp/releases) |
| Build | AGP 9.5.1; Gradle 9.9.0; NDK 30.0.16248370 | [AGP](https://developer.android.com/build/releases/agp-9-5-0-release-notes?hl=en), [Gradle](https://docs.gradle.org/9.9.0/release-notes.html), [NDK](https://developer.android.com/ndk/downloads) |
| Pruebas | JUnit 4.13.2; Robolectric 4.17; AndroidX Test Ext JUnit 1.3.0 | [JUnit](https://github.com/junit-team/junit4/releases), [Robolectric](https://github.com/robolectric/robolectric/releases), [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test) |
| Rust directo | adblock 0.13.5; jni 0.22.4; serde_json 1.0.154 | [adblock](https://github.com/brave/adblock-rust/blob/master/CHANGELOG.md), [jni](https://github.com/jni-rs/jni-rs/releases), [serde_json](https://github.com/serde-rs/json/releases) |
| CI | checkout 7.0.1; setup-java 6.2.0; gradle/actions 6.4.1; upload-artifact 7.0.2; download-artifact 8.0.2; rust-cache 2.9.3; android-emulator-runner 2.38.0; action-gh-release 3.0.4 | Publicaciones oficiales de cada acción en GitHub |

Este contraste cubre versiones directas, no certifica todas las transitivas de
Gradle/Cargo ni sustituye compilación y pruebas sobre el commit integrado.

## Integración

Trabajar en una rama descriptiva desde el `master` remoto actual. Revisar los cambios,
ejecutar las comprobaciones pertinentes y exigir CI verde sobre el SHA exacto antes
de integrar. Eliminar la rama al terminar. No reescribir el historial como parte de
una limpieza y no publicar versiones sin petición expresa.

[AGENTS.md](../AGENTS.md) y la política de regresión contienen las reglas vigentes.
El [plan de implementación](IMPLEMENTATION_PLAN.md) describe contratos y escenarios;
sus referencias fechadas deben contrastarse con el código actual antes de actuar.
