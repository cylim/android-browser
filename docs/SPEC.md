# JRNY browser v1 specification

This document describes what v1 does. Terms in **bold** are defined in [GLOSSARY.md](../GLOSSARY.md). For the reasons behind major decisions, see [docs/adr](adr/).

## Platform

- Android 15 (API 35) and later. `targetSdk` is the latest stable API level.
- Kotlin, Jetpack Compose, Material 3, Room. Gradle uses the Kotlin DSL and a version catalog.
- Pages render in Android System WebView, with one androidx.webkit profile for each **Profile** ([ADR 0001](adr/0001-system-webview-as-engine.md)).
- No Google Play Services, Firebase, analytics or API keys ([ADR 0002](adr/0002-no-proprietary-dependencies-or-telemetry.md)).
- Application ID: `app.jrny.browser`. Display name: JRNY browser.

## Profiles

- A **Profile** has a name and a look: a color, how strongly that color shows, and an icon or an emoji for its avatar. It owns its own web data (cookies, site storage, cache, site permissions, service workers), **Tabs**, history, bookmarks and downloads.
- App settings (search engine, suggestions, Safe Browsing, theme) are shared by all Profiles.
- On first launch the app creates one Profile, which becomes the **Active Profile**.
- There is always exactly one **Active Profile**. Switching Profiles changes it.
- Deleting a Profile:
  1. The person confirms. The dialog warns that every **Account** signed in within that Profile will be signed out.
  2. The Profile disappears from the UI and its Tabs close.
  3. If it was the Active Profile, another Profile becomes active. The last remaining Profile cannot be deleted.
  4. Its web data is wiped at the next cold start. Settings show "N profiles pending removal" until then.

## Tabs

- Every **Tab** belongs to exactly one Profile.
- **Move**: carries only the Tab's current address. The original Tab closes, and the page reloads in the target Profile ([ADR 0003](adr/0003-move-carries-only-the-address.md)). Before the Move, the UI warns that unsaved form input will be lost.
- **Open in Profile**: opens the current address as a new Tab in another Profile and keeps the original.
- At most 5 Tabs are live at once, across all Profiles: the visible Tab and the 4 most recently used. Other Tabs are unloaded and reload when opened.
- After Android kills the app, each normal Tab is restored with its address, title and scroll position. Back/forward history is not restored, and page content is never written to disk.

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
- Their storage is discarded once the last Private Tab of the Profile closes. Any leftover private storage is wiped at every cold start.
  - WebView can't delete a store while the app runs. So when the last Private Tab closes, its cookies and site storage are cleared, and the next Private Tab gets a new, empty store. The old store's folder goes at the next cold start.
- Private Tabs are kept in memory only: they survive the app's window being rebuilt, but not the app being closed or killed. Closing the app's window ends them even while Android keeps the app's process around.
- A Private Tab is listed after the Profile's other Tabs, marked with a lock. Its page is named in the list only while a Private Tab is the one showing, which is when the window can't be captured.
- A Private Tab can't be Moved or opened in another Profile. Its downloads are saved to the device but not put on the Profile's list of downloads.

## Incoming Links

- When JRNY browser is the default browser, an **Incoming Link** opens as a new normal Tab in the Active Profile, unless a Site Rule names another Profile for it. It never opens in a Private Tab, even if the person left the app from one.
- A newly opened Incoming Link shows a one-tap "Move to Profile…" action, until it is dismissed or another Tab is shown.
- Only `http` and `https` links are accepted. Anything else another app sends is ignored.
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
- The page menu holds Move, Open in Profile and, in a window narrower than 600dp, Forward.

## Browsing features

- History, bookmarks and downloads, each per Profile.
  - History keeps web pages the person goes to, not reloads, Back, Forward, or pages restored with a Tab. A redirect's stops on the way are left out. Each Profile keeps its latest 5,000 visits.
  - History and bookmarks open in the page's place: beside the Sidebar, or full screen above the Bottom Bar layout.
  - Deleting a Profile removes its history, its bookmarks, its Folders and its list of downloads at once. Downloaded files stay on the device.
  - A page can only offer a file: it downloads once the person agrees. Android's download service fetches it with the Profile's cookies and saves it to the device's Downloads folder. Files a page makes itself (`blob:` and `data:` addresses) can't be downloaded yet, and a site that only answers a real browser may refuse the download service.
- Search: Google by default, and the engine can be changed in settings (Google, Bing, DuckDuckGo, Brave Search). Address-bar suggestions are on by default and can be turned off.
  - While the person types, the address field offers matching pages from the Active Profile's bookmarks and history, then searches the search engine suggests.
  - Typed text goes to the search engine only when it would be searched for anyway. An address, or text shaped like credentials, is never sent. The request carries no cookies. A Private Tab offers no suggestions at all.
- Safe Browsing: on by default and can be turned off. The change also reaches pages that are already open.
- Theme: the device's, light or dark. It sets the browser's own bars and lists; pages keep following the device's setting.
- Settings open in the page's place, like history and bookmarks.
- Password managers work through the Android Autofill framework. JRNY browser does not store passwords itself.
- Passkeys are not supported until the app is approved as a privileged browser ([ADR 0001](adr/0001-system-webview-as-engine.md)).

## Data protection

- `android:allowBackup="false"`, and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer. Session cookies must never leave the device.
- The app has no telemetry and no crash-reporting SDK.
- Logs never contain addresses, page titles or cookies in release builds.

## Out of scope for v1

Sync, extensions, our own password manager, content blocking, per-Profile biometric lock, Custom Tabs, and passkeys.
