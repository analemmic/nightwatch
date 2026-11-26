#!/usr/bin/env python3
"""Generate the nightwatch WFF face (res/raw/watchface.xml, moon and tide sprites) and the companion's FaceSpec.kt.

Run from anywhere:  python3 face/tools/gen_face.py      Smoke test:  python3 face/tools/test_gen_face.py
Geometry follows CLAUDE.md "Visual spec" (radii as fractions of R, the face radius); why things are this way: DECISIONS.md.

Data flow: the astro/ companion publishes 7 RANGED_VALUE complications (one per slot, see SLOTS). The data block decodes
each into WFF <Reference>s once; every element reads [REFERENCE.<name>]. The wire format (CODEC) is shared with the
companion through the generated FaceSpec.kt, so encoder and decoder can't drift.
"""
import math
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "src" / "main" / "res"
ASTRO = HERE.parent.parent / "astro" / "src" / "main"
ASTRO_PKG = "com.analemmic.nightwatch.astro"

# ---- geometry --------------------------------------------------------------------------------------------------------
W = 450            # WFF canvas (scaled to the device)
C = W / 2          # centre
R = W / 2          # face radius
DISC = 0.60        # centre disc radius: as large as fits inside the sunrise/sunset labels
HAND_START = DISC + 0.065   # hands begin just outside the moon ring
FONT = "jetbrains_mono_thin"      # weight-100 instance of JetBrains Mono (OFL): every text on the face
FONT_LABEL = "jetbrains_mono_extralight"   # weight-200 instance: the small time labels (thin read too faint)
FONT_REGULAR = "jetbrains_mono"   # only the tiny NO DATA note (thin would vanish at that size)
TICK_MINOR_R, TICK_MAJOR_R = 0.955, 0.927   # inner ends of the hour ticks (lines stop at TICK_MINOR_R)

# Sunrise/sunset (and season) times sit inside the centre disc, on black, on their line's axis where it meets the
# disc (owner 2026-11-25: the framed tags out in the sky were busy). 4 digits at size 18 are 43 long, centred at
# LABEL_R: from ~80 (clear of the big time digits for lines within ~50 deg of 06/18) to ~123 (inside the low-tide dots).
LABEL_SIZE, LABEL_R = 18, DISC * R - 34
LABEL_BW, LABEL_BH = 52, 24
SEASON_CLASH_DEG = 10   # two labels this close would overlap: the sun label yields

TIDE_DOT_R, TIDE_OFF = 2.6, 7          # tide dots: radius; centre this far outside (high) / inside (low) the ring
MOON_R = 0.045 * R                     # moon marker radius
MOON_DAYS = 29                         # one sprite per started day of the moon's age
SYNODIC = 29.530589
TIME_SIZE = (DISC - 0.045 - 0.065) * R / 1.02   # centre digits: fill the disc, clear of the moon marker
TIME_DY = 0.45 * TIME_SIZE
# Date (owner 2026-11-25: off the crowded bottom): curved along the rim at 12, centred at radius DATE_R. Solar noon
# breaks around it when it falls within the date's span (NOON_BREAK_DEG), pieces below and above the text.
DATE_SIZE, DATE_R = 24, 0.82 * R
DATE_GAP = 10
NOON_BREAK_IN = DATE_R - 0.75 * DATE_SIZE / 2 - DATE_GAP    # the lower piece ends here...
NOON_BREAK_OUT = DATE_R + 0.75 * DATE_SIZE / 2 + DATE_GAP   # ...the upper one starts here
NOON_BREAK_DEG = math.degrees((5 * 0.6 * DATE_SIZE / 2 + DATE_GAP) / DATE_R)   # half the date's span, plus the gap

# ---- colours ---------------------------------------------------------------------------------------------------------
RED = "#c41a10"
RED_DIM = "#82140c"
RED_BRIGHT = "#ff2a1a"   # night time labels: small thin digits need full intensity to read
NOON_W = 3               # solar noon line width (owner 2026-11-25: 2 px was too faint)
NIGHT_NOON = "#9d150d"   # RED at 0.8: one step up from RED_DIM, still below the sunrise/sunset lines
NIGHT_DATE = "#ff5038"   # night date on the rim: full red was still faint there (esp. ambient), so a touch warmer
WHITE = "#ffffff"
AMBER = "#ff9f0a"        # equinox/solstice line, both palettes
OFF_WHITE = "#d9cfc4"    # day ambient hands and moon sprite
NIGHT_INK = "#7d7873"    # muted warm grey: the night palette's hands, moon sprite and scrubber date


def dim(color, level):
    """Opaque colour at `level` brightness. Never alpha: thin translucent strokes dither into dots in ambient."""
    return "#" + "".join(f"{round(int(color[i:i + 2], 16) * level):02x}" for i in (1, 3, 5))


AMB_LEVEL = 200 / 255
OFF_WHITE_AMB = dim(OFF_WHITE, AMB_LEVEL)

# NIGHT: red night vision. DAY: from true sunrise to sunset (is_day). Ambient uses the same palette, pre-dimmed.
# fills: astronomical, nautical, civil, day; whisk: sunrise/sunset, civil, nautical, astronomical.
NIGHT = dict(name="night", fills=["#260504", "#300705", "#3a0906", "#440a06"],
             whisk=[dim(RED, lv) for lv in (1.0, 0.85, 0.7, 0.55)],
             tick_major=RED, tick_minor=RED_DIM, noon=NIGHT_NOON, label=RED_BRIGHT, moon=RED, tide=RED,
             digits=RED, date=NIGHT_DATE, nodata=RED_DIM, scrub_date=NIGHT_INK,   # scrub_date: the scrubber's date
             hand=NIGHT_INK, moon_sprite=NIGHT_INK)
NIGHT_AMB = dict(NIGHT, tick_minor=RED)   # ambient has no fills, so minor ticks go up to the main red
DAY = dict(name="day", fills=["#0b0f24", "#0f1733", "#142145", "#1b2d5c"],
           whisk=["#ffc24a", "#ff8a3d", "#e0507a", "#8a64d6"],
           tick_major="#e6e9ee", tick_minor="#7c8594", noon="#a8823a", label="#ffc24a",
           moon="#d6dce6", tide="#3fc6e0", digits="#e8edf4", date="#aeb7c4", nodata="#7c8594", scrub_date=WHITE,
           hand=WHITE, moon_sprite=WHITE)
