# Releasing Nightwatch Companion to internal testing

The companion is a **Wear OS-only** app. Play rejects a Wear OS bundle on the default (phone) track, so **add the
Wear OS form factor with a dedicated track before you upload anything** (step 2). Every time you're on a testing or
release page, check that the form-factor selector at the top right says **Wear OS only**.

Copy-paste text: `play/listing.md`. Images: `play/`. Menu names drift; if one isn't where these steps say, look for
the same words one level up or down.

| What | Value |
|---|---|
| App name | Nightwatch Companion |
| Package | `com.analemmic.nightwatch.astro` |
| Bundle | `astro/build/outputs/bundle/release/astro-release.aab` |
| Icon | `play/companion-icon-512.png` |
| Feature graphic | `play/companion-feature-1024x500.png` |
| Wear OS screenshots | `play/screenshots/companion-scrubber.png`, `companion-table.png` |
| Privacy policy | `https://analemmic.com/nightwatch/privacy.html` |
| Website, email | `https://analemmic.com/nightwatch/`, `dev@analemmic.com` |

## 0. Before you start
- [ ] Bundle is current: ask Claude to rebuild if the code changed since the last build (needs `keystore.properties`).
- [ ] Decide on background location (step 5's "Sensitive app permissions" asks about it). The bundle currently
      requests "Allow all the time". Play then requires a declaration, a short demo video, and an in-app
      explanation shown before the request. Dropping the permission avoids all of that: the companion then updates
      your location whenever you open it (tap the face), and otherwise uses the last one. Decide **before**
      uploading: a bundle with the permission, once uploaded, keeps asking for the declaration.

## 1. Create the app
1. Play Console home -> **Create app**.
2. App name **Nightwatch Companion**; default language **English (United States)**; **App**; **Free**.
3. If it asks for the package name: `com.analemmic.nightwatch.astro` (permanent; copy exactly).
4. Tick the declarations -> **Create app**.

## 2. Add Wear OS with a dedicated track (do this before any upload)
1. Left menu: **Test and release** -> **Advanced settings** (sometimes under **Setup**) -> **Form factors** tab.
2. **Add form factor** -> **Wear OS**. Accept the Wear OS terms.
3. On the Wear OS row, **Manage** -> under Releases choose the **dedicated Wear OS release track** option -> **Save**.
4. Check: on **Testing -> Internal testing**, the selector at the top right now offers **Wear OS only**.

> If you see "remove your Wear OS bundle and create a dedicated Wear OS track", the bundle went to the phone
> track: discard that draft release, do step 2, and upload again on the Wear OS only track.

## 3. Testers
1. **Testing -> Internal testing**, selector **Wear OS only** -> **Testers** tab.
2. Create an email list (e.g. "Nightwatch testers") with your personal Google account -> **Save**.
3. Copy the **join link** for later (step 7).

## 4. Internal release
1. Same page, still **Wear OS only** -> **Create new release**.
2. App signing: accept **Google-managed (Play App Signing)** if asked.
3. **Upload** `astro-release.aab`. It should show package `com.analemmic.nightwatch.astro`, version code 1.
4. Release notes: the companion's block from `play/listing.md`.
5. **Next**. Errors block; warnings (e.g. "no deobfuscation file") are fine for a test.
6. **Save**, then **Start rollout to Internal testing** once step 5's required items are done.

## 5. App content (left menu: **Policy -> App content**, or the dashboard's "Set up your app" list)
- [ ] **Privacy policy**: the URL above.
- [ ] **Ads**: No.
- [ ] **App access**: all functionality available without special access.
- [ ] **Content rating**: questionnaire, category Utility/Other; answer No throughout.
- [ ] **Target audience**: 18 and over (simplest; avoids the children's rules).
- [ ] **Data safety**: does the app collect or share user data? **No**. Location is processed only on the watch and
      never transmitted, which Play doesn't count as "collected". Encryption in transit: not applicable (no network).
- [ ] **Sensitive app permissions -> Location** (only if the bundle keeps background location): purpose text from
      RELEASE.md section 6, plus a video link (≤ 30 s: open the companion -> the explanation -> the "Allow all the
      time" prompt -> the face showing times).
- [ ] Anything else it lists (government app, financial features, health, news): No.

## 6. Store listing (left menu: **Grow users -> Store presence -> Main store listing**)
- [ ] Name, short description, full description: from `play/listing.md` (the full text must contain "Wear OS").
- [ ] App icon, feature graphic: files above.
- [ ] **Wear OS** screenshots section: both companion screenshots. If it also demands phone screenshots, stop and
      ask Claude (Wear-only apps shouldn't need them).
- [ ] Category: Tools (or Weather). Contact email and website: above.
- [ ] **Save**.

## 7. Install it on the watch
1. Uninstall the sideloaded copy first: Play's build is signed with a different key, so it won't install over it
   (`adb uninstall com.analemmic.nightwatch.astro`, or ask Claude). The face keeps working once the Play copy is in.
2. Open the join link (step 3) on your phone or computer, signed in to your personal account -> **Accept**.
3. On the watch: Play Store -> search won't find a test app; use the join link's "download on Google Play" and pick
   the watch as the install target.
4. Open the companion once and allow location.

## Later: closed test and production
Same pattern on **Closed testing** (selector **Wear OS only**): a Google Group for testers, 12+ opted in for 14 days,
then apply for production access. After the first closed-testing release, opt in to Wear OS review under
**Advanced settings -> Form factors** and accept the review policy.
