# VireoLumaTV 3.0.3

Correcciones de actualizaciones y Ajustes, y reducción de trabajo en la restauración de pestañas y el bloqueo de contenido.

## Actualizaciones

- El aviso de una versión nueva se conserva durante el vídeo a pantalla completa o mientras la app está en segundo plano y se presenta al volver al navegador.
- La comprobación diaria también funciona si la app permanece abierta. Los errores se reintentan después de una hora; el temporizador se detiene en segundo plano.
- La descarga sigue requiriendo confirmación y verificando la firma de los hashes y la suma del APK antes de abrir el instalador del sistema.

## Ajustes y Acerca de

- Cerrar Ajustes cancela las operaciones pendientes e impide que aparezcan después sus diálogos. La destrucción de la actividad también cancela el trabajo.
- Cambiar idioma guarda primero los textos pendientes, incluidas las direcciones personalizadas.
- La actualización manual muestra su estado sin perder el foco del mando.
- Las pestañas se desplazan cuando no caben y los márgenes siguen las medidas adaptables de la interfaz.
- Ajustar el puntero ya no vuelve a formatear el estado del bloqueador.

## Pestañas y bloqueo de contenido

- La lectura y decodificación del historial guardado se realizan fuera del hilo principal; la restauración de WebView conserva el hilo que exige Android y descarta resultados de navegaciones anteriores.
- Los estados de pestaña se liberan tras confirmar su persistencia, conservando el modo de una WebView activa.
- Los filtros procedurales reparten el trabajo en pasadas limitadas y la búsqueda de clases e identificadores evita recorrer todo el documento de una vez.
- Las listas de filtros admiten gzip con el límite de descompresión existente. Las descargas reanudables conservan su tratamiento de Range.
- Gradle actualizado a 9.9.1.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37, además de recorridos con el mando mediante Macrobenchmark. Estas comprobaciones no cuantifican una mejora de rendimiento en un TV físico.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.
