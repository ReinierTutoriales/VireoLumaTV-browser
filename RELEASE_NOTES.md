# VireoLumaTV 1.0.0

Primera versión de VireoLumaTV publicada en este repositorio. Navegador WebView para Android TV.

- Cursor con clics precisos de ratón y selección de enlaces con iconos o texto dentro.
- Movimiento del cursor sincronizado con los fotogramas de pantalla.
- Pausa correcta durante buffering y saltos dentro del rango disponible del vídeo en directo.
- Solicitudes auxiliares limitadas, iconos en segundo plano y reintentos de descargas con espera.
- Caché limitada de decisiones del bloqueador para reducir evaluaciones repetidas.
- Registros de consola limitados y diagnóstico de errores HTTP/transporte de streaming.
- Una sola pestaña con WebView activo y recuperación del renderizador.

Validación: pruebas de JavaScript y Android, compilaciones debug y release con R8. La reproducción en cada web depende también del servidor, conexión, versión de Android System WebView y capacidad de decodificación del dispositivo. No se garantiza ausencia de buffering en dispositivos de 2 GB.

El APK declara versionName 1.0.0 y versionCode 70. Consulte RELEASE_SOURCE.txt para el commit de código y SIGNING.txt para el certificado utilizado. SHA256SUMS.txt permite comprobar la integridad de los archivos.
