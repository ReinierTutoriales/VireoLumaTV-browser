# VireoLumaTV 2.0.11

**Android versionName:** `2.0.11`  
**Android versionCode:** `105`

## Seguridad y privacidad
- #131: evita que aplicaciones externas sustituyan la pestaña activa; limita la apertura de aplicaciones externas a actividades BROWSABLE; sanea los caracteres Unicode invisibles de nombres de descarga; desactiva la depuración WebView al cambiar el ajuste; avisa de proveedores WebView potencialmente desactualizados.
- #132: diálogos HTTP Auth con host y realm identificados, advertencias de conexiones HTTP no cifradas, rechazo de solicitudes de credenciales de recursos de otros hosts bajo páginas ya mostradas y un único diálogo activo. Las cargas que terminan como descarga o error de red dejan de considerarse navegaciones pendientes.
- #135: al terminar una visita con una excepción de certificado SSL aceptada, o al destruir la vista, se borran las preferencias SSL de WebView. **Alcance:** WebView borra las excepciones del perfil, no únicamente las de la pestaña.

## Red y descargas
- #133: observa cambios de capacidades de la red predeterminada y recupera una sola vez las páginas principales que fallaron por errores transitorios de conexión, sin recargar tras errores SSL.
- #134: reintentos limitados y reanudación HTTP con Range e If-Range cuando existe un validador de contenido apropiado; comprueba las respuestas parciales, reinicia transferencias incompatibles y detecta cuerpos incompletos.

## Interfaz
- #135: icono visible de cierre de pestaña de 18 dp con objetivo de interacción de 48 dp; conserva navegación con mando y pulsación.

## Verificación y limitaciones
- Los PR #131–#135 superaron sus CI individuales. La CI de esta versión debe verificar la integración completa antes de publicar.
- No se ha validado físicamente el comportamiento de reconexión en todos los firmwares Android TV, la reanudación de descargas MediaStore en Android 11+ ni una sesión DevTools abierta al desactivar la depuración.
- El proveedor Android System WebView, sus códecs y la compatibilidad DRM siguen dependiendo del dispositivo y sus actualizaciones.

## Distribución
Se generan APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`. Verifique `SHA256SUMS.txt`, su firma y `SIGNING.txt` antes de instalar.
