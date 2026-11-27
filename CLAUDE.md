# Nightwatch: Wear OS 24-hour sun, moon, and tide watch face + companion app

Published as **Analemmic** (pen name: no real name anywhere in the repo, builds, or pushed history). MIT; free Google
Play release planned (RELEASE.md). Read DECISIONS.md before changing the design: the owner settled it deliberately.
The face is feature-complete; new detail goes in the day table.

## Layout
- `face/`: Watch Face Format, no code. `face/tools/gen_face.py` generates `res/raw/watchface.xml`, the sprites, and
  the companion's `FaceSpec.kt` (geometry, palettes, codec). It is the source of truth for every size and color.
  Never edit generated files. Smoke test: `python3 face/tools/test_gen_face.py`.
- `astro/`: companion app (`com.analemmic.nightwatch.astro`).
  - `Astro.kt` sky snapshot; `Tides.kt` harmonic tides (TICON-4: `tools/build_tides.py` -> `res/raw/tide_stations.bin`,
    then rebuild datums: `NW_BUILD_DATUMS=1 ./gradlew :astro:testDebugUnitTest --tests '*BuildTideDatums*'`, ~3 min;
    TideDatumsTest fails if stale); `Seasons.kt`.
  - `Sources.kt` 7 complication sources + snapshot cache; `SlotCodec.kt` wire format.
  - `Sky.kt` location/time (or debug simulation), gauge choice; `TzCenters.kt` time-zone backup location
    (`tools/build_tz.py` -> `res/raw/tz_centers.txt`).
  - `ScrubberActivity`/`ScrubberView` day scrubber; `TableActivity`/`DayTable` day table; `SetupActivity` location
    disclosure + permission, Continue/Done back to its opener, Location settings, licenses; `BootReceiver` refresh on boot/update/locale/time zone; `Palettes.kt` palette setting.
  - Tests: `./gradlew :astro:testDebugUnitTest` (reference implementations in `src/test/.../Reference.kt`).
