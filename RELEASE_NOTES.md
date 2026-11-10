# VireoLumaTV 3.0.1

Actualización de seguridad y privacidad sobre la versión 3.0.0.

## Correcciones

- **Seguridad de compilación:** las pruebas habituales ya no necesitan la clave privada de firma de sumas de comprobación. Las acciones de GitHub quedan fijadas a revisiones verificadas y Dependabot vigila sus actualizaciones.
- **Publicación:** se elimina el lanzamiento automático por creación de ramas. La publicación se solicita manualmente y usa el entorno `release`, cuya aprobación y secretos deben configurarse en GitHub.
- **Privacidad:** limpieza de datos privados residuales de WebView y recuperación de perfiles normales apartados por versiones antiguas de Android.
- **Protección contra detección:** el puente de filtros deja de estar expuesto como propiedad global de la página tras la inicialización del script.
- **Red:** las listas de bloqueo descargadas por la aplicación requieren HTTPS; el acceso HTTP a loopback queda limitado a pruebas.
- **Certificados:** se aclara que una comparación sin bytes del certificado se rechaza.
- **Pestañas:** el título y la dirección de una pestaña se actualizan mientras la página carga; al cerrar una pestaña desde su X, el foco queda en la pestaña seleccionada.
- **Atrás en Inicio:** sale de la aplicación (tras cerrar la edición de accesos, si está abierta), aunque la cápsula esté abierta o la pestaña tenga historial.
- **Actualizaciones:** la variante generic comprueba sola, como máximo una vez al día y nunca en modo privado, si hay una versión nueva firmada y ofrece instalarla; si no hay red al arrancar, reintenta al recuperarla.
- **Barra de dirección:** las sugerencias vuelven a ocupar el lugar de la fila de pestañas, Arriba desde la primera sugerencia vuelve al texto que se edita y Atrás durante la edición deja el foco en la dirección.

## Validación

La implementación de seguridad previa a esta preparación de versión pasó CI, pruebas y arranque en emuladores Android API 24, 28, 34 y 37, además de Macrobenchmark, en el commit `366268c0`. La versión 3.0.1 requiere repetir CI sobre su commit definitivo antes de publicar. La validación física de rendimiento y mando en TV sigue pendiente.

## Instalación

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos normales.

**Seguridad de publicación:** antes de distribuir, configura revisores obligatorios y secretos en el entorno protegido `release` de GitHub.
