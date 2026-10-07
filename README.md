# VireoLumaTV

Simple web browser optimized to use with Android TV remotes.

VireoLumaTV is a WebView-only Android TV browser based on the source code of TV Bro. The GeckoView engine, Gecko build variant, and Gecko module sources have been removed to keep the app smaller, simpler, and more predictable on low-memory Android TV hardware.

Features:
- works with TV remote
- tabs and bookmarks support
- voice search support
- switch user agent support
- uses Android built-in web rendering engine (WebView / WebKit-Blink based)
- built-in download manager
- browsing history
- shortcuts

Historical discussion of the original TV Bro project:
- https://forum.xda-developers.com/android/apps-games/tv-bro-browser-android-based-tvs-t3545295

Source:
- VireoLumaTV repository: https://github.com/ReinierTutoriales/VireoLumaTV-browser
- Original TV Bro source code: https://github.com/truefedex/tv-bro

## License and attribution

The original TV Bro copyright, conditions, and disclaimer are preserved in [LICENSE.md](LICENSE.md). This license has specific requirements for modified binaries; it is not an unmodified standard BSD license. VireoLumaTV must retain its distinct name, application ID, and icon and show the original-source attribution in its About screen.

Additional component notices are recorded in [NOTICE.md](NOTICE.md). The historical license and rebranding audit is preserved in [the audit archive](docs/audit/archive-2026-10-01/LICENSE_REBRANDING_AUDIT.md).

## Development status

The [current work plan](docs/ROADMAP.md) and [consolidated audit](docs/audit/CONSOLIDATED_AUDIT_20261007.md) track the one-branch review, corrections and validation status. Older audits describe their own source revisions and must be checked against current code before applying changes.