DAY_AMB = {k: [dim(c, AMB_LEVEL) for c in v] if isinstance(v, list) else dim(v, AMB_LEVEL) if str(v).startswith("#") else v
           for k, v in DAY.items()}
DAY_AMB.update(hand=OFF_WHITE_AMB, moon_sprite=OFF_WHITE_AMB)


class Pal:
    """The palette a builder draws with: interactive colours and their ambient twins."""
    def __init__(self, on, amb):
        self.on, self.amb, self.name = on, amb, on["name"]


PALETTES = [Pal(NIGHT, NIGHT_AMB), Pal(DAY, DAY_AMB)]

# ---- wire format (shared with the companion via FaceSpec.kt) -----------------------------------------------------------
# Each slot is a RANGED_VALUE (min, value, max). Two non-negative integers ride in min/max as min = -OFFSET - lo,
# max = OFFSET + hi (exact in a float up to 2^24), so min <= value <= max holds for any value in [-OFFSET, OFFSET].
# An edge (a dial angle in [-180, 540]) is enc = round((angle + 180) * EDGE_SCALE), + EDGE_REAL if it's a real event
# (not just the noon-window boundary of a polar day); EDGE_NONE = no such edge. Anything not shaped like this (e.g. the
# system's placeholder data while the face reloads behind the lock screen) is treated as ABSENT: a plain clock.
CODEC = dict(OFFSET=1000, EDGE_SCALE=4, EDGE_REAL=3000, EDGE_NONE=6500, PAIR=1440, AGE_SCALE=100)
ABSENT = -999          # "nothing here" for a value; the face hides anything below -500
OFFSET = CODEC["OFFSET"]
MIN, MAX, VAL = "[COMPLICATION.RANGED_VALUE_MIN]", "[COMPLICATION.RANGED_VALUE_MAX]", "[COMPLICATION.RANGED_VALUE_VALUE]"
LO, HI = f"(-{MIN} - {OFFSET})", f"({MAX} - {OFFSET})"
VALID = f"(({MIN}) <= -{OFFSET} && ({MIN}) >= -2100000 && ({VAL}) >= -{OFFSET} && ({VAL}) <= {OFFSET})"


def checked(expr, fallback=ABSENT):
    return f"({VALID}) ? ({expr}) : {fallback}"


def edge(enc):
    """Packed edge -> dial angle, or ABSENT."""
    return f"(({enc}) > {CODEC['EDGE_NONE'] - 500} || ({enc}) < 0) ? {ABSENT} : " \
           f"((({enc}) % {CODEC['EDGE_REAL']}) / {CODEC['EDGE_SCALE']} - 180)"


def edges(lo_name, hi_name):
    """A band's two edges, each with its real-event flag (<name>Real: 1 real, 0 boundary only)."""
    return {lo_name: checked(edge(LO)), lo_name + "Real": checked(f"floor({LO} / {CODEC['EDGE_REAL']})", 0),
            hi_name: checked(edge(HI)), hi_name + "Real": checked(f"floor({HI} / {CODEC['EDGE_REAL']})", 0)}


# slotId, companion class, {Reference: expression}. The runtime binds sources by slot POSITION and remembers it: after
# changing this list, `adb uninstall com.analemmic.nightwatch.face` and install fresh. Slot 8 (WFF's max) is spare.
P = CODEC["PAIR"]
SLOTS = [
    (1, "SunSource",      {**edges("sunRise", "sunSet"), "solarNoon": checked(VAL)}),
    (2, "CivilSource",    edges("sunCivilDawn", "sunCivilDusk")),
    (3, "NauticalSource", edges("sunNauticalDawn", "sunNauticalDusk")),
    (4, "AstroSource",    edges("sunAstroDawn", "sunAstroDusk")),
    (5, "MoonSource",     {**edges("moonRise", "moonSet"), "moonTransit": checked(VAL)}),
    # Up to 5 high/low waters (minutes after local midnight), alternating; meta = count * 2 + (first is high).
    (6, "TideSource",     {"tide0": checked(f"floor({LO} / {P})", 0), "tide1": checked(f"{LO} % {P}", 0),
                           "tide2": checked(f"floor({HI} / {P})", 0), "tide3": checked(f"{HI} % {P}", 0),
                           "tideMeta": checked(VAL, 0)}),
    # min: tide 5 + PAIR * (date is day-first); value: equinox/solstice; max: moon age * AGE_SCALE (> 30 days: none).
    (7, "ExtrasSource",   {"tide4": checked(f"{LO} % {P}", 0),
                           "dateDayFirst": checked(f"floor({LO} / {P})", -1),
                           "seasonEvent": checked(VAL),
                           "moonAge": checked(f"({HI} / {CODEC['AGE_SCALE']} <= 30) ? {HI} / {CODEC['AGE_SCALE']} : -1", -1)}),
]

# Face editor setting (owner 2026-11-25): palette auto (sunrise..sunset), always day, or always night. The centre tap
# opens the scrubber through a per-option activity alias, which is how the companion learns the setting.
PALETTE_CFG = "palette"
PALETTE_MODES = [("auto", "Auto", "ScrubberAuto"), ("day", "Day", "ScrubberDay"), ("night", "Night", "ScrubberNight")]


# ---- XML helpers -----------------------------------------------------------------------------------------------------
def a(v):
    """Escape an attribute value (expressions contain < > & and quotes)."""
    return escape(str(v), {'"': "&quot;"})


def f(x):
    return f"{x:.3f}".rstrip("0").rstrip(".")


def n(x):
    """Part/Group x, y, width, height must be integers (runtime: 'fail to parseInt')."""
    return str(int(round(x)))


def ref(name):
    return f"[REFERENCE.{name}]"


def ambient(alpha):
    return f'<Variant mode="AMBIENT" target="alpha" value="{alpha}" />'


