#!/bin/bash
set -ex

echo "=== ADB devices ==="
adb devices -l

echo "=== Finding APKs ==="
find . -name "*.apk" -type f || true
find /home/runner -name "*.apk" -type f 2>/dev/null || true

APP_APK=$(find . -name "app-debug.apk" -not -name "*androidTest*" -type f | head -1)
TEST_APK=$(find . -name "app-debug-androidTest.apk" -type f | head -1)

echo "App APK: $APP_APK"
echo "Test APK: $TEST_APK"

if [ -z "$APP_APK" ] || [ -z "$TEST_APK" ]; then
  echo "ERROR: APKs not found!"
  exit 1
fi

echo "=== Installing app APK ==="
adb install -t "$APP_APK"
echo "=== Installing test APK ==="
adb install -t "$TEST_APK"
echo "=== Listing packages ==="
adb shell pm list packages | grep dnssd || echo "No dnssd packages found"

echo "=== Running nsdRegistrationWorks ==="
adb shell am instrument -w \
  -e class expo.modules.dnssd.test.NsdDiscoveryTest#nsdRegistrationWorks \
  expo.modules.dnssd.test.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee test-output.txt

echo "=== Running nsdDiscoveryStarts ==="
adb shell am instrument -w \
  -e class expo.modules.dnssd.test.NsdDiscoveryTest#nsdDiscoveryStarts \
  expo.modules.dnssd.test.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee -a test-output.txt

echo "=== Done ==="
cat test-output.txt
