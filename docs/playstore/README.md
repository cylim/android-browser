# Google Play submission kit

This folder holds everything Okapi, a browser by JRNY, needs for a Google Play listing: the listing text, the graphics, the privacy policy, the answers to Play Console's declaration forms, and a release checklist. Nothing here is submitted yet. [ROADMAP.md](../ROADMAP.md) puts the Google Play release under "After v1", Milestone 6 (a signed APK on GitHub Releases and an F-Droid check) comes first, and no release signing key exists.

> **Engine change, 2026-11-19.** Okapi now renders pages with GeckoView, not Android System WebView ([ADR 0006](../adr/0006-geckoview-as-engine.md)). The privacy policy, both copies, is updated. The Data safety answers, the App content notes, the listing and the release checklist still describe WebView in places (`MetricsOptOut`, `X-Requested-With`, WebView crash reports, Safe Browsing through WebView). Those answers must be redone for GeckoView once its own network traffic is captured, before anything is submitted.

The repository is public. Names, email addresses, phone numbers, postal addresses, account IDs, keystores and passwords go into Play Console, GitHub secrets or the owner's own machine, never into these files. A `TODO(owner)` marks each place where the owner has to decide something or enter personal data outside the repository.

## Files and the Play Console page each one feeds

Play Console moves its menus around. Confirm each path when you get there.

| File | Play Console page | What it holds |
| --- | --- | --- |
| [release-checklist.md](release-checklist.md) | All of them, in order | The ordered steps from no Play presence to a production release, and how they relate to Milestone 6 |
| [store-listing.md](store-listing.md) | Create app. Grow users > Store presence > Main store listing. Store settings. | Every field, the value to enter, what to check before pasting, and the character counts |
| [listing/en-US/title.txt](listing/en-US/title.txt) | Main store listing > App name | `Okapi Browser by JRNY` |
| [listing/en-US/short-description.txt](listing/en-US/short-description.txt) | Main store listing > Short description | The 80-character pitch |
| [listing/en-US/full-description.txt](listing/en-US/full-description.txt) | Main store listing > Full description | Features in glossary terms, privacy, requirements, known limits |
| [listing/en-US/release-notes/0.1.0.txt](listing/en-US/release-notes/0.1.0.txt) | Test and release > the track > Create release > Release notes | Notes for versionName 0.1.0, versionCode 1 |
| [graphics/icon-512.png](graphics/icon-512.png) | Main store listing > App icon | 512 x 512 icon |
| [graphics/feature-graphic.png](graphics/feature-graphic.png) | Main store listing > Feature graphic | 1024 x 500 graphic |
| `graphics/icon.svg`, `graphics/feature-graphic.svg`, `graphics/render.sh` | None | Sources of the two PNGs, and the script that renders them |
| [graphics/README.md](graphics/README.md) | Main store listing > Phone, 7-inch tablet and 10-inch tablet screenshots | Play's graphic specs, the screenshot shot list and the capture commands |
| [app-content.md](app-content.md) | Policy > App content: Ads, App access, Content rating, Target audience, News, Health, Financial features, Government, COVID-19, Advertising ID | Each answer, why it is true, and whether it is verified |
| [data-safety.md](data-safety.md) | Policy > App content > Data safety | Each answer with its evidence, the blockers, and decisions D1 to D3 |
| [privacy-policy.md](privacy-policy.md) | Policy > App content > Privacy policy, through the page below | The canonical policy text |
| [website/public/privacy.html](../../website/public/privacy.html) | The address entered under Privacy policy | The published copy of the policy, served at `https://cy.my/android-browser/privacy.html` once merged |

Keep `privacy-policy.md`, `privacy.html` and `data-safety.md` in step. A change to one of them changes the others.

## Order of work

Follow [release-checklist.md](release-checklist.md) from section 1 to section 14. In short:

1. Choose the signing key option (section 1) before Milestone 6 puts the first signed APK on GitHub Releases. That release fixes the key for every GitHub install after it.
2. Create the developer account (section 2). Start early, because the same account handles Android developer verification for the GitHub and F-Droid builds.
3. Fix the app and finish the privacy policy (section 3). The Data safety answers, the policy and the listing's privacy section are true only after these fixes.
4. Generate the keys, change the build, test the R8 release build and add the release workflow (sections 4 to 7).
5. Create the app and set up Play App Signing (sections 8 and 9).
6. Fill in the store listing from `store-listing.md`, `listing/` and `graphics/`, the App content forms from `app-content.md`, and the Data safety form from `data-safety.md` (section 10).
7. Release to internal testing, run the closed test, read the pre-launch report, and roll out to production (sections 11 to 14).

Before you paste the listing, check the "Before you paste" table in [store-listing.md](store-listing.md). Before you enter the privacy policy address, this command must print nothing:

```sh
grep -n "TODO(owner)" docs/playstore/privacy-policy.md website/public/privacy.html
```

## Every TODO(owner) in this folder

This list merges every `TODO(owner)` in `docs/playstore/` and `website/public/privacy.html`, without duplicates. To check that it is still complete, run:

```sh
grep -rn "TODO(owner)" docs/playstore website/public/privacy.html
```

The first upload means the first AAB uploaded to internal testing, release-checklist.md section 11.

### Blocking before the first upload

Signing key:

- [ ] Choose signing option A, B or C, and record it in a new ADR before the first signed GitHub Releases APK. (release-checklist.md section 1)
- [ ] Under option A, decide whether key K signs in GitHub Actions or only on your own machine. (section 1)

Developer account:

