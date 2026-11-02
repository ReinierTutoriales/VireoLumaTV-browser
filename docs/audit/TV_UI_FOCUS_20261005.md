# Android TV focus and overlay audit — 2026-10-05

Sources: Android TV [navigation](https://developer.android.com/training/tv/get-started/navigation),
[focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), and
[ViewPropertyAnimator](https://developer.android.com/reference/android/view/ViewPropertyAnimator).

## Findings and changes

- The cursor menu invoked link actions, direct navigation and zoom when a D-pad direction merely moved focus. Those actions now require Select/OK; all five buttons retain focus while moving between them. The center button has a visible focused background and labels were added for accessibility.
- A 500 ms menu animation updated four button positions every frame. The menu now animates only opacity and scale for 180 ms. Its small child is clamped to screen bounds without shifting the full-screen input layer. A pending show callback checks that it still belongs to the current menu.
- Overlapping menu show/hide animations could apply a stale end action after a quick toggle. The previous animations are canceled before changing direction; the already visible menu is not reopened. Durations are 220 ms.
- Popup notifications retained the last Activity view in a process-wide reference and could leave old delayed callbacks after replacement. Notifications now cancel pending work when removed, hold only a weak reference, and animate for 180 ms.
- The address bar's delayed Select All could run after focus left. It now checks focus and removes pending work on blur or detach. The ineffective delayed layout transition was removed.

## Scope and validation

`git diff --check` passes. The Android SDK is unavailable in this workspace; use repository CI for Kotlin compilation and unit tests. Manual TV checks still needed: D-pad focus versus OK on each cursor menu button; repeated open/close and Back; menu position at the four screen corners; notifications arriving rapidly; address bar focus after the keyboard opens; real video playback while opening menus on a 2 GB device.