def present(names):
    return " || ".join(f"({ref(k)} < -500)" for k in names)


def visible(names, alpha=255):
    """alpha, or 0 while any named value is ABSENT."""
    return f"({present(names)}) ? 0 : {alpha}"


def shown(names, alpha=255):
    """Like visible(), and also 0 when a named band edge isn't a real event (lines/ticks/labels only on real ones)."""
    cond = [present(names)] + [f"({ref(k + 'Real')} < 0.5)" for k in names if k in EDGE_NAMES]
    return f"({' || '.join(cond)}) ? 0 : {alpha}"


EDGE_NAMES = {k for _, _, fields in SLOTS for k in fields if k + "Real" in fields}


def group(name, body, alpha=None, variant=None, alpha_expr=None, angle_expr=None, pivot=False):
    attrs = (f' alpha="{alpha}"' if alpha is not None else "") + (' pivotX="0.5" pivotY="0.5"' if pivot else "")
    tf = (f'\n      <Transform target="angle" value="{a(angle_expr)}" />' if angle_expr else "") + \
         (f'\n      <Transform target="alpha" value="{a(alpha_expr)}" />' if alpha_expr else "") + \
         (f"\n      {variant}" if variant else "")
    return f'    <Group name="{name}" x="0" y="0" width="{W}" height="{W}"{attrs}>{tf}\n{body}\n    </Group>'


def full_part_draw(name, inner, angle_expr=None, alpha=255, static_angle=None, alpha_expr=None, ambient_alpha=None):
    """A PartDraw covering the whole face, pivoting about the centre."""
    attrs = f'x="0" y="0" width="{W}" height="{W}" pivotX="0.5" pivotY="0.5" name="{name}" alpha="{alpha}"'
    if static_angle is not None:
        attrs += f' angle="{f(static_angle)}"'
    tf = f'\n      <Transform target="angle" value="{a(angle_expr)}" />' if angle_expr else ""
    if alpha_expr:
        tf += f'\n      <Transform target="alpha" value="{a(alpha_expr)}" />'
    if ambient_alpha is not None:
        tf += f'\n      {ambient(ambient_alpha)}'
    return f'    <PartDraw {attrs}>{tf}\n      {inner}\n    </PartDraw>'


def radial_line(r0, r1, color, thickness, cap="BUTT"):
    """Vertical line from radius r0 to r1 above the centre (rotate the parent to place it)."""
    return (f'<Line startX="{f(C)}" startY="{f(C - r0)}" endX="{f(C)}" endY="{f(C - r1)}">'
            f'<Stroke color="{color}" thickness="{f(thickness)}" cap="{cap}" /></Line>')


def text_part(name, x, y, w, h, size, color, expr, angle_expr=None, template="%s", alpha_expr=None,
              ambient_alpha=None, alpha=None, font=FONT):
    """expr: one expression or a list (one per %s in template)."""
    exprs = expr if isinstance(expr, list) else [expr]
    params = "".join(f'<Parameter expression="{a(e)}" />' for e in exprs)
    tf = f'\n        <Transform target="angle" value="{a(angle_expr)}" />' if angle_expr else ""
    if alpha_expr:
        tf += f'\n        <Transform target="alpha" value="{a(alpha_expr)}" />'
    if ambient_alpha is not None:
        tf += f'\n        {ambient(ambient_alpha)}'
    return (f'      <PartText x="{n(x)}" y="{n(y)}" width="{n(w)}" height="{n(h)}" pivotX="0.5" pivotY="0.5" '
            f'name="{name}"' + (f' alpha="{alpha}"' if alpha is not None else "") + f'>{tf}\n'
            f'        <Text align="CENTER">\n          <Font family="{font}" size="{f(size)}" color="{color}">\n'
            f'            <Template>{template}{params}</Template>\n          </Font>\n        </Text>\n      </PartText>')


def mode_twins(p, name, make, key):
    """make(color, suffix) -> XML. One copy if the colour is the same in ambient; else an interactive copy and an
    ambient-only copy (Variants on wrapper groups, so the parts keep their own alpha transforms)."""
    c, ca = p.on[key], p.amb[key]
    if c == ca:
        return make(c, "")
    return (group(f"{name}_{p.name}_on", make(c, ""), variant=ambient(0)) + "\n"
            + group(f"{name}_{p.name}_amb", make(ca, "_a"), alpha=0, variant=ambient(255)))


def wrap180(x):
    return f"((({x}) % 360) + 540) % 360 - 180"


def hhmm_from_angle(theta):
    """Expression: dial angle -> 4-digit local time with no colon (0610, 1645)."""
    m = f"round(({theta}/15+12)*60)"
    mm = f"(({m} % 1440) + 1440) % 1440"
    return f'numberFormat("0000", floor(({mm})/60)*100 + ({mm}) % 60)'


def is_day():
    """1 for the day palette: by the editor setting, else (auto) while the hour hand is between today's sunrise and
    sunset lines (polar day: always; no data: never)."""
    hand = "([HOUR_0_23_MINUTE] * 15 - 180)"
    r, st = ref("sunRise"), ref("sunSet")
    cfg = f"[CONFIGURATION.{PALETTE_CFG}]"
    auto = f"(({r} < -500) || ({st} < -500)) ? 0 : (((({hand} - {r}) % 360 + 360) % 360 <= {st} - {r}) ? 1 : 0)"
    return f'({cfg} == "day") ? 1 : (({cfg} == "night") ? 0 : ({auto}))'


def user_configurations():
    opts = "".join(f'\n      <ListOption id="{i}" displayName="cfg_{i}" screenReaderText="cfg_{i}" />'
                   for i, _, _ in PALETTE_MODES)
    return (f'  <UserConfigurations>\n    <ListConfiguration id="{PALETTE_CFG}" displayName="cfg_{PALETTE_CFG}" '
            f'screenReaderText="cfg_{PALETTE_CFG}" defaultValue="auto">{opts}\n    </ListConfiguration>\n'
            f'  </UserConfigurations>')


