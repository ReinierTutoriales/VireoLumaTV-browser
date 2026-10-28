# VireoLumaTV

An Android TV browser maintained by ReinierTutoriales. It uses the system WebView and supports TV remotes and gamepads on Android API 24 and later.

The interface has one top command capsule, a tab strip and native dialogs. Local address suggestions use bookmarks and history; the app also provides downloads, remote shortcuts, per-site blocking and a Rust filter engine. One WebView stays live at a time while other tabs retain their saved state.

The current source migration is tracked in [the removal backlog](docs/audit/TVBRO_REMOVAL_BACKLOG_20261027.md). Automated checks cover parts of the behavior; real TV navigation, playback and memory measurements remain separate acceptance steps.

Source and support: https://github.com/ReinierTutoriales/VireoLumaTV-browser
Original TV Bro source code: https://github.com/truefedex/tv-bro

## License and attribution

The original TV Bro copyright, conditions, and disclaimer are preserved in [LICENSE.md](LICENSE.md). This license has specific requirements for modified binaries; it is not an unmodified standard BSD license. VireoLumaTV must retain its distinct name, application ID, and icon and show the original-source attribution in its About screen.

Additional component notices are recorded in [NOTICE.md](NOTICE.md). The license and rebranding audit, including unresolved release requirements, is in [docs/audit/archive-2026-10-01/LICENSE_REBRANDING_AUDIT.md](docs/audit/archive-2026-10-01/LICENSE_REBRANDING_AUDIT.md).

## Development status

The [regression policy](docs/REGRESSION_POLICY.md), [contributor rules](AGENTS.md), [current work plan](docs/ROADMAP.md) and [consolidated audit](docs/audit/CONSOLIDATED_AUDIT_20261007.md) track the one-branch review, corrections and validation status. Older audits describe their own source revisions and must be checked against current code before applying changes.
