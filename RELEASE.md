# Releasing to Google Play (pen name, free app)

Plan: a personal Play developer account publishing as Analemmic; free, no ads, no purchases. Play shows the
developer name and email on the listing, and the legal name and country in the developer details (owner accepts
this; the repo, builds, and website still carry only the pen name). Full address and phone appear only for
monetized or trader accounts: declare non-trader (free hobby app). Two listings:
the watch face (`face/`) and the companion (`astro/`); the face needs the companion for data, so cross-link them.

Chosen (2026-11-26): `<pen>` = Analemmic, `<domain>` = analemmic.com (Network Solutions), `<prefix>` = com.analemmic,
GitHub = github.com/analemmic. `<email>` = dev@analemmic.com (ImprovMX forwarding; replies would expose the personal address).

Step-by-step Console guide (written for the companion, same for the face): `play/companion-release.md`. Listing
text and release notes: `play/listing.md`. Listing art and the background-location demo video: `play/`.

## Status (2026-11-27)
- Both apps exist in the Play Console with Wear OS dedicated tracks, listings, and content forms.
- **Internal testing**: both live (version code 1), installed from Play on the owner's watch.
- **Closed testing: in review** (sent 2026-11-27): companion version 2 (1.1.0, new setup screen, background-location
  declaration + demo video) and face version 1 (promoted from internal). Tester group added to both closed tracks.
- GitHub `main` = the code under review (local tag `play-closed-2`).
- Next: when approved, recruit 12+ testers (join the group, then each app's closed-test link) and keep them for 14
  days; then apply for production.

## Lessons (cost hours the first time)
- **Add the Wear OS form factor with a dedicated track before any upload.** A Wear OS bundle on the phone track is
  rejected ("create a dedicated Wear OS track"); every testing page has a Phone / Wear OS only selector.
- **WFF version**: Play rejects features newer than the manifest's declared WFF version (verticalAlign is v5; we
  stay on v4 for all Wear OS 6 watches). test_gen_face.py guards this.
- **Testers are per app and per track**: a track with no tester list ticked shows Inactive and its join link says
  "not available". Internal testing skips review (temporary app name "(unreviewed)"); closed testing is reviewed.
- **Play-signed and sideloaded builds can't replace each other** (different signing keys): uninstall first. Each
  reinstall of the companion can unbind the face's complications ("NO DATA"): reinstall the face too.

## 1. Decide (one-time)
- [x] Pen name, checked for clashes on Play and the web.
- [x] A domain you control with no real name in it: analemmic.com, WHOIS privacy on, auto-renew.
- [x] Email on the domain: dev@analemmic.com, ImprovMX forwarding (SPF set).
- [x] A new Google account for the pen name, used for the Play Console: `<email>`.
- [x] Package prefix = the domain reversed: `com.analemmic`. Package names are permanent once published.
- [x] Open source: MIT (2026-11-26); public repo github.com/analemmic/nightwatch with a clean single-commit history.

## 2. Remove the real name from everything that ships
- [x] `applicationId` + `namespace` -> `com.analemmic.nightwatch.face` / `.astro`; Kotlin package and ASTRO_PKG moved.
- [x] Copyright: `copyright` string (setup screen), LICENSE (MIT), licenses/NOTICE.txt, res/raw/licenses.txt -> `<pen>`.
- [x] PRIVACY.md contact -> `<email>`.
- [x] Upload key generated with `-dname "CN=Analemmic"` (2026-11-27).
- [x] Public git history: one clean first commit as Analemmic; old history only on the local branch `private-history`.
- [ ] Every upload: grep the bundles for the real name and email:
      `unzip -p astro-release.aab | strings | grep -iE "$REAL_NAME"` (likewise for the face; set REAL_NAME in your
      shell, never in the repo). Done for version 1 (both) and version 2 (companion). The tide data contains a gauge
      named "Wolfe Tone Bridge": a place name, not a leak.
- [x] Your own watch: old sideloaded apps uninstalled, new package names installed (2026-11-26).

## 3. Privacy policy page
- [x] Privacy policy: https://analemmic.com/nightwatch/privacy.html (docs/privacy.html on GitHub Pages; the site is
      https://analemmic.com/nightwatch/), HTTPS live.

