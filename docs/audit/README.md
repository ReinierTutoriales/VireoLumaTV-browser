# Repository checks

Commit SHAs in these audit documents refer to the history before the current root
commit (`927249bb`, followed by `212bcdab` on `master`). Those commits are not in
`master`; they are kept only in the private backup taken before the rewrite.
CI run IDs, artifact IDs and dated branch names are not commit SHAs.
Historical artifacts may expire; their recorded identifiers do not guarantee availability.

The current application identity and license are documented in the repository's
`README.md`, `LICENSE.md`, `NOTICE.md` and `docs/BRAND_STANDARD.md`.
`SOURCE_INVENTORY.tsv` covers the files tracked in this tree. Its classifications
record the existing inventory and do not establish exclusive authorship.

## Current verification

The current CI definition is `.github/workflows/ci.yml`. It checks the source
inventory, unit tests, debug and minified release builds, lint, Room schemas and
APK contents. Use the successful run for the exact source SHA being reviewed.
Generated APK licenses are compared with the canonical root license files.
Physical validation is deferred by the owner; CI does not establish device performance.

## Revisión del 10 de noviembre de 2026 (PR #264)

Base: `master` `1bfae18`; PR original `2b1af14`. La revisión añade comprobaciones de
integración y entradas adversariales; no cambia motor, datos, versión ni firma.

- Cápsula: los márgenes XML se sumaban a los de Compose. La captura anterior no
  enlazaba las acciones y podía pasar sin dibujar controles. Las capturas ahora
  exigen controles y pestañas visibles, sin solapamiento, y márgenes relativos a
  la ventana. Se conserva la comprobación con texto ampliado.
- Ventana compacta: la fila de comandos permite desplazarse y recuperar mediante
  D-pad el último comando sin reducir su objetivo táctil.
- Pestañas: el máximo incluye título y cierre, conservando el límite de 188 dp y
  el objetivo de cierre de 48 dp del contrato del proyecto. No son un ancho
  universal recomendado por Google. El cierre comparte centro vertical con el
  título y tiene espaciado interior simétrico. Se omite el borde en reposo:
  `border(0.dp)` dibujaba una línea de un píxel. La captura previa medía 284 px
  para la pestaña larga en el lienzo mdpi de 960 px y mostraba ese borde interior.
- Sugerencias: la lista se desplaza dentro de su altura disponible; seis resultados
  de dos líneas con texto al 150 % conservan el acceso con D-pad y puntero. La
  [prueba de reproducción](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/42917807057)
  falló al intentar alcanzar el sexto resultado; las otras 601 pruebas pasaron.
- Selector: el resultado conserva su propietario; una segunda solicitud no lo
  sustituye y navegar o destruir la página cancela su callback una vez. Sólo
  acepta URI de contenido de proveedores externos; rechaza rutas directas y
  proveedores del propio UID, también en selecciones múltiples.
- Descargas: Referer elimina credenciales y fragmento, se limita al origen para
  otro origen y se omite de HTTPS a HTTP, también tras redirecciones. Se aplica
  un valor seguro predeterminado; no se conoce la Referrer-Policy de la página.
- Blob: la admisión limita peticiones pendientes por página y por proceso, tanto
  en el trabajador como en las respuestas al hilo principal. Las pruebas incluyen
  saturación, recuperación, cancelación y entrega de una transferencia completa.

La [ejecución de reproducción](https://github.com/ReinierTutoriales/VireoLumaTV-browser/actions/runs/42915590028)
detectó los márgenes duplicados, capturas sin controles, Referer y cola sin límite.
El parser de resultados del selector requiere un sustituto de plataforma en
Robolectric: esas pruebas no ejecutan el proveedor Chromium real.
Los resultados finales se registran en el PR para su SHA exacto; una reproducción
fallida no acredita la corrección. Mando, vídeo, proveedor WebView y rendimiento
en una TV física siguen pendientes. No se ha publicado una versión por esta revisión.

Fuentes: [entrada TV](https://developer.android.com/training/tv/get-started/controllers),
[layouts TV](https://developer.android.com/design/ui/tv/guides/styles/layouts),
[pestañas TV](https://developer.android.com/design/ui/tv/guides/components/tabs),
[bordes Compose](https://developer.android.com/reference/kotlin/androidx/compose/foundation/border.modifier),
[selector WebView](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser(android.webkit.WebView,%20android.webkit.ValueCallback%3Candroid.net.Uri[]%3E,%20android.webkit.WebChromeClient.FileChooserParams)),
[Referer HTTP](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.1.3).

## Historical source migration record

Closed on 2026-11-01 at `66459a60`, measured with
`python3 scripts/measure-inherited-lines.py --rev 66459a60`: 878 of 14 310 counted
lines in `app/src/main` are still attributed by `git blame -w -C` to commits made
before this project. Breakdown: layouts 362, Kotlin 309, manifest and other XML 133,
values 56, HTML 18, JavaScript 0.

Those lines are the forms the Android platform requires (`super.onCreate`,
`setContentView`, Room annotations, `<intent-filter>`, `android:exported`,
`layout_width`, document boilerplate). They carry no upstream design, text or
identity. Upstream translations, names, credits, notices, data layout and the
pointer, tab, download, history and database designs have been replaced.
Rewriting the remaining lines would only alter `git blame`, so it is not pursued.

## Device and regression evidence

Dated reports describe their own recorded builds. Preserve measurements and traces;
compare their source SHA and conditions before using them as a baseline.

- [Android TV and low-memory audit](ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md)
- [Restore and User-Agent fixes](OFF_DEVICE_FIXES_20261007.md)
- [Filter update checks](ADBLOCK_OFF_DEVICE_20261007.md)
- [TV interface checks](TV_UI_REFRESH_20261007.md)
- [Regression policy](../REGRESSION_POLICY.md)
- [Roadmap](../ROADMAP.md)

Obsolete identity and migration reports are retained in Git history and the
repository backups, rather than presented as current project instructions.

## Interface icon sources (2026-11-03)

The current interface symbols come from the pinned Material Symbols Rounded
snapshot in `NOTICE.md`, converted from SVG paths to Android VectorDrawable.
The resource comments identify each upstream symbol; the inventory marks these
adapted resources as MODIFICADO. This is an
asset import under Apache 2.0, not a claim of independent icon authorship.
No icon library, icon font or network font loader is added.
