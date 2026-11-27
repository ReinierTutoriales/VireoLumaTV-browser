# VireoLumaTV — component notices

Copyright (c) 2026 ReinierTutoriales.

The MIT license in LICENSE.md covers the original VireoLumaTV application code.
Separately licensed components retain their own notices and terms; the application
license does not relicense those components.

## Native blocking engine and JNI bridge

adblock-rust 0.13.6, by Brave Software, and the bridge in native/adblock-jni
are licensed under MPL 2.0. The bridge builds libvireoadblock.so.

- Source: https://crates.io/crates/adblock/0.13.6
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

## Interface symbols

The interface uses Material Symbols Rounded by Google, under Apache 2.0.
SVG paths are converted to native Android VectorDrawable resources in
`app/src/main/res/drawable/ic_*.xml`; each file names its source symbol.
Native Home uses the same drawable resources; no icon font is loaded.

- Source: https://github.com/google/material-design-icons/tree/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web
- Snapshot commit: `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7`
- License: licenses/Apache-2.0.txt

The launcher bird and banner remain the application's separate artwork.

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

## YouTube cast receiver protocol

The YouTube and YouTube Music cast receiver (`LoungeBindParams.kt`, `LoungeMessage.kt`,
`LoungeSession.kt`, `YouTubeCastReceiver.kt` and the DIAL responses in `CastDialServer.kt`,
under `app/src/main/java/com/reiniertutoriales/vireolumatv/activity/main/cast/`) is a Kotlin
port of the protocol handling in yt-cast-receiver by Patrick Kan, and of the DIAL behavior of
its peer-dial dependency by Louay Bassbouss (Fraunhofer FOKUS).

- Source: https://github.com/patrickkfkan/yt-cast-receiver (commit `83d61fa`, version 2.1.1)
- License: MIT (declared in its package.json; the repository has no separate license file).
  peer-dial is LGPL-3.0; only its observable DIAL responses are reproduced, not its code.

```text
Copyright (c) Patrick Kan

Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the Software
without restriction, including without limitation the rights to use, copy, modify, merge,
publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.
```

## Agent writing skill

`.agents/skills/no-ai-slop/` is an unmodified copy of the no-ai-slop skill by
Peter Yang, used by contributors' agents to edit prose. It is not part of the app.

- Source: https://github.com/petergyang/no-ai-slop/tree/000650b156983f5159695b441477f4e63b25dc85/skills/no-ai-slop
- Snapshot commit: `000650b156983f5159695b441477f4e63b25dc85`
- License: MIT, in `.agents/skills/no-ai-slop/LICENSE`

## Release sources

Each published release includes RELEASE_SOURCE.txt, identifying the exact application
source commit, and SIGNING.txt, identifying its APK certificate. SHA256SUMS.txt and
its signature authenticate the release files. Consult the source and notices of the
specific release you distribute.
