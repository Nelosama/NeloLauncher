#!/usr/bin/env bash
# Read-only observations except starting/stopping Nelo and resetting its gfx counters.
# Leaves every other package and the default HOME selection untouched.
set -euo pipefail
out="${1:-measurements}"
mkdir -p "$out"
adb get-state
adb shell getprop ro.product.model > "$out/model.txt"
adb shell getprop ro.build.version.release > "$out/android.txt"
adb shell wm size > "$out/display.txt"
adb shell dumpsys package tv.nelo.launcher > "$out/package.txt"
for sample in 1 2 3 4 5; do
  adb shell am force-stop tv.nelo.launcher
  adb shell am start -W -n tv.nelo.launcher/.MainActivity > "$out/cold-$sample.txt"
  adb shell sleep 2
done
adb shell sleep 30
adb shell dumpsys meminfo tv.nelo.launcher > "$out/meminfo-before-navigation.txt"
adb shell dumpsys gfxinfo tv.nelo.launcher reset > "$out/gfx-reset.txt"
printf 'Navega con el mando durante 60 segundos; abre y cierra aplicaciones. Pulsa Enter al terminar.\n'
read -r
adb shell dumpsys gfxinfo tv.nelo.launcher framestats > "$out/framestats.txt"
adb shell dumpsys meminfo tv.nelo.launcher > "$out/meminfo-after-navigation.txt"
adb logcat -d -s NeloStartup:I AndroidRuntime:E > "$out/startup-crash-log.txt"
printf 'Resultados guardados en %s\n' "$out"
