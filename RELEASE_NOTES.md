# VireoLumaTV 2.0.0

Interfaz nativa renovada para Android TV y consolidación de los componentes del navegador.
Versión de aplicación 2.0.0; versionCode 94.

## Cambios

- Cápsula superior con dirección, Atrás, Adelante, Recargar, Inicio, bloqueador y menú.
- Sugerencias locales de favoritos e historial accesibles con el mando.
- Ajustes y diálogos renovados, interfaz oscura e iconografía actualizada.
- Listas nativas para historial, favoritos y descargas; paginación por fecha e ID.
- Una WebView activa y conservación del estado de otras pestañas; recuperación del renderer.
- Bloqueador con siete fuentes, estado de actualización y detalle de listas no renovadas.
- Descargas condicionadas a una activación reciente; transferencias blob con entrega y cancelación únicas.
- Edición aislada de favoritos y conservación del esquema Room 19 y sus datos.
- Migración del user agent heredado al arrancar; conservación de valores personalizados.
- Actualizador de generic conectado a las releases de VireoLumaTV, con APK por arquitectura,
  SHA-256 y firma de checksums.
- Repositorio independiente en GitHub, inventario completo y auditoría del APK en CI.

## Instalación y actualización

El flujo de publicación exige el certificado de release vigente desde 1.2.1.
Las instalaciones con ese mismo certificado pueden actualizarse conservando sus datos.
Android rechaza actualizar una instalación firmada con otra clave, incluidos builds debug.
Desinstalar borra los datos: exporta tus favoritos antes si necesitas cambiar de firma.

En generic: Ajustes → Acerca de → Actualizar. Android solicita confirmar la instalación
y, cuando corresponda, autorizar instalaciones desde esta aplicación.
Las variantes google y foss no utilizan el actualizador integrado.

APK: armeabi-v7a (32 bits), arm64-v8a (64 bits) y x86_64.

## Validación y alcance

La preparación y publicación ejecutan las pruebas de scripts y Android, el esquema Room,
compilaciones debug/release, lint y los controles de firmas y checksums correspondientes.
El código funcional previo pasó la CI 557; esta versión debe superar sus propios controles.

Pendiente de validación física del build actual: mando, vídeo, WebView, descargas y memoria.
Las mediciones históricas del onn no certifican esta versión. Se conservan los avisos
de procedencia y las licencias de componentes aplicables.