- [ ] Choose a personal or an organization account. (section 2)
- [ ] Register in Play Console and pay the US$25 fee. (section 2)
- [ ] Complete identity verification. (section 2)
- [ ] Enter the developer details, and verify the email and phone. Know first which of them Play shows publicly. (section 2)
- [ ] For a personal account, verify an Android device with the Play Console app. (section 2)

App fixes and the privacy policy:

- [ ] Add the developer name, exactly as Google Play shows it, to both copies of the privacy policy. (privacy-policy.md, privacy.html, section 3, data-safety.md Blocker 4)
- [x] ~~Test whether androidx.webkit's `setRequestedWithHeaderOriginAllowList` stops the `X-Requested-With` header.~~ No longer needed: GeckoView sends no such header (ADR 0006).
- [ ] Capture what GeckoView requests by itself on a fresh install, and describe it in both copies of the privacy policy and the Data safety answers. (privacy-policy.md, privacy.html, data-safety.md)
- [ ] Check on a device with Google Play Services whether androidx.emoji2's `EmojiCompatInitializer` fetches an emoji font from Google Play Services. Remove the initializer, or describe the request in the policy. (privacy-policy.md, privacy.html, store-listing.md, section 3)
- [ ] Confirm the privacy contact: GitHub issues, and the private "Report a vulnerability" route from SECURITY.md. (section 3)
- [ ] Decide whether search suggestions, which are on by default, need a prominent in-app notice or should be off by default. (section 3, data-safety.md D3, app-content.md)

Keys and GitHub:

- [ ] Generate the upload key on your own machine. (section 4)
- [ ] Under option A, generate app signing key K in a separate keystore. (section 4)
- [ ] Back up each keystore and its password in two places. (section 4)
- [ ] Record each key's SHA-256 fingerprint. (section 4)
- [ ] Create the GitHub environment `release` with yourself as required reviewer. (section 4)
- [ ] Store the upload key as `release` environment secrets. (section 4)
- [ ] If K signs in GitHub Actions, store it the same way. (section 4)

Play Console app and signing:

- [ ] Create the app: default language, name, App, Free. (section 8, store-listing.md)
- [ ] Enter the app's contact email. (section 8, store-listing.md)
- [ ] Accept the Developer Program Policies, the US export laws declaration and the Play App Signing Terms of Service. (section 8)
- [ ] Set up Play App Signing for the option you chose. (section 9)
- [ ] Compare the app signing certificate's fingerprint with the one you recorded. (section 9)

Store listing:

- [ ] Capture the phone and tablet screenshots from the shot list. (section 10, store-listing.md, graphics/README.md)
- [ ] Confirm whether 7-inch and 10-inch tablet screenshots are separate required uploads. (store-listing.md, graphics/README.md)
- [ ] Approve the feature graphic's alt text, or write another of 140 characters or fewer. (store-listing.md, graphics/README.md)
- [ ] Confirm the category, Communications, and choose up to 5 tags. (section 10, store-listing.md)
- [ ] Enter the support email. Play shows it publicly. (section 10, store-listing.md)
- [ ] Decide whether to publish a phone number. The recommendation is to leave it empty. (store-listing.md)
- [ ] Turn external marketing on or off. (store-listing.md)

App content and Data safety:

- [ ] Fill in the content rating questionnaire, and enter the IARC contact email in Play Console only. (section 10, app-content.md)
- [ ] Confirm the target age groups: 18 and over (recommended) or 13 and over. (section 10, app-content.md, store-listing.md)
- [ ] Decide on Restrict Minor Access. The recommendation is to leave it off. (app-content.md)
- [ ] D1: declare search suggestions as In-app search history (recommended), or claim the exemption. (data-safety.md, section 10)
- [ ] D2: confirm that Safe Browsing lookups and WebView crash reports stay out of the Data safety form. (data-safety.md, section 10)
- [ ] Confirm the wording of the data deletion and account creation questions in Play Console. (data-safety.md)

Not marked `TODO(owner)`, because any contributor can do them, but still blocking:

- Link the privacy policy from inside the app, for example a row in Settings. (data-safety.md Blocker 3)
- Merge `website/public/privacy.html` so the Pages workflow publishes it. (data-safety.md Blocker 4)
- Check the merged manifest of the final AAB for `AD_ID` and other new permissions. (data-safety.md Blocker 5, app-content.md)

### Later

First upload and testing:

- [ ] Approve the `release` environment run of the release workflow. (section 11)
- [ ] Create the internal testing release, upload the AAB and paste the release notes. (section 11)
- [ ] Add internal testers by email list in Play Console. (section 11)
- [ ] Create the closed testing track. (section 12)
- [ ] Choose the countries and regions for the closed testing track. (section 12)
- [ ] Recruit at least 12 testers for 14 consecutive days, if the account is personal. (section 12)
- [ ] Apply for production access after the 14 days. (section 12)

Production:

- [ ] Create the production release from the tested closed-testing build. (section 14)
- [ ] Choose the countries and regions for production. (section 14)
- [ ] Choose a staged rollout and its starting share. (section 14)
- [ ] Decide whether to use managed publishing. (section 14)

Android developer verification (also needed for Milestone 6):

- [ ] Verify your identity in Play Console. (release-checklist.md, "How this relates to Milestone 6")
- [ ] Register `app.jrny.browser` with every key that signs it, before Google's 2027 enforcement. (same section)
- [ ] If F-Droid signs with its own key, decide whether to add `assets/adi-registration.properties`. (same section)

Optional or conditional:

- [ ] Add a preview video, if one is recorded. (store-listing.md)
