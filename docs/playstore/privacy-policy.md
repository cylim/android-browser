# Okapi privacy policy

Effective date: 18 November 2026

<!--
This is the canonical text. website/public/privacy.html publishes the same policy. Change both
together, and change the effective date in both.

TODO(owner): Before you enter this policy's address in Play Console:
- Add the developer name exactly as Google Play shows it, in "Who makes Okapi".
- Resolve the X-Requested-With and emoji font comments below.
- Delete each TODO(owner) once it is done. Before you enter the address in Play Console, this must print nothing:
  grep -n "TODO(owner)" docs/playstore/privacy-policy.md website/public/privacy.html
-->

This policy covers Okapi by JRNY, the Android app with the application ID `app.jrny.browser`. It says what the app keeps on your device, what it sends off your device and who receives it, and how you delete it.

In short: Okapi has no server, no accounts, no ads and no analytics. Your history, bookmarks, cookies and site data stay on your device. What leaves your device goes to the sites you open, to your search engine while you type in the address field, and to Google through Android System WebView.

## Who makes Okapi

Okapi is free software under the MIT License. It is built in public at <https://github.com/cylim/android-browser>. In this policy, "we" means the people who maintain it.

> TODO(owner): developer name, exactly as Google Play shows it.

We run no server for the app, and the app sends nothing to us.

## What stays on your device

Okapi keeps your data in its private storage on your device, where other apps can't read it. Android's cloud backup and device-to-device transfer leave all of it out, so none of it is copied to a backup or to a new device.

Each Profile keeps its own:

- cookies, site storage, cache, site permissions and service workers
- Tabs: the address, title and scroll position of each normal Tab, so that they come back after Android closes the app
- history, up to its latest 5,000 visits
- bookmarks, which are its Pinned Tabs, and its Folders
- list of downloads
- site icons: the icon of each site the Profile has shown, as the site sent it while its page loaded
- name, color and avatar

One Profile can't see another Profile's data.

For all Profiles together, the app keeps your settings (search engine, Search suggestions, Safe Browsing, theme and compact rows) and your Site Rules.

None of this is sent to us or to anyone else.

## What leaves your device, and who receives it

### Sites you open

Okapi shows pages with Android System WebView, the web engine built into Android. When you open a page, WebView requests it from that site's server, as any browser does. The site receives your IP address, the address you asked for, the Profile's cookies for that site and the browser details that WebView sends. WebView also sends the app's ID, `app.jrny.browser`, in an `X-Requested-With` header with every request. Okapi doesn't turn this header off.

<!--
TODO(owner): androidx.webkit 1.18.0, which the app uses, has WebSettingsCompat.setRequestedWithHeaderOriginAllowList
(feature REQUESTED_WITH_HEADER_ALLOW_LIST). ADR 0001 and the engine research found the header can't be removed,
but nobody has tested this API in Okapi. Test it on the installed WebView. If an empty allow-list stops the
header, either use it and rewrite this paragraph, or keep this wording. Change privacy.html, ADR 0001, CHANGELOG.md
and the listing's known limits to match.
-->

A site can store cookies and other data in the Profile you opened it in. What a site does with your data is up to that site and its own privacy policy.

If you use a password manager through Android Autofill, that app sees the forms it fills, under its own privacy policy. Okapi doesn't store passwords.

### Your search engine

Your search engine is Google, unless you choose Bing, DuckDuckGo or Brave Search in Settings. It receives your text in two ways:

- **When you search.** The search opens as a page in the Tab, like any other site you open.
- **Search suggestions.** While you type in the address field, the app sends what you have typed to the search engine, a moment (150 milliseconds) after you stop typing, and shows the searches it suggests. The request goes over HTTPS and carries no cookies, so the search engine can't link it to an Account or a Profile. Its user agent names a browser, but not your device. The search engine still sees your IP address.

The app doesn't send text to the search engine for suggestions:

- when Search suggestions is off in Settings
- from a Private Tab
- when the text starts with `http://` or `https://`, or reads as a web address
- when the text is shaped like credentials, such as `name:secret@example.com`
- when the text has a colon before its first `/`, `?` or `#`, such as `admin:hunter2`, because an `@` typed next would make it credentials

So a search such as `python: list comprehension` gets no suggestions from the search engine. Pressing Go still searches for it.

With Search suggestions off, the address field still suggests pages from the Profile's bookmarks and history, from the device.

### Google Safe Browsing

Android System WebView checks the addresses of pages with Google Safe Browsing, and warns you before a page known to be dangerous opens. Google runs the service. WebView makes the checks, not Okapi, and the app never sees them. Safe Browsing is on by default. To turn it off, turn off Safe Browsing in Settings. The change also applies to pages that are already open.

### Android's download service

A page can only offer a file. Nothing downloads until you agree. Then:

