# Play Console store listing for Okapi

This page lists every field for Okapi, a browser by JRNY, on two Play Console pages, **Grow users > Store presence > Main store listing** and **Store settings**, with the value to enter. A value marked `TODO(owner)` is the owner's decision or personal data that stays out of this public repository.

The listing text lives in [`listing/en-US/`](listing/en-US/), one file per field, so it can be reviewed in pull requests and pasted as is. The files hold the text Play shows, with its line breaks.

The ROADMAP puts the Google Play release under "After v1". Milestone 6 comes first, and no release signing key exists yet.

## Before you paste: claims that depend on unfinished work

The listing text is written for the app as it will be at submission. Each item below makes a sentence true or false, so check it first.

| Item | What the listing says | What to do if it isn't done |
| --- | --- | --- |
| The Start Page is in the working tree but not merged (`CHANGELOG.md`, "Start Page (not merged yet)"). | The full description and the release notes name the Start Page. | Delete the Start Page line from `full-description.txt` and "a Start Page" from `release-notes/0.1.0.txt`. |
| The privacy policy page at https://cy.my/android-browser/privacy.html. It is written, as `website/public/privacy.html` (canonical text: [`privacy-policy.md`](privacy-policy.md)), but not merged or published yet, and its developer name is still a `TODO(owner)`. | Play links to it from the listing. | Merge it so `.github/workflows/pages.yml` publishes it, and fill in the developer name. Play needs a live, public URL. See [`data-safety.md`](data-safety.md), Blocker 4. |
| The merged release manifest registers androidx.emoji2's `EmojiCompatInitializer`, which Compose brings in. The app's code never calls it. By default it asks the device's downloadable-font provider for an emoji font, and on devices with Google Play Services that provider is usually Google Play Services. Nobody has checked this on such a device. | The privacy section says "no Google Play Services libraries". It doesn't say the app never talks to Google Play Services. | `TODO(owner)`: check on a device with Google Play Services. Then either remove the initializer with a `tools:node="remove"` entry in `AndroidManifest.xml`, or keep it and describe the font request in the privacy policy. |

Done: `android.webkit.WebView.MetricsOptOut` is in `android/app/src/main/AndroidManifest.xml` (checked by `ManifestTest`), so the privacy section can say Okapi turns off WebView's usage statistics and name crash reports as the only WebView diagnostics that leave the device. The suggestion fix for credential-shaped text is in too. The listing makes no claim about it.

## Main store listing

### App details

| Field | Limit | Value |
| --- | --- | --- |
| App name | 30 characters | [`listing/en-US/title.txt`](listing/en-US/title.txt): `Okapi Browser by JRNY`. The launcher label, `app_name` in `android/app/src/main/res/values/strings.xml`, is just `Okapi`. The store title adds "Browser" because people search Play for that word, and "by JRNY" for the maker, which also keeps it apart from the "OK Browser" apps (see below). |
| Short description | 80 characters | [`listing/en-US/short-description.txt`](listing/en-US/short-description.txt) |
| Full description | 4,000 characters | [`listing/en-US/full-description.txt`](listing/en-US/full-description.txt) |

The metadata policy rules out emojis, rank claims ("#1", "best") and price claims in the title. Keep the title free of a Profile emoji too. "Browser" is capitalized only here, in the store title. Everywhere else the product is "Okapi", or "Okapi, a browser by JRNY" where the reader needs the maker.

Store text always pairs Okapi with JRNY. Play search mixes results for "Okapi browser" with the many apps called "OK Browser", and "by JRNY" is what tells this app apart. So the title, the start of the short and full descriptions, the release notes and the feature graphic's alt text all say "Okapi by JRNY", "Okapi, a browser by JRNY" or "Okapi Browser by JRNY". Later mentions inside a description can say just "Okapi". Keep this rule when you edit any of these texts or add a translation.

