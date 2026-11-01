# VireoLumaTV — component notices

Copyright (c) 2026 ReinierTutoriales.

The MIT license in LICENSE.md covers the original VireoLumaTV application code.
Separately licensed components retain their own notices and terms; the application
license does not relicense those components.

## Native blocking engine and JNI bridge

adblock-rust 0.13.5, by Brave Software, and the bridge in native/adblock-jni
are licensed under MPL 2.0. The bridge builds libvireoadblock.so.

- Source: https://crates.io/crates/adblock/0.13.5
- License: licenses/MPL-2.0.txt
- Exact dependency versions: native/adblock-jni/Cargo.lock
- Crate notices and license texts: licenses/rust-crates.txt

Other linked Rust crates use MIT, Apache 2.0, Unicode-3.0 or Unlicense, as recorded
in the crate inventory. Preserve their notices and provide corresponding covered
source when distributing binaries.

## Android and build dependencies

AndroidX, Kotlin, coroutines and other dependencies keep their respective licenses.
Their declarations are in gradle/libs.versions.toml and app/build.gradle.kts.
Android System WebView is provided by the device and has its own provider terms.

## Filter data

A dated baseline of EasyList, EasyPrivacy, EasyList Spanish and uBlock Origin
filter data is bundled under assets/adblock/baseline and updated at runtime.
The EasyList authors (https://easylist.to/), EasyList Spanish contributors and
uBlock Origin contributors retain authorship of these lists. They are distributed
under GPL-3.0 for this snapshot; GPL-3.0.txt contains the license.
The manifest records original source URLs, retrieval time and SHA-256 hashes;
gzip files contain the original UTF-8 source including its attribution headers.
The application's original code remains separately licensed as described above. Redirect resources and scriptlets written for this application are in
app/src/main/assets/adblock and app/src/main/assets/surrogates.

## Release sources

Each published release includes RELEASE_SOURCE.txt, identifying the exact application
source commit, and SIGNING.txt, identifying its APK certificate. SHA256SUMS.txt and
its signature authenticate the release files. Consult the source and notices of the
specific release you distribute.

## TV Bro source attribution

VireoLumaTV originated from source code of TV Bro, copyright (c) 2019 Fedir Tsapana.
TV Bro source: https://github.com/truefedex/tv-bro
The TV Bro source license and its redistribution conditions remain applicable to
any portions of that source retained in this application. The MIT license for
original VireoLumaTV contributions does not supersede those conditions.
Full TV Bro license: https://github.com/truefedex/tv-bro/blob/master/LICENSE.md
