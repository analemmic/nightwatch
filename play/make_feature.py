"""Play feature graphic (1024x500): the name in the face's font beside a day face and a night face.
Run from the repo root: python3 play/make_feature.py"""
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
FONT = "face/src/main/res/font/jetbrains_mono_extralight.ttf"
im = Image.new("RGB", (W, H), "#000000"); d = ImageDraw.Draw(im)
title = ImageFont.truetype(FONT, 58); sub = ImageFont.truetype(FONT, 21)
left = 48 + d.textlength("Nightwatch", font=title) + 36          # faces start right of the title
face = min(330, int((W - 24 - left - 16) / 2))
day = Image.open("docs/img/day-full.png").convert("RGB").resize((face, face), Image.LANCZOS)
night = Image.open("docs/img/night-full.png").convert("RGB").resize((face, face), Image.LANCZOS)
y = (H - face) // 2
im.paste(day, (int(left), y)); im.paste(night, (int(left) + face + 16, y))
d.text((48, H / 2 - 18), "Nightwatch", font=title, fill="#e8e6e3", anchor="ls")
d.text((50, H / 2 + 28), "sun, moon, and tides", font=sub, fill="#f0b13a", anchor="ls")
im.save("play/nightwatch-feature-1024x500.png", optimize=True)
