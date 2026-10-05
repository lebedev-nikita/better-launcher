#!/usr/bin/env bash
# Temporary cold-boot reproducer, independent of the APK and Gradle.
set -euo pipefail
probe_serial=${1:?Disposable emulator serial required}
[[ "$probe_serial" == emulator-* ]] || exit 2
probe_output=app/build/emulator-probe
mkdir -p "$probe_output"
probe_adb() { adb -s "$probe_serial" "$@"; }
probe_capture() {
  local result=$?
  trap - EXIT
  probe_adb logcat -d > "$probe_output/logcat.txt" 2>&1 || true
  probe_adb shell dumpsys activity activities > "$probe_output/activities.txt" 2>&1 || true
  probe_adb shell dumpsys meminfo > "$probe_output/device-memory.txt" 2>&1 || true
  probe_adb shell cat /proc/pressure/cpu /proc/pressure/memory /proc/pressure/io > "$probe_output/device-pressure.txt" 2>&1 || true
  probe_adb exec-out screencap -p > "$probe_output/screen.png" || true
  { nproc; free -h; cat /proc/pressure/cpu /proc/pressure/memory /proc/pressure/io; } > "$probe_output/host.txt"
  exit "$result"
}
trap probe_capture EXIT
cat "${ANDROID_HOME}/system-images/android-36/google_apis/x86_64/source.properties" > "$probe_output/image.txt"
sleep 60
probe_adb shell input keyevent KEYCODE_WAKEUP
probe_adb shell wm dismiss-keyguard
probe_adb shell input keyevent KEYCODE_HOME
for probe_attempt in {1..15}; do
  probe_focus=$(probe_adb shell dumpsys activity activities | sed -n '/mCurrentFocus=/p')
  echo "[BOOT-PROBE] sample $probe_attempt: $probe_focus"
  [[ "$probe_focus" != *'Application Not Responding'* ]] || exit 1
  sleep 2
done
probe_adb logcat -d > "$probe_output/logcat.txt"
if rg -q 'ANR in com.android.systemui' "$probe_output/logcat.txt"; then
  echo '[BOOT-PROBE] System UI timed out during cold boot.' >&2
  exit 1
fi
[[ "$probe_focus" == *com.google.android.apps.nexuslauncher/* ]] || exit 1
echo '[BOOT-PROBE] Cold boot stayed responsive.'
