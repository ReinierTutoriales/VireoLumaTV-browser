#!/usr/bin/env bash
# Repeatable measurements for physical Android TV (not synthetic performance claims).
set -euo pipefail
ADB="${ADB:-adb}"
PKG="${1:-com.reiniertutoriales.vireolumatv}"
OUT="${2:-tv-baseline}"
mkdir -p "$OUT"
"$ADB" get-state >/dev/null
"$ADB" shell getprop ro.build.version.release | tr -d '\r' > "$OUT/android-version.txt"
"$ADB" shell getprop ro.product.model | tr -d '\r' > "$OUT/device-model.txt"
"$ADB" shell dumpsys webviewupdate > "$OUT/webview-provider.txt"
"$ADB" shell am force-stop "$PKG"
# am start -W reports displayed/total launch time (cold process start, not page load).
"$ADB" shell am start -W -n "$PKG/.activity.main.MainActivity" > "$OUT/launch.txt"
sleep 5
"$ADB" shell dumpsys meminfo "$PKG" > "$OUT/memory-5s.txt"
"$ADB" shell dumpsys gfxinfo "$PKG" framestats > "$OUT/frames-5s.txt"
echo "Cold launch, process memory and frame stats: $OUT"
echo "For reproducibility, open the same page/video with the same Wi-Fi signal and run again."
echo "This script does not claim per-request network latency or video FPS."
