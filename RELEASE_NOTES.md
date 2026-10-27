# VireoLumaTV 1.2.2

Actualización de mantenimiento sobre 1.2.1. Usa el mismo certificado de release:
se instala encima de 1.2.1 conservando los datos.

Desde 1.1.1 o 1.2.0 firmada con la clave anterior, exporta tus favoritos antes
de desinstalar la versión anterior e instalar esta; la desinstalación borra los datos.

## Cambios

- Toolchain nativo actualizado a NDK r30 LTS (30.0.16248370).
- La tarea nativa registra la versión del NDK para recompilar al cambiar de toolchain.
- Dependencias Rust: smallvec 1.16.4 y syn 3.0.9; mínimo declarado de serde_json alineado con 1.0.154, ya usado por el lockfile.
- Acciones de CI/release actualizadas y lint incluido en el control de release.
- Inventario de licencias sincronizado con 17 versiones de paquetes comprobadas.

Las dependencias Android, AGP, Gradle, Kotlin y KSP ya usan las versiones
estables de sus líneas actuales. Room se mantiene en 2.8.5; Room 3 exige una
migración al paquete androidx.room3.

## APK

- armeabi-v7a: Android de 32 bits.
- arm64-v8a: Android de 64 bits.
- x86_64: emuladores o equipos Intel/AMD.

## Validación

Controles de entrega: siete suites JavaScript, pruebas JVM con el motor nativo,
APK debug y release con R8 para tres ABI, esquema Room y lint de release.
[Resultados del PR #78](https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/78).
Pendiente de validación física en Android TV.
