* WebView-only build: removed GeckoView engine sources, Gecko build variant, Gecko module, and Mozilla Maven dependency path.
* CI and release workflows now build the generic WebView APK.
* Improved fullscreen restore behavior for WebView playback.
* Hardened adblock client access and adblock list loading.
* CI debug builds use a shared debug signing key so APKs can be installed over previous CI builds with `adb install -r`.
