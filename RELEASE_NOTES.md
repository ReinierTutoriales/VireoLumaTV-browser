# VireoLumaTV 3.0.5

Correcciones en Inicio, bloqueo de contenido, red y descargas.

## Inicio

- Añadir y Editar ya no quedan desactivados al volver a abrir la app con el Inicio restaurado: la lectura de los accesos se repite hasta completarse.

## Bloqueo de contenido

- Las páginas que cambian clases continuamente, como YouTube, ya no obligan a revisar el documento entero una y otra vez.
- Las consultas de ocultado que una página puede hacer por segundo están limitadas.

## Red

- La página solo recibe el aviso de «sin conexión» si la red falta más de 3 segundos; las reconexiones breves del Wi-Fi ya no detienen los reproductores.
- En Android 17, si una página de la red local (router, NAS) no carga, la app pide el permiso de red local que exige el sistema y la recarga al concederlo.

## Descargas y privacidad

- Una descarga iniciada por https no continúa por http.
- Las cookies que fija una descarga se guardan con las del navegador y cada petición envía una sola cabecera Cookie.
- El registro del sistema solo anota el sitio de un error de certificado, no la dirección completa.
- El botón Volver del aviso de certificado usa los colores de la marca.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37, además de recorridos con el mando mediante Macrobenchmark. Estas comprobaciones no cuantifican una mejora de rendimiento en un TV físico.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.
