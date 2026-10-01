# Auditoría de nombre, licencias y atribución de Vireo Browser

Fecha: 1 de octubre de 2026. Repositorio: https://github.com/ReinierTutoriales/vireo-browser

## Resultado y alcance

El repositorio aún no puede considerarse listo para publicación en este tema. El rebranding del APK está parcialmente hecho; quedan imágenes públicas de TV Bro y falta verificar el conjunto exacto de licencias, avisos y fuentes correspondiente al APK distribuido. La revisión del árbol completo identifica los pendientes; no equivale a una autorización jurídica de la marca ni a una certificación de los binarios publicados.

- Rama de trabajo revisada: `fixes/audit`, commit `c339fcfb848593491a683f8aeec6efb50d73dadb`, 285 archivos versionados.
- `migration/vireo-package-namespace`: exactamente el mismo commit y árbol; no contiene una migración independiente en el estado descargado. No se integró ni modificó.
- Rama predeterminada remota: `master`, commit `6cb4b7c9be1bc2a231cc2590c184faa2be9c4ff3`. Sigue siendo el árbol heredado de TV Bro; los cambios Vireo de `fixes/audit` no están en la rama que GitHub muestra por defecto. No se cambió la rama predeterminada ni se hizo merge.
- Se revisaron LICENSE, README, PRIVACY, RELEASE_NOTES, Gradle, catálogo de versiones, manifests, todos los strings, HTML/JS, ProGuard, workflows, plantillas, funding, metadata y archivos binarios versionados. Se compararon los blobs de imágenes y se inspeccionaron visualmente los iconos/banner Vireo, el logo de inicio y las siete imágenes de publicación.
- El inventario de coincidencias del commit base está en [LEGACY_REFERENCE_INVENTORY.txt](LEGACY_REFERENCE_INVENTORY.txt). Incluye paquetes/imports/XML, nombres internos, atribuciones, URLs y dependencias; una coincidencia no significa que deba eliminarse.
- Fuentes de dependencias consultadas: ad-block tag 0.0.4 y segmented-button tag v1.0.0; pinned-section-listview upstream predeterminado. No se resolvió el grafo Gradle ni se inspeccionó un APK de release: este entorno no tiene Java/Android SDK disponibles. No se comprobó la titularidad de assets ni registros de marcas.

## Hallazgos concretos

