# VireoLumaTV 2.0.15

**Android versionName:** `2.0.15`  
**Android versionCode:** `109`

## Cambios desde 2.0.14

- Correcciones de foco en sugerencias, cierre de pestañas y entrada del mando; los controles multimedia seleccionan el vídeo en pantalla y permiten controlar audio sin un elemento visual.
- Las consultas multimedia conservan la evaluación nativa de WebView sobre fluidez y eficiencia.
- Ampliación de scriptlets, incluida la familia `json-edit`, protección frente a detección del bloqueador y carga diferida de la biblioteca en los frames que reciben llamadas.
- Los scriptlets `trusted-*` se restringen a las listas propias de uBlock Origin. La protección de iframes conserva los documentos legítimos y cubre la inserción mediante `DocumentFragment`.
- Soporte adicional de `$to=`/`$denyallow=`, resolución de coincidencias múltiples y prioridad de `$important`; las reglas `$method=` usan el método HTTP real.
- Correcciones de edición JSON y bloqueo de copiar/cortar desde la selección de campos de texto, conservando las excepciones de las reglas.
- Recursos de redirección compartidos entre los motores auxiliares, dependencias actualizadas y Room 3.0.3. Se conservan `main.db`, el esquema 20 y la identidad de la aplicación.
- La caché del bloqueador pasa a v5. La caché anterior se recompila desde los textos locales disponibles; no exige descargarlos de nuevo.
- Cobertura y límites del bloqueador documentados en `docs/ADBLOCK_AUDIT.md`.

## Validación y precauciones

- El flujo de publicación ejecuta pruebas, lint, compilación de las tres arquitecturas, verificación de firma de APK, checksums firmados y auditoría de procedencia.
- **Pendiente:** instalar la APK 2.0.15 firmada sobre la 2.0.14 con datos reales y verificar favoritos, historial, pestañas y ajustes.
- Las pruebas con TV, WebView y sitios reales, así como las mediciones de CPU, RAM y latencia en ARM, siguen pendientes. No se garantiza bloqueo total ni una mejora medida de rendimiento en TV.
- No desinstalar la versión anterior para probar la migración: desinstalar elimina los datos.

## Distribución

APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con verificación de firma y SHA-256.
