# VireoLumaTV 2.0.12

**Android versionName:** `2.0.12`  
**Android versionCode:** `106`

## Cambios
- #137: conserva el modo de puntero o D-pad durante pantalla completa y restaura el estado de navegación al salir.
- #138: permite guardar una pestaña en favoritos y editar o eliminar accesos de Inicio, incluido YouTube.
- #139: solicita persistencia de cookies al pasar a segundo plano sin bloquear la interfaz; mantiene la política de incógnito.

## Validación pendiente
- Confirmar CI de la integración y de esta rama de release, incluida compilación release y lint.
- Comprobar en Android TV real los controles del vídeo, los diálogos de Inicio y la sesión después de apagar y encender.
- La sesión depende de la política del servidor y del proveedor WebView; no se garantiza permanencia.

## Distribución
La publicación requiere APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, y verificación de firma y hashes.
