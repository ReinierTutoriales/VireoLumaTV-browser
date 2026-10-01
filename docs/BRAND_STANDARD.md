# VireoLumaTV identity

- Product and application label in every locale: **VireoLumaTV**.
- Repository name requested by the maintainer: **VireoLumaTV-browser**.
- Repository URL: https://github.com/ReinierTutoriales/VireoLumaTV-browser (confirmed October 1, 2026).
- CI artifact prefixes: `vireolumatv-debug`, `vireolumatv-release`.
- Release APK prefix: `vireolumatv-`.
- Installation ID and application namespace: `com.reiniertutoriales.vireolumatv`; the FOSS installation ID adds `.foss`.
- Common module namespace: `com.reiniertutoriales.vireolumatv.common`.
- Application class: `VireoLumaTVApp`; convention plugin IDs start with `vireolumatv.android`.
- Local home page origin: `https://vireolumatv.invalid/appcontent/home/`, served from packaged assets by the existing WebView request interceptor. This is not a hosted website.

Changing the installation ID creates a separate Android application. It cannot update the previous `com.reiniertutoriales.vireobrowser` installation or automatically access its private data. Keep the old installation until any desired bookmarks or other data have been saved. This proposal does not uninstall applications, erase user data, change Room tables or import data between applications.

This proposal incorporates the previously reviewed audit documents, privacy policy, third-party notices, source pointers and removal of inherited promotional images, with the new product identity. It does not merge any existing audit or migration branch. Earlier audit PRs overlap with this proposal and require reconciliation before merging.

The bird launcher icon remains the existing text-only ChatGPT artwork reported by the maintainer. The TV banner was edited with the image generation tool on October 1, 2026 to display VireoLumaTV, then exported at 640 by 360 pixels. The home page now uses the existing launcher bird. Historical store screenshots were removed; replacements require captures of an actual build.

Keep the original LICENSE.md verbatim, original copyright notices, the TV Bro source link in the application, and third-party license texts. Historical performance records retain their original artifact names. Recognition of the old `TV Bro/1.0` user agent is compatibility code, not the current browser identity. Built-in updating remains disabled for every build variant and now returns before attempting a network request when disabled. The fork's latest_version.json has no published channels or changelog; never advertise upstream TV Bro binaries as updates for this app. Enable updating only after preparing signed fork-specific artifacts and a real feed.

Validation is recorded in the PR: XML parsing and consistent application labels, Kotlin package/path and XML class references, convention plugin entry points, original license comparison, injected script playback/blob-download behavior, and CI unit/debug/release builds. Device testing remains a separate check. The chosen name has preliminary search checks only, not trademark clearance.
