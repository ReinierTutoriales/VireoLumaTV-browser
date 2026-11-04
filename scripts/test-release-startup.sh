#!/usr/bin/env bash
# Only for disposable, rootable CI emulators; never run against a user's device.
set -Eeuxo pipefail
candidate=$1
baseline=$2
package=com.reiniertutoriales.vireolumatv
report=startup-report
app_pid=
mkdir -p "$report"
adb root
adb wait-for-device
adb shell getprop ro.build.version.sdk > "$report/android-api.txt"
adb shell dumpsys webviewupdate > "$report/webview-provider.txt"
capture() {
  # Bounded: if the emulator drops, adb waits for a device forever and the job only ends at the timeout.
  timeout 20s adb logcat -d > "$report/$scenario-logcat.txt" || true
  if [ -n "$app_pid" ]; then
    timeout 20s adb logcat -d -b crash --pid="$app_pid" > "$report/$scenario-crash.txt" || true
  else
    timeout 20s adb logcat -d -b crash > "$report/$scenario-crash.txt" || true
  fi
  timeout 20s adb shell dumpsys activity activities > "$report/$scenario-activities.txt" || true
  timeout 20s adb exec-out screencap -p > "$report/$scenario.png" || true
}
trap 'capture' ERR
# The FontsProvider connection appears when Android System WebView loads in the app process, with
# Google Sans / Noto Color Emoji Compat requests that match Chromium's AndroidFontLookupImpl; the
# app has no font or EmojiCompat initializer. When Play Services restarts, Android kills every
# client of that provider. The browser declares no Play Services dependency, so that
# kill is accepted only with this exact reason, and the app must then start again with its data.
external_font_kill() {
  adb logcat -d | grep -q "Killing ${app_pid}:${package}/.*depends on provider com.google.android.gms/.fonts.provider.FontsProvider"
}
launch() {
  if attempt_launch; then return 0; fi
  if [ -n "$app_pid" ] && external_font_kill; then
    echo "Play Services restart killed the WebView font client in $scenario; relaunching once"
    cp "$report/$scenario-logcat.txt" "$report/$scenario-provider-kill-logcat.txt" 2>/dev/null || true
    # A Play Services restart already happened: in the cold scenario it counts as the stop check.
    [ "$scenario" = cold ] && gms_checked=1
    attempt_launch
    return
  fi
  return 1
}
gms_checked=0
attempt_launch() {
  app_pid=
  adb shell am force-stop "$package"
  adb logcat -c
  timeout 30s adb shell am start -W -n "$package/.activity.main.MainActivity" -d https://vireolumatv.invalid/appcontent/home/ > "$report/$scenario-launch.txt" || true
  app_pid=$(adb shell pidof "$package" | tr -d '\r') || { capture; return 1; }
  local notice_dismissed=0
  for attempt in $(seq 1 12); do
    sleep 3
    if ! adb shell pidof "$package" >/dev/null; then
      capture
      cat "$report/$scenario-crash.txt"
      return 1
    fi
    # The platform dumper can crash during shutdown after writing a valid hierarchy.
    # Require a fresh parsed dump and the original app process, not its exit status.
    adb shell rm -f /sdcard/startup-window.xml
    timeout 20s adb shell uiautomator dump /sdcard/startup-window.xml > "$report/$scenario-uiautomator.txt" 2>&1 || echo "UI dumper exited: $?"
    if ! adb pull /sdcard/startup-window.xml "$report/$scenario-window.xml"; then continue; fi
    # Old emulator images show the app's existing first-run provider notice.
    notice=$(python - "$report/$scenario-window.xml" <<'PYNOTICE'
import re, sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).getroot().iter('node'))
if any(n.get('text', '').startswith('The page engine (Android System WebView) has not been updated') for n in nodes):
    for n in nodes:
        if n.get('resource-id') == 'android:id/button2' and n.get('text') == 'OK' and n.get('package') == 'com.reiniertutoriales.vireolumatv':
            m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds', ''))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print((x1 + x2) // 2, (y1 + y2) // 2)
