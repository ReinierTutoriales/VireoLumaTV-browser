# VireoLumaTV 2.0.13

**Android versionName:** `2.0.13`  
**Android versionCode:** `107`

## Cambios desde 2.0.12

- Navegación con mando, puntero y pestañas: simplificación de callbacks y contratos de interacción.
- Idiomas: selección nativa de idioma por aplicación y traducciones actualizadas.
- Instalación: gestión de APK mediante sesiones de PackageInstaller.
- Privacidad: confirmación del borrado de cookies y preservación de incógnito.
- Descargas: integración de historial con Room v20 y correcciones de servicio y progreso.
- Ajustes: simplificación de callbacks y operaciones de descarga.
- Limpieza y documentación de migración consolidadas.

## Validación

La CI comprueba compilación, pruebas automatizadas y Android Lint. La reproducción, la navegación con mando y la recuperación de sesión deben comprobarse también en hardware real. Los APKs publicados requieren firma de release y verificación de SHA-256.

## Limitaciones conocidas

- Pendiente de comprobación en Android TV físico: controles de vídeo, recuperación de sesión y persistencia tras reinicio.
- El comportamiento de páginas y vídeos también depende del proveedor WebView y del servidor.

## Distribución

APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con verificación de certificado y hashes.
