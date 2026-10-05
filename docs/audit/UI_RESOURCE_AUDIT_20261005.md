# UI and resource audit — 2026-10-05

Scope: Android TV tab rows, favorites, branding assets, preview images and UI-thread disk access. Baseline: `0002a92665df6597768c1e792d19638b022763fd` on `fixes/audit`.

## Corrections

- Recycled tab rows explicitly reset checked state. Focus/click actions resolve `bindingAdapterPosition`, reject removed rows and retain focused identity when tabs move. Unsaved tabs with Room ID zero are distinct in DiffUtil. Removed-tab title/favicon callbacks no longer notify index -1; tab menus resolve their index at action time.
- One shared view-bound favicon loader cancels work on rebind/detach and restarts on attach. Results require the same request generation. Home aliases use the brand icon; unavailable site icons use the existing neutral placeholder.
- Favicon database reads, file reads and decoding run on IO. Download/cache images are byte bounded (2 MiB) and sampled to a maximum side of 256 pixels. The memory cache remains 2 MiB. Two concurrent requests and 32 fixed host-lock stripes bound discovery work and deduplicate successful requests for the same host. Cancellation propagates instead of becoming another network attempt.
- New favicon filenames use SHA-256 of the normalized host, with atomic file writes protected by a persistence lock. Existing database filenames remain readable.
- Disk previews are sampled to at most 480 pixels per side and are not retained on background tab objects. After IO, the preview checks overlay visibility, focus identity, closed state and request generation. Closing the overlay invalidates pending work and clears delayed requests.
- Exit persistence captures state immediately and completes its atomic IO write without blocking Activity.onPause. Domain settings load asynchronously at page start; synchronous popup callbacks read only matching in-memory host settings. Until settings are available, the existing default blocks dialogs and automatic new windows. A changed domain cannot inherit the previous site's exemption.
- CheckableContainer avoids refreshing drawable state when its checked value has not changed.

## Branding and compatibility

The existing launcher PNGs have 48/72/96/144/192 pixel density variants. The TV banner is 640 × 360 pixels in xhdpi (320 × 180 dp), with the product name inside the image. Visually inspected the launcher and banner; no distorted or cropped artwork was found. No extra image library, renderer, adaptive-icon mask, or redraw was introduced.

The current one-live-renderer policy is preserved and covered by SingleLiveTabTest. `docs/W2_MEASUREMENT.md` is a historical two-renderer measurement protocol, not the current policy. Do not interpret its old RAM numbers as measurements of this change. `largeHeap` remains until removal is measured on target hardware.

## Verification

Regression coverage: recycled selection; reorder identity and unsaved IDs; cached oversized icons; off-main favicon lookup; cancellation propagation; bounded legacy previews without background bitmap retention; popup policy scoped to the current host. Existing tests cover incognito favicon isolation, renderer lifetime, atomic tab persistence, navigation and homepage security.

CI runs the full generic unit test suite, homepage security checks, a debug APK and a minified release APK. Consult the linked pull request and exact-head Actions run for results.

Required device checks before claiming a measured performance gain: 720p/1080p/4K display, D-pad selection/reordering/closing, favorites scrolling, rapid preview changes, home launcher/banner on the device launcher, background/restore, slow network and low-memory pressure. Record `adb shell dumpsys meminfo com.reiniertutoriales.vireolumatv` for the same pages before/after. No device RAM or frame-time measurements were available in this environment; this audit does not certify zero defects.

## Primary documentation

- https://developer.android.com/topic/performance/graphics/load-bitmap
- https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.Adapter
- https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.ViewHolder
- https://developer.android.com/topic/performance/anrs/find-unresponsive-thread
- https://developer.android.com/topic/libraries/architecture/coroutines
- https://kotlinlang.org/docs/cancellation-and-timeouts.html
- https://developer.android.com/training/tv/get-started/create
