# VireoLumaTV — component notices

This document records component provenance. It does not replace or change any license and is not a complete inventory of a resolved release APK.

## Original TV Bro sources

Copyright (c) 2019, Fedir Tsapana. All rights reserved.

VireoLumaTV uses sources from https://github.com/truefedex/tv-bro.
The original license, conditions, and disclaimer are retained verbatim in [LICENSE.md](LICENSE.md). Modified binaries require a different application name, icon, and application ID, plus an About screen crediting and linking the original sources.

The historical `WebViewEx.kt` carried Copyright (c) 2016 Fedir Tsapana, retained here as a provenance notice. The current WebView implementation is `app/src/main/java/com/reiniertutoriales/vireolumatv/webengine/webview/BrowserWebView.kt`.

## DownloadUtils.kt (releases up to v1.2.2)

In releases up to and including v1.2.2, `app/common/src/main/java/com/reiniertutoriales/vireolumatv/utils/DownloadUtils.kt` carries an MPL 2.0 notice and incorporates work with Copyright (C) 2006 The Android Open Source Project under Apache 2.0, with both original notices in that file. See [MPL 2.0](licenses/MPL-2.0.txt) and [Apache 2.0](licenses/Apache-2.0.txt).

Source of that version: https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/43ffce3ec0bb21c7f6cfddc9af6d9208d509e154/app/common/src/main/java/com/reiniertutoriales/vireolumatv/utils/DownloadUtils.kt
For each published binary, provide the corresponding source tag or immutable commit, rather than relying solely on this moving branch link.

The commit that adds this paragraph replaces the file with a new implementation that does not contain that code; its behavior is fixed by `DownloadUtilsGuessFileNameTest`.

## adblock-rust 0.13.3 and the JNI bridge

`adblock` 0.13.3 (Brave Software), built from crates.io into `libvireoadblock.so` by
`native/adblock-jni` (the bridge itself is MPL 2.0 as well).

MPL 2.0: [license text](licenses/MPL-2.0.txt).
Source: https://github.com/brave/adblock-rust/tree/v0.13.3 — exact transitive versions are pinned in
`native/adblock-jni/Cargo.lock`. The other crates linked into the library are licensed MIT,
Apache 2.0, Unicode-3.0 or Unlicense (dual-licensed crates are used under MIT or Apache 2.0).
The list of these crates and their license texts is in `licenses/rust-crates.txt`.
Preserve per-file notices and make the exact corresponding source available with binary distribution.

Filter lists (EasyList, EasyPrivacy, EasyList Spanish, uBlock Origin filters) are downloaded at
run time as data and are not part of the application package. The redirect resources and
scriptlets in `assets/adblock` and `assets/surrogates` are written for VireoLumaTV.

## Other declared dependencies and resources

AndroidX, Kotlin, coroutines, build tooling, test dependencies, filter lists, icons, logos, and screenshots require the artifact-specific inventory described in [the audit](docs/audit/archive-2026-10-01/LICENSE_REBRANDING_AUDIT.md). Do not interpret this partial notice inventory as certification of all transitive dependencies or as permission to use third-party trademarks.
