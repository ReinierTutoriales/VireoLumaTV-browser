# VireoLumaTV 3.0.2

Interfaz adaptada a las medidas de TV Material 3 y correcciones de mando, archivos y descargas sobre la versión 3.0.1.

## Interfaz

- **Medidas oficiales y adaptables:** botones de 40 dp con iconos de 20 dp, filas de 48 dp y texto de 16/14 sp según TV Material 3. Márgenes seguros de 48 dp a los lados y 24 dp arriba. Las medidas se eligen según la ventana: en pantallas táctiles los objetivos siguen siendo de 48 dp y en ventanas estrechas la dirección pasa encima de los comandos. El texto respeta la escala del sistema.
- **Pestañas:** pestaña, cierre y «+» tienen la misma altura que los comandos; el ancho se reparte para que quepan cuatro pestañas y «+». Sin borde fino en reposo.
- **Sugerencias:** la lista se desplaza dentro del espacio disponible y todas las sugerencias siguen accesibles con el mando o el puntero, también con texto ampliado.
- **Ventanas estrechas:** la fila de comandos se desplaza y el último comando sigue alcanzable con el mando.

## Correcciones

- **Pulsación larga:** mantener OK sobre una pestaña, el escudo o una descarga abre sus opciones sin ejecutar la primera al soltar el botón.
- **Selector de archivos:** queda vinculado a la página que lo pidió y se cancela al navegar o cerrarla. Solo acepta archivos de proveedores de contenido externos y rechaza rutas directas o archivos privados de la aplicación.
- **Descargas:** la cabecera Referer elimina credenciales y fragmentos, se reduce al origen entre sitios distintos y se omite al pasar de HTTPS a HTTP, también tras redirecciones.
- **Descargas blob:** se limita el número de transferencias pendientes por página y en total para no agotar memoria.

## Validación

CI, pruebas y arranque en emuladores Android API 24, 28, 34 y 37, además de Macrobenchmark, en el commit de esta versión. La validación física en dispositivos sigue pendiente.

## Instalación

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.
