#!/usr/bin/env bash
set -eu
package=com.reiniertutoriales.vireolumatv
failed=0
for scenario in baseline upgrade fresh; do
  if [ "$scenario" = baseline ]; then
    adb install -r 2.0.14.apk
  elif [ "$scenario" = upgrade ]; then
    adb install -r 2.0.15.apk
  else
    adb uninstall "$package"
    adb install 2.0.15.apk
  fi
  adb shell am force-stop "$package"
  adb logcat -c
  adb shell am start -W -n "$package/.activity.main.MainActivity" > "startup-report/$scenario-launch.txt"
  sleep 15
  adb logcat -d > "startup-report/$scenario-logcat.txt"
  adb logcat -d -b crash > "startup-report/$scenario-crash.txt"
  adb shell dumpsys activity activities > "startup-report/$scenario-activities.txt"
  if ! adb shell pidof "$package"; then
    cat "startup-report/$scenario-crash.txt"
    failed=1
  fi
  adb shell am force-stop "$package"
done
exit "$failed"
