#!/usr/bin/env bash
# Only for disposable, rootable CI emulators; never run against a user's device.
set -euxo pipefail
candidate=$1
baseline=$2
package=com.reiniertutoriales.vireolumatv
report=startup-report
mkdir -p "$report"
adb root
adb wait-for-device
capture() {
  adb logcat -d > "$report/$scenario-logcat.txt"
  adb logcat -d -b crash > "$report/$scenario-crash.txt"
  adb shell dumpsys activity activities > "$report/$scenario-activities.txt"
  adb exec-out screencap -p > "$report/$scenario.png"
}
trap 'capture' ERR
launch() {
  adb shell am force-stop "$package"
  adb logcat -c
  timeout 30s adb shell am start -W -n "$package/.activity.main.MainActivity" -d https://vireolumatv.invalid/appcontent/home/ > "$report/$scenario-launch.txt" || true
  for attempt in $(seq 1 12); do
    sleep 3
    if ! adb shell pidof "$package" >/dev/null; then
      capture
      cat "$report/$scenario-crash.txt"
      return 1
    fi
    timeout 20s adb shell uiautomator dump /sdcard/startup-window.xml > "$report/$scenario-uiautomator.txt" 2>&1 || { capture; cat "$report/$scenario-uiautomator.txt"; return 1; }
    adb pull /sdcard/startup-window.xml "$report/$scenario-window.xml" >/dev/null
    if grep -q 'text="DuckDuckGo"' "$report/$scenario-window.xml"; then
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
