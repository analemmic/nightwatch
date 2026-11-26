# Nightwatch

A 24-hour watch face for Wear OS: the sun, moon, and tides at a glance.

<p>
  <img src="docs/img/day-full.png" width="200" alt="Nightwatch by day">
  <img src="docs/img/night-full.png" width="200" alt="Nightwatch at night">
</p>

Website: [analemmic.com/nightwatch](https://analemmic.com/nightwatch/)

## What's here

- `face/`: the watch face, in Watch Face Format. `face/tools/gen_face.py` generates it.
- `astro/`: the companion app. It calculates sun, moon, and tide times on the watch and sends them to the face.
- `docs/`: the website.

## Build

Needs JDK 17+, the Android SDK, and Python 3 with Pillow. The face needs Wear OS 6.

```sh
python3 face/tools/gen_face.py
./gradlew :face:assembleDebug :astro:assembleDebug
```

Install both APKs on the watch, then choose Nightwatch as the watch face.

## License

[MIT](LICENSE) © 2026 Analemmic. Third-party components keep their own licenses: see
[licenses/NOTICE.txt](licenses/NOTICE.txt).

Not for navigation. Questions: dev@analemmic.com.