## 4. Play Console account
- [x] Google account for the Console: `<email>` (2026-11-26; its own Chrome profile).
- [x] Sign up at play.google.com/console with it: personal account, $25 once (2026-11-26).
- [x] Identity and device verification (developer account active 2026-11-27).
- [ ] Confirm: trader status non-trader; developer page name Analemmic, email `<email>`, website
      `https://<domain>/nightwatch`, icon and header (analemmic.github.io repo, `brand/`).

## 5. Build the release
- [x] Upload key: `../nightwatch-upload.jks` (outside the repo) with its password in `keystore.properties`
      (git-ignored). Back both up to a password manager; never in git or the Drive zip.
- [ ] Each release: bump `versionCode` (+1) and `versionName` in the changed app's build.gradle.kts;
      `python3 face/tools/gen_face.py`; `./gradlew :astro:testDebugUnitTest`; `python3 face/tools/test_gen_face.py`;
      `./gradlew :face:bundleRelease :astro:bundleRelease` ->
      face/build/outputs/bundle/release/face-release.aab, astro/build/outputs/bundle/release/astro-release.aab.
      Versions so far: face 1 (1.0.0); companion 1 (1.0.0), 2 (1.1.0).
- [x] Release builds exclude src/debug (RefreshReceiver); the Play-installed internal builds work.
- [x] For future automatic publishing (GitHub Actions -> Play Developer API): GitHub environment `play`, usable only
      by workflows on `main`, holds secrets `UPLOAD_KEYSTORE_BASE64` (the .jks, base64) and
      `UPLOAD_KEYSTORE_PASSWORD` (store and key), and variable `UPLOAD_KEY_ALIAS` = `upload` (2026-11-27). Still to
      do when we automate: a Play service account (secret `PLAY_SERVICE_ACCOUNT_JSON`), Gradle signing from those
      env vars when keystore.properties is absent, and the workflow itself (publish to the Wear OS closed track;
      production stays a manual promotion).

## 6. Create the two apps in the Console
- [x] "Nightwatch" (face) and "Nightwatch Companion": free, app.
- [x] Play App Signing (Google holds the signing key; ours is only the upload key).
- [x] Form factors: Wear OS with a dedicated track, for both.
- [x] Store listing text (`play/listing.md`, mentions "Wear OS"), icons, feature graphics, Wear OS screenshots.
- [x] App content forms, both apps: privacy policy URL; ads: none; app access: no restrictions; content rating;
      target audience; data safety (location processed only on the watch, never transmitted).
- [ ] Background location declaration (companion; required: following the wearer when they travel is the point of
      the app). Answers in the session notes (main purpose; one feature: the face's times follow you while the app
      is closed). Video: `play/background-location-demo-2.mp4` (local only, git-ignored; 27 s: disclosure, runtime prompts, the face updating
      in the background, the day browser and table), hosted on the dev@analemmic.com Drive or YouTube (unlisted).
- [x] Licenses are shown in the app (Setup > Licenses); text in licenses/.

## 7. Closed test (required for new personal accounts: 12+ testers opted in for 14 days in a row)
- [x] Internal testing first (no review): both apps, version 1, installed from Play (2026-11-27).
- [x] Google Group for testers: nightwatch-dev-testers@googlegroups.com (anyone can join; members hidden).
- [x] Add the group to both apps' **closed** tracks (Wear OS only), Testers tab.
- [x] Companion version 2 release on the closed track: sent for review (2026-11-27).
- [x] Face: version 1 promoted to the closed track; sent for review (2026-11-27).
- [ ] Testers: join the group, then each app's closed-test link; install the companion, then the face; allow location.
- [ ] Keep at least 12 opted in for 14 continuous days (recruit 15+ for slack); collect feedback; ship fixes to the
      closed track as needed.

## 8. Production
- [ ] Apply for production access in the Console (a short questionnaire about the closed test).
- [ ] Promote both releases to production; review usually takes a few days, longer with background location.
- [ ] Cross-link the listings (each description names the other).
- [ ] A new or wiped watch is then: install Nightwatch from Play, install the companion, allow location.
