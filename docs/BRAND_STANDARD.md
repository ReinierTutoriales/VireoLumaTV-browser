# VireoLumaTV identity

- Product and application label in every locale: **VireoLumaTV**.
- Repository name requested by the maintainer: **VireoLumaTV-browser**.
- Repository URL: https://github.com/ReinierTutoriales/VireoLumaTV-browser (awaiting confirmation; this URL was not reachable during preparation).
- CI artifact prefixes: `vireolumatv-debug`, `vireolumatv-release`.
- Release APK prefix: `vireolumatv-`.
- Keep `com.reiniertutoriales.vireobrowser` and the existing `.foss` suffix for update/data compatibility. Keep the internal namespace `com.phlox.tvwebbrowser`; namespace migration is a separate proposal.

This proposal incorporates the previously reviewed audit documents, privacy policy, third-party notices, source pointers and removal of inherited promotional images, with the new product identity. It does not merge any existing audit or migration branch. Earlier audit PRs overlap with this proposal and require reconciliation before merging.

The bird launcher icon remains the existing text-only ChatGPT artwork reported by the maintainer. The TV banner was edited with the image generation tool on October 1, 2026 to display VireoLumaTV, then exported at 640 by 360 pixels. The home page now uses the existing launcher bird. Historical store screenshots were removed; replacements require captures of an actual build.

Keep the original LICENSE.md verbatim, original copyright notices, the TV Bro source link in the application, and third-party license texts. Historical performance records retain their original artifact names. The inherited updater URL and latest_version.json are legacy material; built-in updating is disabled for every current build variant. They must not be enabled until a fork-specific update feed is prepared.

Validation: XML parsing and consistent application labels, whitespace checks, original license comparison, and visual inspection of the banner. Android compilation and device testing remain pending because this workstation lacks the Android SDK/Java runtime. The chosen name has preliminary search checks only, not trademark clearance.
