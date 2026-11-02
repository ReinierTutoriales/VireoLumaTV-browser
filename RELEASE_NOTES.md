# VireoLumaTV 2.0.15

**Android versionName:** `2.0.15`  
**Android versionCode:** `109`

## Cambios desde 2.0.14

- Publicación desde el historial independiente actual de `master`, sin alterar las funciones de navegación, bloqueo, idiomas ni vídeo.
- Actualización de dependencias y mantenimiento del motor de almacenamiento existentes en `master`, incluido Room 3.0.3.
- Conserva el nombre de la base de datos `main.db` y el esquema 20. La compatibilidad de actualización desde 2.0.14 debe verificarse en un dispositivo real.

## Validación y precauciones

- Exigir CI verde, compilación de las tres arquitecturas, firma de APK, comprobación de checksums y procedencia del código antes de publicar.
- **Pendiente:** instalar la APK 2.0.15 firmada sobre la 2.0.14 con datos reales y verificar favoritos, historial, pestañas y ajustes.
- No desinstalar la versión anterior para probar la migración: desinstalar elimina los datos.
- La publicación de esta versión no elimina por sí sola las referencias históricas de GitHub ni modifica obligaciones de licencias de terceros.

## Distribución

APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con verificación de firma y SHA-256.
