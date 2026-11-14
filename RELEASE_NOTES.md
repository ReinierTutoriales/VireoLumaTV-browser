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
