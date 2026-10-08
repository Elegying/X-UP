#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
./gradlew assembleRelease --console=plain
cp build/outputs/apk/release/X-UP-release.apk ../X-UP-1.8.0-beta4.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose ../X-UP-1.8.0-beta4.apk
