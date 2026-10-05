#!/usr/bin/env bash
# Temporary cold-boot reproducer, independent of the APK and Gradle.
set -euo pipefail
probe_serial=${1:?Disposable emulator serial required}
probe_boot=${2:-cold}
probe_target=${3:-google_apis}
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
cat "${ANDROID_HOME}/system-images/android-36/$probe_target/x86_64/source.properties" > "$probe_output/image.txt"
probe_adb shell cat /proc/meminfo > "$probe_output/meminfo.txt"
cp "$ANDROID_AVD_HOME/test.avd/config.ini" "$probe_output/avd-config.ini"
sleep 60
if [[ "$probe_boot" == warm ]]; then
  probe_adb logcat -d > "$probe_output/cold-logcat.txt"
  probe_adb shell dumpsys activity activities > "$probe_output/cold-activities.txt"
  probe_adb exec-out screencap -p > "$probe_output/cold-screen.png"
  probe_old_boot=$(probe_adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r')
  probe_adb reboot
  for probe_attempt in {1..150}; do
    probe_new_boot=$(probe_adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r' || true)
    probe_completed=$(probe_adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [[ -n "$probe_new_boot" && "$probe_new_boot" != "$probe_old_boot" && "$probe_completed" == 1 ]]; then
      break
    fi
    sleep 2
  done
  [[ -n "$probe_new_boot" && "$probe_new_boot" != "$probe_old_boot" && "$probe_completed" == 1 ]] || exit 1
  echo '[BOOT-PROBE] New boot ID confirmed; waiting for warm-boot services.'
  sleep 60
fi
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
if grep -q 'ANR in com.android.systemui' "$probe_output/logcat.txt"; then
  echo '[BOOT-PROBE] System UI timed out during cold boot.' >&2
  exit 1
fi
[[ "$probe_focus" == *com.google.android.apps.nexuslauncher/* || "$probe_focus" == *com.android.launcher3/* ]] || exit 1
echo '[BOOT-PROBE] Cold boot stayed responsive.'