def center_tap():
    """A transparent disc-sized part whose tap opens the scrubber, through the alias of the selected palette option."""
    rd = DISC * R
    box = f'x="{n(C - rd)}" y="{n(C - rd)}" width="{n(2 * rd)}" height="{n(2 * rd)}"'
    opts = "".join(
        f'\n      <ListOption id="{i}">\n        <PartDraw {box} name="tap_{i}">'
        f'<Launch target="{ASTRO_PKG}/{ASTRO_PKG}.{alias}" />'
        f'<Rectangle x="0" y="0" width="{n(2 * rd)}" height="{n(2 * rd)}"><Fill color="#00000000" /></Rectangle>'
        f'</PartDraw>\n      </ListOption>' for i, _, alias in PALETTE_MODES)
    return (f"    <!-- Centre tap -> day scrubber (told the palette setting by which alias opens it). -->\n"
            f'    <ListConfiguration id="{PALETTE_CFG}">{opts}\n    </ListConfiguration>')


# ---- data block ------------------------------------------------------------------------------------------------------
def data_block():
    """One hidden complication slot per companion source; each exports its decoded numbers as References
    (ABSENT by default, i.e. while the slot is empty or not yet delivered)."""
    out = ["    <!-- DATA BLOCK: hidden complication slots fed by the astro/ companion app. -->"]
    for slot_id, cls, fields in SLOTS:
        exports = "".join(
            f'\n        <PartDraw x="0" y="0" width="1" height="1" name="src_{k}">'
            f'<Transform target="angle" value="{a(expr)}" />'
            f'<Reference name="{k}" source="angle" defaultValue="{ABSENT}" />'
            f'<Rectangle x="0" y="0" width="1" height="1"><Fill color="#00000000" /></Rectangle></PartDraw>'
            for k, expr in fields.items())
        out.append(
            f'    <ComplicationSlot slotId="{slot_id}" name="astro{slot_id}" displayName="slot_{cls}" '
            f'x="0" y="0" width="1" height="1" supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">\n'
            f'      <DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY" '
            f'primaryProvider="{ASTRO_PKG}/{ASTRO_PKG}.{cls}" primaryProviderType="RANGED_VALUE" />\n'
            f'      <BoundingBox x="0" y="0" width="1" height="1" />\n'
            f'      <Complication type="RANGED_VALUE">{exports}\n      </Complication>\n'
            f'      <Complication type="EMPTY"><PartDraw x="0" y="0" width="1" height="1" name="empty{slot_id}">'
            f'<Rectangle x="0" y="0" width="1" height="1"><Fill color="#00000000" /></Rectangle></PartDraw></Complication>\n'
            f'    </ComplicationSlot>')
    return "\n".join(out)


# ---- scene pieces (each takes the palette it draws with) ----------------------------------------------------------------
SKY_BANDS = [("astro", "sunAstroDawn", "sunAstroDusk"), ("nautical", "sunNauticalDawn", "sunNauticalDusk"),
             ("civil", "sunCivilDawn", "sunCivilDusk"), ("day", "sunRise", "sunSet")]


def sky_fills(p):
    """Five sky zones: nested annular sectors from the disc rim to the edge, widest/darkest first, interactive only.
    Each is hidden when its band is ABSENT (the sun never gets that high)."""
    r_in = DISC * R
    rm, th = (r_in + R) / 2, R - r_in
    out = ["    <!-- Sky fills (interactive only): astro < nautical < civil < day (sunrise to sunset). -->"]
    for (name, dawn, dusk), color in zip(SKY_BANDS, p.on["fills"]):
        out.append(f'    <PartDraw x="0" y="0" width="{W}" height="{W}" name="fill_{name}">\n'
                   f'      <Transform target="alpha" value="{a(visible([dawn, dusk]))}" />\n'
                   f'      {ambient(0)}\n'
                   f'      <Arc centerX="{f(C)}" centerY="{f(C)}" width="{f(2 * rm)}" height="{f(2 * rm)}" '
                   f'startAngle="0" endAngle="1">\n'
                   f'        <Transform target="startAngle" value="{a(ref(dawn))}" />\n'
                   f'        <Transform target="endAngle" value="{a(ref(dusk))}" />\n'
                   f'        <Stroke color="{color}" thickness="{f(th)}" cap="BUTT" />\n'
                   f'      </Arc>\n'
                   f'    </PartDraw>')
    return "\n".join(out)


def ticks(p):
    """24 hour ticks, 00/06/12/18 major. Interactive: black inside the lit sky (nautical fill and brighter), where
    coloured ticks vanish; ambient (no fills): all in the palette's tick colours."""
    dawn, dusk = ref("sunNauticalDawn"), ref("sunNauticalDusk")

    def tick(i, color_major, color_minor):
        return (radial_line(R, TICK_MAJOR_R * R, color_major, 7) if i % 6 == 0
                else radial_line(R, TICK_MINOR_R * R, color_minor, 4))

    on, amb = [], []
    for i in range(24):
        t = 15 * i
        inside = f"(({dawn} < -500) || ({dusk} < -500)) ? 0 : (((({t} - {dawn}) % 360 + 360) % 360 <= {dusk} - {dawn}) ? 1 : 0)"
        for name, color, alpha in [(f"tick_{i:02d}", None, f"({inside}) > 0.5 ? 0 : 255"),
                                   (f"tick_{i:02d}_blk", "#000000", f"({inside}) > 0.5 ? 255 : 0")]:
            line = tick(i, color or p.on["tick_major"], color or p.on["tick_minor"])
            on.append(f'    <PartDraw x="0" y="0" width="{W}" height="{W}" pivotX="0.5" pivotY="0.5" name="{name}" '
                      f'angle="{t}">\n      <Transform target="alpha" value="{a(alpha)}" />\n      {line}\n    </PartDraw>')
        amb.append(full_part_draw(f"tick_{i:02d}_amb", tick(i, p.amb["tick_major"], p.amb["tick_minor"]), static_angle=t))
    return ("    <!-- Hour ticks: interactive (black inside the lit sky), then ambient. -->\n"
            + group("ticks_on", "\n".join(on), variant=ambient(0)) + "\n"
            + group("ticks_amb", "\n".join(amb), alpha=0, variant=ambient(255)))


