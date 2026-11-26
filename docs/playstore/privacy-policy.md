# Okapi privacy policy

Effective date: 25 November 2026

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

In short: Okapi has no server, no accounts, no ads and no analytics. Your history, bookmarks, cookies and site data stay on your device. What leaves your device goes to the sites you open, to your search engine while you type in the address field, and to Google's Safe Browsing service through the app's web engine.

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
- photos and videos you take with "Take photo" or "Record video" for a page's file field, and a copy of each file you choose for one, kept in the app's cache until the app next starts, so the page can read them. This is the same in a Private Tab.
- site icons: the icon of each site the Profile has shown, as the site sent it while its page loaded
- name, color and avatar

One Profile can't see another Profile's data.

For all Profiles together, the app keeps your settings (search engine, Search suggestions, Safe Browsing, theme and compact rows) and your Site Rules.

None of this is sent to us or to anyone else.

### Backup files you make

Settings → Export backup saves a copy of every Profile's name and look, bookmarks and Folders, history, open Tabs and Site settings, with your settings and Site Rules, to a file in a place you choose. Cookies, site storage, sign-ins, Private Tabs and downloaded files are never in it. The file is encrypted with a password you set (AES-256-GCM, with a key derived from the password by PBKDF2), unless you turn that off; then anyone who has the file can read it. Okapi sends the file nowhere itself. If you save it to a cloud drive, that drive's provider holds it, under their own terms. Import backup reads a file you choose, only when you ask.

## What leaves your device, and who receives it

### Sites you open

Okapi shows pages with GeckoView, Mozilla's web engine, which is part of the app. When you open a page, the engine requests it from that site's server, as any browser does. The site receives your IP address, the address you asked for, the Profile's cookies for that site and the browser details the engine sends, which name Firefox for Android. A site embedded in another site gets its own cookies there, so it can't use its cookies to follow you from site to site. The exception is an embedded site that asks for its cookies, as an embedded sign-in does: Okapi gives it them, so it can recognise you on the site that embeds it. Clearing the Profile's cookies and site data takes that access back. None of this stops other ways of recognising a device, such as fingerprinting.

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

The engine downloads Google Safe Browsing's lists of dangerous sites and checks the addresses of pages against them on your device, and warns you before a page known to be dangerous opens. When an address matches an entry, the engine sends Google a short part of a hash of the address to confirm it, not the address itself. Google runs the service; the engine uses Mozilla's access to it. Safe Browsing is on by default. To turn it off, turn off Safe Browsing in Settings. The change also applies to pages that are already open.

### The engine's own updates

<!--
TODO(owner): capture what GeckoView requests by itself on a fresh install with no page open (Mozilla's
Remote Settings for certificate revocation and blocklists, Safe Browsing list updates, anything else), and
describe each here: who receives it and what it contains. ADR 0006 and ROADMAP.md track this.
-->

GeckoView keeps some security data current by itself, such as lists of revoked certificates. Okapi sets up no telemetry and no crash reporting in the engine, so the engine sends Mozilla no usage statistics or crash reports.

### Downloads

A page can only offer a file. Nothing is saved until you agree. The engine asks the site for the file as the page would, with the Profile's cookies for that site, and the site receives what is listed under "Sites you open". Once you agree, Okapi saves the file to your device's Downloads folder. Downloads is a shared folder, so other apps that can read your files can read the files you download. When you open a finished file, Android gives it to the app that you choose.

### Protected video (DRM)

Some streaming sites protect their videos against copying (DRM). The engine plays them through Android's own DRM system (Widevine on most devices), without asking first. To play such a video, your device's DRM system asks the site's licence server for a key. The request carries information about your device's DRM system, which the site and its DRM provider can use to tell devices apart. The first time a video needs it, Android may also ask Google to set up the DRM system on your device. Pages without protected video send none of this.

## Private Tabs

Each Profile can open Private Tabs. Private Tabs:

- share a temporary store of cookies and site data, separate from the Profile's own
- never write to history and never take links from other apps
- aren't saved, and don't come back after the app closes
- block screenshots and the recent-apps preview while one is showing
- never send text to the search engine for suggestions
- don't keep site icons

Their cookies and site storage are kept in memory, and are cleared when the last Private Tab of the Profile closes. Site databases (IndexedDB) are the one exception: the engine writes them to disk encrypted, with a key it holds only in memory, and deletes them once no Private Tab is open. If Android ends the app first, the key goes with it, so what is left can't be read. A file you download in a Private Tab is saved to your Downloads folder, but isn't added to the Profile's list of downloads.

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
- App data stays in the app's private storage, and Android's cloud backups and device transfers leave it out.
- A backup file you export is encrypted with your password unless you choose otherwise.
- Search suggestion requests use HTTPS and carry no cookies.
- During a download, each redirect step receives only its own cookies, so one site's session cookies don't go to another site.
- Release builds don't write addresses, page titles or cookies to the device log.
- The app contains no API keys or other secrets.

## What Okapi doesn't do

- There is no JRNY account. You don't sign in to the app itself.
- It shows no ads of its own.
- It has no analytics and no crash reporting, and sets up neither in its engine.
- It contains no Google Play Services or Firebase libraries.
- It sends none of your data to us, and we don't sell data.
- It asks Android for internet access. It asks for the camera, the microphone or your location only when a site you allowed wants them, and passes them to that site alone. It also asks for the camera when you choose "Take photo" or "Record video" for a page's file field, because Android lets it start the camera app only then. Okapi never uses or keeps them itself, and doesn't ask for your contacts.

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
