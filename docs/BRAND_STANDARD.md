# VireoLumaTV identity

- Product and application label: **VireoLumaTV**.
- Platforms: Android TV, Google TV and compatible TV boxes, API 24+.
- Maintainer: **ReinierTutoriales**.
- Repository: https://github.com/ReinierTutoriales/VireoLumaTV-browser
- Application namespace and generic installation ID: `com.reiniertutoriales.vireolumatv`.
- The FOSS installation ID adds `.foss`.
- Application class: `VireoLumaTVApp`.
- Local home origin: `https://vireolumatv.invalid/appcontent/home/`, served from packaged assets.

The application license is MIT; component notices are in `NOTICE.md` and `licenses/`.
About links to this repository, its license and privacy policy.
The generic variant checks this repository's latest release and verifies signed
checksums and the APK certificate before installation. Google and FOSS variants
do not use the built-in updater.

Keep the installation ID and release certificate stable to preserve Android update
compatibility. A differently signed installation cannot be updated in place.

The launcher and banner use the VireoLumaTV bird. The 640 × 360 px banner is in
`drawable-xxxhdpi` (160 × 90 dp). Publication screenshots must show an actual build.
See `ASSET_PROVENANCE.md` for the maintainer's artwork declaration.
