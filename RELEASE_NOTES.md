# VireoLumaTV 2.0.3

## Qué deberías notar

- La barra superior permite moverse a izquierda y derecha entre controles habilitados, sin quedar atrapado en Atrás o Adelante cuando no hay historial.
- El campo de dirección y búsqueda se alcanza desde la barra; OK activa la edición.
- Cada pestaña tiene un botón visible para cerrarla.
- Se retiran los marcadores del menú y de las sugerencias; sus datos guardados se conservan.
- Las ventanas emergentes requieren una activación real consumible, salvo permiso explícito del sitio. Se limita la apertura repetida con el mismo gesto.
- Después de actualizar el motor, las listas guardadas se recuperan antes de esperar a internet.
- Una actualización a la que le faltan listas conserva el motor activo.
- Los filtros de página reintentan cuando el motor sigue cargando, con un temporizador por documento y espera limitada (~36 segundos).

Estos cambios deberían reducir fallos de navegación y los huecos de bloqueo al arrancar o actualizar.
No garantizan eliminar todos los anuncios ni deshacer una detección antiadblock que la página ya ejecutó.

## Actualización

Versión 2.0.3; versionCode 97. Mismo applicationId, certificado de release y esquema Room 19.
En la variante generic: Ajustes → Acerca de → Actualizar.
El actualizador comprueba los checksums firmados y el APK antes de abrir el instalador de Android.
Con el mismo certificado se puede actualizar sin desinstalar; una firma distinta, incluidos builds debug, no es compatible.
Las variantes google y foss no utilizan el actualizador integrado.

APK: armeabi-v7a, arm64-v8a y x86_64.

## Validación

Pruebas automatizadas, lint, compilación release y auditoría de contenido de APK.
La publicación verifica el certificado habitual y firma los checksums con la clave del actualizador.
Pendiente de validación física: navegación con mando, vídeo y bloqueo en sitios reales.
