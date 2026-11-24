# Okapi v1 specification

This document describes what v1 of Okapi, a browser by JRNY, does. Terms in **bold** are defined in [GLOSSARY.md](../GLOSSARY.md). For the reasons behind major decisions, see [docs/adr](adr/).

## Platform

- Android 15 (API 35) and later. `targetSdk` is the latest stable API level.
- Kotlin, Jetpack Compose, Material 3, Room. Gradle uses the Kotlin DSL and a version catalog.
- Pages render in GeckoView, Mozilla's Gecko engine, shipped inside the app, with one session context for each **Profile** ([ADR 0006](adr/0006-geckoview-as-engine.md)). Okapi takes each GeckoView security release as it comes, about every 4 weeks.
- No Google Play Services, Firebase, analytics or API keys ([ADR 0002](adr/0002-no-proprietary-dependencies-or-telemetry.md)).
- Application ID: `app.jrny.browser`. Display name: Okapi ([ADR 0005](adr/0005-product-name-okapi.md)). Google Play title: "Okapi Browser by JRNY".

## Profiles

- A **Profile** has a name and a look: a color, how strongly that color shows, and an icon or an emoji for its avatar. It owns its own web data (cookies, site storage, cache, site permissions, service workers), **Tabs**, recently closed Tabs, history, bookmarks, downloads, Site settings and Home Screen Shortcuts.
- App settings (search engine, suggestions, Safe Browsing, theme, text size, dark pages, pull to refresh) are shared by all Profiles.
- On first launch the app creates one Profile, which becomes the **Active Profile**.
- There is always exactly one **Active Profile**. Switching Profiles changes it.
- Deleting a Profile:
  1. The person confirms. The dialog warns that every **Account** signed in within that Profile will be signed out.
  2. The Profile disappears from the UI and its Tabs close.
  3. If it was the Active Profile, another Profile becomes active. The last remaining Profile cannot be deleted.
  4. Its web data is wiped at once, and again at the next cold start, in case a page still on screen wrote something meanwhile. Settings show "N profiles pending removal" until then.
  5. Its Site settings and recently closed Tabs are deleted at once, and its Home Screen Shortcuts are disabled.

## Tabs

- Every **Tab** belongs to exactly one Profile.
- **Move**: carries only the Tab's current address. The original Tab closes, and the page reloads in the target Profile ([ADR 0003](adr/0003-move-carries-only-the-address.md)). Before the Move, the UI warns that unsaved form input will be lost.
- **Open in Profile**: opens the current address as a new Tab in another Profile and keeps the original.
- At most 5 Tabs are live at once, across all Profiles: the visible Tab and the 4 most recently used. Other Tabs are unloaded and reload when opened.
- After Android kills the app, each normal Tab is restored with its address, title and scroll position. Back/forward history is not restored, and page content is never written to disk.
- A Tab without a web page shows the **Start Page**.

### Reopen closed Tab

- Each Profile remembers its last 10 closed Tabs, newest first, with only their address and title. Back/forward history is not kept.
- The list is kept on disk, like the Tabs it restores, so it survives Android killing the app.
- Right after a Tab closes, a bar offers "Undo" for a few seconds, which reopens it in its old place in the Tab list and shows it.
- "Recently closed" sits at the top of the history and lists the same Tabs. Choosing one reopens it at the end of the Tab list and takes it off the list.
- Private Tabs and Pinned Tabs never go on the list. A closed Pinned Tab keeps its bookmark, so it can already be opened again.
- Clearing a Profile's history clears the list too, as does Clear browsing data with history or recently closed Tabs checked.

### Start Page

- The Start Page takes the page's place in a Tab that has no web page: a new Tab, a restored Tab without an address, or `about:blank`. The address field stays empty.
- It belongs to the Tab's Profile. It shows the Profile's avatar and name, and its tiles and cards are tinted with the Profile's color, as strongly as the Profile sets.
- "Pinned" shows the Profile's bookmarks as tiles, in the Tab list's order, with each Folder's tiles under its name. Choosing a tile works like choosing that Pinned Tab, except that a bookmark without an open Tab opens in this Tab, which becomes its Pinned Tab, so no empty Tab is left behind. A bookmark with an open Tab shows that Tab, and this one closes.
- "Recent" lists the Profile's latest 6 pages, each page once, newest first. Choosing one loads it in this Tab. "Show all" opens the history.
- With nothing pinned and no history, a short note says how to pin a Tab and what a Profile is, in place of both sections.
- A Private Tab's Start Page shows neither section. It shows the lock and says what a Private Tab keeps and what it discards (see "Private Tabs").
- The Start Page makes no network requests. A tile or row shows a site's icon only if the Profile already keeps it (see "Site icons"), and the first letter otherwise.

