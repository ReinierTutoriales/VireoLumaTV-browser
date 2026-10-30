# VireoLumaTV 2.0.4

## Qué deberías notar

- Inicio centra el buscador y los accesos con iconos. YouTube abre su versión móvil; el botón «+» permite añadir accesos propios.
- Las sugerencias locales de la dirección se pueden seleccionar con el puntero del mando o con foco D-pad. Abajo entra en la lista y Arriba desde su borde superior vuelve al campo.
- La interfaz utiliza un único estilo con fondos negros, texto claro suave, foco visible y tipografía del sistema, también en Inicio y los diálogos.
- El bloqueador incluye sus siete listas base: puede preparar reglas antes de esperar a internet en la primera instalación o si faltan copias guardadas válidas. Conserva las reglas guardadas y la caché compilada cuando están disponibles; una suscripción personalizada mantiene sus propias fuentes.
- Los avisos repetidos sobre un mismo elemento de la página se agrupan para evitar recorrer una y otra vez su subárbol dentro del mismo lote. Las mutaciones posteriores siguen procesándose.
- Los controles multimedia priorizan el vídeo principal, visible o a pantalla completa. Se corrige el desplazamiento en vídeos de duración conocida cuando el reproductor aún no publica rangos de búsqueda.
- VP8, VP9 y AV1 se comprueban por separado y la consulta de decodificadores se comparte una vez por proceso.

YouTube conserva el reproductor de su web móvil y las rutas de filtros existentes.
La compatibilidad, la calidad de vídeo y los controles disponibles dependen también del sitio y del WebView del dispositivo.
La base de filtros añade aproximadamente 2 MB al APK; no sustituye las actualizaciones ni garantiza bloquear todos los anuncios o avisos antiadblock.

## Actualización

Versión 2.0.4; versionCode 98. Mismo applicationId, certificado de release y esquema Room 19 que 2.0.3.
En la variante generic: Ajustes → Acerca de → Actualizar.
El actualizador comprueba los checksums firmados y el APK antes de abrir el instalador de Android.
Con el mismo certificado se puede actualizar sin desinstalar; una firma distinta, incluidos builds debug, no es compatible.
Las variantes google y foss no utilizan el actualizador integrado.

APK: armeabi-v7a, arm64-v8a y x86_64.

## Validación

La publicación exige pruebas automatizadas, lint, compilación release y auditoría del contenido de los APK.
Verifica el certificado habitual y firma los checksums con la clave del actualizador.
Las pruebas y capturas automáticas no son una medición de fluidez, consumo o memoria en TV.
Pendiente de validación física: foco y puntero con mando, vídeo, bloqueo en sitios reales y actualización instalada conservando datos.
