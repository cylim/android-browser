# Google Play release checklist

This checklist takes Okapi, a browser by JRNY, from no Play presence to a production release on Google Play. Work through the sections in order. A step says so when it can run in parallel with another section.

A step marked `TODO(owner)` is one that only the repository owner can do or decide: it needs their identity, money, devices, keys or judgment. Everything else can be done by any contributor in a pull request.

This repository is public. Names, email addresses, phone numbers, postal addresses, account IDs, keystores and passwords go into Play Console, GitHub secrets or the owner's own machine. They never go into this file or any other file in the repository.

## Where things stand on 2026-11-17

- [ROADMAP.md](../ROADMAP.md) lists "Google Play release" under "After v1". Milestone 6 comes first: a signed APK on GitHub Releases, built by GitHub Actions, and a check against F-Droid's requirements. Milestone 6 is not started.
- No release signing key exists. [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md) says release signing keys live only in encrypted GitHub Actions secrets and on the maintainer's machine.
- `android/app/build.gradle.kts` has `versionCode = 1`, `versionName = "0.1.0"`, `minSdk = 35`, `targetSdk = 37` and `compileSdk = 37`. It has no `signingConfigs`. The `release` build type turns on R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with only the default ProGuard file.
- CI (`.github/workflows/ci.yml`) builds and tests only the debug build. Nothing in the repository shows that a release build has ever run on a device.
- `.gitignore` already ignores `*.jks`, `*.keystore`, `*.p12`, `keystore.properties`, `signing.properties`, `*.aab` and `*.apk`, and CI runs gitleaks over the full history. Those catch mistakes. They don't replace keeping the keystore outside the repository folder.
- The targetSdk requirement is met. Since 31 August 2026, new Play apps must target API 36 or higher, and Okapi targets API 37.

## 1. Decide the signing key before Milestone 6 ships

This decision comes first because it is hard to reverse. Once the first signed APK is on GitHub Releases, and once Play App Signing is set up, the keys are fixed. Android installs an update only if it carries the same signing certificate as the installed app.

That matters more for Okapi than for most apps. `android:allowBackup="false"` and the data extraction rules keep every Profile, Tab, bookmark and sign-in on the device. A person who switches between a Play install and a GitHub install signed with a different key has to uninstall first, and uninstalling erases all of it.

The three workable options:

