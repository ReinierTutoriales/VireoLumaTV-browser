# VireoLumaTV 2.0.6

## Cambios

- Atrás en TV omite entradas consecutivas con la misma URL para no retroceder a una recarga idéntica; conserva navegación por fragmentos (#) y páginas distintas.
- Una excepción de certificado TLS solo se reutiliza cuando coinciden los bytes DER de ambos certificados; si no están disponibles, se rechaza.
- Al cambiar entre modo normal y privado, se conserva una dirección web permitida sin copiar extras arbitrarios del intent de origen.
- WebView deshabilita explícitamente el acceso a archivos y el acceso cruzado entre URLs locales, manteniendo JavaScript y funciones web.
- Los recursos internos de Inicio validan origen, segmentos y rutas antes de abrir assets, rechazando recorridos de directorios y separadores codificados.

## Compatibilidad y actualización

VersionName 2.0.6, versionCode 100; sin cambios intencionados en applicationId, firma, esquema Room, motor WebView ni formatos de datos.
Las variantes oficiales de APK son armeabi-v7a, arm64-v8a y x86_64. La variante generic conserva la actualización integrada con comprobación de checksums firmados.

## Validación y límites

Las correcciones incluyen pruebas de regresión automatizadas. La publicación exige CI, compilación release, lint y verificación de firma/artefactos.
No equivale a validar reproducción, navegación, descargas y rendimiento con mando en una TV física.

El aislamiento por origen del puente JavaScript, las reglas de backup y otros endurecimientos identificados en auditoría requieren cambios y verificaciones posteriores; **no se presentan como resueltos en 2.0.6**.
