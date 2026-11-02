# VireoLumaTV 2.0.14

**Android versionName:** `2.0.14`  
**Android versionCode:** `108`

## Novedades desde 2.0.13

- **Idiomas:** Inicio y textos que aparecen fuera de las pantallas de ajustes respetan el idioma seleccionado en la aplicación (#157).
- **Rendimiento:** instrumentación de fotogramas lentos y preparación de perfiles de arranque para medir y mejorar la fluidez (#158). La generación del perfil de referencia requiere una ejecución validada por separado.
- **Vídeo:** selección de calidad máxima de acuerdo con las capacidades de decodificación de hardware que comunica el dispositivo (#159). La resolución efectiva también depende del sitio, el códec, WebView y el reproductor.

## Validación

Las pruebas automatizadas de los cambios individuales finalizaron correctamente. La integración y los APK de esta versión requieren CI, firma y comprobación de hashes. No se afirma que se hayan medido mejoras de rendimiento en un dispositivo físico sin una medición reproducible.

## Limitaciones conocidas

- Pendiente de validar en hardware real el comportamiento de reproducción, el rendimiento y la actualización conservando datos de la versión anterior.
- El perfil de referencia debe generarse y verificarse antes de atribuirle mejoras de arranque.

## Distribución

APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con certificado y SHA-256 verificados.
