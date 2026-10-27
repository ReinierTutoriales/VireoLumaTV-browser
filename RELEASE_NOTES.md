# VireoLumaTV 1.2.1

**Esta versión usa una clave de firma nueva. No se instala encima de la 1.1.1 ni de la 1.2.0 firmada con la clave anterior: hay que desinstalar la versión que tengas e instalar la 1.2.1. Los datos (favoritos, historial, ajustes) se pierden salvo que se exporten antes.**

Incluye los cambios de la 1.2.0 que aparecen a continuación.

---

# VireoLumaTV 1.2.0

**Esta versión usa una clave de firma nueva. No se instala encima de la 1.1.1: hay que desinstalar la 1.1.1 e instalar la 1.2.0. Los datos (favoritos, historial, ajustes) se pierden salvo que se exporten antes.**

Actualización sobre la 1.1.1.

## Qué APK instalar

Cada release trae un APK por arquitectura. Elige el que corresponde a tu dispositivo:

- **armeabi-v7a**: televisores de 32 bits, incluido el onn 4K Streaming Device. Es el APK más pequeño.
- **arm64-v8a**: televisores y móviles de 64 bits.
- **x86_64**: emuladores y equipos Intel/AMD. No es para televisores.

## Cambios

- Páginas que piden abrir un enlace `intent:` ahora preguntan antes, igual que con otros esquemas externos. La acción del menú contextual, que elige el usuario, sigue abriendo directamente.
- Favoritos: una URL vacía no se guarda, y una URL sin esquema se guarda con `https://`.
- Dependencias: targetSdk 36 → 37, Robolectric 4.17, y actualización de los crates `psl` e `icu_*` del motor de bloqueo. Las decisiones del bloqueador se mantuvieron iguales en un corpus de 48.120 líneas.
- Lint: de 82 a 32 avisos, 0 errores. Se corrigieron cadenas con puntos suspensivos, llamadas KTX equivalentes (`toUri`, `isGone`, `edit {}`) y la configuración regional por defecto en fechas.
- PRIVACY.md: se corrigen las listas de bloqueo por defecto, se elimina la mención a un servicio de favicons que el código no usa y se describe el calendario real de actualización de listas.

## Validación

Pruebas JVM (127, sin fallos), lint sin errores, builds debug y release con R8. Compilada con targetSdk 37 (Android 17). Pendiente de prueba en dispositivo, sobre todo el comportamiento en Android 17 y el nuevo aviso para enlaces `intent:`.