1. The app asks the file's address whether it redirects somewhere else, one step at a time, for up to 5 steps. Each address gets only the Profile's cookies for that address.
2. The app gives the last address to Android's download service, with the Profile's cookies for that address, the browser's user agent and the address of the page you came from.
3. The download service, which is part of Android, fetches the file from that site and saves it to your device's Downloads folder.

The site that serves the file receives what is listed under "Sites you open". Downloads is a shared folder, so other apps that can read your files can read the files you download. When you open a finished file, Android gives it to the app that you choose.

### Android System WebView's own reports

Google makes Android System WebView and updates it through Google Play, separately from Okapi. If your device shares usage and diagnostics data with Google, WebView can send Google crash reports and usage statistics, such as system information, feature use, performance and memory use. Google's WebView documentation says that these reports don't contain the addresses of the pages you visit. Google handles them under its own privacy policy.

Okapi tells WebView not to send usage statistics, whatever your device's setting is. Crash reports still follow your device's setting. To stop them, turn off usage and diagnostics sharing with Google in your device's settings. Where that setting is depends on the device.

## Private Tabs

Each Profile can open Private Tabs. Private Tabs:

- share a temporary store of cookies and site data, separate from the Profile's own
- never write to history and never take links from other apps
- aren't saved, and don't come back after the app closes
- block screenshots and the recent-apps preview while one is showing
- never send text to the search engine for suggestions
- don't keep site icons

When the last Private Tab of a Profile closes, the app clears its cookies and site storage. The rest of the temporary store is deleted the next time the app starts fresh. A file you download in a Private Tab is saved to your Downloads folder, but isn't added to the Profile's list of downloads.

Private Tabs don't hide you from the sites you open, from your network or from Safe Browsing. Sites still receive what is listed under "Sites you open".

## How to delete your data

We hold no data about you, so there is nothing for us to give you or delete. Your data is on your device, and you delete it there:

- **Clear history.** In a Profile's history, Clear history removes every visit, and the icons of sites the Profile has no bookmark or open Tab of. Sign-ins, cookies and open Tabs stay. You can also delete one visit at a time.
- **Delete a Profile.** Its history, bookmarks, Folders, list of downloads, site icons and the Site Rules that name it are deleted at once, and its Tabs close. Its cookies, site storage and cache are wiped the next time the app starts fresh, for example after you restart your device. Until then, Settings shows how many Profiles are waiting to be removed. The last Profile can't be deleted.
- **Uninstall the app.** Android deletes all of the app's data. Clearing the app's storage in Android's settings does the same and keeps the app.
- **Downloaded files** stay in your Downloads folder in every case. Delete them with a file manager.

## How your data is protected

- The app has no server, so we have no copy of your data to lose.
- Each Profile has its own web data store. A Move to another Profile carries only the page's address.
- App data stays in the app's private storage, and backups and device transfers leave it out.
- Search suggestion requests use HTTPS and carry no cookies.
- During a download, each redirect step receives only its own cookies, so one site's session cookies don't go to another site.
- Release builds don't write addresses, page titles or cookies to the device log.
- The app contains no API keys or other secrets.

## What Okapi doesn't do

- There is no JRNY account. You don't sign in to the app itself.
- It shows no ads of its own.
- It has no analytics and no crash reporting of its own, and it tells WebView not to send usage statistics.
- It contains no Google Play Services or Firebase libraries.
- It sends none of your data to us, and we don't sell data.
- It asks Android for internet access. It asks for the camera, the microphone or your location only when a site you allowed wants them, and passes them to that site alone. It never uses or keeps them itself. It doesn't ask for your contacts.

<!--
TODO(owner): the merged release manifest registers androidx.emoji2's EmojiCompatInitializer (Compose brings it in).
By default it asks the device's downloadable-font provider for an emoji font, which on devices with Google Play
Services is usually Google Play Services. Check on such a device. Then remove the initializer in AndroidManifest.xml
(tools:node="remove"), or keep it and add the font request to "What leaves your device". Change privacy.html to match.
-->

## Children

Okapi is a general-purpose web browser. It opens any site and has no content filter or parental controls, so it isn't designed for children. It sends us nothing about anyone, children included.

## Changes to this policy

When a release of Okapi changes what leaves your device, we update this policy in that release and change its effective date. Every change to this policy is in the history of this file on GitHub: <https://github.com/cylim/android-browser/commits/main/docs/playstore/privacy-policy.md>.

## Contact

To ask about this policy or about privacy in Okapi, open an issue at <https://github.com/cylim/android-browser/issues>. Issues are public, so don't put personal information in one.

To report a privacy problem privately, for example data that leaves your device when this policy says it doesn't, follow the [security policy](https://github.com/cylim/android-browser/blob/main/SECURITY.md): on the repository's Security tab, select **Report a vulnerability**.