| Prioridad | Evidencia | Clasificación y acción |
|---|---|---|
| Alta | `LICENSE.md:3-21` conserva copyright 2019 Fedir Tsapana y condiciones específicas para derivados | **Conservar íntegro.** No reemplazar por BSD estándar, MIT o un copyright exclusivo de Vireo. Las URLs `truefedex/tv-bro` en esta licencia son necesarias. |
| Alta | `VersionSettingsView.kt:35-67`, `values/strings.xml:4` | **Conservar** el crédito visible y el enlace a las fuentes originales. El texto existe; `view_settings_version.xml:28` limita el crédito a una línea y puede recortarlo en TV. Proponer varias líneas y validar con mando en dispositivo. |
| Alta | `app/build.gradle.kts:7-10`, manifests y todos los `app_name` localizados | El `applicationId` ya es `com.reiniertutoriales.vireobrowser` (`.foss` para foss). Los labels visibles son Vireo/Vireo Browser. El namespace Kotlin aún es `com.phlox.tvwebbrowser`; **no equivale al applicationId**, y la licencia no exige cambiarlo. Mantenerlo hasta una migración funcional revisada por separado. |
| Alta | Cinco `drawable-*/ic_launcher.png` y `drawable-xhdpi/banner.png` tienen blobs distintos de master; muestran el pájaro Vireo | El icono de launcher y banner fueron cambiados. **Conservar** esos assets si se acredita su procedencia. No se puede inferir titularidad o derechos por apariencia. |
| Alta | `app/src/main/assets/pages/home/logo.png` contiene TV BRO y tiene el mismo blob que master | **Cambiar** por un asset Vireo existente. La atribución obligatoria permanece en Acerca de; no necesita presentar el icono original como identidad de la app. |
| Alta | `metadata/en-US/images/{icon.png,featureGraphic.png,tvBanner.png}` contienen logo TV BRO; siete imágenes coinciden exactamente con master | **Cambiar antes de publicar.** Capturas 2 de teléfono/TV muestran pestaña `truefedex/tv-bro`; todas son capturas históricas. Generar material nuevo desde la app real, respetando tamaños de tienda y derechos sobre contenido, fotografías y marcas. No fabricar capturas ni copiar un icono pequeño a un campo de tienda grande. |
| Alta | `VersionSettingsView.kt:38-40` abre LICENSE/PRIVACY de truefedex | **Cambiar** los enlaces documentales a la rama Vireo que mantiene sus documentos. El enlace explícito a fuentes TV Bro sigue intacto. Usar `fixes/audit` mientras master no publique los documentos actuales; revisar estos enlaces al promover la rama de publicación. |
| Media | `README.md:21`, `PRIVACY.md:64` enlazan `ReinierTutoriales/tv-bro`; README enlaza XDA como discusión general | **Cambiar** la URL propia a `vireo-browser`; etiquetar XDA como discusión histórica de TV Bro, no soporte del fork. |
| Media | `.github/FUNDING.yml`, tres plantillas de issues, `assignees: truefedex` | **Eliminar** la asignación heredada y la promesa de prioridad ligada a donaciones al autor original. No son obligaciones de licencia. No inventar un canal de donaciones Vireo. En la GUI el apoyo voluntario al autor original puede conservarse si se identifica claramente a quién se apoya. |
| Alta | `DownloadUtils.kt:1-21`, `WebViewEx.kt:61` | **Conservar** MPL 2.0, copyright AOSP 2006/Apache 2.0 y copyright Fedir 2016. Eliminar GeckoView no elimina esas obligaciones. Registrar avisos, incluir licencias y ofrecer las fuentes exactas del código cubierto; comprobar avisos de modificación exigibles. |
| Alta | No había NOTICE, directorio de licencias ni pantalla de terceros; Gradle incluye AAR nativo ad-block | **Añadir** inventario y textos verificados. Comprobar que el APK o los materiales que realmente lo acompañan entregan todos los avisos necesarios. Un archivo nuevo en la raíz de GitHub no se empaqueta automáticamente en el APK. |
| Media | `AutoUpdateModel.kt:39`, `latest_version.json` apuntan a APK TV Bro `geckoIncluded` | Las tres variantes tienen `BUILT_IN_AUTO_UPDATE=false`, y las rutas visibles de comprobación están condicionadas. **Pendiente latente:** no activar actualizaciones hasta sustituir endpoint/manifest por un canal propio con identidad, versión y firma verificadas. No cambiar URLs por APKs Vireo inexistentes. Se puede retirar el manifest histórico en un cambio específico tras revisar usos externos. |
| Media | `Config.kt:51` usa `https://tvbro.phlox.dev/appcontent/home/`; WebViewEx intercepta sus rutas y sirve assets locales | **Migración técnica separada.** No sustituir por un dominio inventado. Revisar origen virtual, fallback de red, bookmarks/historial, JS bridge y páginas guardadas. No es atribución requerida. |
| Baja | `settings.gradle.kts:21`, plugins `tvbro.android.*`, `TVBro.kt`, temas `Theme.TVBro*`, nombres JS `TVBro` | **Rebranding interno opcional.** Cambiar coordinadamente Kotlin/XML/JS/ProGuard/tests si se desea. No eliminarlos por búsqueda global: son símbolos funcionales. |
| Media | `com.phlox...EXTRA_OPEN_IN_SAME_TAB`, preferencias y modelos persistidos | **Conservar por compatibilidad** o introducir aliases/migración explícita. No son un nombre público ni prueba de incumplimiento por sí solos. Una migración de namespace debe revisar Room, R/BuildConfig, componentes manifest, widgets XML, R8 y restauración de datos. |
| Baja | `docs/ROADMAP.md:125,145,146` usa el package original en comandos adb | **Cambiar** ejemplos operativos al applicationId Vireo. Las menciones históricas sobre Gecko/actualizador pueden mantenerse si se identifican como estado antiguo. |

