# Why nightwatch is the way it is

Motivation for the design and architecture, so changes don't undo something on purpose. Current spec: CLAUDE.md.

## Platform
- **Watch Face Format, not a Kotlin watch face.** WFF is required to install faces on current Wear OS; the old
  androidx watch-face libraries are deprecated. WFF faces are XML only: no code, no location, no sun data.
- **A companion app feeds the face through hidden complication slots.** That's the only way to get computed numbers
  into a WFF face. WFF allows 8 slots; each RANGED_VALUE carries three floats, so data is packed (CODEC) and decoded
  in one data block. A pure-WFF fallback (fixed location, solar math in expressions) was the plan B; never needed.
- **One codec, generated constants, self-checking slots.** The packing grew ad hoc and drifted between the Kotlin
  encoder and the face's decoder; now gen_face.py owns the constants and generates FaceSpec.kt. Every slot has to look
  like our encoding (min <= -1000, value within +-1000) because, off-wrist, while the face reloads behind the lock
  screen, the system briefly feeds placeholder numbers (~0..100) that otherwise drew every line at noon. Each edge
  carries its own "real event" bit, which retired the old flags slot and freed slot 8.
- **The scrubber redraws the face in Kotlin.** WFF can't be rendered anywhere but the watch face, and the crown on
  the face belongs to the system (it opens notifications; WFF has no rotary input). So the scrubber is an activity
  that mirrors gen_face.py from the generated FaceSpec (and the face's own sprites) to keep the two in step.
- **Always-on stays on the main CPU.** Pixel Watch can offload ambient rendering to a low-power chip, but every WFF
  layout was rejected by the vendor firmware (even a bare AnalogClock; Google's native face offloads fine), and the
  offload path only supports 12h hands anyway, while the 24h hour hand is non-negotiable. Re-test after system
  updates with a minimal AnalogClock-only face.

## Looks
- **Night vision first.** Red on black preserves dark adaptation; the original face was red with white only for the
  hands. **Day mode** (sunrise to sunset) is more colourful and legible: blue sky, whiskers in sunset hues, so each
  twilight level is told apart by hue rather than only brightness. The always-on display follows the palette too
  (owner chose consistency over the small battery saving of all-red).
- **At night, nothing is white.** The scrubber's white night date was too brilliant; its muted warm grey worked, so
  the night hands and moon sprite use it too (NIGHT_INK). By day the hands stay white.
- **Only the hands are white (by day).** White sunrise/sunset labels were mistaken for the hands at a glance; labels became
  the palette's label colour. A bright yellow solar-noon line
  was too close to the white hands in daylight, so it's a muted gold.
- **No translucency anywhere.** Thin semi-transparent strokes dither into dots in ambient; dimmer steps are opaque
  pre-dimmed colours.
- **Five sky zones and four whiskers.** A sunrise/sunset clock must show true sunrise/sunset (upper limb + refraction,
  as weather apps), not civil twilight; each of night, astronomical, nautical, civil and day gets its own colour.
- **Clock positions everywhere.** Moonrise/set, transit and tides sit where they happen on the 24h clock (matching
  weather apps), not where the moon is in the sky; the heavy moon arc means "the moon is up". The moon marker sits at
  the transit ("lunar noon", highest in the sky), which on most coasts sits in a stable relation to high water.
- **Only sunrise and sunset get lines.** Eight whiskers competed with the hands; the twilight fills already show
  where each twilight begins (the night reds turned out distinct enough on the watch), and the day table has the exact
  times. A gradient sky was mocked up and rejected as too busy: the stepped fills stay.
- **The moon is an arc, the tides are dots.** From the design study: a thin partial arc (no ticks, no disc rim)
  says "the moon is up from here to here" more quietly than a heavy arc with ticks on a full circle, and dots at high
  (outside) and low (inside) water read faster than triangles. Rejected from the same study: a tide wave (too busy, highs
  and lows hard to pick out), a gradient sky (ugly) and a starfield (busy).
- **Thin type everywhere.** Hairline digits look calmer and light fewer pixels; in ambient they stay solid (no
  outlines). Labels and date follow for consistency (~1 px strokes on the watch), except the small
  time labels in the disc, which needed ExtraLight. The scrubber's date is white by day and a subtle grey at night (white was too brilliant).
- **Times inside the disc.** Thin red digits on the red sky fills were hard to read at night; a black box made them
  readable but jarring, and a framed tag was readable but busy. On the disc's black, at the root of their line, small
  times need no box and leave the sky clean (owner picked this from seven sketched options). Same in both palettes.
  Small thin digits read faint, so they are ExtraLight, and at night a near-full red (RED_BRIGHT).
- **The date curves along the rim at 12.** At the bottom it was squeezed between the moon marker and the system's
  media/notification icons, which can't be moved. The top was free once solar noon could break around it (picked
  from seven sketched placements). The scrubber's year goes in the same place.
- **Solar noon stays a long faint line.** It anchors the dial's symmetry (and reads a little like an aircraft HUD).
- **Ticks black inside the lit sky.** Coloured ticks vanished against the fills.
- **Big digits, big centre disc.** The disc (0.60R) was sized to clear the old sky-borne labels; it now also holds
  the sunrise/sunset times around the big digits.
- **Date order follows the user's preference** (DD.MM or MM.DD), not a locale list.
- **Equinox/solstice line** is a once-a-quarter easter egg, amber in both palettes.
- **Enough is enough.** The owner called the information density "the sweet spot": dark-sky windows, spring/neap tide
  scaling and similar ideas were turned down as clutter. New detail goes in the day table.

## Behaviour
- **The "night" is noon to noon.** After noon the face shows tonight's dusk and tomorrow's dawn; in the morning, last
  night's dusk and today's dawn. Each twilight level is handled independently at high latitudes ("show what people see
  on the ground"): full rings, no lines, one-sided days.
- **Tides from an on-device harmonic model** (TICON-4, CC BY 4.0) instead of an API: works offline, worldwide, no
  data leaves the watch. Nearest gauge within 50 km, else nothing. Records ending before 1990 are dropped (a historic
  Boston gauge ran ~37 min late). TICON phases need the Doodson argument without the usual +180 degrees. Validated
  against NOAA tables (median error 3-8 min). Not for navigation.
- **The time zone is the backup location.** Without a fix (permission declined, never had one) the face used to
  show only the time; the zone's principal city gives a plausible sky (sunrise within roughly half an hour in a wide
  zone). Same when the saved fix plainly belongs to another zone (travelled, no fix yet). Tides from that guess may
  be from the wrong gauge; the owner accepted that rather than hide them, and nothing on the face marks the guess.
- **Not for navigation**, said on the setup screen, mirroring the tide data's own warning: everything is an
  approximate prediction made on the watch.
- **Tide heights read like tide tables.** TICON only gives the tide around mean sea level, so each gauge's chart
  datum is computed once, with the app's own model, over the US datum epoch 1983-2001 (a full 18.6-year node
  cycle): MLLW for US gauges, as NOAA; LAT elsewhere, the usual chart datum. Checked against NOAA: MLLW within 5 cm,
  heights within 0.3 ft at Boston and 0.5 ft at The Battery. Feet or metres by the locale's measurement system.
