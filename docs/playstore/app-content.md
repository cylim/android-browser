# Play Console App content declarations

This page lists the answers Okapi, a browser by JRNY, gives on the **Policy and programs** > **App content** page in Play Console, and why each answer is true. It covers every declaration except the privacy policy and the Data safety form, which have their own documents.

Terms such as Profile, Tab and Private Tab mean what [GLOSSARY.md](../../GLOSSARY.md) says. The Play release is listed under "After v1" in [ROADMAP.md](../ROADMAP.md), so nothing here has been submitted yet.

Each answer carries one of two labels:

- **Verified**: Google's help page states the rule, and the repo shows the fact Okapi's answer rests on.
- **Confirm in Play Console**: Google's pages don't settle the wording or the outcome. Read the form before you answer.

## Fixes these answers depend on

Two answers below, and the store listing, depend on these fixes. Both are in the code.

1. **WebView usage statistics.** `android/app/src/main/AndroidManifest.xml` sets the `android.webkit.WebView.MetricsOptOut` meta-data, and `ManifestTest.webViewMetricsAreOptedOut` checks it on a device. Android System WebView sends no usage statistics from inside Okapi, whatever the person's usage and diagnostics setting is. The opt-out doesn't stop WebView crash reports, which contain no URLs.
2. **Search suggestions and credentials.** `AddressInput.isSearch` keeps text with a colon before its first `/`, `?` or `#` on the device while it is typed, so `admin:hunter2` isn't sent before the `@` arrives. `AddressInputTest` and `BrowserStateTest.whatIsTypedGoesToTheSearchEngineOnlyWhenItMay` test it. A search with an early colon, such as `python: list comprehension`, gets no search engine suggestions as a result.

## Answers at a glance

| Declaration | Answer | Label |
| --- | --- | --- |
| Ads | No | Confirm in Play Console |
| App access | All functionality is available without special access | Confirm in Play Console |
| Content rating | Category "Utility, Productivity, Communication, or Other". No to every content question. Yes to unrestricted internet access | Confirm in Play Console |
| Target audience and content | 18 and over only. The listing doesn't appeal to children | Verified |
| News apps | Not a news app | Confirm in Play Console |
| Health apps | "My app doesn't provide any health features" | Verified |
| Financial features | "My app doesn't provide any financial features" | Verified |
| Government apps | Not a government app | Confirm in Play Console |
| COVID-19 contact tracing and status apps | Not a COVID-19 contact tracing or status app | Confirm in Play Console |
| Data deletion and accounts | No account creation. No deletion URL | Verified |
| Advertising ID | Doesn't use the advertising ID | Verified |
| Sensitive permissions | No declaration. The only requested system permission is `INTERNET` | Verified |
| Foreground services | No declaration. No foreground service | Verified |
| Default browser role | No declaration exists for it | Verified |

## Ads

**Answer:** No, the app doesn't contain ads.

**Why:** Okapi has no ad SDK, no house ads and no sponsored content. Its own screens are its UI and the Start Page, which shows the Profile's Pinned Tabs and recent pages and makes no network requests. The dependencies in `android/app/build.gradle.kts` are AndroidX, Compose, Room, Guava and kotlinx.serialization. None of them serves ads.

Ads on websites the person opens belong to those sites. Google's ads help page doesn't discuss browsers, so this answer rests on the plain meaning of "your app contains ads". If a later version adds anything Okapi itself promotes, such as sponsored tiles on the Start Page, the answer becomes Yes.

**Label:** Confirm in Play Console.

## App access

**Answer:** All functionality is available without special access.

**Why:** Okapi has no sign-in, accounts, server, paywall or region lock of its own. A reviewer installs it and can open pages, create Profiles, open Private Tabs and add Site Rules at once. Sites the person signs in to are outside the app, and a reviewer doesn't need credentials for them. Leave the instructions empty.

**Label:** Confirm in Play Console. Google's page describes when instructions are needed, not how an app with no restrictions answers.

## Content rating

Every app needs an IARC rating, and Google removes apps without one. IARC assigns the rating at once when you submit the questionnaire.

### Fill in the questionnaire

