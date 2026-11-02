# VireoLumaTV 2.0.16

**Android versionName:** `2.0.16`  
**Android versionCode:** `110`

## Corrección de arranque

- Corrige un cierre al cargar Inicio y el aviso de certificado: Android requiere escapar las llaves de cierre literales en las expresiones regulares. El defecto se reprodujo con los APK publicados de 2.0.14 y 2.0.15.
- Conserva el escape HTML de los textos y todos los cambios funcionales de 2.0.15, incluidas las reglas del bloqueador y Room 3.0.3. La base de datos sigue siendo `main.db`, con esquema 20.
- Los controles usan los emoji incluidos en Android. Se retira la carga automática de una fuente EmojiCompat descargable: una parada de Google Play Services ya no debe cerrar el navegador por esa dependencia. La auditoría del APK conserva el inicializador de perfiles y rechaza que vuelva la carga automática de emojis.
- La corrección no añade bibliotecas ni cambia la firma o el identificador de la aplicación.

## Verificación

- CI comprueba pruebas, lint, compilaciones de las tres arquitecturas y arranque del APK minificado en Android 9 y 14, con instalación limpia, segundo arranque y actualización desde 2.0.15 conservando un favorito de prueba.
- La publicación exige además arrancar el APK con la firma oficial y comprobar la actualización antes de distribuirlo.
- Pendiente de validación física en TV y ARM: mando, reproducción, sitios reales, consumo de RAM y latencia. Las pruebas de arranque no acreditan esos recorridos.

## Instalación

Actualizar instalando el APK de la arquitectura correspondiente sobre la versión existente, con el certificado habitual. Desinstalar elimina los datos.

APKs para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con firma de APK y checksums firmados verificados por el flujo de publicación.