PYNOTICE
    )
    if [ -n "$notice" ] && [ "$notice_dismissed" = 0 ]; then
      adb exec-out screencap -p > "$report/$scenario-provider-notice.png"
      read -r tap_x tap_y <<< "$notice"
      adb shell input tap "$tap_x" "$tap_y"
      notice_dismissed=1
      continue
    fi
    if python - "$report/$scenario-window.xml" <<'PYXML'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
assert any(node.get('text') == 'DuckDuckGo' for node in root.iter('node'))
# Native Home must finish its existing origin-local storage read before editing is available.
assert any(node.get('resource-id', '').endswith(':id/home_search') for node in root.iter('node'))
assert any(node.get('text') == 'Add shortcut' and node.get('enabled') == 'true' for node in root.iter('node'))
assert not any(node.get('class') == 'android.webkit.WebView' for node in root.iter('node'))
PYXML
    then
      test "$(adb shell pidof "$package" | tr -d '\r')" = "$app_pid" || { capture; return 1; }
      if [ "$scenario" = cold ]; then
        python scripts/test-native-home-navigation.py "$report" "$package" "$app_pid" || { capture; return 1; }
        adb shell dumpsys activity providers > "$report/cold-providers-before-stop.txt"
        adb shell dumpsys activity processes > "$report/cold-processes-before-stop.txt"
        if [ "$gms_checked" = 0 ]; then
          # A Play Services restart may only end the app through WebView's font client (see above);
          # launch() then requires a clean restart with the home page and data.
          gms_checked=1
          adb shell am force-stop com.google.android.gms
          sleep 3
          test "$(adb shell pidof "$package" | tr -d '\r')" = "$app_pid" || { capture; return 1; }
        fi
        python scripts/capture-webview-memory.py "$report" "$package" "$app_pid" normal-home
        if [ "$(cat "$report/android-api.txt" | tr -d '\r')" -ge 28 ]; then
          python scripts/test-private-startup.py "$report" "$package" "$app_pid" || { capture; return 1; }
          private_pid=$(adb shell pidof "$package:incognito" | tr -d '\r')
          python scripts/capture-webview-memory.py "$report" "$package" "$private_pid" private-home
        else
          echo "Private browsing intentionally unavailable before API 28" > "$report/private-mode-unsupported.txt"
        fi
      fi
      capture
      if grep -q 'FATAL EXCEPTION\|Fatal signal' "$report/$scenario-crash.txt"; then return 1; fi
      return 0
    fi
  done
  capture
  echo "Home page did not render in $scenario"
  return 1
}
# The old APK can initialize storage without rendering the broken home template.
scenario=baseline
adb install --no-incremental "$baseline"
adb shell am start -W -n "$package/.activity.main.MainActivity" -d https://startup.invalid/ > "$report/baseline-launch.txt" || true
sleep 10
adb shell am force-stop "$package"
adb pull "/data/data/$package/databases" "$report/before" >/dev/null
# Seed real schema-20 data left by the published version, including WAL if present.
python - "$report/before/main.db" <<'PY'
import sqlite3, sys
with sqlite3.connect(sys.argv[1]) as db:
    db.execute("INSERT INTO bookmarks (id,title,address) VALUES (900001,'Startup sentinel','https://startup.invalid/saved')")
    db.commit()
    db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
PY
adb shell rm -f "/data/data/$package/databases/main.db-wal" "/data/data/$package/databases/main.db-shm"
database_uid=$(adb shell stat -c %u "/data/data/$package/databases/main.db" | tr -d '\r')
adb push "$report/before/main.db" "/data/data/$package/databases/main.db" >/dev/null
adb shell chown "$database_uid:$database_uid" "/data/data/$package/databases/main.db"
scenario=upgrade
adb install --no-incremental -r "$candidate"
launch
adb shell am force-stop "$package"
adb pull "/data/data/$package/databases" "$report/after" >/dev/null
python - "$report/after/main.db" <<'PY'
import sqlite3, sys
with sqlite3.connect(sys.argv[1]) as db:
    assert db.execute('PRAGMA user_version').fetchone() == (20,)
    assert db.execute('SELECT title,address FROM bookmarks WHERE id=900001').fetchone() == ('Startup sentinel','https://startup.invalid/saved')
PY
scenario=fresh
adb uninstall "$package"
adb install --no-incremental "$candidate"
launch
scenario=cold
launch
