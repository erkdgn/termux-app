#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

export TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=1
./gradlew :app:assembleDebug

APK_DIR="app/build/outputs/apk/debug"
OUT_DIR="build-apk64"
mkdir -p "$OUT_DIR"
cp "$APK_DIR"/termux-app_*_arm64-v8a.apk "$OUT_DIR/"
sha256sum "$OUT_DIR"/*_arm64-v8a.apk
echo "arm64-v8a APK written to $OUT_DIR/"
ls -la "$OUT_DIR"
