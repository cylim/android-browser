# Google Play Data safety answers

This page answers the Data safety form in Play Console (**Policy** → **App content** → **Data safety**) for Okapi, a browser by JRNY (`app.jrny.browser`), one question at a time. Each answer gives the reason and the code or spec that supports it. The public policy is [privacy-policy.md](privacy-policy.md), published as [website/public/privacy.html](../../website/public/privacy.html). Keep the three in step.

Status: not submitted. [ROADMAP.md](../ROADMAP.md) puts the Google Play release under "After v1". The answers describe the code on 2026-11-17 (`main` at `58e7f11`, plus the uncommitted Start Page work, which makes no network requests, the WebView usage statistics opt-out and the suggestion fix for credential-shaped text).

## How to read this page

- **Answer** is what to select in Play Console.
- Every answer assumes a build with the WebView usage statistics opt-out from [Blocker 1](#1-turn-off-webview-usage-statistics). It is in the manifest, and a device test fails any build without it.
- **Confidence** comes from Google's help pages: *verified* means a page says it, *likely* means it follows from a page's definitions, and *uncertain* means Google's pages don't settle it. Confirm every *uncertain* answer in Play Console before you submit, and record what you decided here.
- `TODO(owner)` marks a decision only the owner can make.

## Blockers before you submit

### 1. Turn off WebView usage statistics

Done. `android/app/src/main/AndroidManifest.xml` sets `android.webkit.WebView.MetricsOptOut` to `true` inside `<application>`, and `ManifestTest.webViewMetricsAreOptedOut` (`android/app/src/androidTest/java/app/jrny/browser/ManifestTest.kt`) reads it back from the installed app. Android System WebView now sends no usage statistics (system information, feature use, performance, memory) from inside Okapi, whatever the device's usage and diagnostics setting is. Google's WebView documentation says an app uploads them only if the person consents and the app doesn't opt out.

The opt-out doesn't stop WebView crash reports. Those follow the device's own setting and, according to Google, contain no page addresses. See D2.

Both copies of the privacy policy now say this.

Source: <https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy>

### 2. Fix the suggestion bug for credential-shaped text

Done. `AddressInput.isSearch` (`android/app/src/main/java/app/jrny/browser/address/AddressInput.kt:36`) now also refuses text with a colon before the first `/`, `?` or `#` (after the scheme, if there is one). An `@` typed later would make what follows the colon a password, so `admin:hunter2` stays on the device at a pause in typing, before the `@router.example` arrives. Tests: `AddressInputTest.onlyTextThatWouldBeSearchedForMayGoToTheSearchEngineWhileTyping` (unit) and `BrowserStateTest.whatIsTypedGoesToTheSearchEngineOnlyWhenItMay` (device).

The cost: a search with an early colon, such as `python: list comprehension`, gets no suggestions from the search engine while it is typed. Bookmark and history suggestions still show, and Go still searches. Both copies of the privacy policy no longer have the "Known bug" paragraph.

### 3. Link the privacy policy inside the app

Google Play requires a privacy policy link in Play Console and inside the app. The app has no such link yet. A row in Settings that opens the published page in a new Tab is enough.

Source: <https://support.google.com/googleplay/android-developer/answer/10144311>

### 4. Publish the policy and fill in its placeholders

- Merge `website/public/privacy.html`. The Pages workflow publishes it at `https://cy.my/android-browser/privacy.html` until the site has its own domain. Enter that address in **App content** → **Privacy policy**.
- TODO(owner): add the developer name, exactly as Google Play shows it, to both copies of the policy. Play requires the policy to name the entity on the store listing. Both copies mark the place with `TODO(owner)`.
- TODO(owner): resolve the `X-Requested-With` and emoji font comments in [privacy-policy.md](privacy-policy.md).
- Before you enter the address, `grep -n "TODO(owner)" docs/playstore/privacy-policy.md website/public/privacy.html` must print nothing.

### 5. Check the final build's merged manifest

A local release build on 2026-11-17 (`android/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`) has two permissions: `android.permission.INTERNET` and the `app.jrny.browser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that AndroidX adds. Since the web capabilities change, the manifest also asks for `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`, which pages get only after the person allows them (SPEC, "Site permissions and Site settings"). It has no `com.google.android.gms.permission.AD_ID`. Check the merged manifest of the bundle you upload again, because a new dependency can add permissions.

## Decisions for the owner

These change answers below. Record each decision here when you make it.

- **D1. Search suggestions.** TODO(owner): declare them (recommended) or claim an exemption. See [In-app search history](#in-app-search-history).
- **D2. Android System WebView's own traffic.** TODO(owner): confirm that Safe Browsing lookups and WebView crash reports are left out of the form. See [Web browsing history](#web-browsing-history) and [App info and performance](#app-info-and-performance).
- **D3. Disclosure for suggestions.** Search suggestions are on by default (`settings/Settings.kt:31`, `suggestions: Boolean = true`). Settings explains them ("What you type in the address field is sent to %1$s to suggest searches…", `res/values/strings.xml:158`), but only in Settings. Google's Prominent Disclosure rule asks for an in-app notice during normal use when data handling may surprise people. TODO(owner): decide whether to add a first-run notice or turn suggestions off by default. Google's pages don't settle whether the rule applies here (confidence: uncertain).

Source for D3: <https://support.google.com/googleplay/android-developer/answer/9888076>

## Step 1: Overview

Nothing to answer. The form covers every version and every region of `app.jrny.browser`. You can export the answers as CSV and import them again.

## Step 2: Data collection and security

### Does your app collect or share any of the required user data types?

| | |
| --- | --- |
| Answer | **Yes**, if D1 is "declare" (recommended): only **App activity → In-app search history**. **No**, if D1 is "exempt". |
| Why | Google defines "collect" as sending data off the device from the app, to the developer or to a third party. Search suggestions are the only data Okapi's own code sends off the device that the open-web exemption doesn't clearly cover. Everything else stays on the device or is open-web browsing in WebView. |
| Evidence | `settings/Suggestions.kt:30` (`HttpSearchSuggestions`), `BrowserState.kt:500-517` (`suggest`), and the [data types table](#step-3-data-types) |
| Confidence | Uncertain, because of D1 and D2 |

If the answer is No, Play Console skips the next two questions.

### Is all of the user data collected by your app encrypted in transit?

| | |
| --- | --- |
| Answer | **Yes** |
| Why | The only declared data, suggestion requests, goes over HTTPS to all four engines: `suggestqueries.google.com`, `api.bing.com`, `duckduckgo.com/ac/` and `search.brave.com/api/suggest`. The request doesn't follow redirects, so a redirect can't downgrade it to `http`. |
| Evidence | `settings/Settings.kt:13-22` (`SearchEngine.suggestTemplate`), `settings/Suggestions.kt:40` (`instanceFollowRedirects = false`) |
| Confidence | Verified for the code. Google: the answer must cover all collected data, including SDK traffic. |

### Do you provide a way for users to request that their data is deleted?

| | |
| --- | --- |
| Answer | **No** |
| Why | Okapi has no server and receives nothing, so there is nothing for anyone to ask us to delete. Suggestion requests go to the search engine, which keeps them under its own policy. Okapi can't delete them there. Don't claim the deletion badge. On-device deletion (clear history, delete a Profile, uninstall) is described in the privacy policy, not here. |
| Evidence | No server or account in the code. [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md). [SPEC.md](../SPEC.md), "Profiles" and "Browsing features". |
| Confidence | Likely. TODO(owner): confirm the answer options in Play Console. |

### Account creation and deletion

| | |
| --- | --- |
| Answer | The app doesn't let users create an account. No deletion web link is needed. |
| Why | There is no JRNY account. A Profile is a local browsing identity, not an account, and an Account in [GLOSSARY.md](../../GLOSSARY.md) is a sign-in on some other website. Google requires an in-app deletion path and a web link only from apps that allow account creation. |
| Evidence | [GLOSSARY.md](../../GLOSSARY.md), "Profile" and "Account". The manifest has no sign-in component. |
| Confidence | Verified for the rule. TODO(owner): confirm the question's wording in Play Console. |

Source: <https://support.google.com/googleplay/android-developer/answer/13327111>

### Independent security review (optional)

Answer **No**, or leave it unanswered. Okapi has had no independent security review.

## Step 3: Data types

"Declare" means "select this type as collected or shared". The open-web exemption is Google's rule that "you do not need to declare data collection from a webview in which users are navigating the open web".

| Data type | Declare | Why | Evidence |
| --- | --- | --- | --- |
| Location: approximate, precise | No | Okapi holds the location permissions only to pass a location to a site the person allowed, while they browse it: open-web browsing, under the open-web exemption. Okapi never reads, keeps or sends a location itself. | `tab/Tab.kt` (`onGeolocationPermissionsShowPrompt`), `ui/PageAsks.kt` |
| Personal info: name, email, user IDs, address, phone, race, political or religious beliefs, sexual orientation, other | No | No account and no forms of its own. What people type into sites is open-web browsing. Profile names stay on the device. | `profile/ProfileRepository.kt` (Room, on device) |
| Financial info | No | No purchases or payments | No billing library in `android/app/build.gradle.kts` |
| Health and fitness | No | None handled | |
| Messages: emails, SMS, other in-app messages | No | None handled | |
| Photos and videos, Audio | No | The camera and microphone reach only a site the person allowed, and a file only the site whose file field the person chose it for: open-web browsing. A photo or video the camera app takes for a file field stays in the app's cache until the next start. Nothing goes to us. No media or storage permission. | `tab/Tab.kt` (`onPermissionRequest`, `onShowFileChooser`), `ui/PageAsks.kt` |
| Files and docs | No | Downloads are files the person asked a site for. Android's download service fetches them from that site to the device's Downloads folder. Nothing goes to us or to a third party. | `download/SystemDownloader.kt:19-31`, `download/DownloadRedirects.kt` |
| Calendar, Contacts | No | No permission, none handled | `AndroidManifest.xml` |
| App activity: App interactions | No | No analytics, and WebView's usage statistics, which include feature use, are turned off ([Blocker 1](#1-turn-off-webview-usage-statistics)). | [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md) |
| App activity: In-app search history | See [below](#in-app-search-history) | Typed text goes to the chosen search engine for suggestions | `BrowserState.kt:512` |
| App activity: Installed apps | No | The app doesn't query installed apps | `AndroidManifest.xml` (no `QUERY_ALL_PACKAGES`) |
| App activity: Other user-generated content, Other actions | No | Bookmarks, Folders, Site Rules and settings stay on the device | `data/JrnyDatabase.kt`, `settings/Settings.kt` |
| Web browsing: Web browsing history | No | History stays on the device, per Profile. Requests to pages fall under the open-web exemption. Safe Browsing: see [below](#web-browsing-history). uBlock Origin decides what to block on the device. Its filter list downloads carry only the list's address, nothing about the pages opened ([ADR 0008](../adr/0008-ublock-origin-as-content-blocker.md)). | `library/Library.kt`, `res/xml/data_extraction_rules.xml` |
| App info and performance: Crash logs, Diagnostics, Other app performance data | No | No crash-reporting SDK, and WebView's usage statistics are turned off. See [below](#app-info-and-performance). | [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md) |
| Device or other IDs | No | The app reads no device ID and has no advertising ID. WebView's crash reports carry a random pseudonymous token (D2). | Merged manifest: no `AD_ID` ([Blocker 5](#5-check-the-final-builds-merged-manifest)) |

Sources: <https://support.google.com/googleplay/android-developer/answer/10787469> for the data types, the definition of "collect" and the open-web exemption.

### In-app search history

While someone types in the address field, `BrowserState.suggest` sends the trimmed text to the chosen engine's suggestion address, 150 ms after the last keystroke. The open-web exemption doesn't clearly cover this, because the app's own code sends the request (`HttpURLConnection` in `settings/Suggestions.kt`), not the WebView, and it happens before the person searches.

It is not sent:

- when **Search suggestions** is off (`BrowserState.kt:512`, `current.suggestions`)
- from a Private Tab (`BrowserState.kt:502`, `tab.isPrivate`)
- for an address, credential-shaped text, or text with a colon before the first `/`, `?` or `#` that an `@` could still turn into credentials (`AddressInput.isSearch`, [Blocker 2](#2-fix-the-suggestion-bug-for-credential-shaped-text))

The request carries no cookies and a generic user agent that names no device (`settings/Suggestions.kt:26-29` and `USER_AGENT`).

**D1, option A: declare it (recommended).** It matches Google's definition of "collect", which counts data sent to a third party whether or not the developer receives it. A listing that admits it can't be called misleading later.

| Question | Answer | Why |
| --- | --- | --- |
| Collected or shared? | **Collected** and **Shared** | Sent off the device to a third party, the search engine. The developer never receives it. |
| Is this data processed ephemerally? | **No** | Okapi keeps the text only in memory for one request. But the search engine's retention isn't under Okapi's control, so "ephemeral" can't be promised. |
| Is this data required or optional? | **Optional** | Every user can turn **Search suggestions** off in Settings. Private Tabs never send it. |
| Why is this data collected? | **App functionality** | Suggestions in the address field. Not analytics, advertising, personalization, fraud prevention or account management. |
| Why is this data shared? | **App functionality** | Same as above |

The listing then shows "Data shared: App activity" and "Data collected: App activity", optional, encrypted in transit, and "Data can't be deleted".

**D1, option B: claim an exemption.** Google exempts a transfer from "sharing" when a specific user action triggers it and the person expects it, and allows ephemeral processing to stay off the listing. Typing in an address field with suggestions on could be read as such an action. Weak points: suggestions are on by default, the text leaves before the person presses search, and the exemption covers sharing, not collection. Choose B only after confirming it in Play Console's help or with Google support.

Confidence for both: uncertain. Google gives no browser example.

### Web browsing history

| | |
| --- | --- |
| Answer | **Not declared** |
| Why | History, bookmarks, Tabs, downloads and site icons stay in the app's private storage, per Profile, and are left out of cloud backup and device transfer. Requests to the pages someone opens fall under the open-web exemption. That includes site icons, which the WebView loads with the page (`onReceivedIcon`), and downloads, which the person asks for. Safe Browsing lookups are made by Android System WebView, a system component that Google updates, not by code Okapi bundles, while the person browses the open web. The app only switches them on or off. |
| Evidence | `android:allowBackup="false"` and `res/xml/data_extraction_rules.xml` (everything excluded). `icons/SiteIcons.kt`. `tab/Tab.kt:440` (`onReceivedIcon`). `tab/Tab.kt:57-67` (`setSafeBrowsingEnabled`). |
| Confidence | Verified for on-device data. Likely for icons and downloads. Uncertain for Safe Browsing (D2): Google's Data safety page doesn't address system WebView features. |

### App info and performance

| | |
| --- | --- |
| Answer | **Not declared** |
| Why | Okapi has no crash-reporting SDK or analytics, and crash reports come from people who paste a log into a GitHub issue. WebView's usage statistics are turned off ([Blocker 1](#1-turn-off-webview-usage-statistics)), so the only remaining reports are WebView's crash reports. Android System WebView sends them under the device's own usage-and-diagnostics consent, and Google says they contain no page addresses. |
| Evidence | `android.webkit.WebView.MetricsOptOut` in `AndroidManifest.xml`, checked by `ManifestTest`. [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md). [SPEC.md](../SPEC.md), "Data protection". The dependencies in `android/app/build.gradle.kts`: AndroidX, Compose, Room, Guava and kotlinx-serialization, with no crash or analytics library. |
| Confidence | Uncertain (D2). Google's pages require declaring what an app's SDKs send, but don't say whether a system component's uploads count. |

## Step 4: Data usage and handling

Only **In-app search history** needs this step, and only under D1 option A. Its answers are in the [option A table](#in-app-search-history).

## Step 5: Store listing preview

Check that the preview matches the privacy policy:

- **Option A:** "Data shared: App activity". "Data collected: App activity". "Data is encrypted in transit". "Data can't be deleted". No other types.
- **Option B:** "No data shared with third parties" and "No data collected".

If the preview shows anything else, a data type was selected by mistake.

## Sources

- Data safety form, definitions and exemptions: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Privacy policy requirements: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Account deletion requirements: <https://support.google.com/googleplay/android-developer/answer/13327111>
- User Data policy and prominent disclosure: <https://support.google.com/googleplay/android-developer/answer/9888076>
- WebView privacy, `MetricsOptOut` and crash reports: <https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy>
- WebView Safe Browsing: <https://developer.android.com/develop/ui/views/layout/webapps/managing-webview>
