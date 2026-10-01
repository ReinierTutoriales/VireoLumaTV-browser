# VireoLumaTV privacy policy

Last updated: October 1, 2026.

VireoLumaTV is an Android TV browser maintained by ReinierTutoriales and distributed through GitHub. It uses sources from [TV Bro](https://github.com/truefedex/tv-bro); this policy describes VireoLumaTV, not the original project.

## Browser data

VireoLumaTV processes pages and search queries to provide browsing. It stores browser information on your device: history, bookmarks, settings, open-tab URLs and state, previews, favicon/host information, cookies and website storage, and download records. Download records can include URL, referring page, filename, destination, size and progress. Files are saved to your chosen or configured destination.

The reviewed application source does not include a developer-operated analytics or crash-reporting service. This does not mean that no information leaves the device: the services described below receive information needed to provide their functions.

## Websites and network connections

Websites and your selected search provider receive page requests, search queries and information transmitted by WebView, such as IP address, browser headers and applicable cookies. Their policies apply. HTTP pages may transmit information without transport encryption.

VireoLumaTV downloads and caches filters from configured ad-block providers. Defaults include EasyList, EasyPrivacy and EasyList Spanish. Their hosts receive connection information, including IP address, even when you are not visiting those websites. Lists can be checked at browser startup and when refreshed.

Site icons may be requested from websites, referenced icon hosts, and a Google-hosted favicon service used by the internal home page. That service receives the requested website origin in its URL. Default bookmark suggestions can load third-party icons. These requests are separate from developer analytics.

Opening source, license, support and policy links contacts GitHub or the linked provider. Android WebView, the operating system and installed device services may perform their own network activity under their policies.

## Voice and permissions

Voice search uses your device's speech recognition service. That service may send audio to remote servers; VireoLumaTV does not require on-device-only recognition. Recognized text may be sent to your selected search provider.

Websites may request camera, microphone or approximate location access. VireoLumaTV displays prompts and uses Android permissions where required. If you allow access, websites may receive the corresponding data under their own policies. Protected-media websites may interact with device DRM facilities through WebView.

Other declared permissions support connectivity, download notifications, foreground downloads, keeping the device awake, file access on supported Android versions and package-installation flows. The reviewed variants disable the built-in app updater. Declared permissions are not necessarily used in every session.

## Incognito limitations

Incognito is intended to reduce ordinary browsing history. It does not hide activity from websites, search providers, speech services, network operators or the operating system.

In the currently reviewed code, private-tab URLs/state and previews, favicon/host information and download records can still be written to persistent storage. Cleanup is not guaranteed after a crash or forced termination. Downloads remain until removed. Do not rely on this version to prevent all local traces. This section will be revised when fixes are integrated and verified in the distributed build.

## Backup, retention and deletion

Android backup is enabled in the manifest. Depending on the device, Android version and settings, eligible app data may be copied by system backup or device-transfer services. VireoLumaTV cannot promise that all app data stays exclusively on the device.

Data can remain until removed through available browser controls or Android app-data settings. Clearing browser cache does not necessarily remove history, bookmarks, cookies, tab state or downloaded files. Clearing app data or uninstalling may remove app-private data, but externally saved downloads, backups and information held by websites/providers require separate deletion. System/provider policies control backup retention.

## Children and updates

Parents and guardians should supervise minors' browsing. Websites and device services apply their own age restrictions and data practices. VireoLumaTV does not control those providers' collection.

This policy will be updated when the distributed application's behavior changes. Review it together with the source and release notes for your installed version.

## Contact

Contact the maintainer through [VireoLumaTV issues](https://github.com/ReinierTutoriales/VireoLumaTV-browser/issues). Issues are public: do not post browsing history, credentials, personal files or other sensitive information. GitHub's policy applies to information submitted there.