### Pinned Tabs

- A Profile's **bookmarks** are its **Pinned Tabs**: the Tab list shows them under "Pinned", above the Profile's other Tabs, in the Sidebar and in the Tab Overview. With nothing pinned there is no "Pinned" section.
- A Pinned Tab is open while a Tab shows it, and closed otherwise. A closed one is dimmed. Choosing it opens a new Tab with the bookmark's address; choosing an open one shows its Tab. A bookmark has at most one open Tab.
- Closing an open Pinned Tab closes its Tab and keeps the bookmark. Unpinning removes the bookmark, and its Tab stays open as an ordinary Tab.
- A Tab is pinned from its row (hold it, then "Pin Tab") or by bookmarking its page from the page menu. Either bookmarks the page, unless the Profile already has it, and makes that Tab the bookmark's Pinned Tab. Only a Tab with a web page can be pinned, and never a Private Tab.
- A Pinned Tab keeps its bookmark's name and address when its page moves on. Its menu then offers the way back to the pinned page.
- Pinned Tabs can be grouped in **Folders**, one level deep. A Folder can be collapsed, renamed and deleted; deleting it keeps its bookmarks, at the top level.
- New bookmarks and Folders go to the end. A bookmark moved into or out of a Folder goes to that list's end.
- A Pinned Tab or a Folder that is held follows the finger. Dragged past the row next to it, the two change places; a Pinned Tab let go on a Folder's row goes into that Folder, and one dragged past its Folder's first or last row leaves it. Let go without having been dragged, the row opens its menu, where "Move up" and "Move down" do the same without dragging.
- The Bookmarks panel lists the same bookmarks, each Folder under its name. Choosing one there opens its Pinned Tab.

### Styling

- While a Profile is active, the browser wears its color: the Sidebar, the Bottom Bar and the Tab Overview are tinted with it, and the browser's accents (the chosen option, switches, buttons, links in dialogs) take it. Switching Profiles glides from one color to the other.
- Each Profile sets its own color (one of 12) and its color strength: soft, medium or strong. Strength changes the tint of the surfaces, never the text on them.
- A Profile's avatar shows one of 8 icons or any one emoji. Choosing an emoji replaces the icon, and choosing an icon gives the emoji up.
- "Compact rows" (Settings, for all Profiles) makes every row of the Tab list one line and 40dp tall: a Tab shows its title without its site.
- The app's own theme stays one setting for all Profiles: the device's, light or dark.

### Site icons

- Every row of the Tab list starts with a mark for its site: the site's own icon once the Profile has shown a page of that site, and until then the first letter of the site's name on a color picked by that name.
- Icons are kept on the device, per Profile, in the app's private storage. A Profile never shows an icon another Profile fetched.
- A Private Tab's icons are never kept.
- Clearing a Profile's history removes the icons of every site it has no bookmark or open Tab of. Deleting a Profile removes all of its icons at once.

### Private Tabs

- Each Profile can open **Private Tabs**. All of a Profile's Private Tabs share one temporary web data store, which is separate from the Profile's normal store.
- Private Tabs never write to history, are never restored after the app is killed, and block screenshots and recent-apps previews (`FLAG_SECURE`).
- Their storage is kept in memory, and is discarded once the last Private Tab of the Profile closes. The next Private Tab gets a new, empty store. The one exception is site databases (IndexedDB): the engine writes them to disk encrypted, with a key it holds only in memory, under names that don't say the site. They are deleted once no Private Tab is open in any Profile. If Android ends the app first, the key goes with it, so what is left can't be read.
- Private Tabs are kept in memory only: they survive the app's window being rebuilt, but not the app being closed or killed. Closing the app's window ends them even while Android keeps the app's process around.
- A Private Tab is listed after the Profile's other Tabs, marked with a lock. Its page is named in the list only while a Private Tab is the one showing, which is when the window can't be captured.
- A Private Tab can't be Moved or opened in another Profile. Its downloads are saved to the device but not put on the Profile's list of downloads.
- In a Private Tab, Share, Copy link and Print work. A printed or saved PDF stays off the Profile's list of downloads, like its downloads. Add to Home screen is hidden: the shortcut would open a normal Tab and leave the private address on the Home screen.
- A Private Tab never remembers a site permission. Each grant lasts until the page leaves its site or the Tab closes.
- Links held in a Private Tab open only in Private Tabs, and handing a link to another app always asks first (see "Links to other apps").

