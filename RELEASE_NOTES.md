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
