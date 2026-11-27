# Google Play listing text

Copy-paste source for the Play Console. Limits: short description 80 characters, full description 4,000, release
notes 500 per language. Plain words, Oxford comma; don't mention the equinox/solstice line.

Assets in this folder: icons `nightwatch-icon-512.png` (make_icon.py) and `companion-icon-512.png`; feature graphics
`nightwatch-feature-1024x500.png` and `companion-feature-1024x500.png` (make_feature.py); Wear OS screenshots in
`screenshots/` (simulated location). Play requires the words "Wear OS" in each full description. Privacy policy: https://analemmic.com/nightwatch/privacy.html

---

## Nightwatch (watch face): `com.analemmic.nightwatch.face`

### Short description
```
A 24-hour watch face: the sun, moon, and tides at a glance.
```

### Full description
```
Nightwatch shows your whole day on one 24-hour dial, with noon at the top and midnight at the bottom.

• Sun: daylight and twilight shading, sunrise and sunset times, and solar noon
• Moon: an arc shows when the moon is up, with its current phase
• Tides: dots mark high and low tide at the nearest tide gauge
• Day and night: switches at sunrise and sunset; night mode is all red to save your night vision. Or choose Day or Night in the face's settings.
• Any day: tap the centre and turn the crown to see other days, then tap again for the day's times, including tide heights

Private: everything is calculated on your watch, and nothing leaves it. No accounts, ads, or tracking.

Requires the free Nightwatch Companion app, which does the calculations. Install both, open the companion once to allow location, then choose Nightwatch as your watch face. Without location access, Nightwatch uses your time zone's main city.

Needs Wear OS 6 or later.

Times are calculated estimates and are not suitable for navigation.

Open source: github.com/analemmic/nightwatch
```

### Release notes: first test release
```
<en-US>
First test release of Nightwatch, a 24-hour watch face showing the sun, moon, and tides at a glance.
Install Nightwatch Companion too: it calculates the times on your watch. Then allow location (or it uses your time zone's main city).
Tap the centre to browse other days. Times are estimates, not for navigation.
</en-US>
```

---

## Nightwatch Companion: `com.analemmic.nightwatch.astro`

### Short description
```
Calculates sun, moon, and tide times for the Nightwatch watch face.
```

### Full description
```
Nightwatch Companion is a Wear OS app that does the astronomy for the Nightwatch watch face. It calculates sunrise, sunset, twilight, moonrise and moonset, moon phase, and high and low tides on your watch, and provides them to the face as complications.

• Works offline, worldwide, with tide predictions from about 2,800 tide gauges
• Private: your location never leaves your watch. No network access, accounts, ads, or tracking.
• Optional "Allow all the time" location keeps the times right when you travel. Without location access, it uses your time zone's main city.
• Also opens the day browser and the table of the day's times from the watch face

Install it together with the Nightwatch watch face. Needs Wear OS 6 or later.

Times are calculated estimates and are not suitable for navigation.

Open source: github.com/analemmic/nightwatch
```

### Release notes: first test release
```
<en-US>
First test release of Nightwatch Companion. It calculates sunrise, sunset, twilight, moon, and tide times on your watch for the Nightwatch watch face. Nothing leaves your watch.
Open it once to allow location (or it uses your time zone's main city). Times are estimates, not for navigation.
</en-US>
```
