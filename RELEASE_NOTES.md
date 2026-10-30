# VireoLumaTV 2.0.5

## Qué deberías notar

- Después de cerrar el teclado de Inicio, el puntero puede pulsar un marcador aunque el buscador conserve el foco. Se corrige la ruta que enviaba la pulsación al campo y volvía a abrir el teclado.
- El escudo se muestra verde cuando el bloqueador está habilitado para la pestaña y rojo cuando está deshabilitado. Mantenerlo pulsado conserva el detalle del estado de las reglas y los bloqueos: verde indica la selección, no garantiza que todas las reglas estén disponibles.
- La X de cada pestaña es más visible y tiene un área de 48 dp. Con el mando, Derecha desde el título lleva a la X, Izquierda vuelve al título y OK cierra. También admite pulsación directa con el puntero.
- Mantener pulsado el título o la X abre las opciones de esa pestaña. Cerrar una pestaña desde la barra mantiene la barra abierta; cerrar una pestaña en segundo plano conserva la página activa. Una orden repetida sobre una pestaña ya cerrada se ignora.
- El icono del escudo se reutiliza mientras no cambie su estado, evitando cargar de nuevo el mismo recurso en cada actualización.

Se conservan los cambios de 2.0.4: Inicio con accesos propios, sugerencias locales seleccionables, interfaz negra, siete listas base del bloqueador y controles del vídeo principal.
YouTube mantiene su web móvil y su reproductor, con las rutas de filtros existentes.

## Actualización

Versión 2.0.5; versionCode 99. Mismo applicationId, certificado de release y esquema Room 19 que 2.0.4.
En la variante generic: Ajustes → Acerca de → Actualizar.
El actualizador comprueba los checksums firmados y el APK antes de abrir el instalador de Android.
Con el mismo certificado se puede actualizar sin desinstalar; una firma distinta, incluidos builds debug, no es compatible.
Las variantes google y foss no utilizan el actualizador integrado.

APK: armeabi-v7a, arm64-v8a y x86_64.

## Validación

Las correcciones de navegación incluyen pruebas automatizadas; la publicación exige pruebas, lint, compilación release y auditoría del contenido de los APK.
Se verifica el certificado habitual y se firman los checksums con la clave del actualizador.
Las pruebas y capturas automáticas no son una medición de fluidez, consumo o memoria en TV.
Pendiente de validación física: foco y puntero con mando, vídeo, bloqueo en sitios reales y actualización instalada conservando datos.