def noon_line(p):
    """Solar noon: a quiet NOON_W line from the disc rim to the ticks, no label; broken around the date when it falls
    within the date's span at 12."""
    th = ref("solarNoon")
    near = f"(abs({wrap180(th)}) < {f(NOON_BREAK_DEG)})"

    def make(c, sfx):
        parts = [("", DISC * R, TICK_MINOR_R * R, f"({near}) ? 0 : ({shown(['solarNoon'])})"),
                 ("_in", DISC * R, NOON_BREAK_IN, f"({near}) ? ({shown(['solarNoon'])}) : 0"),
                 ("_out", NOON_BREAK_OUT, TICK_MINOR_R * R, f"({near}) ? ({shown(['solarNoon'])}) : 0")]
        return "\n".join(full_part_draw(f"line_solarNoon{k}{sfx}", radial_line(r0, r1, c, NOON_W), angle_expr=th,
                                        alpha_expr=al) for k, r0, r1, al in parts)
    return "    <!-- Solar noon. -->\n" + mode_twins(p, "noon", make, "noon")


def season_d(line_key):
    """Signed angle from a sunrise/sunset line to the season line, [-180, 180)."""
    return wrap180(f"{ref('seasonEvent')} - {ref(line_key)}")


def label_clash(key):
    """True when today's equinox/solstice line is close enough to this sun line that their labels would overlap."""
    d = season_d(key)
    return f"(({ref('seasonEvent')} > -500) && ({ref(key)} > -500) && (abs({d}) < {SEASON_CLASH_DEG}))"


def whiskers(p):
    """Sunrise and sunset lines only (the twilights show as fills): 3px from the centre (under the disc) to the edge."""
    def make(color, sfx):
        return "\n".join(full_part_draw(f"line_{key}{sfx}", radial_line(0, R, color, 3), angle_expr=ref(key),
                                        alpha_expr=shown([key])) for key in ("sunSet", "sunRise"))
    return ("    <!-- Sunrise / sunset lines. -->\n"
            + group(f"whisk_{p.name}_on", make(p.on["whisk"][0], ""), variant=ambient(0)) + "\n"
            + group(f"whisk_{p.name}_amb", make(p.amb["whisk"][0], "_a"), alpha=0, variant=ambient(255)))


def time_label(p, name, theta, color, alpha_expr):
    """HHMM inside the disc on the line's axis at `theta`, reading left-to-right on both halves."""
    flip = f"(sin(rad({theta})) < 0) ? 90 : -90"
    return text_part(name, C - LABEL_BW / 2, C - LABEL_R - LABEL_BH / 2, LABEL_BW, LABEL_BH,
                     LABEL_SIZE, color, hhmm_from_angle(theta), angle_expr=flip, alpha_expr=alpha_expr, font=FONT_LABEL)


def season_marker(p):
    """On the local day of an equinox or solstice: an amber line at the exact moment (its label: sun_labels).
    Interactive only."""
    th = ref("seasonEvent")
    return ("    <!-- Equinox/solstice (only on that day). -->\n"
            + group("season", full_part_draw("line_season", radial_line(0, R, AMBER, 3), angle_expr=th,
                                             alpha_expr=shown(["seasonEvent"])), variant=ambient(0)))


def sun_labels(p):
    """Sunrise/sunset (and equinox/solstice) times inside the disc on their lines' axes; a sun label too close to the
    season label yields. Interactive only."""
    th = ref("seasonEvent")
    out = ["    <!-- Time labels. -->",
           group("label_season", time_label(p, "t_season", th, AMBER, shown(["seasonEvent"])),
                 angle_expr=th, pivot=True, variant=ambient(0))]
    for key in ("sunSet", "sunRise"):
        th = ref(key)
        label = time_label(p, f"t_{key}", th, p.on["label"], f"({label_clash(key)}) ? 0 : ({shown([key])})")
        out.append(group(f"label_{key}", label, angle_expr=th, pivot=True, variant=ambient(0)))
    return "\n".join(out)


def hands(p):
    """24h hour hand and ordinary minute hand: white by day (off-white in ambient), muted grey at night."""
    hour, minute = "[HOUR_0_23_MINUTE]*15 - 180", "[MINUTE]*6"
    out = []
    for suffix, color, alpha, amb in [("", p.on["hand"], 255, 0), ("_amb", p.amb["hand"], 0, 255)]:
        out.append(full_part_draw(f"hand_hour{suffix}", radial_line(HAND_START * R, 0.80 * R, color, 6, cap="ROUND"),
                                  angle_expr=hour, alpha=alpha, ambient_alpha=amb))
        out.append(full_part_draw(f"hand_minute{suffix}", radial_line(HAND_START * R, 0.91 * R, color, 2, cap="ROUND"),
                                  angle_expr=minute, alpha=alpha, ambient_alpha=amb))
    return "    <!-- Hands. -->\n" + "\n".join(out)


def center_disc(p):
    """Black disc over the line roots. No rim: the moon's arc alone marks the ring."""
    rd = DISC * R
    return ("    <!-- Centre disc. -->\n"
            f'    <PartDraw x="0" y="0" width="{W}" height="{W}" name="disc">\n'
            f'      <Ellipse x="{f(C - rd)}" y="{f(C - rd)}" width="{f(2 * rd)}" height="{f(2 * rd)}"><Fill color="#000000" /></Ellipse>\n'
            f'    </PartDraw>')


def moon_ring(p):
    """When the moon is up: a thin arc from moonrise clockwise to moonset on the disc's edge. Nothing else."""
    def make(color, sfx):
        rd = DISC * R
        rise, set_ = ref("moonRise"), ref("moonSet")
        end = f"{set_} + (({set_} < {rise}) ? 360 : 0)"
        return (f'    <PartDraw x="0" y="0" width="{W}" height="{W}" name="moon_arc{sfx}">\n'
                f'      <Transform target="alpha" value="{a(visible(["moonRise", "moonSet"]))}" />\n'
                f'      <Arc centerX="{f(C)}" centerY="{f(C)}" width="{f(2 * rd)}" height="{f(2 * rd)}" '
                f'startAngle="0" endAngle="1">\n'
                f'        <Transform target="startAngle" value="{a(rise)}" />\n'
                f'        <Transform target="endAngle" value="{a(end)}" />\n'
                f'        <Stroke color="{color}" thickness="2" cap="BUTT" />\n'
                f'      </Arc>\n'
                f'    </PartDraw>')
    return "    <!-- Moon arc. -->\n" + mode_twins(p, "moonring", make, "moon")


