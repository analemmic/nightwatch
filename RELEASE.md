# Releasing to Google Play (pen name, free app)

Plan: a personal Play developer account publishing as Analemmic; free, no ads, no purchases. Play shows the
developer name and email on the listing, and the legal name and country in the developer details (owner accepts
this; the repo, builds, and website still carry only the pen name). Full address and phone appear only for
monetized or trader accounts: declare non-trader (free hobby app). Two listings:
the watch face (`face/`) and the companion (`astro/`); the face needs the companion for data, so cross-link them.

Chosen (2026-11-26): `<pen>` = Analemmic, `<domain>` = analemmic.com (Network Solutions), `<prefix>` = com.analemmic,
GitHub = github.com/analemmic. `<email>` = dev@analemmic.com (ImprovMX forwarding; replies would expose the personal address).

## 1. Decide (one-time)
- [x] Pen name, checked for clashes on Play and the web.
- [x] A domain you control with no real name in it (`<domain>`, e.g. `<pen>.dev`): register it with WHOIS privacy
      on (free at most registrars; check that the registrant name and address are redacted in a public WHOIS lookup),
      paid with auto-renew so it can't lapse and be taken over. It anchors the package names, the contact email and
      the privacy policy URL.
- [x] Email on the domain (`<email>`, e.g. `nightwatch@<domain>`): forward to your inbox (registrar or Cloudflare
      email routing, free) or a mailbox; set up SPF/DKIM so replies don't land in spam.
- [x] A new Google account for the pen name, used for the Play Console: `<email>`.
- [x] Package prefix = the domain reversed: `<prefix>` = `com.analemmic`. Package names are permanent once
      published, and owning the domain keeps the prefix yours.
- [x] Open source: MIT (2026-11-26); public repo github.com/analemmic/nightwatch with a clean single-commit history.

## 2. Remove the real name from everything that ships
- [x] `applicationId` + `namespace` in face/ and astro/ build.gradle.kts -> `<prefix>.nightwatch.face` / `.astro`;
      move the Kotlin package (now com.analemmic.nightwatch.astro) to match; ASTRO_PKG in gen_face.py; regenerate.
- [x] Copyright: `copyright` string (setup screen), LICENSE (MIT), licenses/NOTICE.txt, res/raw/licenses.txt -> `<pen>`.
- [x] PRIVACY.md contact -> `<email>`.
- [ ] Upload key: generate with `-dname "CN=<pen>"` (the certificate's name is visible to anyone who inspects the APK).
- [ ] If open-sourcing: rewrite git author/committer to `<pen>` / `<email>` before the first push (commits before
      2026-11-26 carry the old placeholder identity; later ones use Analemmic and the GitHub noreply address).
- [ ] Grep the repo and both built bundles for the real name and email before every upload:
      `unzip -p astro-release.aab | strings | grep -iE "$REAL_NAME"` (likewise for the face; set REAL_NAME in your shell, never in the repo).
- [x] Your own watch: old sideloaded apps uninstalled, new package names installed (2026-11-26).

## 3. Privacy policy page
- [x] Privacy policy: https://analemmic.com/nightwatch/privacy.html (docs/privacy.html on GitHub Pages; the site is
      https://analemmic.com/nightwatch/), HTTPS live.

## 4. Play Console account
- [x] Google account for the Console: `<email>` (2026-11-26; its own Chrome profile).
- [ ] Sign up at play.google.com/console with it: personal account, $25 once.
- [ ] Identity verification with your legal name and ID (the legal name and country are shown on Play), plus
      device verification with the Play Console app on an Android phone.
- [ ] Trader status: non-trader (free, no monetization), so no public phone or address.
- [ ] Developer name `<pen>`, contact email `<email>`, website `https://<domain>/nightwatch`.

## 5. Build the release
- [ ] Upload key (git-ignored, backed up outside the project, not in the Drive zip):
      `keytool -genkeypair -v -keystore ../nightwatch-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=<pen>"`
      then `cp keystore.properties.example keystore.properties` and fill in the passwords.
- [ ] Each release: bump `versionCode` (+1) and `versionName` in both build.gradle.kts; `python3 face/tools/gen_face.py`;
      `./gradlew :astro:testDebugUnitTest`; `python3 face/tools/test_gen_face.py`;
      `./gradlew :face:bundleRelease :astro:bundleRelease` ->
      face/build/outputs/bundle/release/face-release.aab, astro/build/outputs/bundle/release/astro-release.aab.
- [ ] Release builds exclude src/debug (RefreshReceiver: adb refresh and simulation); confirm the scrubber,
      table and setup work without it.

## 6. Create the two apps in the Console
- [ ] Create "Nightwatch" (face) and "Nightwatch Astro" or similar (companion): free, app, not a game.
- [ ] Enroll both in Play App Signing (Google holds the signing key; ours is only the upload key).
- [ ] Form factors: add Wear OS for both (Wear OS gets its own review).
- [ ] Store listing: short and full description (include "Not for navigation"), app icon 512x512, feature graphic
      1024x500, Wear OS screenshots (square, from `adb exec-out screencap`; day, night, scrubber, table).
- [ ] App content forms, both apps: privacy policy URL; ads: none; content rating questionnaire; target audience
      (adults, not for children); data safety.
- [ ] Data safety (companion): approximate location is accessed but processed only on the watch and never
      transmitted (check Play's current definition of "collected"); no other data. Face: no data.
- [ ] Background location declaration (companion), or drop ACCESS_BACKGROUND_LOCATION to skip it (then times
      follow the location saved on the setup screen). Text: "Nightwatch Astro is a complication data source for the
      Nightwatch watch face. It recalculates sunrise, twilight, moon and tide times from the watch's approximate
      location while the watch face is displayed, so the times stay correct when the user travels. Optional:
      without it, the last location saved in the setup screen is used." Video: setup screen -> Allow all the time
      -> face showing the times.
- [ ] Licenses are shown in the app (Setup > Licenses); text in licenses/.

## 7. Closed test (required for new personal accounts: 12+ testers opted in for 14 days in a row)
- [ ] Make a Google Group for testers; add the group as the closed track's tester list (members join the group, no
      per-person edits).
- [ ] Upload both bundles to the closed track; send testers the opt-in link (web) and the setup notes: install the
      face, then the companion, allow location, pick the face.
- [ ] Keep at least 12 opted in for 14 continuous days (recruit 15+ for slack); collect feedback; ship fixes to the
      closed track as needed.
- [ ] Optional first: an internal test track (up to 100 testers, no review) to check the Play install on your
      own watch before the closed test starts.

## 8. Production
- [ ] Apply for production access in the Console (a short questionnaire about the closed test).
- [ ] Promote both releases to production; review usually takes a few days, longer with background location.
- [ ] Cross-link the listings (each description names the other).
- [ ] A new or wiped watch is then: install Nightwatch from Play, install the companion, allow location.
