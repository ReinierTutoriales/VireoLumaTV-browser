#!/usr/bin/env bash
# Old command-line tools can silently create android-0 metadata for API 37.
set -euo pipefail
if [ "$1" = '37.0' ]; then
  grep -Fx 'target=android-37.0' "$ANDROID_AVD_HOME/test.ini"
fi