- `docs/`: website (GitHub Pages, main /docs, https://analemmic.com/nightwatch/). `privacy.html` mirrors PRIVACY.md;
  `license.html` mirrors licenses/NOTICE.txt. Screenshots use a simulated location (never the owner's) with the
  notification dot painted out.
- User-facing text: plain, short, Oxford comma. Don't mention the equinox/solstice line anywhere public.

## Data path (face <- companion)
The companion publishes 7 RANGED_VALUE complications; the face decodes each into `<Reference>`s once.

| slot | source | min (-1000 - lo) | value | max (1000 + hi) |
|---|---|---|---|---|
| 1 | SunSource | sunrise edge | solar noon | sunset edge |
| 2-4 | Civil/Nautical/AstroSource | dawn edge | 0 | dusk edge |
| 5 | MoonSource | moonrise edge | transit | moonset edge |
| 6 | TideSource | t0 * 1440 + t1 | count * 2 + first-is-high | t2 * 1440 + t3 |
| 7 | ExtrasSource | t4 + 1440 * date-is-day-first | equinox/solstice | moon age * 100 |

Edge = round((dial angle + 180) * 4) + 3000 for a real event (else a noon-window boundary); 6500 = none. Tide times
are minutes after local midnight. Anything not shaped like this decodes as ABSENT (-999); the face hides values
below -500. Slot 8 is free.
- Slots bind by POSITION and the runtime remembers: after changing SLOTS or renaming a source,
  `adb uninstall com.analemmic.nightwatch.face` and install fresh. Inspect:
  `adb shell dumpsys activity service com.google.wear.watchface.runtime`.
- Sources update every 900 s; `Refresh.all` re-requests on boot, update, locale/time zone change, or a move.
- Location: recent fix, else saved, else the time zone's city (also when the saved fix is > 1000 km from the zone's
  city and its own nearest city has a different UTC offset). Tides follow it.

## Dial
Angles are clock positions: local time T sits at 15 * (T - 12) degrees, noon at the top. Canvas 450, R 225.
- Palettes NIGHT/DAY from the Palette setting (`[CONFIGURATION.palette]`); Auto = day from sunrise to sunset (no
  data = night). Ambient = same palette pre-dimmed, never alpha. Each element is built per palette in two masked
  groups; the editor's Auto preview (frozen clock at PREVIEW_TIME) shows night left, day right.
- Center tap launches activity alias ScrubberAuto/Day/Night: that's how the scrubber learns the setting.
- Sky fills (interactive only), sunrise/sunset lines with HHMM labels inside the disc, solar noon line broken around
  the curved date at 12, 24 ticks, center disc with HH over MM, moon arc with phase sprite at transit, tide dots,
  24-hour hour hand and a minute hand.
- Fonts: JetBrains Mono Thin; ExtraLight for time labels and the date; Regular for NO DATA; a 4-glyph Noto Sans
  Symbols subset (`noto_sans_symbols_seasons.ttf`, OFL) for the equinox/solstice signs (the system shows them as emoji).
- Easter eggs (never mention them publicly), interactive only, no time labels: an amber line at the exact moment,
  capped on the rim (EGG_R) by an amber symbol; the nearest hour tick is removed. Equinoxes/solstices: the astronomical
  sign by local month (♈ ♋ ♎ ♑). Pi Day (03.14, MM.DD date order only): π, line at 01:59, 02:00 tick removed. The day
  browser and the day table show the same symbols. Previews: `NW_FORCE_PI_DAY=1` or `NW_FORCE_SEASON_MONTH=3|6|9|12`
  before `python3 face/tools/gen_face.py` (test builds only; regenerate without them before committing), plus a
  simulated event day for the season line.
- Corner cases: each twilight level independent; the night is the noon-to-noon window; a missing moonrise/set is
  taken from up to 3 days back. Known, accepted: at high latitudes a sunrise/sunset label can touch the big digits.

## Companion screens
- Scrubber: opens on today (setup on top if location isn't granted). Crown = days, with a stronger notch and hold on
  today. Palette = face setting (last alias, `Palettes.mode`); Auto judged by right now, not the date shown. Center
  tap -> day table; swipe right exits; finishes when stopped.
- Day table: events in time order (emoji | HH:MM | label); tides as "Hi tide +11.7′" / "Lo tide −0.6m" above chart
  datum (`TideDatums.kt`); feet for US-unit locales. Night: red color filter. Tap closes; last row opens setup.

## Ops (Mac, watch on Wi-Fi debugging; run gradle/adb via `zsh -lc` for ANDROID_HOME/JAVA_HOME)
- Build: `python3 face/tools/gen_face.py && ./gradlew :face:assembleDebug :astro:assembleDebug`; `adb install -r`
  each APK. Activate: `adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation
  set-watchface --es watchFaceId com.analemmic.nightwatch.face`.
- After a fresh companion install: `adb shell pm grant com.analemmic.nightwatch.astro
  android.permission.ACCESS_COARSE_LOCATION` (+ ACCESS_BACKGROUND_LOCATION).
- Simulation (debug builds): `adb shell am broadcast -n com.analemmic.nightwatch.astro/.RefreshReceiver --es lat ..
  --es lon .. [--es at '2026-12-21T13:00+01:00[Europe/Oslo]']`; undo with `--ez clear true`. Change the watch's time
  zone with `adb shell service call alarm 3 s16 <Zone>`; restore it and clear the simulation afterwards.
- Logs: `adb logcat | grep -E "NightwatchAstro|DWF"`. Screenshot: `adb exec-out screencap -p > shot.png`. Use
  KEYCODE_BACK (HOME toggles the launcher). Buzz the owner: `adb shell cmd vibrator_manager synced oneshot 300`.
- Git: repo config commits as Analemmic with the GitHub noreply address. Two public branches: `dev` (the default:
  all work lands here) and `main` (= exactly what's on Play; GitHub must match what Play reviews). Merge `dev` into
  `main` only together with a Play upload, and tag it locally (`play-internal-1`, `play-closed-2`). A pre-push hook
  allows only these two branches (`private-history` and the old tags carry the real name).
- Verify visual changes on the watch with screenshots; never present guessed numbers as computed.
