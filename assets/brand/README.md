# VireoLumaTV identity

**VireoLumaTV is the official application and project name. VLTV is only its initials, used in the logo; it is not a separate brand or a replacement name.** Use VireoLumaTV in application labels, project descriptions and release titles. Keep VLTV unchanged when referring to the lettering in the logo.

The approved identity uses the bird and its VLTV initials in ice blue (`#C7D8EC`) on blue graphite (`#141B24`). Wing and eye are negative space. The previous bird artwork is retired.

- `approved-source.png`: unchanged approved reference, retained outside the APK.
- `logo.svg`: production master with outlined lettering, flat sRGB colors and no embedded font or bitmap. The approved contours were traced and cleaned for scalable rendering.
- `python scripts/generate-brand-assets.py`: regenerate Android resources; `--check` detects drift and runs in CI.

Android 7 receives scalable legacy launcher resources (80 × 80 dp icon and 160 × 90 dp banner). Android 8+ receives adaptive foreground/background layers. The launcher mark remains inside the 66 dp circular safe region of its 108 dp layer; the TV banner uses a 16:9 foreground. Android applies its own mask: no black corner pixels, pre-applied shadows or duplicate outlines are baked into the resources. The initials VLTV are identical across app languages.

The small in-app favicon uses the bird alone; About and launcher assets use the complete mark. The app accent follows the foreground color. Black AMOLED surfaces and semantic shield/error colors retain their existing behavior.

Guidance consulted on 2026-11-11:
- https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

The vector resources and generator ship with the source; the reference image does not ship in the application. No additional runtime library is used.