1. Enter the contact email for IARC correspondence. TODO(owner): IARC contact email. Don't commit it to the repo.
2. Choose the category **Utility, Productivity, Communication, or Other**. Google's description of this category lists "Search apps, browsers" under Other.
3. Answer each question about Okapi's own content and features, using the table below.
4. Submit, then apply the rating.

Google's help page doesn't print the questions, and IARC can reword them. The table names each question by topic. Match the topic to the question on screen. For any question the table doesn't cover, the rule is the same: Okapi ships no content of its own besides its UI and the Start Page, so the answer is No unless the question is about access to the web.

| Question topic | Answer | Why |
| --- | --- | --- |
| Violence, blood, fear or horror | No | Okapi ships no media or stories. |
| Sexuality or nudity | No | Same. |
| Profanity or crude humor | No | Okapi's strings are UI labels. |
| Drugs, alcohol or tobacco | No | Same. |
| Gambling, simulated or real | No | Okapi has no games and no betting. |
| Hate speech or discriminatory content | No | Same. |
| Users interact or exchange content with each other in the app | No | Okapi has no chat, comments, accounts or server. Nothing one person does in Okapi reaches another Okapi user. |
| The app shares the user's location with other users | No | Okapi doesn't request a location permission. |
| Digital purchases | No | Okapi sells nothing. Purchases on websites happen on the open web. |
| Unrestricted internet access, for example a web browser | **Yes** | Okapi is a general-purpose browser. It opens any `http` or `https` address and has no content filter. |

### What to expect

IARC returns a rating for each region. The likely result is the following:

- The **Unrestricted Internet** interactive element. IARC lists it next to the age rating. ESRB defines it as "Provides unrestricted access to the internet (e.g., browser, search engine)" and says interactive elements "do not influence the rating assignment of a product".
- Possibly PEGI's **Parental Guidance** icon, which Google describes for "portal" apps that offer a broad, variable range of content.
- An age rating based on Okapi's own content. With No to every content question, that is often the lowest tier in each region.

No official source states a typical rating for a browser, and some third-party posts report Teen or 12+ for browsers. Treat whatever IARC returns as the answer. Don't edit questionnaire answers to push the rating down.

### When to submit it again

Submit the questionnaire again when a change affects the answers. Examples: a feature where Okapi users see each other's content, sync through a server, purchases, or content Okapi itself supplies, such as a feed on the Start Page.

**Label:** Confirm in Play Console. The category is verified. The outcome is not.

## Target audience and content

Play asks for the ads answer, the app access answer and the privacy policy before this section. Review of this section can take up to 7 days.

**Answer:** Select **18 and over** only. Leave every younger age group unselected.

**Why:**

- Play says to select age groups that include children only if the app is truly designed for all ages. Okapi gives unfiltered access to the whole web. It has no content filter and no parental controls, and `docs/SPEC.md` plans neither for v1.
- Selecting any group under 13 brings in the Families policy. Content that children can reach must suit them, and one listed violation is links to sites with policy-violating content. An unfiltered browser can't meet that.
- Okapi is built for people who keep separate Accounts on the same site, such as work and personal Google Accounts. That is an adult use.

13 and over is also a valid answer under Play's rules, because the Families policy starts below 13. This page recommends 18 and over because nothing in Okapi is designed for teenagers. TODO(owner): confirm the age groups.

**Store listing appeal:** If Play Console asks whether the listing could unintentionally appeal to children, answer No. Keep it true. Profiles can have an emoji avatar, so screenshots with emoji Profiles are fine, but don't use cartoon art, child characters or child-oriented wording in the icon, feature graphic or description. Play may reject an app whose listing appeals to children when the app isn't designed for them.

**Restrict Minor Access:** Play offers this setting for apps for adults only. Okapi has no adult content of its own, and the 18 and over choice is about staying out of the Families policy, so this page doesn't recommend turning it on. TODO(owner): decide.

**Label:** Verified.

## News apps

**Answer:** No, Okapi isn't a news app.

**Why:** The declaration covers apps in the News and Magazines category, apps that describe themselves as news, and apps with news-category attributes, including aggregators. Okapi's category is Communications, which Google's category list describes as including browsers. Okapi publishes no articles, and the Start Page shows the Profile's own Pinned Tabs and recent pages, not a feed. Play Console prompts only in-scope apps, so this question may not appear at all.

