# Privacy policy evidence — October 1, 2026

Base: fixes/audit c339fcfb848593491a683f8aeec6efb50d73dadb. Source review, not a device network capture or legal certification. Intended distribution: GitHub only, per maintainer.

| Claim | Evidence |
|---|---|
| Stored tabs/previews | TabsModel.saveTab/loadState; common/model/WebTabState.saveWebViewStateToFile/saveThumbnail |
| Stored download records | DownloadTask inserts File/Blob/Stream metadata into Room, with no base incognito exclusion |
| Stored favicon/host data | common/singleton/FaviconsPool; TVBro.databaseDelegate |
| Filter connections | AdblockModel.init/loadAdBlockList/getConfiguredFilterLists; Config.DEFAULT_ADBLOCK_LIST_URL |
| Google favicon connections | assets/pages/home/index.html applySearchEngine: t2.gstatic.com/faviconV2 includes website origin |
| Voice provider | VoiceSearchHelper ACTION_RECOGNIZE_SPEECH/createSpeechRecognizer, no on-device-only requirement |
| Camera/audio/location/DRM | WebViewEx.onPermissionRequest/onGeolocationPermissionsShowPrompt; manifest |
| Backup | Manifest allowBackup=true; no explicit exclusions |
| No developer analytics found in reviewed source | Direct dependency/source search; not a guarantee about all transitive artifacts, WebView or services |
| App updater disabled | All Gradle flavors set BUILT_IN_AUTO_UPDATE=false |

Privacy fixes #42–46 are not integrated in this base. Revise claims only after checking the distributed build. Remaining checks: traffic capture, complete APK dependency inventory, actual retention/deletion behavior and applicable jurisdictional requirements.

Primary documentation:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/identity/data/autobackup
- If Google Play is selected later, review its current requirements: https://support.google.com/googleplay/android-developer/answer/10144311