#!/usr/bin/env bash
# One command for every automated test that runs without a phone.
#   scripts/run_all_tests.sh            everything available on this machine
#   scripts/run_all_tests.sh --quick    skip the slow golden-image pass (about 11 minutes)
#   scripts/run_all_tests.sh --device   also run the instrumented UI tests on a connected emulator / phone (adb)
# Each stage writes into reports/; the summary is reports/test_summary.txt.
# A stage that cannot run here (no Android SDK, no adb device) is reported as SKIPPED with the
# reason - it is never counted as passed. Exit code 1 when any stage failed.
set -u
cd "$(dirname "$0")/.."
QUICK=0; DEVICE=0
for a in "$@"; do case "$a" in --quick) QUICK=1;; --device) DEVICE=1;; esac; done
mkdir -p reports/logs
SUMMARY=reports/test_summary.txt
echo "Hikari automated tests  $(date -u +%Y-%m-%dT%H:%MZ)  commit $(git rev-parse --short HEAD 2>/dev/null)" > "$SUMMARY"
FAILED=0
stage() { # name, command...
  local name="$1"; shift
  local log="reports/logs/${name// /_}.log" t0=$SECONDS
  if "$@" > "$log" 2>&1; then echo "PASS     $name  ($((SECONDS - t0)) s)" | tee -a "$SUMMARY"
  else echo "FAIL     $name  ($((SECONDS - t0)) s)  see $log" | tee -a "$SUMMARY"; FAILED=1; fi
}
skip() { echo "SKIPPED  $1  - $2" | tee -a "$SUMMARY"; }

# 1. colour science of the looks (python, synthetic patches: sky, foliage, skin, whites, clean LUTs)
stage "look checks (daylight collection)" python3 tools/looks/daylight_lab.py --checks-only
# 2. composition geometry, scene analysis, recommender (pure Kotlin, JVM)
stage "vision unit tests" gradle -p vision test --rerun-tasks -q
[ -f vision/build/reports/vision_metrics.txt ] && cp vision/build/reports/vision_metrics.txt reports/vision_metrics.txt
# 2b. timestamp + frame layout, category browsing, rendered output on the fixed photos (pure Kotlin, JVM)
stage "studio tests (timestamps, frames, categories)" gradle -p studio test --rerun-tasks -q
[ -d studio/build/reports/frames ] && mkdir -p reports/frames && cp studio/build/reports/frames/*.jpg reports/frames/ && cp studio/build/reports/studio_perf.txt reports/
# 3. the shipped shaders in headless Chromium: resolution independence, grain, banding, tiled export
TMP=$(mktemp -d)
python3 - "$TMP" <<'PY'
import sys
from PIL import Image
Image.open("app/src/main/assets/samples/04_city.jpg").convert("RGB").save(sys.argv[1] + "/city.png")
PY
stage "engine shader tests" python3 tools/engine/engine_tests.py --photo "$TMP/city.png" --tmp "$TMP"
# 4. golden images + image quality for every look on every standard photo
if [ $QUICK = 1 ]; then skip "golden images + quality" "--quick given"
else stage "golden images + quality" python3 tools/golden/golden.py; fi
rm -rf "$TMP"
# 5. Android unit tests and build (needs the Android SDK)
if [ -n "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] || [ -f local.properties ]; then
  stage "android unit tests + release build" ./gradlew testDebugUnitTest assembleRelease
else skip "android unit tests + release build" "no Android SDK on this machine (runs on CI: .github/workflows/build.yml)"; fi
# 6. instrumented UI tests on an emulator or phone
if [ $DEVICE = 1 ]; then
  if command -v adb >/dev/null && [ -n "$(adb devices | sed 1d | grep -w device)" ]; then
    KIND=$(adb shell getprop ro.kernel.qemu | tr -d '\r'); [ "$KIND" = 1 ] && KIND="EMULATOR" || KIND="PHYSICAL DEVICE $(adb shell getprop ro.product.model | tr -d '\r')"
    stage "instrumented UI tests on $KIND" ./gradlew connectedDebugAndroidTest
    mkdir -p reports/device && adb pull /sdcard/Download/HikariLab reports/device/ >/dev/null 2>&1
  else skip "instrumented UI tests" "no adb device attached"; fi
else skip "instrumented UI tests" "--device not given (they run on the CI emulator)"; fi

echo >> "$SUMMARY"
[ $FAILED = 0 ] && echo "RESULT: all stages that ran passed" | tee -a "$SUMMARY" || echo "RESULT: FAILED" | tee -a "$SUMMARY"
exit $FAILED
