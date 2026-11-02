# VireoLumaTV — mantenimiento actual

El desarrollo parte de `master`, con Android System WebView como único motor.
Las versiones de bibliotecas y herramientas se fijan en `gradle/libs.versions.toml`
y `native/adblock-jni/Cargo.lock`. No se conserva una cola de cambios del fork.

## Estado comprobado

- GitHub identifica este repositorio como independiente.
- La versión publicada que se conserva es 2.0.14. El código de `master` puede
  incluir correcciones posteriores que todavía no se han publicado.
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

No hay mediciones nuevas que permitan afirmar mejoras de memoria o rendimiento.
La comprobación en dispositivo queda aplazada por decisión del propietario.

## Dependencias contrastadas el 2 de noviembre de 2026

Las versiones directas consultadas coinciden con las publicaciones estables de
los proveedores; no se cambian números ni se introducen previews.

| Grupo | Versiones fijadas y contrastadas | Fuente |
| --- | --- | --- |
| AndroidX | AppCompat 1.8.0; WebKit 1.18.0; RecyclerView 1.4.0; Lifecycle 2.11.0; Room3 3.0.3; SQLite 2.7.1 | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Perfiles y métricas | ProfileInstaller 1.4.1; Metrics 1.0.0; Benchmark 1.5.0 | [AndroidX](https://developer.android.com/jetpack/androidx/versions) |
| Kotlin | Kotlin 2.4.21; Coroutines 1.11.0; KSP 2.3.13 | [Kotlin](https://kotlinlang.org/docs/releases.html), [Coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases), [KSP](https://github.com/google/ksp/releases) |
| Build | AGP 9.5.0; Gradle 9.8.2; NDK 30.0.16248370 | [AGP](https://developer.android.com/build/releases/agp-9-5-0-release-notes?hl=en), [Gradle](https://docs.gradle.org/9.8.2/release-notes.html), [NDK](https://developer.android.com/ndk/downloads) |
| Pruebas | JUnit 4.13.2; Robolectric 4.17; AndroidX Test Ext JUnit 1.3.0 | [JUnit](https://github.com/junit-team/junit4/releases), [Robolectric](https://github.com/robolectric/robolectric/releases), [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test) |
| Rust directo | adblock 0.13.5; jni 0.22.4; serde_json 1.0.154 | [adblock](https://github.com/brave/adblock-rust/blob/master/CHANGELOG.md), [jni](https://github.com/jni-rs/jni-rs/releases), [serde_json](https://github.com/serde-rs/json/releases) |
| CI | checkout 7.0.1; setup-java 6.1.0; gradle/actions 6.4.1; upload-artifact 7.0.2; rust-cache 2.9.3 | Publicaciones oficiales de cada acción en GitHub |

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
