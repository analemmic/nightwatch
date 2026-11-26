# nightwatch: Wear OS watch face (24h sun/moon/tide dial) + companion app

Owner: the user, publishing as Analemmic (pen name: no real name anywhere in the repo or builds). Personal face for a Pixel Watch 3, prepared for a free Google Play release; open source under MIT (2026-11-26): see
RELEASE.md, PRIVACY.md, LICENSE, licenses/. WHY things are the way they are: DECISIONS.md (read it before changing
the design; the owner settled most of it deliberately). The design is feature-complete; new detail goes in the day
table, not on the face.

## Layout
- `face/` Watch Face Format face (no code). `face/tools/gen_face.py` generates `res/raw/watchface.xml`, the moon/tide
  sprites, and the companion's `FaceSpec.kt` (geometry, palettes, wire-format constants). Never edit generated files.
  Smoke test: `python3 face/tools/test_gen_face.py`. `preview.png` is a device screenshot (see Ops).
- `astro/` companion app "Nightwatch" (package `com.analemmic.nightwatch.astro`):
  - `Astro.kt` sky snapshot (sun bands, moon band/transit/age, solar noon, season, tides); `Tides.kt` harmonic tide
    predictor (TICON-4 data, `tools/build_tides.py` -> `res/raw/tide_stations.bin`; then rebuild the chart datums:
    `NW_BUILD_DATUMS=1 ./gradlew :astro:testDebugUnitTest --tests '*BuildTideDatums*'` -> `tide_datums.bin`, ~3 min;
    TideDatumsTest fails if they don't match); `Seasons.kt` equinox/solstice.
  - `Sources.kt` the 7 complication data sources (one per face slot) + snapshot cache; `SlotCodec.kt` wire format.
  - `Sky.kt` where/when (location or debug simulation, tide gauge), tide DB, picker PreviewSky. `TzCentres.kt` backup
    location: the time zone's principal city (`tools/build_tz.py` -> `res/raw/tz_centres.txt`, from IANA zone.tab).
  - `ScrubberActivity`/`ScrubberView` day scrubber (the face redrawn in Kotlin for any date); `TableActivity`/`DayTable`
    day table; `SetupActivity` location permission + licenses; `BootReceiver` refresh on boot/update/locale/time zone change.
  - Tests: `./gradlew :astro:testDebugUnitTest` (test-only reference implementations in `src/test/.../Reference.kt`).
- `docs/` the public website (GitHub Pages from main /docs: https://analemmic.github.io/nightwatch/): index, privacy
  (mirror of PRIVACY.md: keep in step), license (hand-written credits + licenses/ full texts: keep in step with licenses/NOTICE.txt).
  Plain, short copy, Oxford comma in all user-facing text (site, app strings, notices); the equinox/solstice line is an unadvertised easter egg (don't mention it on the site).
  Screenshots in docs/img are taken with a SIMULATED location (Lisbon day, Tokyo night), never the owner's real one,
  and the system notification dot painted out.

## Data path (face <- companion)
WFF has no location or sun data, so the companion computes everything and publishes 7 RANGED_VALUE complications; the
face's data block decodes each into `<Reference>`s once and every element reads `[REFERENCE.x]`.

| slot | source | min (-1000 - lo) | value | max (1000 + hi) |
|---|---|---|---|---|
| 1 | SunSource | sunrise edge | solar noon | sunset edge |
| 2-4 | Civil/Nautical/AstroSource | dawn edge | 0 | dusk edge |
| 5 | MoonSource | moonrise edge | transit | moonset edge |
| 6 | TideSource | t0 * 1440 + t1 | count * 2 + first-is-high | t2 * 1440 + t3 |
| 7 | ExtrasSource | t4 + 1440 * date-is-day-first | equinox/solstice | moon age * 100 |

Edge = round((dial angle + 180) * 4) + 3000 if a real event (else a noon-window boundary); 6500 = none. Tide times are
minutes after local midnight. Constants: gen_face.py CODEC -> FaceSpec.kt `Codec`. Any slot not shaped like this
(min <= -1000, value in [-1000, 1000]) decodes as ABSENT (-999); the face hides anything below -500. Slot 8 is free.
- The runtime binds sources by slot POSITION and remembers it: after changing SLOTS or renaming a source class,
  `adb uninstall com.analemmic.nightwatch.face` and install fresh (a -r update keeps stale bindings). Inspect:
  `adb shell dumpsys activity service com.google.wear.watchface.runtime` (primaryDataSource / data=).
- Sources request updates every 900 s; Refresh.all re-requests all (boot, app update, locale/time zone change, location
  moved).
- Location: a recent fix, else the saved one, else the time zone's city (TzCentres; also when the saved fix is > 1000 km
  from the zone's city and its own nearest zone city keeps a different UTC offset, i.e. travelled without a new fix).
  Tides follow whichever location is used. No indication on the face that it's a guess (owner: keep it sleek).

## Dial (all angles are clock positions: an event at local time T sits at 15 * (T - 12), noon at top, clockwise)
Canvas 450, R = 225. Two palettes (gen_face.py NIGHT / DAY), chosen by the face editor's one setting, Palette:
Auto / Day / Night (`[CONFIGURATION.palette]`, in is_day). Auto: DAY from true sunrise to sunset (hour hand between the
sunrise and sunset lines; polar day = day; no data = night), NIGHT otherwise. Ambient uses the same palette pre-dimmed
to 200/255, never alpha. All elements are built once per palette in two masked groups (palette_layers); the editor's
Auto preview (recognised by its frozen clock at PREVIEW_TIME 07:40:37) shows night on the left half, day on the right.
- Centre tap: a transparent disc-sized part with `<Launch>` per palette option, opening the companion's activity alias
  ScrubberAuto / ScrubberDay / ScrubberNight, which is how the scrubber learns the setting (it can't read the face's).
- Sky fills (interactive only): nested sectors from the disc rim (0.60R) to the edge: astronomical, nautical, civil,
  day (true sunrise -> sunset). Night = black.
- Whiskers: sunrise and sunset ONLY (twilights show as fills; their times are in the day table), 3px from the centre
  to the dial's edge, unbroken. Real edges only.
  Solar noon: quiet 3px line (night #9d150d, day #a8823a) from the rim to the ticks, no label; within NOON_BREAK_DEG of 12 it breaks around the
  date (a piece below and a piece above it).
- Hour ticks: 24, majors at 00/06/12/18 (7px to 0.927R), minors 4px to 0.955R; black inside the lit sky (nautical fill
  and brighter) in interactive mode.
- All face text is JetBrains Mono THIN (weight-100 instance, res/font/jetbrains_mono_thin.ttf) except the time labels
  (ExtraLight, weight-200 instance) and the tiny NO DATA (Regular).
- Sunrise/sunset labels: HHMM, no colon, size 18, INSIDE the centre disc on their line's axis (centred at
  LABEL_R = 0.60R - 34), on black, reading left-to-right; interactive only, both palettes. Night label colour is RED_BRIGHT
  #ff2a1a (the rest of the night face tops out at #c41a10).
- Equinox/solstice (on that local day): amber line at the exact time with its label (amber too) inside the disc the same way; a
  sun label within 10 deg yields.
- Centre disc r 0.60R over the line roots, black, no rim; time HH over MM filling it, solid in ambient too. Date
  curved along the rim at 12 (TextCircular, radius DATE_R 0.82R, size 24, ExtraLight; night: NIGHT_DATE #ff5038, a touch warmer
  than red so it reads on the fill and in ambient),
  MM.DD or DD.MM by the user's date order (companion DateOrder; en_US fallback before data). The bottom stays clear
  for the system's media/notification icons.
  "NO DATA" (dim) while the moon transit is absent.
- Moon: a thin (2px) arc on the disc's edge from moonrise to moonset (when the moon is up), no ticks; phase sprite at
  the transit ("lunar noon"), chosen by the companion's moon age (fallback MOON_PHASE_POSITION, a float on this watch).
- Tides: dots (r 2.6) at high water (7 outside the moon ring) and low water (7 inside); nearest TICON-4
  gauge within 50 km, else none. Up to 5 per day.
- Hands: 24h hour hand (6px, 0.665R-0.80R) and ordinary minute hand (2px to 0.91R). Day: white (off-white in ambient);
  night: the muted grey NIGHT_INK #7d7873 (also the night moon sprite and the scrubber's night date).

Corner cases (each twilight level independent; the "night" is the noon-to-noon window): sun never below a level ->
full ring, no lines; never above -> nothing; only dusk/only dawn in the window -> partial fill, one line. Moon always up
-> full circle; always down -> nothing; rise without set (or vice versa) -> the missing event from up to 3 days
back (if still missing, the arc has zero length and nothing shows). Known: a sunrise/sunset line more than ~50 deg from 06/18 (high latitudes) puts its label against the big time
digits; so does the equinox/solstice label whenever that moment falls nearer noon or midnight (one day; accepted).

## Companion screens
- Launcher and the face's centre tap open the day scrubber on today (setup on top if location isn't granted; the time
  zone's city meanwhile). Crown =
  +-days (haptic tick; a stronger notch + 450 ms hold on today, both directions); no hands; white date stacked in the
  centre (white by day, NIGHT_INK grey at night), year curved at 12 where the face has its date; palette = the face's setting (last alias heard,
  Palettes.mode; stale from the launcher until the next face tap after a change); Auto = TODAY's day/night right now, whatever date is shown. Centre tap -> that day's table. Swipe right exits;
  it finishes when stopped, so every open starts on today. ~13 ms per day on the watch (see DECISIONS.md "Speed").
- Day table: the day's events in time order, emoji | HH:MM | label; moon phase on the transit row; tides as
  "Hi tide +11.7′" / "Lo tide −0.6m": height above the gauge's chart datum like tide tables (MLLW for US gauges,
  LAT elsewhere; `TideDatums.kt`, res/raw/tide_datums.bin); feet where the locale uses US units. Night: everything
  through a red colour filter; day: native emoji, each row in its element's face colour (day/night = right now, as the
  scrubber; it passes its choice). Tap closes. Last row opens setup.

