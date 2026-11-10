# VireoLumaTV 3.0.0

Bloqueador de anuncios más rápido y con mayor cobertura, mando de puntero declarado según la guía oficial de TV y una base de pruebas sin avisos de API obsoleta.

## Novedades y correcciones

- **Bloqueo de iframes:** los marcos incrustados se reconocen por su cabecera de navegación, con prioridad para `Sec-Fetch-Dest` cuando existe; se aplican las reglas `$subdocument` y `$frame` que antes no los bloqueaban.
- **Rendimiento del bloqueador:** la ocultación genérica reutiliza las excepciones de cada página, las reglas `$to=` se comprueban con una compuerta por dominio y cada solicitud evita análisis repetidos de `Accept`, `Uri` y claves. Medido en x86, no en TV.
- **Memoria:** la caché compilada del bloqueador se lee y escribe en Rust, sin pasar por la memoria de Java.
- **Exactitud:** un `$badfilter` de una regla ya no anula el bloqueo de otra equivalente; la descarga de listas valida su codificación HTTP; lista de sufijos públicos actualizada (psl 2.1.249).
- **Mando de puntero:** soporte declarado como `<property>` de la aplicación, la forma de la guía oficial de mandos de TV.
- **Iconos de sitios:** una búsqueda que no puede continuar ya no bloquea las demás.
- **Mantenimiento:** extensiones de androidx.core, pruebas migradas a las reglas de Compose v2, sin commits automáticos de la integración continua y perfil de arranque generado solo al preparar cada versión.

## Validación

Los cambios integrados pasaron pruebas automáticas (544 pruebas JVM y pruebas Rust), lint, compilaciones, arranques en emuladores API 24, 28, 34 y 37 y Macrobenchmark. La validación física de mando, vídeo y memoria en TV sigue pendiente; no se afirma una mejora de rendimiento en hardware.

## Instalación

Disponible para Android 7.0 o superior. Instala el APK compatible sobre tu versión actual para conservar tus datos, favoritos y ajustes.
