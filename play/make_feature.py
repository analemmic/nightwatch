"""Play feature graphics (1024x500): the app name in the face's font beside two watch screenshots.
Run from the repo root: python3 play/make_feature.py (writes both apps' graphics)."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
FONT = "face/src/main/res/font/jetbrains_mono_extralight.ttf"

def feature(lines, subtitle, shots, out):
    im = Image.new("RGB", (W, H), "#000000"); d = ImageDraw.Draw(im)
    title = ImageFont.truetype(FONT, 58); sub = ImageFont.truetype(FONT, 21)
    left = 48 + max(d.textlength(t, font=title) for t in lines) + 36   # shots start right of the title
    face = min(330, int((W - 24 - left - 16) / 2))
    y = (H - face) // 2
    for i, s in enumerate(shots):
        im.paste(Image.open(s).convert("RGB").resize((face, face), Image.LANCZOS), (int(left) + i * (face + 16), y))
    top = H / 2 - 18 - 66 * (len(lines) - 1)
    for i, t in enumerate(lines):
        d.text((48, top + 66 * i), t, font=title, fill="#e8e6e3", anchor="ls")
    d.text((50, H / 2 + 28), subtitle, font=sub, fill="#f0b13a", anchor="ls")
    im.save(out, optimize=True)

feature(["Nightwatch"], "sun, moon, and tides", ["docs/img/day-full.png", "docs/img/night-full.png"],
        "play/nightwatch-feature-1024x500.png")
feature(["Nightwatch", "Companion"], "sun, moon, and tide times",
        ["play/screenshots/companion-scrubber.png", "play/screenshots/companion-table.png"],
        "play/companion-feature-1024x500.png")
