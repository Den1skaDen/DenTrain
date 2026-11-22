#!/usr/bin/env bash
set -euo pipefail

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$(adb shell am instrument -w -r com.dentrain.test/com.dentrain.data.DiaryTestRunner)
printf '%s\n' "$result"
grep -q 'PASS: Room migration and diary synchronization' <<< "$result"
grep -q 'PASS: offline coaching delivery' <<< "$result"
grep -q 'INSTRUMENTATION_CODE: -1' <<< "$result"
