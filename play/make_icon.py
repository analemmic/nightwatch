"""Play listing icon for the Nightwatch face (512x512 PNG): a schematic 24-hour dial in the night palette.
Day band on top between the sunrise and sunset lines, night below, hour ticks at 00/06/12/18, the moon, and the
24-hour hand. Run from the repo root: python3 play/make_icon.py"""
import math
from PIL import Image, ImageDraw

B = 2048; C = B / 2; R = B * 0.40          # drawn 4x, downsampled for smooth edges
SR, SS, HAND, MOON = 6.5, 17.5, 15.2, 1.5  # sunrise, sunset, hour hand, moon (clock hours)
NIGHT, TWILIGHT, DAY = "#160403", "#3a0b08", "#8a1a12"
LINE, TICK, HAND_C, MOON_C = "#ff2a1a", "#c41a10", "#b8b3ad", "#9d9893"

def deg(t): return 15 * (t - 12) - 90       # clock hour -> PIL angle (noon at the top)
def pt(t, r): a = math.radians(deg(t)); return (C + r * math.cos(a), C + r * math.sin(a))

im = Image.new("RGB", (B, B), "#000000"); d = ImageDraw.Draw(im)
def disc(r, col, at=(C, C)): x, y = at; d.ellipse([x - r, y - r, x + r, y + r], fill=col)
def sector(t0, t1, col): d.pieslice([C - R, C - R, C + R, C + R], deg(t0), deg(t1), fill=col)
def line(t, r0, r1, w, col):
    (x0, y0), (x1, y1) = pt(t, r0), pt(t, r1)
    d.line([x0, y0, x1, y1], fill=col, width=int(w)); disc(w / 2, col, (x0, y0)); disc(w / 2, col, (x1, y1))

disc(R, NIGHT); sector(SR - 1.3, SS + 1.3, TWILIGHT); sector(SR, SS, DAY)
for h in (0, 6, 12, 18): line(h, R * 0.84, R * 0.96, B * 0.024, TICK)
for t in (SR, SS): line(t, 0, R, B * 0.024, LINE)
disc(B * 0.04, MOON_C, pt(MOON, R * 0.72))
line(HAND, 0, R * 0.82, B * 0.05, HAND_C); disc(B * 0.036, HAND_C)
im.resize((512, 512), Image.LANCZOS).save("play/nightwatch-icon-512.png", optimize=True)
