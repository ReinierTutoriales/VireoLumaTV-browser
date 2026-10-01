# VireoLumaTV — component notices

This document records component provenance. It does not replace or change any license and is not a complete inventory of a resolved release APK.

## Original TV Bro sources

Copyright (c) 2019, Fedir Tsapana. All rights reserved.

VireoLumaTV uses sources from https://github.com/truefedex/tv-bro.
The original license, conditions, and disclaimer are retained verbatim in [LICENSE.md](LICENSE.md). Modified binaries require a different application name, icon, and application ID, plus an About screen crediting and linking the original sources.

`app/src/main/java/com/reiniertutoriales/vireolumatv/webengine/webview/WebViewEx.kt` also retains Copyright (c) 2016 Fedir Tsapana.

## DownloadUtils.kt

`app/common/src/main/java/com/reiniertutoriales/vireolumatv/utils/DownloadUtils.kt` carries an MPL 2.0 notice and incorporates work with Copyright (C) 2006 The Android Open Source Project under Apache 2.0. Both original notices remain in that file. See [MPL 2.0](licenses/MPL-2.0.txt) and [Apache 2.0](licenses/Apache-2.0.txt).

Source and modifications: https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/cleanup/vireolumatv-identity/app/common/src/main/java/com/reiniertutoriales/vireolumatv/utils/DownloadUtils.kt
For each published binary, provide the corresponding source tag or immutable commit, rather than relying solely on this moving branch link.

## segmented-button v1.0.0

`com.github.truefedex:segmented-button:v1.0.0`

Copyright (c) 2018, Fedir Tsapana. All rights reserved.

BSD 2-Clause license; full copyright, conditions, and disclaimer: [license text](licenses/segmented-button-BSD-2-Clause.txt).
Source: https://github.com/truefedex/segmented-button/tree/v1.0.0 (commit `238b39df3f090a804b60401c2169275fe8d28770`).

## ad-block 0.0.4 and native components

`com.github.truefedex:ad-block:0.0.4`

MPL 2.0: [license text](licenses/MPL-2.0.txt).
Source: https://github.com/truefedex/ad-block/tree/0.0.4 (commit `3bbb044bc3f9e3806c98207766e6a4c5f90ac5b3`).

Its Android CMake build incorporates `bloom-filter-cpp` and `hashset-cpp` from that repository's checked-in `node_modules`. Both include MPL 2.0 licenses. Native files retain notices including Copyright (c) 2015 Brian R. Bondy. Preserve all per-file notices and make the exact corresponding source available with binary distribution. This is separate from the removed GeckoView engine.

## pinned-section-listview

`de.halfbit:pinned-section-listview:1.0.0`

Upstream README: Copyright 2013-2016 Sergej Shafarenka, halfbit.de; Apache 2.0.
Source: https://github.com/beworker/pinned-section-listview
License text: [Apache 2.0](licenses/Apache-2.0.txt).

The upstream default branch was inspected; the exact published 1.0.0 artifact and its complete notices remain to be checked.

## Other declared dependencies and resources

AndroidX, Kotlin, coroutines, build tooling, test dependencies, filter lists, icons, logos, and screenshots require the artifact-specific inventory described in [the audit](docs/LICENSE_REBRANDING_AUDIT.md). Do not interpret this partial notice inventory as certification of all transitive dependencies or as permission to use third-party trademarks.
