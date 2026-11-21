#!/usr/bin/env bash
# Drives the debug app on an emulator and saves screenshots to ./shots. Used by .github/workflows/preview.yml.
set -u
PKG=com.garasi.app
OUT=shots
mkdir -p "$OUT"
n=0

shot() { n=$((n+1)); sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$(printf %02d $n)-$1.png"; echo "shot $1"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }
swipe_up() { adb shell input swipe 540 1900 540 700 400; sleep 1; }
swipe_down() { adb shell input swipe 540 700 540 1900 400; sleep 1; }

# Finds a UI node whose text or content-desc matches a regex; prints "x y" of its centre.
find_node() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > /tmp/ui.xml 2>/dev/null
  python3 - "$1" <<'PY'
import re, sys
pat = re.compile(sys.argv[1])
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for n in re.findall(r'<node [^>]*>', xml):
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1]
    d = (re.search(r'content-desc="([^"]*)"', n) or [None, ''])[1]
    if pat.search(t) or pat.search(d):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        if y2 > y1 and x2 > x1:
            print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}

# Taps the first node matching the regex, scrolling down up to N times to find it.
tap() {
  for i in $(seq 0 "${2:-0}"); do
    xy=$(find_node "$1")
    if [ -n "$xy" ]; then adb shell input tap $xy; sleep 2; return 0; fi
    swipe_up
  done
  echo "not found: $1"; return 1
}

open_app() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; }

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell pm grant $PKG android.permission.CAMERA
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true

open_app
adb shell am broadcast -n $PKG/.debug.DemoSeeder
sleep 25
open_app
shot home

tap "Sony WH-1000XM4" && {
  shot edit-photos
  swipe_up; shot edit-details
  swipe_up; swipe_up; shot edit-text
  swipe_up; shot edit-ai
  swipe_up; swipe_up; shot edit-price
  tap "^Score" && { shot quality; swipe_up; shot quality-more; back; }
  tap "^Post$" && { shot post; swipe_up; shot post-where; back; }
  swipe_down; swipe_down; swipe_down; swipe_down; swipe_down; swipe_down
  tap "Reorder, crop and tag photos" && { shot photos; back; }
  tap "^Price check$" 8 && { shot price; swipe_up; shot price-suggestion; back; }
  swipe_down; swipe_down; swipe_down; swipe_down; swipe_down; swipe_down
  tap "^Camera$" && { shot camera 4; back; }
  back
}

tap "^Stats$" && { shot stats; back; }
tap "^Settings$" && { shot settings; back; }

# Floating checklist over the marketplace (website, since the app isn't installed on the emulator)
tap "Sony WH-1000XM4" && tap "^Post$" && tap "^Copy & open" 3 && { shot overlay-checklist 6; }
adb shell am force-stop $PKG

# Dark mode
adb shell cmd uimode night yes
open_app
shot home-dark
tap "Sony WH-1000XM4" && { swipe_up; swipe_up; swipe_up; shot edit-dark; tap "^Score" && shot quality-dark; }
adb shell cmd uimode night no
ls -la "$OUT"
