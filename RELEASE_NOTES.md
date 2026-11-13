# VireoLumaTV 3.0.7

Correcciones del bloqueador, de la apertura de enlaces y del mando.

## Bloqueador

- Se aplican las reglas de ventanas emergentes sin patrón propio. En las listas incluidas cubren 167 dominios, muchos de streaming y reproductores embebidos.
- Los scriptlets anti-detección aceptan más opciones de uBlock Origin: valores `{}`, `[]` y `""` en `set-constant`; `length:` y `war:` en `no-fetch-if` y `no-xhr-if`; `includes`/`excludes` en `remove-node-text` y `replace-node-text`; y `elements` en `addEventListener-defuser`.
- `no-window-open-if` compara todos los argumentos de `window.open`, y la ventana falsa que devuelve ya no rompe los scripts que la manipulan.
- Las reglas de redirección hacia `nobab`, `nofab` y `popads` entregan su sustituto en lugar de bloquear sin reemplazo. Se añaden `doubleclick_instream_ad_status.js` y `noop-vmap1.0.xml`.
- Al actualizar, el motor de filtros se vuelve a compilar una vez para aplicar estos cambios.

## Navegación y mando

- Un enlace abierto desde otra app ya no se repite como pestaña nueva al recrearse la app, tras cerrarla el sistema o al volver desde Recientes.
- «Abrir en otra aplicación» funciona aunque VireoLumaTV sea el navegador predeterminado: respeta la app predeterminada del enlace o muestra el selector sin VireoLumaTV.
- Los mandos que envían reproducir y pausa como teclas separadas (HDMI-CEC) controlan el vídeo.
- Las miniaturas de pestañas ya no pueden salir corruptas por capturarse mientras se guardaban.

## Seguridad

- Los service workers ya no pueden leer archivos ni contenido local del dispositivo, igual que las páginas.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37, además de recorridos con el mando mediante Macrobenchmark. Estas comprobaciones no cuantifican una mejora de rendimiento en un TV físico.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.