- **Location never blocks a request.** After a reboot the system didn't re-request sources and the old code waited on
  GPS inside the request; now a recent fix, else the saved one, answers instantly, a fresh fix is fetched in the
  background, and a boot receiver re-requests everything.
- **The scrubber starts on today, every time**, and has a haptic notch on today, so you can always get back.
- **The scrubber's palette is chosen by right now, not by the date shown.** Day or night is about what your eyes
  need at this moment; a summer afternoon scrubbed to at night must not light up in day colours.
- **Speed.** Crown scrubbing needs a day computed in a frame or two: tides use per-day prepared sums with a coarse grid
  then a per-minute refine, moon transits use Newton steps, and the debug build isn't debuggable (debuggable code ran
  ~5x slower; the debug APK is the daily driver). Each fast method is tested against the slow original.
- **One setting: palette (Auto / Day / Night).** Auto stays the point, but sometimes you want night vision at noon or
  legibility at night. The face editor is the native place for it; the scrubber can't read face settings, so the
  face's tap tells it (one activity alias per option), accepting staleness from the launcher. Auto's editor preview
  is split night/day because at the preview time it would look exactly like Day. Everything else is customised in
  code; slots are not user-editable.
- **Battery.** Nothing runs in the background besides the 15-minute complication requests; the extra cost versus a
  stock face is ambient rendering on the main CPU (see Platform).