**Label:** Confirm in Play Console.

## Health apps

**Answer:** "My app doesn't provide any health features".

**Why:** Every app on a closed testing, open testing or production track must complete this declaration, including apps with no health features. Okapi has none. Health sites the person opens are open-web browsing.

**Label:** Verified.

## Financial features

**Answer:** "My app doesn't provide any financial features".

**Why:** Every app on a closed testing, open testing or production track must complete this declaration. Okapi has no payments, wallet, loans, trading or crypto features. It stores no passwords. Password managers work through Android Autofill. Banking sites the person opens are open-web browsing.

**Label:** Verified.

## Government apps

**Answer:** No, the app isn't developed by or on behalf of a government.

**Why:** Developers have had to complete this declaration since January 31, 2023. Okapi is an open-source project with no government affiliation, and it shows no government information of its own. The help page doesn't spell out how a non-government app answers, so read the options on screen.

**Label:** Confirm in Play Console.

## COVID-19 contact tracing and status apps

**Answer:** Okapi isn't a publicly available COVID-19 contact tracing or status app.

**Why:** Okapi has no health features and no contact tracing.

**Label:** Confirm in Play Console. The research for this page found no current Google page for this declaration. If Play Console no longer shows it, skip it.

## Data deletion and accounts

This question sits inside the Data safety form, which has its own document. The parts that don't depend on the Data safety answers are below.

**Answer:**

- **Account creation:** Okapi doesn't let users create an account, inside or outside the app.
- **Account deletion URL:** None. It's required only for apps with account creation.

**Why:** Okapi has no accounts of its own and no server. A Profile is a local browsing identity on the device, not an app account. Deleting a Profile wipes its on-device data. Its history, bookmarks, Folders and list of downloads go at once, and its web data goes at the next cold start. Downloaded files stay on the device.

Every developer must still answer the data deletion questions. If the Data safety form declares no collected data, the follow-up questions don't appear. If the owner declares search suggestions as collected, answer No to the question about whether users can request deletion. Okapi never receives the typed text, the chosen search engine does, so Okapi has nothing to delete. Don't claim the deletion badge on the strength of Profile deletion, because that doesn't touch what the search engine received.

**Label:** Verified for account creation. The follow-up depends on the Data safety decision (D1 in [data-safety.md](data-safety.md)).

## Advertising ID

**Answer:** No, the app doesn't use the advertising ID.

**Why:** Apps that target Android 13 or higher and use the advertising ID must declare `com.google.android.gms.permission.AD_ID`, and SDKs can merge it in without the developer noticing. The answer must match the merged manifest.

On 2026-11-17 the merged release manifest had no `AD_ID` and no other ads permission. Its only `uses-permission` entries are these two:

- `android.permission.INTERNET`
- `app.jrny.browser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX Core adds. Okapi defines it itself with `protectionLevel="signature"`, so it covers only Okapi's own broadcast receivers.

Okapi also has no Google Play Services ([ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md)), so nothing in it can read the advertising ID.

To check again, for example after a Dependabot update:

```sh
cd android
./gradlew :app:processReleaseMainManifest -q
grep 'uses-permission' app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

If the output names `AD_ID`, find the dependency that adds it before you answer. Check the final `.aab` too, for example with bundletool's `dump manifest` command, because the bundle is what Play reads.

**Label:** Verified.

## Sensitive permissions

**Answer:** No permission declaration applies. Play Console shows these forms only for restricted permissions, and Okapi requests none.

**Why:** Play's declaration forms cover SMS and Call Log, `QUERY_ALL_PACKAGES`, background location, `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`, `MANAGE_EXTERNAL_STORAGE`, Accessibility services, `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT` and Health Connect. Okapi requests only `INTERNET`, which is a normal permission and isn't on the list. The AndroidX permission above is Okapi's own signature permission and isn't on it either.

Okapi doesn't request location, camera, microphone, storage or notification permissions. Web pages can't get location, camera or microphone access through Okapi, because the app doesn't hold those permissions. Downloads don't need a storage permission, because Android's DownloadManager saves them to the Downloads folder.