def tide_marks(p):
    """A dot at each high (just outside the moon ring) and low water (just inside), at its clock position."""
    def make(color, sfx):
        rd = DISC * R
        meta = ref("tideMeta")
        out = []
        for i in range(5):
            for high in (True, False):
                want = (1 if i % 2 == 0 else 0) if high else (0 if i % 2 == 0 else 1)   # highs and lows alternate
                cy = C - rd - TIDE_OFF if high else C - rd + TIDE_OFF
                dot = (f'    <PartDraw x="0" y="0" width="{W}" height="{W}" name="tide{i}_{"hi" if high else "lo"}{sfx}">'
                       f'<Ellipse x="{f(C - TIDE_DOT_R)}" y="{f(cy - TIDE_DOT_R)}" width="{f(2 * TIDE_DOT_R)}" '
                       f'height="{f(2 * TIDE_DOT_R)}"><Fill color="{color}" /></Ellipse></PartDraw>')
                out.append(group(f"tide{i}_{'hi' if high else 'lo'}_g{sfx}", dot,
                                 angle_expr=f"{ref(f'tide{i}')} / 4 - 180",
                                 alpha_expr=f"(floor({meta} / 2) > {i} && ({meta} % 2) == {want}) ? 255 : 0",
                                 pivot=True))
        return "\n".join(out)
    return "    <!-- Tides. -->\n" + mode_twins(p, "tides", make, "tide")


def moon_res(color, day):
    return f"moon_{color[1:].lower()}_{day:02d}"


def moon_marker(p):
    """Moon phase sprite on the disc rim at the moon's transit; sprite by the companion's moon age (fallback: the
    device's MOON_PHASE_POSITION, a float on this watch). White; off-white in ambient."""
    rd, rm = DISC * R, MOON_R
    th = ref("moonTransit")
    age = ref("moonAge")
    exprs = "\n".join(f'          <Expression name="p{d}">clamp(floor(({age} &gt;= 0) ? {age} : [MOON_PHASE_POSITION]), '
                      f'0, {MOON_DAYS - 1}) == {d}</Expression>' for d in range(MOON_DAYS))

    def sprites(color):
        compares = "\n".join(
            f'          <Compare expression="p{d}">'
            f'<PartImage x="0" y="0" width="{n(2 * rm)}" height="{n(2 * rm)}"><Image resource="{moon_res(color, d)}" /></PartImage>'
            f'</Compare>' for d in range(MOON_DAYS))
        return f'        <Condition>\n          <Expressions>\n{exprs}\n          </Expressions>\n{compares}\n        </Condition>\n'

    size = f'x="0" y="0" width="{n(2 * rm)}" height="{n(2 * rm)}"'
    both = (f'        <Group name="moon_on" {size} alpha="255">\n        {ambient(0)}\n' + sprites(p.on["moon_sprite"]) + '        </Group>\n'
            f'        <Group name="moon_amb" {size} alpha="0">\n        {ambient(255)}\n' + sprites(p.amb["moon_sprite"]) + '        </Group>\n')
    # Outer group rotates the marker onto the rim; the inner group counter-rotates so the lit side stays screen-fixed.
    return ("    <!-- Moon marker. -->\n"
            f'    <Group name="moon_marker" x="0" y="0" width="{W}" height="{W}" pivotX="0.5" pivotY="0.5">\n'
            f'      <Transform target="angle" value="{a(th)}" />\n'
            f'      <Transform target="alpha" value="{a(visible(["moonTransit"]))}" />\n'
            f'      <Group name="moon_sprite" x="{n(C - rm)}" y="{n(C - rd - rm)}" width="{n(2 * rm)}" '
            f'height="{n(2 * rm)}" pivotX="0.5" pivotY="0.5">\n'
            f'        <Transform target="angle" value="{a("-" + th)}" />\n'
            + both +
            f'      </Group>\n'
            f'    </Group>')


def time_text(p):
    """HH over MM filling the disc, thin weight; solid in ambient too (thin strokes light few pixels)."""
    bh, bw = TIME_SIZE * 1.1, TIME_SIZE * 1.5

    def make(color, sfx):
        return "\n".join(text_part(f"time_{name}{sfx}", C - bw / 2, C + sgn * TIME_DY - bh / 2, bw, bh, TIME_SIZE, color,
                                   src)
                         for name, src, sgn in [("hh", "[HOUR_0_23_Z]", -1), ("mm", "[MINUTE_Z]", 1)])
    return "    <!-- Time. -->\n" + mode_twins(p, "time", make, "digits")


def curved_text(name, size, color, exprs, template, font=FONT_LABEL):
    """Text curved along a circle of radius DATE_R, centred at 12 o'clock, reading clockwise."""
    params = "".join(f'<Parameter expression="{a(e)}" />' for e in exprs)
    return (f'      <PartText x="0" y="0" width="{W}" height="{W}" name="{name}">\n'
            f'        <TextCircular centerX="{f(C)}" centerY="{f(C)}" width="{f(2 * DATE_R)}" height="{f(2 * DATE_R)}" '
            f'startAngle="-60" endAngle="60" align="CENTER" direction="CLOCKWISE" verticalAlign="CENTER">\n'
            f'          <Font family="{font}" size="{f(size)}" color="{color}">\n'
            f'            <Template>{template}{params}</Template>\n          </Font>\n'
            f'        </TextCircular>\n      </PartText>')


