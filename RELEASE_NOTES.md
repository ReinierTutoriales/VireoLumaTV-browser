# VireoLumaTV 2.0.13

**Android versionName:** `2.0.13`  
**Android versionCode:** `107`

## Cambios desde 2.0.12

- **Inicio y favoritos:** Inicio muestra los favoritos guardados y Favoritos vuelve al menú (#142).
- **Interfaz:** superficies y paneles renovados; Ajustes agrupados en Navegación, Protección, Vídeo y pantalla, Mando y puntero, y Avanzado (#143).
- **Idioma de la app:** nuevo ajuste para elegir el idioma; en Android 13+ también desde los ajustes del sistema. Textos revisados en los 12 idiomas y mensajes de error concretos en lugar de «Error» (#152).
- **Pestañas:** en hebreo y persa, «Mover a la izquierda/derecha» sigue la dirección que se ve (#152).
- **Ventanas emergentes:** cada nivel describe lo que bloquea: avisos de la página y ventanas abiertas sin pulsar (#152).
- **Descargas:** el servicio pasa a primer plano antes de empezar; si Android lo impide, la descarga se rechaza sin dejar archivos. En Android 15+ avisa cuando el sistema detiene las descargas por límite de tiempo. Icono propio en la notificación (#149).
- **Instalación de APK descargados:** mediante sesión de PackageInstaller; avisa si la instalación falla (#153).
- **Modo privado:** al salir, el almacén de cookies se guarda solo después de confirmar su borrado (#153).
- **Base de datos:** esquema propio (versión 20). Favoritos, pestañas, historial, descargas y ajustes por sitio de 2.0.12 se migran al actualizar (#146).
- **Código:** sustitución del código, textos y referencias heredados; medición reproducible en `scripts/measure-inherited-lines.py` (#144, #150, #151, #154, #155).

## Validación

La CI comprueba compilación, pruebas automatizadas y Android Lint. La reproducción, la navegación con mando y la recuperación de sesión deben comprobarse también en hardware real. Los APKs publicados requieren firma de release y verificación de SHA-256.

## Limitaciones conocidas

- Pendiente de comprobación en Android TV físico: actualización desde 2.0.12 con datos (migración de la base de datos), controles de vídeo, recuperación de sesión y persistencia tras reinicio.
- El comportamiento de páginas y vídeos también depende del proveedor WebView y del servidor.

## Distribución

APKs firmados para `arm64-v8a`, `armeabi-v7a` y `x86_64`, con verificación de certificado y hashes.