**Coming for apps that target API 37:** Okapi targets API 37, and Play's permissions policy announces two declarations for such apps:

- `ACCESS_FINE_LOCATION` needs a declaration from 27 January 2027.
- The Local Network permission needs a declaration from 25 August 2027, and only for core functionality where device pickers aren't enough.

Okapi requests neither today. `android/app/src/main/res/xml/network_security_config.xml` allows `http` pages so people can reach a router, a NAS or an intranet site. If Android 17 requires the Local Network permission for those pages, adding it brings in the declaration. The policy page doesn't name the permission constant or say whether browsers get an exception. Check again before release.

**Label:** Verified for today's manifest. The API 37 items are announcements.

## Foreground services

**Answer:** No declaration. Okapi has no foreground service.

**Why:** The merged release manifest has no `FOREGROUND_SERVICE` permission and no service with a `foregroundServiceType`. Its one service is `androidx.room.MultiInstanceInvalidationService`, which Room adds. It isn't exported and doesn't run in the foreground. Downloads run in Android's DownloadManager, a system service with its own notification, so Okapi keeps no service of its own alive for them. Okapi doesn't request `POST_NOTIFICATIONS`.

**Label:** Verified.

## Default browser role

**Answer:** Nothing to declare. Play Console has no form for the browser role.

**Why:**

- The manifest's `VIEW` filter for `http` and `https`, with the `DEFAULT` and `BROWSABLE` categories, is what makes Android list Okapi as a browser.
- The menu offers "Set as default browser" while Okapi doesn't hold the role. `MainActivity.askToBeDefaultBrowser()` calls `RoleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)`, and Android shows its own dialog. The person must agree. Okapi can't take the role silently.
- Play's default-handler rules cover only the SMS and Call Log permission groups, which require the app to be the default SMS, Phone or Assistant handler. Okapi requests neither group.
- The Deceptive Behavior policy's only browser rule is not to change a person's browser settings or bookmarks without consent. Okapi changes no other browser's settings, and Android's dialog is the consent for the role.

**Passkeys are separate.** Passkeys need approval as a privileged browser from Google Password Manager and from each other credential provider ([ADR 0001](../adr/0001-system-webview-as-engine.md)). That approval isn't a Play Console declaration, and Okapi doesn't claim passkey support until it has it.

**Label:** Verified.

## Policy points for a browser

These aren't App content forms, but a reviewer checks them, and a browser touches each one.

### Webviews and affiliate spam

Play doesn't allow apps that wrap one website in a webview without the site owner's permission, or that exist to drive affiliate traffic. Okapi opens any site the person chooses, wraps no single site and earns no referral credit. Describe it in the listing as a web browser, not as a front end for any one site.

### JavaScript interfaces and executable code

Apps may not download executable code from outside Google Play. JavaScript in a webview or browser is exempt. A listed violation is a webview with a JavaScript interface that loads untrusted content. `android/app/src/main` has no `addJavascriptInterface` or `addWebMessageListener` call. Keep it that way, or limit any future bridge to trusted origins.

### Accurate claims

The Deceptive Behavior policy requires the title, description, icon and screenshots to match what the app does. Claim only what `docs/SPEC.md` and `CHANGELOG.md` support:

- "No telemetry" is accurate now that the `MetricsOptOut` opt-out is in. The system component still sends Google Safe Browsing lookups while Safe Browsing is on, and crash reports if the device shares usage and diagnostics with Google. The privacy policy says so.
- Typed credentials are never sent for suggestions, including before the `@` is typed.
- Don't claim the known limits away. Okapi has no passkeys, no Custom Tabs service, and no `blob:` or `data:` downloads.

### Impersonation

Okapi renders pages with Android System WebView. Saying so is a fact and is fine. The developer name, title, icon and screenshots must not suggest a link with Google or Chromium. Show Okapi's own UI and the Start Page in screenshots rather than third-party sites and logos. The screenshot advice is an inference from the policy, not policy text.

### Repetitive content