def date_text(p):
    """MM.DD or DD.MM by the user's date order (from the companion); before data arrives, en_US -> MM.DD."""
    def make(color, sfx):
        us = curved_text(f"date_us{sfx}", DATE_SIZE, color, ["[MONTH_Z]", "[DAY_Z]"], "%s.%s")
        other = curved_text(f"date_intl{sfx}", DATE_SIZE, color, ["[DAY_Z]", "[MONTH_Z]"], "%s.%s")
        d = ref("dateDayFirst")
        month_first = f'(({d} > -0.5) ? ({d} < 0.5) : ([LANGUAGE_LOCALE_NAME] == "en_US"))'
        return (f'    <Group name="date{sfx}" x="0" y="0" width="{W}" height="{W}">\n'
                "      <Condition>\n        <Expressions>\n"
                f'          <Expression name="monthFirst"><![CDATA[{month_first}]]></Expression>\n'
                "        </Expressions>\n"
                f'        <Compare expression="monthFirst">\n{us}\n        </Compare>\n'
                f'        <Default>\n{other}\n        </Default>\n'
                "      </Condition>\n    </Group>")
    return "    <!-- Date. -->\n" + mode_twins(p, "date", make, "date")


def no_data_badge(p):
    """Only while the companion hasn't delivered (no location yet, slots empty, or placeholder data)."""
    return ("    <!-- NO DATA. -->\n"
            + text_part("no_data", C - 60, C + 0.88 * R - 8, 120, 16, 11, p.on["nodata"], '"NO DATA"',
                        alpha_expr=f'({ref("moonTransit")} < -500) ? 255 : 0', font=FONT_REGULAR))


# The face editor previews each palette option at PREVIEW_TIME; at 07:40 Auto would look exactly like Day, so the
# Auto preview splits: night on the left, day on the right (owner 2026-11-25). The editor is recognised by its frozen
# clock at PREVIEW_TIME, including an odd second (a real Auto face shows the split for that one second a day).
PREVIEW_HMS = (7, 40, 37)   # = PreviewSky in the companion (07:40)


def preview_split():
    h, m, sec = PREVIEW_HMS
    return (f'([CONFIGURATION.{PALETTE_CFG}] == "auto") && ([HOUR_0_23] == {h}) && ([MINUTE] == {m}) '
            f'&& ([SECOND] == {sec})')


def half_mask(name, left, alpha_expr=None):
    x = 0 if left else C
    tf = f'<Transform target="alpha" value="{a(alpha_expr)}" />' if alpha_expr else ""
    return (f'    <PartDraw x="{n(x)}" y="0" width="{n(C)}" height="{W}" name="{name}" renderMode="MASK">{tf}'
            f'<Rectangle x="0" y="0" width="{n(C)}" height="{W}"><Fill color="#ffffff" /></Rectangle></PartDraw>')


def palette_layers(segments):
    """Every element once per palette, in two groups: the night copy shows at night, the day copy by day. Each group
    is masked to the full face, or to its half while the editor previews Auto (preview_split)."""
    out = []
    for p in PALETTES:
        day = p.name == "day"
        show = f"({preview_split()}) ? 255 : ((({is_day()}) > 0.5) ? {255 if day else 0} : {0 if day else 255})"
        masks = [half_mask(f"mask_{p.name}_own", left=not day),
                 half_mask(f"mask_{p.name}_other", left=day, alpha_expr=f"({preview_split()}) ? 0 : 255")]
        body = "\n\n".join(f"    <!-- {name} -->\n" + "\n\n".join(b(p) for b in builders) for name, builders in segments)
        out.append(group(f"palette_{p.name}", body + "\n" + "\n".join(masks), alpha_expr=show))
    return "\n\n".join(out)


def watchface_xml():
    body = "\n\n".join([
        data_block(),
        palette_layers([("sky", [sky_fills, ticks, noon_line, whiskers, season_marker]),
                        ("hands", [hands]),
                        ("ring", [center_disc, moon_ring, tide_marks, sun_labels]),
                        ("moon", [moon_marker]),
                        ("text", [time_text, date_text, no_data_badge])]),
        center_tap(),
    ])
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by face/tools/gen_face.py. Do not edit by hand. -->
<WatchFace width="{W}" height="{W}">
  <Metadata key="CLOCK_TYPE" value="ANALOG" />
{user_configurations()}
  <Metadata key="PREVIEW_TIME" value="{PREVIEW_HMS[0]:02d}:{PREVIEW_HMS[1]:02d}:{PREVIEW_HMS[2]:02d}" />  <!-- = PreviewSky in the companion -->
  <Scene backgroundColor="#000000">
{body}
  </Scene>
</WatchFace>
"""


# ---- sprites ---------------------------------------------------------------------------------------------------------
def rgb(color):
    return tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))


def moon_sprite(day, color, px=64, ss=4):
    """Black disc, thin outline, lit part in `color`. Waxing lit on the right, waning on the left."""
    size = px * ss
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    rr = size / 2 - 0.5
    outline = size * 0.06
    phi = 2 * math.pi * ((day + 0.5) / SYNODIC % 1.0)   # middle of the day of the moon's age
    k = math.cos(phi)
    for y in range(size):
        dy = (y + 0.5 - size / 2) / rr
        if abs(dy) > 1:
            continue
        hw = math.sqrt(1 - dy * dy)
        for x in range(size):
            dx = (x + 0.5 - size / 2) / rr
            d = math.hypot(dx, dy)
            if d > 1:
                continue
            lit = dx >= k * hw if phi < math.pi else dx <= -k * hw
            pix[x, y] = (*color, 255) if (lit or d > 1 - outline / rr) else (0, 0, 0, 255)
    return img.resize((px, px), Image.LANCZOS)


# ---- companion constants (FaceSpec.kt) -----------------------------------------------------------------------------------
def kotlin_spec():
    """Geometry, palettes and the wire format for the companion (data sources, scrubber, table)."""
    def col(c):
        return f"0xFF{c[1:].upper()}.toInt()"

    def pal(name, pl):
        return (f"    val {name} = Palette(\n"
                f"        fills = intArrayOf({', '.join(col(c) for c in pl['fills'])}),\n"
                f"        whiskers = intArrayOf({', '.join(col(c) for c in pl['whisk'])}),\n"
                f"        tickMajor = {col(pl['tick_major'])}, tickMinor = {col(pl['tick_minor'])}, noon = {col(pl['noon'])},\n"
                f"        label = {col(pl['label'])}, moon = {col(pl['moon'])}, tide = {col(pl['tide'])},\n"
                f"        digits = {col(pl['digits'])}, date = {col(pl['date'])}, scrubDate = {col(pl['scrub_date'])},\n"
                f"        moonSprite = {col(pl['moon_sprite'])},\n"
                f"    )\n")
    codec = "".join(f"    const val {k} = {v}\n" for k, v in CODEC.items())
    sprites = ", ".join(f"{ASTRO_PKG}.R.drawable.moon_{d:02d}" for d in range(MOON_DAYS))   # R alone = the radius here
    return f"""package com.analemmic.nightwatch.astro

