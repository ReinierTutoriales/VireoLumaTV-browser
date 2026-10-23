# VireoLumaTV 1.1.1

Actualización sobre la 1.1.0. Se instala encima de la 1.1.0 con la misma firma; los datos se conservan.

## Qué APK instalar

Cada release trae un APK por arquitectura. Elige el que corresponde a tu dispositivo:

- **armeabi-v7a**: televisores de 32 bits, incluido el onn 4K Streaming Device. Es el APK más pequeño.
- **arm64-v8a**: televisores y móviles de 64 bits.
- **x86_64**: emuladores y equipos Intel/AMD. No es para televisores.

## Cambios

- Un enlace `intent:` mal formado en una página ya no cierra la app.
- La instalación de APK descargados comprueba el permiso de instalar aplicaciones desconocidas en Android 8 y versiones posteriores.
- Búsqueda del historial: encuentra coincidencias parciales. Antes solo encontraba entradas idénticas a lo escrito.
- Favoritos: no se guarda un favorito con URL vacía, y la barra de progreso se oculta aunque falle el guardado.
- Descargas y historial: un error al cargar una página ya no impide cargar las siguientes.
- Bloqueador: al actualizar, las listas se recompilan una vez. Se borran las cachés de versiones anteriores.
- Accesibilidad: botones de borrar y editar con etiqueta; iconos decorativos ocultos a los lectores de pantalla.
- Mensajes del bloqueador traducidos a alemán, ruso, vietnamita, ucraniano, persa, italiano, polaco, hebreo y chino. Estas traducciones no han sido revisadas por hablantes nativos.
- Dependencias actualizadas: `androidx.webkit` 1.18.0, Kotlin 2.4.21, serde_json (JNI).

## Validación

Pruebas JVM (125, sin fallos), builds debug y release con R8, y pruebas de consulta y búsqueda con base de datos en memoria. Pendiente de prueba en dispositivo.

---

# VireoLumaTV 1.1.0

Navegador WebView para Android TV, optimizado para equipos de 2 GB de RAM. Se instala sobre la 1.0.0 sin perder datos (mismo certificado).

## Bloqueador de anuncios nuevo

- Motor adblock-rust 0.13.3 de Brave (MPL-2.0) integrado como librería nativa: carga las listas en décimas de segundo (antes ~10 s) y decide cada petición en microsegundos, sin bloquear la navegación.
- Listas de uBlock Origin (filters, Quick fixes, Unbreak, Badware) además de EasyList, EasyPrivacy y EasyList Spanish, con actualización diaria condicional (solo descarga lo que cambió).
- Sustitutos inofensivos en lugar de errores (`$redirect`): los reproductores no detectan el bloqueo y siguen funcionando.
- Scriptlets anti-detección de uBlock (set-constant, abort-on-property-read, no-setTimeout-if, json-prune, no-window-open-if y más) ejecutados sin eval, compatibles con la CSP de las webs.
- Ocultación de elementos y filtros procedurales (`:has-text`, `:upward`, `:remove()`, `:style()`...) para quitar avisos anti-adblock y capas que tapan el vídeo.
- Ventanas emergentes: reglas `$popup` propias, protección contra redirecciones de la página actual a publicidad y ventana falsa inerte para que los reproductores no se nieguen a funcionar.
- Puntuación en adblock-tester.com: 98/100.

## Vídeo y pestaña única

- Una web que está reproduciendo vídeo (con sonido) ya no puede sustituir el reproductor por una ventana nueva.
- La página que abre una ventana permitida queda en pausa y se reanuda al cerrarla, sin recargar.
- Modo de vídeo óptimo fijo: elige códecs con decodificación por hardware del televisor; límite opcional de calidad máxima.

## Interfaz y rendimiento

- Interfaz en español, foco y navegación con el mando revisados en menús y pestañas, búsqueda que se activa solo con OK.
- Cursor independiente de la tasa de fotogramas y cargas de página más ligeras.
- Eliminación de código sin uso y librerías actualizadas.

## Notas

- El APK crece a unos 10 MB por incluir la librería nativa del bloqueador para arm64, armv7 y x86_64.
- La reproducción depende también del servidor, la conexión, la versión de Android System WebView y el decodificador del dispositivo.

Validación: pruebas JavaScript y Android (123), compilaciones debug y release con R8, pruebas en Chromium con el motor real sobre sitios reales.

El APK declara versionName 1.1.0 y versionCode 88. Consulte RELEASE_SOURCE.txt para el commit de código y SIGNING.txt para el certificado utilizado. SHA256SUMS.txt permite comprobar la integridad de los archivos. Licencias de terceros: NOTICE.md y los archivos de licencias adjuntos (incluido rust-crates.txt).