| Option | App signing key on Play | GitHub Releases APK | Can Play and GitHub installs update each other? |
| --- | --- | --- | --- |
| A. One owner key | The owner generates key K and uploads it to Play ("Export and upload a key", with Google's PEPK tool). A separate upload key U signs what goes to Play. | Signed with K | Yes |
| B. Separate keys | Google generates it | Signed with the owner's key G | No |
| C. Play's key everywhere | Google generates it | The owner downloads Play's signed universal APK from Play Console for each release and attaches it to the GitHub Release | Yes, but GitHub releases wait for Play, and GitHub Actions can't produce the final APK |

This checklist recommends option A. It is the only option where a person can move between Play and GitHub builds without losing their Profiles. Its cost is real, though. K can't be reset if it is lost or leaked, and every future GitHub and Play update depends on it. Option B leaves Play's app signing key with Google, so a mistake with the owner's keys can't strand Play users. The GitHub key G is still irreplaceable for GitHub users.

- [ ] `TODO(owner)`: choose option A, B or C. Record the choice and the reason in a new ADR before the first signed APK goes on GitHub Releases.
- [ ] `TODO(owner)`: if you choose A, decide whether K signs in GitHub Actions (K stored as a GitHub secret, as ADR 0002 allows) or only on your own machine.

[How this relates to Milestone 6](#how-this-relates-to-milestone-6) covers F-Droid and Android developer verification, which also depend on this choice.

## 2. Create the developer account

- [ ] `TODO(owner)`: choose a personal or an organization account. Okapi is not a financial, health, VPN or government app, so either type is allowed.
  - A personal account created after 13 November 2023 must run a closed test with at least 12 testers for 14 consecutive days before it can publish to production (section 12).
  - An organization account skips that test but needs a D-U-N-S number whose name and address match the Dun & Bradstreet record exactly. The number is free and can take up to 30 days.
- [ ] `TODO(owner)`: register in Play Console and pay the one-time US$25 fee. Google doesn't accept prepaid cards. You must be at least 18 and accept the Developer Distribution Agreement.
- [ ] `TODO(owner)`: complete identity verification. Google may ask for a government ID and a credit card in your legal name. If the details are invalid, Google doesn't refund the fee.
- [ ] `TODO(owner)`: enter the developer details, and verify the email and phone with the one-time codes. Know what goes public first:
  - Personal account: Google Play shows the developer name, legal name, country and developer email. The full address shows only for a monetized app, and the phone only for a trader. Okapi is not monetized.
  - Organization account: Google Play shows the legal name, D-U-N-S number, legal address, developer email and developer phone.
- [ ] `TODO(owner)`: for a personal account, verify that you have an Android device, using the Play Console app on a physical device. Google's page doesn't say whether an emulator works.

## 3. Fix what the review and the Data safety form depend on

Do these before any upload to a testing track. The Data safety answers and the privacy policy describe what the app sends, so the app has to be right first.

- [x] Turn off WebView usage statistics. `android/app/src/main/AndroidManifest.xml` sets `android.webkit.WebView.MetricsOptOut` to `true` inside `<application>`, and `ManifestTest.webViewMetricsAreOptedOut` checks it on a device. The opt-out doesn't stop WebView crash reports, which contain no URLs.
- [x] Fix the bug where search suggestions sent credential-shaped text before the `@` was typed. `AddressInput.isSearch` now keeps text with a colon before its first `/`, `?` or `#` on the device. Tests: `AddressInputTest` and `BrowserStateTest.whatIsTypedGoesToTheSearchEngineOnlyWhenItMay`.
- [x] Write the privacy policy. The canonical text is [privacy-policy.md](privacy-policy.md), and `website/public/privacy.html` publishes it. It covers on-device data per Profile, Private Tabs, search suggestions, Safe Browsing, downloads, site icons, WebView's own reports, deletion, and what the app doesn't have.
- [ ] Merge `website/public/privacy.html`, so `.github/workflows/pages.yml` publishes it at `https://cy.my/android-browser/privacy.html`.
- [ ] `TODO(owner)`: add the developer name, exactly as Google Play shows it, to both copies of the policy. Each copy marks the place with `TODO(owner)`.
- [x] Replace the last paragraph of "Android System WebView's own reports" in both copies, now that the MetricsOptOut fix is in.
- [x] Delete the "Known bug" paragraph from both copies, now that the suggestion bug is fixed.
- [ ] `TODO(owner)`: resolve the two open checks in [privacy-policy.md](privacy-policy.md). One is whether androidx.webkit's `setRequestedWithHeaderOriginAllowList` can stop the `X-Requested-With` header. The other is whether androidx.emoji2's `EmojiCompatInitializer`, which the merged manifest registers, fetches an emoji font from Google Play Services. Change the policy, the listing and the website to match the result.
- [ ] Before you enter the policy's address in Play Console, check that no placeholder is left. This command must print nothing:

    ```sh
    grep -n "TODO(owner)" docs/playstore/privacy-policy.md website/public/privacy.html
    ```

- [ ] `TODO(owner)`: confirm the privacy contact. Both copies of the policy use GitHub issues for questions and the private **Report a vulnerability** route from SECURITY.md for privacy problems. Change both copies if you want an email address instead.
- [x] Make the home page agree with `privacy.html` about telemetry. With the MetricsOptOut fix in, the "No telemetry" claims in `website/public/index.html` and README.md are accurate. The home page says that WebView's crash reports follow the device's setting.
- [ ] Link the privacy policy from inside the app, for example from Settings. Play requires a link or text in the app, and the app has none yet.
- [ ] `TODO(owner)`: decide whether search suggestions need a prominent in-app disclosure, such as a first-run notice or a line next to the setting. Play requires one when data handling is outside a person's reasonable expectation. Google's pages don't settle this for browser suggestions.

## 4. Generate the keys

Run these on your own machine, in a folder outside the repository. Nothing in this section belongs in a pull request.

- [ ] `TODO(owner)`: generate the upload key. Play needs RSA of 2048 bits or more and a validity that ends after 22 October 2033. Google recommends 25 years or more, and 10,000 days is about 27 years.

    ```sh
    keytool -genkeypair -v -keystore ~/keys/jrny-upload.jks -alias upload \
        -keyalg RSA -keysize 4096 -validity 10000
    ```

    Use one password for the keystore and the key. keytool's default PKCS12 keystore ignores a separate key password.
- [ ] `TODO(owner)`: if you chose option A, generate the app signing key K the same way, in a separate keystore with a different alias and password. K also signs the GitHub Releases APK.
- [ ] `TODO(owner)`: back up each keystore and its password in two places, for example a password manager and an offline copy. A lost upload key can be reset through Play Console. A lost K can't, and every GitHub and F-Droid install would be stranded.
- [ ] `TODO(owner)`: record each key's SHA-256 fingerprint. Fingerprints are public and can go in the repository, for example in the release workflow, to check signatures.

    ```sh
    keytool -list -v -keystore ~/keys/jrny-upload.jks -alias upload
    ```

- [ ] `TODO(owner)`: create a GitHub environment named `release` (Settings → Environments). Add yourself as a required reviewer, and allow only `v*` tags to deploy to it.
- [ ] `TODO(owner)`: store the upload key as environment secrets. `gh secret set` reads the value from standard input or prompts for it, so the secret never appears in a command line.

    ```sh
    base64 -w0 ~/keys/jrny-upload.jks | gh secret set JRNY_UPLOAD_KEYSTORE_B64 --env release
    gh secret set JRNY_UPLOAD_KEYSTORE_PASSWORD --env release
    gh secret set JRNY_UPLOAD_KEY_ALIAS --env release
    ```

- [ ] `TODO(owner)`: if K signs in GitHub Actions, store it the same way as `JRNY_APP_KEYSTORE_B64`, `JRNY_APP_KEYSTORE_PASSWORD` and `JRNY_APP_KEY_ALIAS`.

## 5. Change the build

These are changes for a pull request. This checklist describes them and doesn't make them.

- [ ] Add a release `signingConfig` that reads environment variables and applies only when they are set. Without the variables, `bundleRelease` and `assembleRelease` must still build an unsigned release, because contributors and F-Droid build without the owner's key. A sketch, not tested:

    ```kotlin
    android {
        signingConfigs {
            create("release") {
                storeFile = providers.environmentVariable("JRNY_SIGNING_KEYSTORE").orNull?.let(::file)
                storePassword = providers.environmentVariable("JRNY_SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("JRNY_SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("JRNY_SIGNING_STORE_PASSWORD").orNull
            }
        }
        buildTypes {
            release {
                if (providers.environmentVariable("JRNY_SIGNING_KEYSTORE").isPresent) {
                    signingConfig = signingConfigs.getByName("release")
                }
                // existing isMinifyEnabled, isShrinkResources and proguardFiles stay
            }
        }
    }
    ```

    One set of variable names keeps one `signingConfig`. The workflow points them at the upload key for `bundleRelease` and, under option A, at K for `assembleRelease`. `providers.environmentVariable` keeps the build compatible with the configuration cache, which `android/gradle.properties` turns on.
- [ ] Build an Android App Bundle for Play: `./gradlew bundleRelease` in `android/`. Play accepts only AABs from new apps. The GitHub Releases APK comes from `assembleRelease`.
- [ ] Keep one `versionCode` sequence for every channel. Bump it by hand in `android/app/build.gradle.kts` in each release pull request, together with `versionName` and CHANGELOG.md.
  - Play rejects any upload whose `versionCode` it has seen before, on any track, even a discarded one. So every upload, internal testing included, needs a new `versionCode`.
  - Android refuses to downgrade. Under options A and C, a Play update can replace a GitHub install only if its `versionCode` is higher.
  - F-Droid builds from source and reads the version from the Gradle file, so a number computed in CI would be invisible to it.
- [ ] Keep `android/app/build/outputs/mapping/release/mapping.txt` for every release. Users paste stack traces into GitHub issues (ADR 0002), and a stack trace from an R8 build needs this file to read. AGP also puts it in the bundle's metadata for Play Console's crash reports. Confirm in Play Console under Android vitals that crashes are deobfuscated.
- [ ] Check the merged release manifest for permissions: `android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`. A local build on 2026-11-17 had `INTERNET` and androidx's `app.jrny.browser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and no `com.google.android.gms.permission.AD_ID`. The web capabilities change adds `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`, which are expected. Re-check on the build you upload, because a dependency update can add permissions.
- [ ] Check 16 KB page alignment in the final AAB, for example with Android Studio's APK Analyzer or bundletool. Okapi has no native code of its own, but an AndroidX dependency ships `libandroidx.graphics.path.so`. A local release build showed 16 KB alignment for the arm64-v8a and x86_64 copies. From 1 February 2027, Play rejects updates that don't support 16 KB pages.

## 6. Test the R8 release build on a device

R8 removes and renames code in the release build. Anything reached through reflection can break only there, and CI's device tests run against the debug build. Test the bundle itself, because that is what Play receives.

- [ ] Build the bundle and install it on the phone emulator with bundletool. Without signing flags, bundletool signs with the local debug keystore. The debug and release builds share `app.jrny.browser`, so uninstall first. That erases Okapi's data on the emulator.

    ```sh
    cd android && ./gradlew bundleRelease
    adb -s <serial> uninstall app.jrny.browser
    bundletool build-apks --bundle app/build/outputs/bundle/release/app-release.aab \
        --output /tmp/jrny.apks --connected-device --device-id <serial>
    bundletool install-apks --apks /tmp/jrny.apks --device-id <serial>
    ```

- [ ] Smoke-test on the phone emulator, then on the tablet emulator in both orientations:
  - Create, switch, rename and delete Profiles. Sign in to the same site with a different Account in two Profiles.
  - Open several Tabs, kill the app (`adb -s <serial> shell am kill app.jrny.browser` after sending it to the background), reopen it, and check that the Tabs come back.
  - Move a Tab and Open in Profile.
  - Send an Incoming Link (`adb -s <serial> shell am start -a android.intent.action.VIEW -d https://example.com`) with and without a matching Site Rule.
  - History, bookmarks, Pinned Tabs, Folders and dragging a Pinned Tab.
  - Download a file, open it, and cancel a running download.
  - Open and close a Private Tab. Check that screenshots are blocked.
  - Change the search engine, type to get suggestions, turn suggestions and Safe Browsing off and on.
  - Site icons in the Tab list.
  - The Sidebar, the Rail, the Bottom Bar and the Tab Overview.
- [ ] Watch `adb -s <serial> logcat` during the smoke test. SPEC.md says release logs never contain addresses, page titles or cookies.
- [ ] Fix any R8 breakage with keep rules in a project ProGuard file, and repeat this section.

## 7. Add a GitHub Actions job that builds the signed bundle

This describes the job's shape. The workflow is not added here. Milestone 6 needs the same job for the GitHub Releases APK, so build it once for both.

- File: `.github/workflows/release.yml`, separate from `ci.yml`.
- Triggers: a pushed tag matching `v*`, and `workflow_dispatch`. Never `pull_request`.
- Permissions: `contents: read` for the build job. Only a later job that creates the GitHub Release gets `contents: write`.
- Environment: `release`, so the secrets from section 4 are available only after the owner approves the run.
- Steps:
  1. Check out with `persist-credentials: false`, with actions pinned to full commit SHAs as in `ci.yml`.
  2. Set up Java with `actions/setup-java` (Temurin 21) and Gradle with `gradle/actions/setup-gradle`. Turn off the Gradle cache for this job (`cache-disabled: true`), so a cache written by another run can't change a signed release.
  3. Check that the tag matches `versionName` in `android/app/build.gradle.kts`, and fail if it doesn't.
  4. Decode `JRNY_UPLOAD_KEYSTORE_B64` into `$RUNNER_TEMP`, never into the workspace.
  5. Run `./gradlew bundleRelease` with the `JRNY_SIGNING_*` variables pointing at the upload key.
  6. Under option A with K in GitHub Actions, decode K the same way and run `./gradlew assembleRelease` with the variables pointing at K.
  7. Verify each signature against the public fingerprints from section 4: `jarsigner -verify -verbose -certs` for the AAB, `apksigner verify --print-certs` for the APK.
  8. Upload the AAB, the APK and `mapping.txt` as workflow artifacts.
  9. Delete the decoded keystores in a step with `if: always()`.
- Upload to Play by hand in Play Console at first. Automating it through the Play Developer API needs a service account key, which is one more long-lived secret. For a release every few weeks, the manual upload costs less than guarding that key.

## 8. Create the app in Play Console

- [ ] `TODO(owner)`: create the app. Choose the default language, the app name "Okapi Browser by JRNY" (21 characters, under the 30-character limit, no emoji), App, and Free. Google's page doesn't say whether a paid app can later become free, so don't start as paid.
- [ ] `TODO(owner)`: enter the contact email for the app.
- [ ] `TODO(owner)`: accept the Developer Program Policies, the US export laws declaration and the Play App Signing Terms of Service.

## 9. Set up Play App Signing

- [ ] `TODO(owner)`: set up Play App Signing under Test and release → App integrity, following the option from section 1:
  - Option A: choose to use your own key, export K with the PEPK tool as Play Console's on-screen steps describe, and upload it. Register the upload key's certificate as the steps describe.
  - Options B and C: let Google generate the key. The first AAB you upload, signed with the upload key, sets the upload key.
  - Confirm each step's wording in Play Console, because the page changes. Once set up, Google won't give you a copy of the app signing key.
- [ ] `TODO(owner)`: on the App integrity page, compare the app signing certificate's SHA-256 fingerprint with the one you recorded. Under option A they must match. Under options B and C, record Google's fingerprint for developer verification, described in [How this relates to Milestone 6](#how-this-relates-to-milestone-6).

## 10. Complete the store listing and App content

Every item here lives in Play Console under Grow users → Store presence, or under Policy → App content. The values to enter are in [store-listing.md](store-listing.md), [app-content.md](app-content.md) and [data-safety.md](data-safety.md). The listing text is in `listing/en-US/`, and the graphics are in `graphics/`.

- [ ] Store listing: a short description of 80 characters or fewer and a full description of 4,000 characters or fewer. Use GLOSSARY.md terms (Profile, Tab, Private Tab, Move, Open in Profile, Site Rule, Pinned Tab, Folder) and lead with what is specific to Okapi, so Play's spam policy doesn't read it as a generic WebView shell. Claim only what SPEC.md and CHANGELOG.md support. Don't claim passkeys, a Custom Tabs service or `blob:` and `data:` downloads. Claim "no telemetry" only for a build with the MetricsOptOut opt-out, which `ManifestTest` checks.
- [ ] Graphics: upload `graphics/icon-512.png` (512 × 512) and `graphics/feature-graphic.png` (1024 × 500). `TODO(owner)`: capture at least 2 phone screenshots and at least 4 tablet screenshots, following the shot list in [graphics/README.md](graphics/README.md). A full Pixel 7 capture is 1080 × 2400, which breaks the 2:1 limit, so the capture commands set the display to 9:16. Private Tabs can't be captured. Show Okapi's own UI and Start Page, not third-party logos.
- [ ] `TODO(owner)`: category and up to 5 tags. Google's category list names browsers under Communications.
- [ ] `TODO(owner)`: the listing's support email. Play shows it publicly. The website is `https://cy.my/android-browser/` until a custom domain is chosen.
- [ ] Privacy policy URL: the page from section 3.
- [ ] Ads: No. Okapi has no ads and no ad SDK.
- [ ] App access: all functionality is available without special access. Okapi has no sign-in of its own.
- [ ] Advertising ID: not used, provided section 5's manifest check still shows no `AD_ID`.
- [ ] `TODO(owner)`: content rating. Choose "Utility, Productivity, Communication, or Other", and answer yes to unrestricted internet access. Expect the "Unrestricted Internet" interactive element and possibly PEGI's Parental Guidance icon. Accept whatever age rating IARC returns. Enter the IARC contact email in Play Console.
- [ ] `TODO(owner)`: target audience. Choose adult age groups only, such as 18 and over, or 13 and over. A browser with unfiltered web access isn't designed for children, and choosing a group under 13 brings in the Families policy. This review can take up to 7 days.
- [ ] Health apps declaration: "My app doesn't provide any health features".
- [ ] Financial features declaration: "My app doesn't provide any financial features".
- [ ] Government apps declaration: not a government app.
- [ ] News apps declaration: answer that Okapi is not a news app, if Play Console asks.
- [ ] `TODO(owner)`: Data safety form. Answer it for a build with the section 3 fixes. Browsing data never leaves the device, so it is not "collected". Pages people open in a Tab are open-web browsing and need no declaration. Two answers are open, and Play's pages don't settle them:
  - Search suggestions. Either declare App activity → In-app search history as collected and shared, optional, for app functionality and encrypted in transit, or rely on the user-initiated exemption. All four suggestion endpoints in `android/app/src/main/java/app/jrny/browser/settings/Settings.kt` use HTTPS.
  - Safe Browsing lookups, and WebView crash reports when the device shares usage and diagnostics. The system component sends both to Google.
  - Write your reasoning down, and confirm both answers in Play Console's help at the time you fill in the form.
- [ ] Permissions: no declaration form applies before 27 January 2027. After it, fill in the `ACCESS_FINE_LOCATION` declaration: a browser passes a precise location to a site the person allowed (see app-content.md, "Sensitive permissions").

## 11. Release to internal testing

Internal testing reaches a small list of testers quickly. Use it to check that the Play-signed build installs and runs, before anyone else sees it.

- [ ] Tag the release and run the workflow from section 7. `TODO(owner)`: approve the `release` environment run.
- [ ] `TODO(owner)`: create an internal testing release, upload the AAB, and add the release notes. Release notes allow 500 characters per language, so condense CHANGELOG.md. You may have to make a new app's first upload in Play Console by hand. Confirm there.
- [ ] `TODO(owner)`: add testers by email list in Play Console, not in this repository.
- [ ] Install the build from Play on a real device and on the emulator. This is the first build signed with the app signing key rather than the upload key. Repeat the section 6 smoke test.

## 12. Run closed testing, and apply for production access

Skip the 14-day clock if you have an organization account, but still run a closed test for the pre-launch report and real-device feedback.

- [ ] `TODO(owner)`: create a closed testing track and promote the internal build, or upload a new one with a higher `versionCode`.
- [ ] `TODO(owner)`: choose the countries and regions for the closed testing track (Test and release → the track → Countries/regions). Play Console asks for at least one before the release can roll out. If you want worldwide distribution, decide it once here and reuse it for production. Confirm the menu path in Play Console.
- [ ] `TODO(owner)`: recruit at least 12 testers and have them opt in. Each tester must stay opted in for at least 14 consecutive days. A tester who opts out and back in restarts their own count. Production, pre-registration and open testing stay off until production access is granted.
- [ ] Ship fixes from tester feedback as new closed-testing builds. Each needs a new `versionCode`.
- [ ] `TODO(owner)`: after the 14 days, apply for production access in Play Console. The application asks about the test, the app and its readiness for production.

The closed test can run in parallel with Milestone 6. ROADMAP.md puts the Play release after v1, but the 14 days fit inside the time Milestone 6 needs.

## 13. Check the pre-launch report

Play Console runs an automated crawl of each build uploaded to a testing track on real devices, and publishes a pre-launch report under Test and release. Confirm in Play Console which tracks trigger it.

- [ ] Read the report for every testing build. Look at crashes, ANRs, accessibility warnings and security warnings.
- [ ] Expect a small device list. `minSdk = 35` excludes every device below Android 15.
- [ ] Fix every crash before production. Play's policy rejects apps that crash, freeze or fail to load.

## 14. Release to production with a staged rollout

- [ ] `TODO(owner)`: create the production release from the tested closed-testing build. Don't rebuild it, so the build on production is the one testers ran.
- [ ] `TODO(owner)`: choose the countries and regions for production (Test and release → Production → Countries/regions), or reuse the closed-test choice from section 12.
- [ ] `TODO(owner)`: choose a staged rollout and start with a small share of users. Raise it while Android vitals stays clean. Halting a rollout stops new installs of that version, but people who already have it keep it. The fix is a new build with a higher `versionCode`.
- [ ] `TODO(owner)`: decide whether to use managed publishing, which holds an approved release until you publish it.
- [ ] After the release is live, add the Play listing link to README.md. A link on the website needs `website/build.py` to allow it first, because `ALLOWED` admits only `https://github.com/cylim/android-browser` and the SVG namespace.

## 15. For every later release

- [ ] Bump `versionCode` and `versionName`, and update CHANGELOG.md.
- [ ] Repeat the section 6 smoke test on the release build.
- [ ] Tag, approve the workflow, upload to internal testing, then promote to production.
- [ ] Update the content rating, the Data safety form and the privacy policy when a feature changes what the app sends or shows.
- [ ] Before adding a permission, check Play's permissions policy. For apps that target API 37, Play announced declarations for `ACCESS_FINE_LOCATION` from 27 January 2027 and for the Local Network permission from 25 August 2027.
- [ ] Keep `targetSdk` at or above Play's yearly minimum.

## How this relates to Milestone 6

Milestone 6 publishes a signed APK on GitHub Releases and checks the build against F-Droid's requirements. It comes before the Play release, but it shares the key, the version numbers and the workflow.

**The key.** The section 1 choice decides it. Under option A, K signs the GitHub APK and is Play's app signing key, so installs from either source update each other. Under option B, the GitHub key and Play's key differ for good, and switching sources erases a person's Profiles. Under option C, GitHub Actions can't sign the final APK. Make the choice before the first GitHub release, because that release fixes the key for every GitHub install that follows.

**The upload key is never a distribution key.** It signs only what goes to Play. Never sign a GitHub APK with it, or the upload key becomes a second identity that can't be reset without stranding those installs.

**F-Droid.** F-Droid builds the app from source, which is why the build must work unsigned when the `JRNY_SIGNING_*` variables are missing. By default F-Droid signs with its own key. With reproducible builds it can ship the APK signed with the developer's key instead, which puts F-Droid users on the same key as GitHub users. Confirm both points against F-Droid's documentation during Milestone 6. ADR 0002 already keeps the app free of the proprietary dependencies F-Droid rejects.

**Android developer verification.** From 30 September 2026, certified devices in Brazil, Indonesia, Singapore and Thailand block installs from Google Play and other participating stores when the developer isn't verified. GitHub Releases and F-Droid are not participating stores, so their builds aren't blocked today. Google says enforcement extends to all apps on certified devices in 2027, sideloaded APKs included. Without registration, people would need adb or Android's one-time "advanced flow", with a day's wait and a warning on every install, to install or update Okapi.

- [ ] `TODO(owner)`: verify your identity in Play Console. A developer who publishes both on and off Play uses the Play Console account for verification, so no separate US$25 Android Developer Console account is needed. Without a Play release, the off-Play builds need a full-distribution Android Developer Console account, which also costs US$25.
- [ ] `TODO(owner)`: register `app.jrny.browser` with every key that signs it. Play App Signing registers the package and Play's key automatically. Add the GitHub key if it differs, and F-Droid's key if F-Droid signs with its own. Registration takes each key's SHA-256 fingerprint and an APK signed with that key as proof.
- [ ] `TODO(owner)`: if F-Droid signs with its own key and Google needs an ownership check, decide whether to add the `assets/adi-registration.properties` file Google provides. That adds a file to the app's source.
- [ ] Register every key before the 2027 rollout. A lost key that was never registered can't be registered later.

Creating the Play Console account early therefore helps Milestone 6 even though the Play release comes after v1: the same account verifies the developer for the GitHub and F-Droid builds.

## Sources

Google's pages, read for this checklist. Play Console wording changes, so confirm each step in Play Console when you reach it.

- Developer account types: <https://support.google.com/googleplay/android-developer/answer/13634885>
- Registration and fee: <https://support.google.com/googleplay/android-developer/answer/6112435>
- Developer details and verification: <https://support.google.com/googleplay/android-developer/answer/13628312>
- Testing requirement for new personal accounts: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Target API level: <https://support.google.com/googleplay/android-developer/answer/11926878>
- Android App Bundle: <https://support.google.com/googleplay/android-developer/answer/9844679>
- App signing: <https://developer.android.com/studio/publish/app-signing>
- Play App Signing and upload keys: <https://support.google.com/googleplay/android-developer/answer/9842756>
- 16 KB page sizes: <https://developer.android.com/guide/practices/page-sizes>
- Create an app and store listing: <https://support.google.com/googleplay/android-developer/answer/9859152>
- Graphics: <https://support.google.com/googleplay/android-developer/answer/9866151>
- Categories and tags: <https://support.google.com/googleplay/android-developer/answer/9859673>
- Release notes: <https://support.google.com/googleplay/android-developer/answer/9859348>
- App content: <https://support.google.com/googleplay/android-developer/answer/9859455>
- Data safety: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Privacy policy: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Content ratings: <https://support.google.com/googleplay/android-developer/answer/9898843>
- Target audience: <https://support.google.com/googleplay/android-developer/answer/9867159>
- Permissions policy: <https://support.google.com/googleplay/android-developer/answer/9888170>
- Play Console requirements, package registration: <https://support.google.com/googleplay/android-developer/answer/17125096>
- WebView privacy and MetricsOptOut: <https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy>
- Android developer verification: <https://developer.android.com/developer-verification/guides>
- Developer verification FAQ: <https://developer.android.com/developer-verification/guides/faq>
- Open-source app registration: <https://developer.android.com/developer-verification/guides/open-source-app-registration>
