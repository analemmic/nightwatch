# Ambient display offload (low-power chip): research notes

Pixel Watch can hand always-on (ambient) rendering to a low-power microcontroller (MCU), letting the main processor
sleep instead of waking every minute to redraw. Nightwatch's ambient mode runs on the main processor. Status:
**not achievable on current software; re-test after watch system or WFF runtime updates.**

Last tested 2026-11-27 on Pixel Watch 3 (helios), build CP3A.260905.002 (security patch 2026-08-05), WFF runtime
`com.google.wear.watchface.runtime` 335100160. First tested 2026-11-23 on the same versions.

## Findings
1. **Every Watch Face Format layout is rejected by the MCU.** A bare `AnalogClock` with two opaque rectangle hands,
   as a separate app (`com.analemmic.offloadprobe`), passes the runtime's eligibility check and is sent, then the MCU
   answers its first render sync with an error: `Discarding incorrect render sync ack: cmd 7, MCU status 1` ->
   `bad MCU ack` -> `HAL call failed for: setting root as 65536`. Google's stock face
   (`rwf ... AnalogBoldWatchFaceService`) offloads fine at the same moment, even while charging
   (`SendLayout succeeded; Sent 171393 bytes`, `MCU TakeControl`).
2. **The two layouts use the same building blocks**: bitmaps, translation and rotation groups, hands bound to the raw
   metrics `time.hour12.deg` / `time.min.deg`, ids from 0. The stock face adds custom resources (Koru markup,
   arithmetic operations). No structural difference explains the rejection; it is inside Google's firmware/runtime.
3. **The MCU supports a 24-hour hand** (`time.hour24.deg`, `time.hour24` are in `dumpsys DisplayOffloadService`
   bindings), **but WFF can't use it**: the runtime's parser maps `HourHand` to the fixed expression
   `[HOUR_0_23_MINUTE]*30` (12-hour) and `MinuteHand` to `[MINUTE_SECOND]*6`; "hour24" appears nowhere in the runtime.
   Any expression-driven element (our 24-hour hand, moon marker, date) is "[NOT_OFFLOADABLE] Node has a
   timeDataSource".
4. Runtime eligibility rules (strings in the runtime): no time data sources in expressions, no transforms with
   expressions, no rotated parents, no gyro transforms, no localization options; a clock component is required;
   `DigitalClock` digits need a bitmap font with all of 0-9, leading zeros, at most two digits per field.
5. Typical third-party WFF faces (e.g. Radar from Watch Face Studio) don't even pass the eligibility check, so they
   also run ambient on the main processor.
6. Nightwatch's ambient screen cost is already low: about 6.5% of pixels lit, average brightness 1.5% (night) to
   2.7% (day) of full (Google's guideline: at most 15% lit). The potential saving is main-processor wakeups, not pixels.
7. When the watch is locked off-wrist, the system feeds watch faces placeholder data; Nightwatch hides it, so the
   ambient face shows only ticks, digits, hands, and date until unlocked (expected, not a bug).

## If a future update makes WFF offload work
An offloadable Nightwatch ambient would have to: show time as `DigitalClock` digits in a bitmap font (the big HH MM);
hide the hands (no 24-hour hand is possible); and accept that everything else (sky lines, moon marker, tide dots,
date) is baked into one static bitmap, refreshed only when the watch leaves ambient. The owner has said a static
hour hand / moon marker in ambient would be acceptable; hiding the hands is the simpler equivalent.

## How to re-test (about 5 minutes)
1. Scratch project with a throwaway WFF app: one `AnalogClock` with `HourHand`/`MinuteHand` images, package
   `com.analemmic.offloadprobe` (keep it separate from the real face).
2. Install, set it with `adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation
   set-watchface --es watchFaceId com.analemmic.offloadprobe`, wake, then `adb shell input keyevent KEYCODE_SLEEP`.
3. `adb logcat -d | grep -E "DisplayOffloadChecker|NOT_OFFLOADABLE|SendLayout|bad MCU ack|TakeControl"`.
   Success looks like `SendLayout succeeded` followed by `MCU TakeControl`.
4. Control: the stock face `com.google.android.wearable.watchface.rwf/com.google.android.wearable.watchface.analogbold.AnalogBoldWatchFaceService`.
5. Verbose layout dump: `adb shell setprop log.tag.DisplayOffloadManager VERBOSE` (reset with `""` afterwards).

## Measuring the potential saving (not yet done; needs the watch off the charger)
`adb shell cat /sys/class/power_supply/battery/charge_counter` (microamp-hours). Same conditions, 30 minutes of
ambient each: Nightwatch, then the stock face (which offloads). The difference per hour is the most offload could save.
