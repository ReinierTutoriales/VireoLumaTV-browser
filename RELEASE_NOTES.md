# VireoLumaTV 2.0.2

## Cambios

- Motor adblock 0.13.5 con correcciones de coincidencia de dominios y recuperación de cachés antiguas.
- Enlaces intent saneados antes de abrir otras aplicaciones.
- Descargas con cola acotada, rechazo visible y liberación de recursos; reserva de nombres para evitar sobrescrituras en Android antiguo.
- Actualizador: limpieza de APK incompletos, gestión de errores de instalación y reintento del APK verificado mientras permanece abierta la pantalla Acerca de.
- Permisos web limitados a recursos conocidos; las respuestas antiguas no autorizan solicitudes posteriores.
- Puntero: reloj de movimiento estable ante repeticiones del mando y muestras del stick; soltar una dirección conserva la otra.
- Salir del vídeo restaura el modo previo de navegación; los cierres repetidos no cambian el estado.
- Filtrado de service workers cuando WebView lo admite y existe Referer válido. Sin Referer no se filtra; estos bloqueos no suman al contador y la excepción por pestaña se aplica al sitio visible.
- Sugerencias sin ordenación completa, eliminación de fondos redundantes y correcciones del refresco de iconos y acceso a WebView desde hilos de trabajo.
- Herramientas de compilación actualizadas a AGP 9.5.0.

## Actualización

Versión 2.0.2; versionCode 96. Se mantienen applicationId, certificado de release y esquema Room 19.
En generic: Ajustes → Acerca de → Actualizar. Se verifican los checksums firmados y el APK antes de abrir el instalador de Android.
Las instalaciones con el mismo certificado pueden actualizarse sin desinstalar; las firmas distintas, incluidos builds debug, no son compatibles.
Las variantes google y foss no utilizan el actualizador integrado.
APK: armeabi-v7a, arm64-v8a y x86_64.

## Validación

La publicación verifica pruebas, compilación release, certificado vigente y checksums firmados.
La CI comprueba además lint, esquema Room 19 y contenido de los APK.
La validación física de mando, vídeo, bloqueo en sitios reales y memoria sigue pendiente.
Las pruebas de reloj simulado no representan mejoras medidas en una TV.
