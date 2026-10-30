# VireoLumaTV 2.0.7

## Cambios
- OSD multimedia nativo tanto para vídeo en fullscreen como en modo normal, con retroceso/avance, pausa y botón contextual Maximizar o Minimizar.
- Intento de Siguiente para YouTube mediante el control expuesto por su interfaz web; puede no estar disponible en todos los estados del reproductor.
- Navegación Atrás más eficiente: consulta incremental del historial sin crear listas temporales.
- Descargas de actualización restringidas a HTTPS también después de redirecciones, manteniendo firma y hashes.
- Política de códecs cacheada por proceso para evitar recalcular listas en cada navegación.
- Skills de seguridad y verificación de versiones mejoradas.

## Compatibilidad
Versión 2.0.7; versionCode 101. Se conservan applicationId, firma, datos, motor WebView y política de resolución de 2.0.6. No se ha forzado una reducción de calidad; YouTube conserva sus controles originales y el bloqueo de anuncios.

## Validación
Los cambios individuales superaron CI. La versión conjunta exige pruebas, lint y compilación release antes de su publicación. La CI no certifica fluidez, calidad 4K/HDR, ni funcionamiento del OSD en hardware físico. El botón Siguiente/Maximizar depende del DOM del reproductor web y requiere prueba con YouTube actual.