## Incoming Links

- When Okapi is the default browser, an **Incoming Link** opens as a new normal Tab in the Active Profile, unless a Site Rule names another Profile for it. It never opens in a Private Tab, even if the person left the app from one.
- A newly opened Incoming Link shows a one-tap "Move to Profile…" action, until it is dismissed or another Tab is shown.
- Only `http` and `https` links are accepted. Anything else another app sends is ignored.
- A Home Screen Shortcut is not an Incoming Link: it opens in its own Profile (see "Add to Home screen").
- v1 does not provide a Custom Tabs service. Apps that ask for Custom Tabs fall back to opening a normal Tab.

### Site Rules

- A **Site Rule** sends the Incoming Links for the addresses it covers to one Profile, which becomes the Active Profile. The page says a Site Rule opened it and still offers "Move to Profile…".
- A rule names a site, and optionally a path:
  - `example.com` covers that site and every site under it (`www.example.com`, `mail.example.com`).
  - `example.com/work` covers that path and everything under it (`/work/today`, not `/workshop`).
  - `*` stands for any run of characters: `*.example.com` covers only the sites under `example.com`.
- Only the site and the path count. The scheme, port, query and fragment of a link are ignored, and so is the case of the site's name. The path's case counts.
- A link is judged by what a browser would load: `\` reads as `/`, `.` and `..` steps in the path are taken first, and a site's name in its own script matches its ASCII spelling.
- When several rules cover a link, the one that names more of the site wins, then the one that names more of the path, then the older rule. A pattern has one rule, so it never names two Profiles.
- Site Rules apply only to Incoming Links. Links followed inside a page, and addresses typed in the address field, stay in their Tab.
- Site Rules belong to the app, not to one Profile. They are listed, added, changed and deleted under Settings → Site Rules, where "Check a link" says which Profile a given link would open in.
- After an Incoming Link is Moved to another Profile, the page offers a rule with one tap ("Always"): for that link's site, or, when a rule sent the link elsewhere, a change of that rule.
- Deleting a Profile deletes the rules that name it. Their links open in the Active Profile again.

## Layout

The layout depends on window width, not device type. It switches live when the window resizes (rotation, split screen, folding), and Tabs survive the switch.

### Sidebar (window width ≥ 840dp)

- An Arc-style vertical list that shows only the Active Profile's Tabs: its Pinned Tabs and Folders first, then its other Tabs.
- The Profile switcher sits at the bottom of the Sidebar. Choosing a Profile, or swiping between Profiles, replaces the whole Tab list.
- The Sidebar can be collapsed to give the page the full width.

### Bottom Bar (window width < 840dp)

- A Safari-style address and search field along the bottom edge.
- The Active Profile's avatar sits at the left of the field. Tapping it opens the Profile sheet (switch, create, manage).
- Swiping along the bar switches between the Active Profile's Tabs. A tab overview button shows them all.

### Page menu

The page menu ("More") is the same in the Sidebar, the Rail and the Bottom Bar:

1. A row of icons: Forward (only when the bar has no room for its own Forward button), Bookmark this page, Share and Reload.
2. **Page**: Copy link, Find in page, Desktop site (a switch), Print, Add to Home screen.
3. **Profile**: Move, Open in Profile.
4. **Browser**: New Tab, New Private Tab, History, Bookmarks, Downloads, Settings, and "Set as default browser" while Okapi isn't.

Items that need a web page, such as Share and Find in page, are disabled on the Start Page.

## Pages

### Site permissions and Site settings

- A site that asks for the camera, the microphone or the device's location gets a prompt naming the site and what it wants: "Allow once", "Always allow" or "Block". In a Private Tab the prompt offers only "Allow" and "Block", and says the Tab forgets the choice (see "Private Tabs").
- Each Profile keeps its own grants, for each site (scheme, host and port). Allowing meet.google.com the camera in one Profile doesn't allow it in another.
- "Allow once" lasts until the page leaves that site or the Tab closes. "Block" stops the site from asking again; it gets a refusal at once. In a Private Tab, "Allow" and "Block" last only as long as "Allow once" does.
- Android is asked for its own runtime permission when a site the person allowed needs it and the app doesn't have it, never before. If the person refuses there, the site gets a refusal, and the prompt says how to allow it in Android's settings.
- For location, Okapi asks Android for precise location, and Android's dialog lets the person choose approximate instead.
- A site can only ask from a page the person is looking at. A site in a background Tab or an unloaded Tab gets a refusal.
- Settings → Site settings lists the Active Profile's sites that have a permission or a Desktop site choice. Each permission can be changed to Allow or Block, or reset, which makes the site ask again. "Reset all" resets every site of the Profile.
- Deleting a Profile deletes its Site settings.

### File upload

- A page's file field opens Android's file picker. The picker shows the kinds of file the page accepts, and allows several files if the page does.
- When the page accepts images or video, the picker also offers "Take photo" or "Record video", which open the camera app. With `capture` set, the page still gets the choice, not the camera straight away.
- A photo or video the camera app makes is kept in the app's cache, where the page can read it, and removed the next time the app starts.

### Fullscreen video

- A video's fullscreen button fills the whole screen with it and hides Android's status and navigation bars.
- On a phone the screen turns to match the video: a wide video shows in landscape, and a tall one in portrait, even with auto-rotate off. The orientation goes back to normal afterwards.
- On a large screen (600dp and wider, such as a tablet or an unfolded foldable) Android 17 ignores an app's orientation, so the video fills the screen the way the screen is held.
- Back, or the video's own button, leaves fullscreen.

### HTTP sign-in

- A site that asks for a username and password before it sends the page (HTTP authentication) gets a dialog that names the site and its realm.
- The fields are marked as a username and a password, so a password manager can fill them through Android Autofill.
- Okapi never stores what is typed. Cancelling shows the site's own error page.

### Page dialogs

- What a page's scripts ask the person shows as a dialog: an alert, a question with OK and Cancel, and a line of text to type. Each names the site it comes from, so a page can't pass its words off as Okapi's.
- A list field (`<select>`) shows its items in a dialog, grouped as the page groups them. A list that takes several items has checkboxes and OK.
- Date, month, week, time and date-and-time fields open Android-style pickers; "Clear" empties the field. A color field offers a set of colors and a color code.
- A page that wants to stop the person leaving (it may have unsaved changes) asks "Leave this page?". Reloading a page a form made asks before the form is sent again.
- Dismissing a dialog is the page's "Cancel". One dialog shows at a time, and only for the Tab on screen.
- A page's own Share button opens Android's share sheet with what the page shares.
- A pop-up a page opens without a tap stays blocked.

### Error pages

- A page that can't be loaded shows Okapi's own page in the Tab: what went wrong in plain words (no connection, no such site, a connection that isn't secure, a site that refused, a dangerous site) and "Try again". The Tab keeps the page's address, and the Tab list shows its site, not the error.
- A dangerous site, reported by Safe Browsing, isn't opened. Its page offers only "Go back".
- A site whose security certificate can't be trusted isn't opened, and there is no way past it. Certificates are checked against Mozilla's list of trusted authorities, which comes with the engine. Authorities installed on the device by the person or an employer aren't trusted.
- The error page is made by Okapi and needs nothing from the network.

### Links to other apps

- A web address (`http` or `https`) always stays in the Tab.
- `tel:`, `mailto:`, `sms:`, `smsto:` and `geo:` links that the person taps go to the app that handles them at once.
- Any other scheme, `intent:` and `market:` links included, shows a bar under the page with an "Open" button. It names the app that would open the link when Android tells Okapi which one: Android hides most apps from other apps, so for many links the bar only says that the page wants to open another app. Nothing leaves the browser until "Open" is tapped. The bar goes when the page moves on.
- A link for another app that a page opens without a tap, such as a redirect to `tel:`, also waits for "Open", whatever its scheme. A web address opened without a tap still loads in the Tab.
- In a Private Tab, every link to another app waits for "Open".
- An `intent:` link may only open an app that accepts browsable links. Its component, selector, extras and flags are dropped. An `intent:` link for a web page that names no app loads in the Tab. Android hides most apps from other apps, so whether an app can open a link is known only once "Open" tries: if no app takes it and it names a web fallback address, the Tab loads that address, and otherwise the bar says that no app can open it.
- Only the link goes to the other app. The Profile, its cookies and the page's address do not.

### Link and image menu

Holding a link or an image opens a menu:

- On a link: Open in new Tab, Open in Private Tab, Open in Profile…, Copy link, Share link.
- On an image: Open image in new Tab, Save image, Copy image, Share image. An image that is also a link gets both sets.
- "Open in new Tab" opens the address as a new Tab at the end of the Profile's Tab list and stays on the current Tab. A bar offers "Show" to go to it.
- In a Private Tab, "Open in new Tab" opens a Private Tab, "Open in Private Tab" is not offered, and "Open in Profile…" is hidden.
- "Save image" works like a download from the page: it asks first, and it uses the Tab's Profile.

### Share, Copy link and Print

- Share hands the page's address and title to Android's share sheet. Copy link puts the address on the clipboard. Nothing else from the page goes with them.
- Print lays the page out as a PDF, then opens Android's print dialog for it, which can also save the PDF. A page that can't be laid out says so in a short message.

### Find in page

- Find in page shows a field, a "N of M" count and up and down arrows, and highlights every match on the page.
- In the Bottom Bar layout the field takes the address bar's place at the bottom, above the keyboard. In the Sidebar layout it shows as a bar across the top of the page, which moves the page down so no match hides under it.
- It closes with its own button, with Back, or when the page moves on.

### Desktop site

- "Desktop site" asks sites for their desktop version: the Tab sends the user agent of Firefox on a Linux desktop, and lays out a page that asks for the device's width as a desktop browser would.
- The Tab switches before a page of the site loads, including a form sent to that site.
- The choice is remembered for each site, in each Profile, and shows in Site settings. It applies to every Tab of that Profile on that site.
- In a Private Tab the choice lasts until the Tab closes.

### Text size

- Settings → Text size scales the text of every page, from 50% to 200%. The default is 100%. It is one setting for all Profiles.
- Pinch zoom still works on top of it.

### Dark pages

- Settings → "Darken pages without a dark theme" is off by default. It applies only while the app's theme is dark.
- When it's on, pages are asked for their dark theme. A web page whose background is still light once it has loaded has no dark theme of its own, and Okapi darkens it: its colors are inverted, and its images and videos are turned back so they keep theirs. Turning it on or off, or a change of the app's theme while it's on, reaches open pages at once, without loading them again.

### Pull to refresh

- Pulling down on a page that is scrolled to its very top reloads it. A page that scrolls inside itself instead of as a whole counts as at its top, so for such sites the setting below turns it off.
- Settings → "Pull to refresh" turns it off, for sites with their own pull gesture.

### Clear browsing data

- Settings → "Clear browsing data" clears data of the Active Profile only. It names the Profile.
- It offers checkboxes for: history, cookies and site data, cached files, site permissions and Desktop site choices, recently closed Tabs, and site icons. History and cached files are checked at first.
- It clears everything stored, from all time. The engine can't clear a Profile's cookies for a time range, so the dialog offers no range.
- With "cookies and site data" checked, the dialog warns that every Account signed in within the Profile will be signed out. It clears everything sites stored in the Profile's store: cookies, site storage, service workers, Cache Storage and its cached files, and the access to their cookies that embedded sign-ins were given.
- "Cached files" clears the engine's cache of pages and images. Each Profile's cache is kept apart, so no Profile can read another's, but the engine can only clear it for every Profile at once.
- Site icons of the Profile's bookmarks and open Tabs are kept, as when history is cleared.
- Bookmarks, downloaded files and Site Rules are never cleared here.

### Add to Home screen

- "Add to Home screen" asks the launcher to pin a shortcut to the page, with the page's title as its name and the site's icon (or its letter), marked at the lower left with the Profile's color and its emoji or initial. The launcher's own badge for the app takes the lower right. The launcher asks before it adds the shortcut.
- The shortcut opens the page in a new Tab in the Profile it was made in, which becomes the Active Profile. Site Rules don't apply to it.
- The shortcut carries only an id the app made. The address and the Profile stay in the app, so another app can't use a shortcut's form to open a page in a chosen Profile.
- Deleting the Profile disables its shortcuts. Tapping one then says "This Profile was deleted", and so does a shortcut the app no longer knows.
- The action is hidden in Private Tabs.

## Browsing features

- History, bookmarks and downloads, each per Profile.
  - History keeps web pages the person goes to, not reloads, Back, Forward, or pages restored with a Tab. A redirect's stops on the way are left out. Each Profile keeps its latest 5,000 visits.
  - History and bookmarks open in the page's place: beside the Sidebar, or full screen above the Bottom Bar layout.
  - Deleting a Profile removes its history, its bookmarks, its Folders and its list of downloads at once. Downloaded files stay on the device.
  - A page can only offer a file: it downloads once the person agrees. The engine asks for it as the page would, with the Profile's cookies, and Okapi starts reading the file only once the person agrees; declining drops it. Okapi saves it to the device's Downloads folder and lists it at once, with its progress. The download belongs to the app: if Android ends the app while it runs, the file is left unfinished and listed as failed. A file that can't be saved at all says so in a bar. A Private Tab's download isn't listed, so a bar says once it is saved, or that it couldn't be.
  - A file the page makes itself (a `blob:` or `data:` address) downloads the same way, with the name the page's link gives it (`download`).
  - "Save image" fetches the image from the page, as the page would, once the person agrees. A bar says once it is saved.
- A PDF a site shows as a page opens in the engine's own PDF viewer, in the Tab. A PDF the site sends as a file to save is offered as a download, like any file.
- Protected video (DRM), as on streaming sites, plays without asking, through Android's own media DRM.
- Search: Google by default, and the engine can be changed in settings (Google, Bing, DuckDuckGo, Brave Search). Address-bar suggestions are on by default and can be turned off.
  - While the person types, the address field offers matching pages from the Active Profile's bookmarks and history, then searches the search engine suggests.
  - Typed text goes to the search engine only when it would be searched for anyway and typing on can't turn it into credentials. An address, text shaped like credentials, or text with a colon before its first `/`, `?` or `#` (`admin:hunter2`, where an `@` may come next) is never sent. So a search with an early colon, such as `python: list comprehension`, gets only bookmark and history suggestions while it is typed, and Go still searches for it. The request carries no cookies. A Private Tab offers no suggestions at all.
