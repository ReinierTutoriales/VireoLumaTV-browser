# VireoLumaTV 3.0.10

Corrige el envío desde YouTube Music al TV.

## Enviar desde el teléfono

- En la 3.0.9, el TV aparecía en la lista de YouTube Music, pero al elegirlo no llegaba nada: el servidor de YouTube rechazaba la conexión porque el TV se registraba siempre como YouTube y no como YouTube Music. Ahora el TV se registra con la app que envía, así que lo enviado desde YouTube Music se abre en `music.youtube.com` con tu sesión iniciada.
- Se puede pasar de YouTube Music a YouTube, y al revés, sin salir del navegador.
- El emparejamiento termina antes de que el TV confirme al teléfono, y el cambio entre las dos apps ya no espera varios segundos a reconectar.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37. El rechazo y la corrección se reprodujeron contra el servidor de YouTube simulando el TV y el teléfono. Falta la validación física con un iPhone real.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.

# VireoLumaTV 3.0.9

El TV puede recibir lo que envíes desde YouTube, YouTube Music o una app DLNA del teléfono. Las dos funciones vienen desactivadas.

## Enviar desde el teléfono

- **YouTube y YouTube Music (Beta):** al activarlo en Ajustes > General, el TV aparece en la lista de dispositivos para enviar de YouTube y YouTube Music cuando el teléfono está en la misma red Wi-Fi. El vídeo o la canción se abre en una pestaña de `youtube.com` o `music.youtube.com`, con la sesión que tengas iniciada en el navegador. Desde el teléfono se puede pausar, reanudar, buscar una posición y pasar a la pista siguiente o anterior.
- **Receptor DLNA / UPnP:** al activarlo, las apps DLNA del teléfono o del ordenador pueden enviar vídeo, audio o fotos al TV. Se reproducen a pantalla completa y se controlan con el mando o desde la app que los envió.
- Solo funcionan con la app abierta en primer plano. En Android 17, el sistema pide el permiso de red local al activarlas.

## Compilaciones de prueba

- Los APK de depuración que genera CI se instalan como `com.reiniertutoriales.vireolumatv.test`, junto a la app oficial y sin sustituirla, así que instalarlos ya no borra las sesiones de la versión normal.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37. La recepción desde YouTube, YouTube Music y DLNA está pendiente de validación física con un teléfono real.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.

# VireoLumaTV 3.0.8

Descargas y actualización de listas más resistentes a fallos del servidor.

## Descargas

- Si el servidor responde 429 (demasiadas peticiones) o 502, la descarga espera lo que indique `Retry-After` y lo vuelve a intentar, en lugar de fallar al primer error.

## Bloqueador

- Si la dirección principal de una sublista incluida no responde, se prueban los mirrors de la lista.
- Cuando una actualización falla, se usa la lista que ya estaba en caché sin volver a leerla del disco.
- En YouTube, el script contra anuncios ya no se ejecuta dos veces en la misma página.

## Rendimiento interno

- Los scripts que se inyectan en las páginas se leen y compactan una vez por proceso, no una vez por pestaña. No se ha medido el efecto en un TV.

## Validación e instalación

Las comprobaciones automatizadas cubren pruebas, lint, APK de distribución, conservación de datos al actualizar y arranque en Android API 24, 28, 34 y 37, además de recorridos con el mando mediante Macrobenchmark. Estas comprobaciones no cuantifican una mejora de rendimiento en un TV físico.

Compatible con Android 7.0 o superior. El modo incógnito requiere Android 9 o superior. Instala el APK de tu arquitectura sobre la versión existente para conservar los datos.