## Dependencias y obligaciones que siguen abiertas

| Componente declarado | Versión | Evidencia/licencia | Acción |
|---|---|---|---|
| segmented-button | v1.0.0 | Tag exacto, commit `238b39df3f090a804b60401c2169275fe8d28770`; BSD 2-Clause, Fedir Tsapana 2018 | Conservar `com.github.truefedex` como coordenada real. Entregar copyright, condiciones y disclaimer con binarios. Texto añadido en `licenses/`. |
| ad-block | 0.0.4 | Tag exacto `3bbb044bc3f9e3806c98207766e6a4c5f90ac5b3`; LICENSE MPL 2.0 | Mantener coordenada original. Ofrecer fuentes correspondientes al binario nativo y preservar avisos. Resolver el artefacto JitPack real y comprobar fuentes/avisos de ese artefacto. |
| bloom-filter-cpp y hashset-cpp | Copias versionadas dentro de ad-block | CMake compila ambos; ambos LICENSE son MPL 2.0; hay avisos Brian R. Bondy 2015 | No son dependencias de Node que pueda ignorarse al distribuir Android: su C++ se incorpora a la biblioteca nativa. Referenciar el árbol exacto de ad-block y conservar avisos de todos sus archivos. |
| pinned-section-listview | 1.0.0 | README upstream Apache 2.0, Sergej Shafarenka/halfbit.de 2013-2016 | Verificar AAR/POM y código fuente de 1.0.0. El rango del README actual no prueba el aviso exacto de esa versión. |
| AndroidX appcompat/webkit/constraintlayout/recyclerview/lifecycle/room | 1.8.0 / 1.17.1 / 2.2.2 / 1.4.0 / 2.11.0 / 2.8.5 | Declarados en `gradle/libs.versions.toml`; normalmente Apache 2.0, **no verificado por artefacto** | Resolver runtime de generic/google/foss release, recoger POM/licencias/NOTICE y transitivas. No concluir cobertura completa a partir del nombre de la familia. |
| Kotlin stdlib / coroutines | 2.4.20 / 1.11.0 | Declaradas; normalmente Apache 2.0, **no verificado por artefacto** | Mismo inventario por versión exacta y transitivas. |
| AGP / Kotlin plugin / KSP / Gradle / Foojay | 9.4.1 / 2.4.20 / 2.3.12 / wrapper / 1.0.0 | Herramientas de compilación | Separar distribución de herramientas y runtime APK. `gradle-wrapper.jar` sí se distribuye con fuentes; conservar sus avisos aplicables. No son automáticamente dependencias runtime. |
| JUnit / Robolectric | 4.13.2 / 4.16.1 | Solo `testImplementation` en Gradle | Inventariar para fuentes/entorno de tests; no asumir que están en el APK release. |
| EasyList / EasyPrivacy / EasyList Spanish | Descarga dinámica | URLs en Config/AdblockModel; EasyList declara opción GPL-3.0+ o CC-BY-SA-3.0+ | Conservar cabeceras y registrar lista/versión/licencia exacta y sus includes. Revisar caché y cualquier redistribución; la lista española y sus fuentes incluidas requieren comprobación propia. No atribuir automáticamente GPL al navegador entero. |
| Logos de buscadores, iconos vectoriales y capturas | Assets versionados | No hay manifiesto de procedencia por asset | Documentar origen, permiso y licencia. Revisar usos de marcas y material de Wikipedia/Instagram/fotografías de TV; no declarar infracción sin evidencia ni asegurar autorización. |

No hay JAR/AAR/SO de aplicación versionados; solo `gradle/wrapper/gradle-wrapper.jar`. `fileTree("libs", "*.jar")` sigue declarado: cualquier archivo local añadido al proceso de release también debe inventariarse.