- Safe Browsing: on by default and can be turned off. The change also reaches pages that are already open.
- Theme: the device's, light or dark. It sets the browser's own bars and lists; pages keep following the device's setting, unless "Darken pages without a dark theme" is on (see "Dark pages").
- Settings open in the page's place, like history and bookmarks.
- Password managers work through the Android Autofill framework. Okapi does not store passwords itself.
- Passkeys are not supported until the app is approved as a privileged browser ([ADR 0006](adr/0006-geckoview-as-engine.md)).

## Data protection

- `android:allowBackup="false"`, and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer. Session cookies must never leave the device.
- The app has no telemetry and no crash-reporting SDK.
- The engine sends no telemetry and no crash reports: Okapi sets up neither in GeckoView.
- Logs never contain addresses, page titles or cookies in release builds. GeckoView's console output and debug logging are off, and release builds strip debug and verbose log calls, GeckoView's included.
- Cookies follow Total Cookie Protection: a site embedded in another gets its own cookie jar there, so it can't follow the person across sites. Sign-in pop-ups and embedded sign-ins that ask for their cookies still get them.
- The app asks Android for the camera, microphone and location permissions only to pass them on to a site the person allowed (see "Site permissions and Site settings"). It never uses them itself.

## Out of scope for v1

Sync, extensions, our own password manager, content blocking, per-Profile biometric lock, Custom Tabs, and passkeys.
