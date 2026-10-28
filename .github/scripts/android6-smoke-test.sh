#!/usr/bin/env bash
set -euo pipefail

touch "$RUNNER_TEMP/spatialflow-api23-smoke-started"

APK="app/build/outputs/apk/release/app-arm64-v8a-release.apk"
if [ ! -s "$APK" ]; then
  # Use an isolated test key only for the emulator test. It is never published.
  SOURCE_APK="app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk"
  test -s "$SOURCE_APK"
  TEST_KEYSTORE="$RUNNER_TEMP/spatialflow-smoke-test.p12"
  ALIGNED_APK="$RUNNER_TEMP/SpatialFlow-Android6-smoke-aligned.apk"
  APK="$RUNNER_TEMP/SpatialFlow-Android6-smoke.apk"
  keytool -genkeypair -noprompt -keystore "$TEST_KEYSTORE" -storetype PKCS12 \
    -storepass "smoke-test-only" -keypass "smoke-test-only" -alias smoke-test \
    -dname "CN=SpatialFlow Android 6 smoke test" -keyalg RSA -keysize 2048 -validity 2
  "$ANDROID_HOME/build-tools/37.0.0/zipalign" -f -p 4 "$SOURCE_APK" "$ALIGNED_APK"
  "$ANDROID_HOME/build-tools/37.0.0/apksigner" sign \
    --ks "$TEST_KEYSTORE" --ks-type PKCS12 --ks-key-alias smoke-test \
    --ks-pass pass:smoke-test-only --key-pass pass:smoke-test-only \
    --out "$APK" "$ALIGNED_APK"
fi

test -s "$APK"
adb logcat -c
adb install -r "$APK"
adb shell am start -W -n com.codetrio.spatialflow/.MainActivity
sleep 8
RUNTIME_LOG="$(adb logcat -d -s AndroidRuntime:E)"
printf '%s\n' "$RUNTIME_LOG"
if printf '%s' "$RUNTIME_LOG" | grep -q 'FATAL EXCEPTION'; then
  echo "SpatialFlow crashed during startup on Android 6 (API 23)." >&2
  exit 1
fi
if ! adb shell ps | grep -q 'com.codetrio.spatialflow'; then
  echo "SpatialFlow process was not alive after startup on Android 6." >&2
  exit 1
fi
echo "SpatialFlow installed and remained running on Android 6 (API 23)."