## Workflows y metadata de publicación

`ci.yml` y `release.yml` ya usan nombres de artefacto Vireo y compilación WebView generic; no conservan badges/URLs de proyecto TV Bro. `release.yml` publica desde la referencia seleccionada, toma una versión introducida manualmente y sube únicamente APKs. No sincroniza `versionName`/`versionCode` con la etiqueta, ni adjunta explícitamente NOTICE/licencias/fuentes correspondientes. Revisar esos puntos antes de una release pública, sin ejecutar el workflow durante esta auditoría.

`apply-patch-queue.yml` puede escribir en la rama destino derivada del nombre `patchq/...`. No se utilizó: un push de cola hacia `fixes/audit` violaría la separación de propuestas solicitada. Las nuevas propuestas se presentan en ramas normales y PRs sin merge.

`metadata/en-US/title.txt` ya dice Vireo Browser y la descripción es WebView. Los changelogs 56–69 y el número 2.1.6/69 son historia heredada; no deben presentarse como un registro de releases Vireo sin contexto. Añadir atribución a la descripción de tienda y crear un historial propio al publicar.

`PRIVACY.md` describe Vireo, pero no constituye una validación de todas sus afirmaciones: hay permisos de cámara, micrófono, ubicación, instalación de paquetes y backup permitido; también hay peticiones de filtros/favicons y servicios WebView/voz. Contrastar la política y el formulario de datos de la tienda con el comportamiento del APK final. Los PRs de privacidad 42–46 estaban abiertos y no integrados en la base auditada.

La disponibilidad del nombre **Vireo Browser**, sus dominios y el diseño del pájaro requieren comprobar titularidad y registros relevantes para los países/tiendas de distribución. Esta auditoría de repositorio no permite afirmar que la marca está jurídicamente libre.

## Propuestas seguras y criterios de cierre

1. PR de documentación: URLs propias, soporte/plantillas, NOTICE y textos MPL/Apache/BSD verificados, inventario y este informe; LICENSE original intacto. Cambiar ejemplos adb y añadir crédito en descripción de tienda.
2. PR de interfaz: enlaces de política/licencia a documentos Vireo en `fixes/audit`, crédito multilínea, identificación explícita del autor original en apoyo voluntario, reutilización del icono Vireo en el inicio. No cambia paquetes, applicationId, claves persistidas ni JS bridge. La política enlazada depende de que la propuesta documental se acepte o se publique en la rama elegida.
3. Pendientes para publicación: imágenes/capturas reales y procedencia de assets; grafo release y avisos exactos, licencias distribuidas con APKs, acceso a fuentes correspondientes MPL, política contrastada, versión/firma/canal propio y decisión explícita sobre rama predeterminada.
4. Migración opcional de namespace en una propuesta independiente cuando haya cambios reales en `migration/vireo-package-namespace`, con pruebas de compilación/debug/release, inflación XML/R8 y continuidad de datos. No basta con renombrar carpetas.

Validación local de las propuestas: diff sin errores de espacios, LICENSE idéntico al commit base, inventario completo de coincidencias, comprobación XML y URLs/labels. Compilación, pruebas Android, APK y navegación con mando no ejecutadas por falta de Java/SDK local; la propuesta de interfaz permanece en borrador hasta validar CI y dispositivo. No se hizo merge ni se activó auto-merge.

## Fuentes primarias

- TV Bro: https://github.com/truefedex/tv-bro/blob/master/LICENSE.md
- MPL 2.0, secciones 3.1–3.4: https://www.mozilla.org/en-US/MPL/2.0/
- Apache 2.0, sección 4: https://www.apache.org/licenses/LICENSE-2.0.txt
- segmented-button: https://github.com/truefedex/segmented-button/blob/v1.0.0/LICENSE
- ad-block: https://github.com/truefedex/ad-block/tree/0.0.4
- pinned-section-listview: https://github.com/beworker/pinned-section-listview#license
- EasyList: https://easylist.to/pages/licence.html
