#!/usr/bin/env bash
# Only use on a disposable emulator: full mode clears Better Launcher data.
set -euo pipefail

ci_device_serial=${1:?Usage: ci-device-tests.sh emulator-SERIAL [instrumentation|full]}
ci_device_suite=${2:-instrumentation}
case "$ci_device_serial" in
  emulator-*) ;;
  *) echo 'A disposable Android emulator is required.' >&2; exit 2 ;;
esac
case "$ci_device_suite" in
  instrumentation|full) ;;
  *) echo "Unknown suite: $ci_device_suite" >&2; exit 2 ;;
esac

ci_output=app/build/ci-device
ci_apk=app/build/outputs/apk/debug/app-debug.apk
ci_stage=bootstrap
mkdir -p "$ci_output"

device_adb() { adb -s "$ci_device_serial" "$@"; }
capture() {
  device_adb logcat -d > "$ci_output/$ci_stage-logcat.txt" 2>&1 || true
  device_adb shell dumpsys activity activities > "$ci_output/$ci_stage-activities.txt" 2>&1 || true
  device_adb shell dumpsys window windows > "$ci_output/$ci_stage-windows.txt" 2>&1 || true
  device_adb exec-out screencap -p > "$ci_output/$ci_stage.png" || true
  device_adb shell uiautomator dump /data/local/tmp/better-ci.xml >/dev/null 2>&1 || true
  device_adb shell cat /data/local/tmp/better-ci.xml > "$ci_output/$ci_stage-ui.xml" 2>/dev/null || true
}

ci_font_scale=$(device_adb shell settings get system font_scale | tr -d '\r')
ci_auto_rotation=$(device_adb shell settings get system accelerometer_rotation | tr -d '\r')
ci_rotation=$(device_adb shell settings get system user_rotation | tr -d '\r')
ci_override_size=$(device_adb shell wm size | sed -n 's/Override size: //p' | tr -d '\r')
restore_setting() {
  if [[ "$2" == null ]]; then
    device_adb shell settings delete system "$1"
  else
    device_adb shell settings put system "$1" "$2"
  fi
}
cleanup() {
  local result=$?
  trap - EXIT
  capture
  device_adb shell wm size "${ci_override_size:-reset}" || true
  restore_setting font_scale "$ci_font_scale" || true
  restore_setting accelerometer_rotation "$ci_auto_rotation" || true
  restore_setting user_rotation "$ci_rotation" || true
  # connected tests uninstall the app, including on some failure paths.
  device_adb install -r "$ci_apk" || true
  device_adb shell cmd package set-home-activity app.better.launcher/.MainActivity || true
  device_adb shell input keyevent KEYCODE_HOME || true
  exit "$result"
}
trap cleanup EXIT

# Let first-boot services finish before installing and instrumenting the launcher.
echo 'Waiting for first-boot services to settle (60 seconds).'
sleep 60

install_home() {
  device_adb install -r "$ci_apk"
  device_adb shell cmd package set-home-activity app.better.launcher/.MainActivity
  device_adb shell input keyevent KEYCODE_WAKEUP
  device_adb shell wm dismiss-keyguard
  device_adb shell input keyevent KEYCODE_HOME
  device_adb shell am start -W -n app.better.launcher/.MainActivity
}

install_home
for ci_attempt in {1..30}; do
  ci_focus=$(device_adb shell dumpsys activity activities | sed -n '/mCurrentFocus=/p')
  if [[ "$ci_focus" == *'Application Not Responding'* ]]; then
    echo "Emulator system is unresponsive before tests: $ci_focus" >&2
    exit 1
  fi
  if [[ "$ci_focus" == *app.better.launcher/* ]]; then
    break
  fi
  echo "Waiting for launcher focus ($ci_attempt/30): $ci_focus"
  sleep 2
done
if [[ "$ci_focus" != *app.better.launcher/* ]]; then
  echo "Launcher did not receive focus before tests: $ci_focus" >&2
  exit 1
fi
capture
ci_stage=instrumentation
device_adb logcat -c
ANDROID_SERIAL="$ci_device_serial" ./gradlew :app:connectedDebugAndroidTest --no-daemon '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --stacktrace
capture
# Always reinstall after connected tests before any further UI checks.
install_home

if [[ "$ci_device_suite" == full ]]; then
  for ci_stage in portrait narrow-font-1.3 landscape; do
    device_adb shell am force-stop app.better.launcher
    device_adb shell wm size reset
    device_adb shell settings put system font_scale 1.0
    device_adb shell settings put system accelerometer_rotation 0
    device_adb shell settings put system user_rotation 0
    case "$ci_stage" in
      narrow-font-1.3)
        # Compute pixels for a 320 x 693 dp window at the emulator's density.
        ci_density=$(device_adb shell wm density | tr -d '\r' | tail -n 1 | awk '{print $NF}')
        device_adb shell wm size "$((ci_density * 320 / 160))x$((ci_density * 693 / 160))"
        device_adb shell settings put system font_scale 1.3
        ;;
      landscape) device_adb shell settings put system user_rotation 1 ;;
    esac
    # Fresh preferences seed real Camera/Phone favorites and the right alphabet.
    device_adb shell pm clear app.better.launcher
    device_adb shell cmd package set-home-activity app.better.launcher/.MainActivity
    device_adb logcat -c
    python3 scripts/smoke-test.py --serial "$ci_device_serial" 2>&1 | tee "$ci_output/$ci_stage-smoke.txt"
    capture
  done
fi