// GENERATED by face/tools/gen_face.py (kotlin_spec). Do not edit by hand: change the face generator instead.
// Canvas units: the face is {W} x {W}, centre {f(C)}, radius {f(R)}.

/** One of the face's palettes (interactive mode). fills: astronomical, nautical, civil, day; whiskers: sunrise/sunset,
 *  civil, nautical, astronomical. */
class Palette(
    val fills: IntArray, val whiskers: IntArray, val tickMajor: Int, val tickMinor: Int, val noon: Int,
    val label: Int, val moon: Int, val tide: Int, val digits: Int, val date: Int, val scrubDate: Int,
    val moonSprite: Int,
)

/** The slots' wire format (see SlotCodec and gen_face.py CODEC). */
object Codec {{
{codec}}}

object FaceSpec {{
    const val W = {f(W)}f
    const val C = {f(C)}f
    const val R = {f(R)}f
    const val DISC = {f(DISC)}f
    const val TICK_MINOR_R = {f(TICK_MINOR_R)}f
    const val TICK_MAJOR_R = {f(TICK_MAJOR_R)}f
    const val WHITE = {col(WHITE)}
    const val AMBER = {col(AMBER)}
    /** Night (red, night vision) and day (sunrise -> sunset) palettes. */
{pal("NIGHT", NIGHT)}{pal("DAY", DAY)}    const val LABEL_SIZE = {f(LABEL_SIZE)}f
    /** Time labels sit inside the disc, centred at this radius on their line's axis. */
    const val LABEL_R = {f(LABEL_R)}f
    const val SEASON_CLASH_DEG = {f(SEASON_CLASH_DEG)}f
    const val TIDE_DOT_R = {f(TIDE_DOT_R)}f
    const val TIDE_OFF = {f(TIDE_OFF)}f
    const val MOON_R = {f(MOON_R)}f
    /** The face's moon phase sprites (white), one per started day of the moon's age. */
    val MOON_SPRITES = intArrayOf({sprites})
    /** Centre time digits (the scrubber shows the date here): font size and line offset from the centre. */
    const val TIME_SIZE = {f(TIME_SIZE)}f
    const val TIME_DY = {f(TIME_DY)}f
    /** Bottom date (the scrubber shows the year here). */
    const val DATE_SIZE = {f(DATE_SIZE)}f
    /** The date (scrubber: the year) curves along this radius at 12; solar noon breaks around it within NOON_BREAK_DEG. */
    const val DATE_R = {f(DATE_R)}f
    const val NOON_BREAK_IN = {f(NOON_BREAK_IN)}f
    const val NOON_BREAK_OUT = {f(NOON_BREAK_OUT)}f
    const val NOON_BREAK_DEG = {f(NOON_BREAK_DEG)}f
    const val NOON_W = {f(NOON_W)}f
}}
"""


def strings_xml():
    rows = [f'    <string name="slot_{cls}">Astro: {cls.removesuffix("Source").lower()}</string>' for _, cls, _ in SLOTS]
    rows += [f'    <string name="cfg_{PALETTE_CFG}">Palette</string>']
    rows += [f'    <string name="cfg_{i}">{label}</string>' for i, label, _ in PALETTE_MODES]
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED by face/tools/gen_face.py. Do not edit by hand. -->\n'
            '<resources>\n    <string name="watch_face_name">Nightwatch</string>\n' + "\n".join(rows) + "\n</resources>\n")


def main():
    (RES / "raw" / "watchface.xml").write_text(watchface_xml())
    (RES / "values" / "strings.xml").write_text(strings_xml())
    face_dr, astro_dr = RES / "drawable-nodpi", ASTRO / "res" / "drawable-nodpi"
    face_dr.mkdir(parents=True, exist_ok=True)
    astro_dr.mkdir(parents=True, exist_ok=True)
    for old in face_dr.glob("moon_*.png"):
        old.unlink()
    sprite_colors = sorted({pl["moon_sprite"] for pl in (NIGHT, NIGHT_AMB, DAY, DAY_AMB)})
    for day in range(MOON_DAYS):
        for c in sprite_colors:
            moon_sprite(day, rgb(c)).save(face_dr / f"{moon_res(c, day)}.png")
        moon_sprite(day, rgb(WHITE)).save(astro_dr / f"moon_{day:02d}.png")   # the scrubber tints these by palette
    for old in face_dr.glob("tide_*.png"):   # tides are drawn dots now
        old.unlink()
    kt = ASTRO / "kotlin" / "com" / "analemmic" / "nightwatch" / "astro" / "FaceSpec.kt"
    kt.write_text(kotlin_spec())
    (ASTRO / "res" / "font").mkdir(parents=True, exist_ok=True)
    for ttf in ("jetbrains_mono.ttf", "jetbrains_mono_thin.ttf", "jetbrains_mono_extralight.ttf"):
        (ASTRO / "res" / "font" / ttf).write_bytes((RES / "font" / ttf).read_bytes())
    # preview.png is NOT generated: it is a device screenshot (CLAUDE.md "Picker thumbnail").
    print(f"wrote watchface.xml, {len(sprite_colors) * MOON_DAYS} moon sprites, FaceSpec.kt")


if __name__ == "__main__":
    main()
