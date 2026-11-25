#!/bin/bash
# Installs the app + tests on the emulator, runs them, and collects results (called by GitHub Actions).
set -x
PKG=com.syedtechdevelopers.imageenhancerpro
mkdir -p devtest
adb install -r -g app-under-test.apk
adb install -r -g tests.apk
adb logcat -c
adb shell am instrument -w -r -e class $PKG.DeviceTests $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee devtest/instrument.txt
adb pull /sdcard/Android/data/$PKG/files/devtest/. devtest/ || true
adb logcat -d -s DeviceTests:I > devtest/timings.txt
adb logcat -d -b crash > devtest/crashes.txt
adb logcat -d '*:E' | grep -i -E "$PKG|onnx|FATAL|AndroidRuntime" | tail -200 > devtest/errors.txt
grep -E "^OK \(|Tests run|FAILURES" devtest/instrument.txt
if grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" devtest/instrument.txt || ! grep -q "^OK (" devtest/instrument.txt; then
  echo "DEVICE TESTS FAILED" > devtest/STATUS.txt; exit 1
fi
echo "DEVICE TESTS PASSED" > devtest/STATUS.txt
