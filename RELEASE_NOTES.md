# VireoLumaTV 2.0.1

## Cambios

- Licencia MIT para el código propio de la aplicación, Copyright (c) 2026 ReinierTutoriales.
- Acerca de muestra la identidad de VireoLumaTV, la licencia MIT y los enlaces del proyecto.
- Eliminada la fila de atribución anterior y sus traducciones de la interfaz actual.
- La licencia incluida en el APK coincide con LICENSE.md; NOTICE.md conserva los avisos de componentes externos.
- Auditoría del APK actualizada para detectar el recurso y el archivo de licencia retirados.
- Respaldo verificado de releases anteriores antes de cada publicación.

Se mantienen la interfaz, el bloqueador, las pestañas y los datos de la 2.0.0.
Versión de aplicación 2.0.1; versionCode 95.

## Actualización

En generic: Ajustes → Acerca de → Actualizar. La aplicación verifica los checksums
firmados y el APK antes de abrir el instalador de Android.

El certificado de release se mantiene. Las instalaciones con ese certificado pueden
actualizarse sin desinstalar; una firma diferente, incluidos builds debug, no es compatible.
Las variantes google y foss no utilizan el actualizador integrado.

APK: armeabi-v7a, arm64-v8a y x86_64.

## Componentes y validación

Las licencias de AndroidX, Kotlin, adblock-rust, el puente JNI y otras dependencias
se conservan por separado; MIT no sustituye esas licencias.
La publicación exige CI del SHA exacto, pruebas, builds, esquema Room 19, lint,
auditoría del APK, certificado vigente y checksums firmados.
La validación física de mando, vídeo y memoria del build actual sigue pendiente.
