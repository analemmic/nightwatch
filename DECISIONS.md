# Why Nightwatch is the way it is

Goals, values, and constraints, so changes don't undo something on purpose. The current spec is CLAUDE.md and the code.

## Goals
- **The whole day at a glance.** A 24-hour dial with noon at the top shows where you are between sunrise and sunset,
  how much light is left, when the moon is up, and when the tide turns.
- **Show what people see on the ground.** True sunrise and sunset (upper limb plus refraction, as weather apps), each
  twilight level on its own, events where they happen on the clock rather than where they are in the sky.
- **Enough is enough.** The face is at its sweet spot. New detail goes in the day table, not on the dial.

## Values
- **Night vision first.** At night everything is red or a muted warm gray on black; nothing is white. By day the
  face is colorful and legible, with each twilight level told apart by hue.
- **Calm.** Thin type, quiet lines, stepped fills. No gradients, wave curves, starfields, or translucency (thin
  translucent strokes dither in ambient; dimmer steps are opaque, pre-dimmed colors).
- **The hands stand out.** Only the hands are bright white by day; nothing else may be mistaken for them.
- **Sleek over explanatory.** No on-face indicators for guesses, sources, or modes. The equinox/solstice line and
  Pi Day (03.14: a pi at 02:00, a line at 01:59, no label so nothing crowds the digits) are unadvertised easter eggs.
- **Private and offline.** Everything is computed on the watch; no data leaves it. No network, accounts, or analytics.
- **Honest numbers.** Predictions are approximate and labeled "not for navigation". Fast paths are tested against
  slow reference implementations; accuracy claims are measured, never guessed.
- **Pen name.** The project publishes as Analemmic. No real name in the repo, builds, or public history.

## Platform constraints
- **Watch Face Format.** Required for faces on current Wear OS. WFF is XML only: no code, no location, no sun data.
- **A companion app feeds the face** through hidden RANGED_VALUE complication slots, the only way to get computed
  numbers into a WFF face. Data is packed by one codec whose constants gen_face.py owns and generates for both sides.
  Every slot must look like our encoding, because the system sometimes feeds placeholder values while the face
  reloads; anything else is treated as absent.
- **The scrubber redraws the face in Kotlin.** WFF renders only as the watch face, and the face's crown belongs to
  the system. The scrubber mirrors gen_face.py through the generated FaceSpec to stay in step.
- **Always-on runs on the main CPU.** The low-power offload path rejects WFF layouts on this watch and only supports
  12-hour hands; the 24-hour hour hand is non-negotiable. Re-test offload after system updates.

## Design rules
- **Lines only for sunrise and sunset.** Twilight shows as fills; exact times are in the day table.
- **Times sit inside the disc** at the root of their line, on black, so the sky stays clean.
- **The date curves along the rim at 12.** The bottom belongs to the system's media and notification icons.
- **Solar noon is a long, faint line**: it anchors the dial's symmetry and breaks around the date.
- **The moon is a thin arc while it's up**, with its phase drawn at the transit. **Tides are dots**: high outside the
  moon ring, low inside.
- **Ticks turn black inside the lit sky** so they don't vanish against the fills.
- **Date order follows the user's preference**, not a locale list.

## Behavior
- **The night runs noon to noon.** After noon the face shows tonight's dusk and tomorrow's dawn; before noon, last
  night's dusk and today's dawn. Polar cases follow from each level independently: full rings, no lines, one-sided days.
- **Tides from an on-device harmonic model** (TICON-4): offline and worldwide. Nearest gauge or nothing. Heights read
  like local tide tables: MLLW for US gauges, LAT elsewhere; feet or meters by locale.
- **Location never blocks.** A recent fix, else the saved one, else the time zone's main city, answered instantly;
  fresh fixes arrive in the background. A wrong-gauge tide from a time-zone guess is acceptable.
- **One setting: Palette (Auto, Day, or Night).** Auto is the point; the override is for night vision at noon or
  legibility at night. Everything else is fixed in code.
- **The scrubber always opens on today**, with a haptic notch there, and its palette follows what your eyes need
  right now, not the date shown.
- **Fast enough to scrub.** A day must compute in a frame or two on the watch.
- **Light on battery.** Nothing runs in the background beyond the 15-minute complication requests.