## Ops (Mac, watch over Wi-Fi debugging; run gradle/adb via `zsh -lc` so ~/.zprofile sets ANDROID_HOME/JAVA_HOME)
- Build/install: `python3 face/tools/gen_face.py && ./gradlew :face:assembleDebug :astro:assembleDebug`, then
  `adb install -r` each APK (face fresh-install when slots change, see above). Activate the face:
  `adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId com.analemmic.nightwatch.face`.
- Permissions after a fresh companion install: `adb shell pm grant com.analemmic.nightwatch.astro
  android.permission.ACCESS_COARSE_LOCATION` (+ ACCESS_BACKGROUND_LOCATION).
- Debug refresh/simulation (debug builds only; hands keep real time):
  `adb shell am broadcast -n com.analemmic.nightwatch.astro/.RefreshReceiver [--es lat 78.22 --es lon 15.65 --es at '2026-12-21T13:00+01:00[Europe/Oslo]']`,
  back to real: `--ez clear true`. Day mode in the evening: `adb shell service call alarm 3 s16 Asia/Tokyo` + simulate
  `--es lat 35.68 --es lon 139.69`; restore `America/New_York` (owner uses manual time zone) and clear.
- Logs: `adb logcat | grep -E "NightwatchAstro|DWF"`; scrubber timing: `grep "scrubber:"`. Screenshot:
  `adb exec-out screencap -p > shot.png`. On this watch KEYCODE_HOME toggles the launcher; use KEYCODE_BACK.
- preview.png (picker thumbnail fallback): screenshot of the face simulating PreviewSky (NYC 2026-10-06 07:40) with
  the watch time zone set so the clock reads 07:40; paint out the system notification dot.
- Buzz the owner: `adb shell cmd vibrator_manager synced oneshot 300`.
- Git identity (pen name, GitHub noreply): `git -c user.name=Analemmic -c user.email=126299041+analemmic@users.noreply.github.com commit ...` (tags too).
- Verify on the watch with screenshots before calling a visual change done; never present guessed numbers as computed.