The Spam policy disallows apps that repeat an experience already on Play. Lead the description with what only Okapi does: Profiles with separate web data, Move and Open in Profile, Site Rules for Incoming Links, Pinned Tabs and Folders, and Private Tabs.

### Working functionality

Play rejects apps that crash, freeze or fail to load. Reviewers must be able to open pages. Before each submission, smoke-test the release build on a clean device. Private Tabs set `FLAG_SECURE`, so a reviewer's screenshots of a Private Tab come out blank. That is intended, not a fault.

### User-generated content

The UGC policy covers content that users contribute and other users of the app can see, and it names "specialized browsers or clients to direct users to a UGC platform". Okapi hosts no content and has no accounts or server, and nothing one person does in Okapi reaches another Okapi user. It isn't a client for one platform. The policy has no carve-out for general-purpose browsers, so this conclusion rests on the definition.

### Prominent disclosure for search suggestions

Search suggestions are on by default. While the person types, Okapi sends the text to the chosen search engine: Google by default, or Bing, DuckDuckGo or Brave Search. Play requires an in-app disclosure during normal use, before collection, when data handling may be outside what a person expects.

Today the disclosure is in Settings only. With Google chosen, the text under the **Search suggestions** switch reads: "What you type in the address field is sent to Google to suggest searches. Addresses are not sent, and nothing is sent from a Private Tab." One can argue that people expect a browser's address field to suggest searches. A one-time notice on first use would remove the doubt. TODO(owner): decide, and keep the decision consistent with the Data safety answer for "In-app search history".

## Owner to-do list

- [x] Add `android.webkit.WebView.MetricsOptOut` to the manifest before writing the Data safety answers or the listing.
- [x] Fix the bug that sends credential-shaped text before the `@` is typed.
- [ ] Link the privacy policy from inside the app, for example a row in Settings. Play requires it.
- [ ] TODO(owner): add the developer name, exactly as Google Play shows it, to both copies of the privacy policy ([privacy-policy.md](privacy-policy.md) and `website/public/privacy.html`).
- [ ] TODO(owner): IARC contact email, entered in Play Console only.
- [ ] Confirm the target age groups: 18 and over, or 13 and over.
- [ ] Decide on Restrict Minor Access.
- [ ] Decide whether search suggestions need a first-use notice.
- [ ] Re-run the merged-manifest check on the release `.aab` before the first upload.
- [ ] Check whether Play Console still shows the COVID-19 declaration.
- [ ] Before release, check the Local Network and `ACCESS_FINE_LOCATION` declarations for API 37 apps.

## Sources

Google pages, as gathered for this document. Re-read them before you submit, because Play's forms change.

- [Prepare your app for review: ads, app access and other declarations](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Content ratings](https://support.google.com/googleplay/android-developer/answer/6159978) and [content rating policy](https://support.google.com/googleplay/android-developer/answer/9898843)
- [IARC](https://globalratings.com/about/) and the [ESRB ratings guide](https://www.esrb.org/ratings-guide/)
- [Target audience and content](https://support.google.com/googleplay/android-developer/answer/9867159) and the [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335)
- [News apps declaration](https://support.google.com/googleplay/android-developer/answer/16189314)
- [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271)
- [Government apps declaration](https://support.google.com/googleplay/android-developer/answer/9514050)
- [Account and data deletion](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Advertising ID](https://support.google.com/googleplay/android-developer/answer/6048248)
- [Permissions policy](https://support.google.com/googleplay/android-developer/answer/9888170)
- [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)
- [Spam and minimum functionality](https://support.google.com/googleplay/android-developer/answer/9899034) and [limited functionality](https://support.google.com/googleplay/android-developer/answer/9898783)
- [Device and network abuse](https://support.google.com/googleplay/android-developer/answer/9888379)
- [Deceptive behavior](https://support.google.com/googleplay/android-developer/answer/9888077), [impersonation](https://support.google.com/googleplay/android-developer/answer/9888374) and [misrepresentation](https://support.google.com/googleplay/android-developer/answer/9888689)
- [User generated content](https://support.google.com/googleplay/android-developer/answer/9876937)
- [User data and prominent disclosure](https://support.google.com/googleplay/android-developer/answer/9888076)
- [WebView privacy](https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy)
