# VireoLumaTV

Navegador para Android TV desarrollado y mantenido por **ReinierTutoriales**.
Utiliza Android System WebView y funciona con mando o gamepad desde Android 7.0 (API 24).

## Descargar e instalar

**[Descargar VireoLumaTV 2.0.0](https://github.com/ReinierTutoriales/VireoLumaTV-browser/releases/tag/v2.0.0)**

Elige el APK compatible con tu dispositivo:

| Arquitectura | Dispositivo |
|---|---|
| `armeabi-v7a` | Android de 32 bits, incluido el onn de referencia de 32 bits |
| `arm64-v8a` | Android de 64 bits |
| `x86_64` | Equipos Intel/AMD o emuladores compatibles |

En la variante `generic`, abre **Ajustes → Acerca de → Actualizar** para buscar nuevas
releases de este repositorio. La app comprueba la firma de los checksums y el SHA-256
del APK antes de abrir el instalador de Android. Las variantes `google` y `foss`
no utilizan este actualizador.

Las instalaciones con el certificado de release vigente desde 1.2.1 pueden actualizarse
sin desinstalar. Android rechaza actualizaciones con otra firma, incluidos los builds debug.
Si necesitas desinstalar para cambiar de firma, exporta antes tus favoritos: desinstalar borra los datos.

## Funciones

- Barra superior con dirección, navegación, Inicio, bloqueador y menú.
- Pestañas, interfaz oscura y controles nativos para mando.
- Sugerencias locales de favoritos e historial.
- Favoritos, historial, descargas, modo privado y atajos del mando.
- Bloqueador basado en Rust, con siete fuentes de filtros y control por sitio.
- Conservación del estado de pestañas y recuperación cuando se pierde el renderer.

El comportamiento de las webs y del vídeo depende también del proveedor WebView,
el servidor, la conexión y el hardware. Las pruebas automáticas no sustituyen la
validación física de mando, reproducción y memoria del build actual.

## Proyecto y documentación

Este es el repositorio independiente de VireoLumaTV. La rama principal es `master`.

- [Notas de la versión](RELEASE_NOTES.md)
- [Privacidad](PRIVACY.md)
- [Licencia](LICENSE.md) y [avisos de componentes y procedencia](NOTICE.md)
- [Reglas de desarrollo](AGENTS.md) y [contratos de regresión](docs/REGRESSION_POLICY.md)