The short and full descriptions open with the name and the maker. The full description's first sentence, "Okapi, a browser by JRNY, signs you in to the same site with two different accounts at once", says what the app is, who makes it and the separate-accounts point. Then it lists what Okapi does that a plain WebView wrapper doesn't: Move, Open in Profile, Site Rules for Incoming Links, Pinned Tabs and Folders, Private Tabs. This answers the Spam policy's rule against apps that repeat what other apps already do. That first sentence also states the app's purpose plainly, a browser, as the Misrepresentation policy asks.

The description names GeckoView or Gecko only as a fact about the engine. Don't add wording that suggests a link with Mozilla or Firefox.

### Graphics

| Field | Required | Spec | Value |
| --- | --- | --- | --- |
| App icon | Yes | 32-bit PNG with alpha, 512 x 512 px, at most 1024 KB | [`graphics/icon-512.png`](graphics/icon-512.png), rendered from `graphics/icon.svg` by `graphics/render.sh`. It is the peek compact Okapi mark on the brand cream `#F5EFE4`, the same art as `brand/okapi-peek-compact/icon-512.png`. Play shows the icon small, so it uses the compact drawing. |
| Feature graphic | Yes | JPEG or 24-bit PNG without alpha, 1024 x 500 px. Keep the focal content centered. Avoid pure white, black or dark gray backgrounds. Alt text up to 140 characters. | [`graphics/feature-graphic.png`](graphics/feature-graphic.png), rendered from `graphics/feature-graphic.svg` by `graphics/render.sh`. It shows the peek full Okapi mark, "Okapi" with "by JRNY", and the website's headline "Sign in to the same site twice." on the brand cream. Alt text, 118 characters, `TODO(owner)`: approve it. `The Okapi by JRNY logo, an okapi whose striped back curls into an O, beside the line "Sign in to the same site twice."` |
| Video | No | One public or unlisted YouTube URL, embeddable, no ads, no playlist or timecode | Leave empty, or `TODO(owner)` if a recording from the emulator is made. |
| Phone screenshots | At least 2 across all device types, up to 8 | JPEG or 24-bit PNG without alpha. Sides 320 to 3,840 px. The long side at most twice the short side. For larger-format promotion, at least 4 at 1080 x 1920 (9:16). | `TODO(owner)`: capture on the `JRNY_Pixel_7_API_37` emulator, following the [phone shot list](graphics/README.md#phone-portrait). The capture commands set the display to 1080 x 1920 (9:16), because a full 1080 x 2400 capture is 2.22:1. |
| 7-inch tablet screenshots | At least 4 for large screens | Sides 1,080 to 7,680 px, 16:9 or 9:16 | `TODO(owner)`: capture on the `JRNY_Pixel_Tablet_API_37` emulator, following the [7-inch shot list](graphics/README.md#7-inch-tablet-landscape). |
| 10-inch tablet screenshots | At least 4 for large screens | Same as 7-inch | `TODO(owner)`: capture following the [10-inch shot list](graphics/README.md#10-inch-tablet-landscape). Confirm in Play Console whether the 7-inch and 10-inch slots both need uploads. Google's help page gives one spec for both. |
| Chromebook screenshots | Optional | Same as tablets | Leave empty. Okapi isn't tested on ChromeOS. |
| Android XR, Wear OS, Android TV, Android Automotive | Not applicable | | Leave empty. |

Screenshot rules that follow from the app:

- Private Tabs set `FLAG_SECURE`, so a screenshot of one comes out black. To show a Private Tab, show its row in the Tab list from a normal Tab.
- Show Okapi's own UI: the Sidebar, the Bottom Bar, the Tab Overview, the Profile sheet, Site Rules. Use `example.com` pages, not other companies' sites or logos.
- Don't use art that appeals to children. The target audience excludes them (see the Target audience note below).
- Captions may cover at most 20% of each of the first three screenshots.

## Store settings

### App category

| Field | Value |
| --- | --- |
| App or game | App |
| Category | Communications |
| Tags | `TODO(owner)`: up to 5, chosen from the list Play Console offers. |

Why Communications: Google's category list describes Communications as "Messaging, chat/IM, dialers, address books, browsers, call management". It is the only category that names browsers. Tools ("Tools for Android devices") was the alternative, but the Deceptive behavior policy counts a wrong category as a violation, so take the category that names browsers.

Tags: Play Console offers a fixed list of tags, and this document can't confirm their exact names. Pick tags about web browsing, privacy and accounts if the list has them. Don't pick tags for features the app doesn't have, such as ad blocking, VPN or sync.

### Store listing contact details

Play shows every value in this section on the public listing.

| Field | Required | Value |
| --- | --- | --- |
| Email address | Yes | `TODO(owner)`: a support address. Play publishes it. |
| Phone number | No | Leave empty. If the owner wants one, `TODO(owner)`. Play publishes it. |
| Website | No, but Google strongly recommends one | `https://cy.my/android-browser/` until a custom domain is chosen. `https://github.com/cylim/android-browser` is the alternative. |

### External marketing

| Field | Value |
| --- | --- |
| Advertise outside Google Play | `TODO(owner)`: on or off. |

## Values asked for when you create the app

These come before the store listing, under **Create app**, and under **Policy > App content**.

| Field | Value |
| --- | --- |
| Default language | English (United States), en-US |
| App name | `Okapi Browser by JRNY` (same as the listing) |
| App or game | App |
| Free or paid | Free. The help page doesn't say whether a paid app can later become free, so don't pick paid. |
| Contact email | `TODO(owner)` |
| Privacy policy URL (App content) | `https://cy.my/android-browser/privacy.html`, published from `website/public/privacy.html`. The page must be live, public, labeled "Privacy policy", and reachable without a login. Play also needs a link to it inside the app, which the app doesn't have yet. |
| Ads | No. Okapi has no ads and no ad SDK. |
| Target audience | `TODO(owner)`: adult age groups only, for example 18 and over. Don't include an age group under 13. A browser gives unfiltered web access, and selecting a child group brings in the Families policy. |

## Release notes

[`listing/en-US/release-notes/0.1.0.txt`](listing/en-US/release-notes/0.1.0.txt) holds the notes for versionName 0.1.0 (versionCode 1), condensed from `CHANGELOG.md`. Play Console allows 500 characters per language. When you paste into the release's notes box, wrap the text in language tags:

```
<en-US>
(text of release-notes/0.1.0.txt)
</en-US>
```

## Character counts

Measured on 2026-11-17. Play counts Unicode characters, so `…` counts as one. Line breaks inside the text count. The final newline of each file doesn't, because Play trims it.

| File | Characters | Limit |
| --- | --- | --- |
| `listing/en-US/title.txt` | 21 | 30 |
| `listing/en-US/short-description.txt` | 78 | 80 |
| `listing/en-US/full-description.txt` | 3,841 | 4,000 |
| `listing/en-US/release-notes/0.1.0.txt` | 491 | 500 |

To measure again after an edit, run this from `docs/playstore/`:

```sh
python3 -I -c '
for path, limit in [("listing/en-US/title.txt", 30), ("listing/en-US/short-description.txt", 80), ("listing/en-US/full-description.txt", 4000), ("listing/en-US/release-notes/0.1.0.txt", 500)]:
	n = len(open(path, encoding="utf-8").read().rstrip("\n"))
	print(path, n, limit, "OK" if n <= limit else "OVER")
'
```

## Sources

- Store listing fields and text limits: https://support.google.com/googleplay/android-developer/answer/9859152
- Contact details: https://support.google.com/googleplay/android-developer/answer/9859041
- Categories and tags: https://support.google.com/googleplay/android-developer/answer/9859673
- Graphic assets and screenshots: https://support.google.com/googleplay/android-developer/answer/9866151
- Metadata policy: https://support.google.com/googleplay/android-developer/answer/9898842
- Release notes: https://support.google.com/googleplay/android-developer/answer/9859348
- Privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Target audience: https://support.google.com/googleplay/android-developer/answer/9867159
- WebView privacy and `MetricsOptOut`: https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy
