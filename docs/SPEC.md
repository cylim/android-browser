# JRNY browser v1 specification

This document describes what v1 does. Terms in **bold** are defined in [GLOSSARY.md](../GLOSSARY.md). For the reasons behind major decisions, see [docs/adr](adr/).

## Platform

- Android 15 (API 35) and later. `targetSdk` is the latest stable API level.
- Kotlin, Jetpack Compose, Material 3, Room. Gradle uses the Kotlin DSL and a version catalog.
- Pages render in Android System WebView, with one androidx.webkit profile for each **Profile** ([ADR 0001](adr/0001-system-webview-as-engine.md)).
- No Google Play Services, Firebase, analytics or API keys ([ADR 0002](adr/0002-no-proprietary-dependencies-or-telemetry.md)).
- Application ID: `app.jrny.browser`. Display name: JRNY browser.

## Profiles

- A **Profile** has a name, a color and an icon. It owns its own web data (cookies, site storage, cache, site permissions, service workers), **Tabs**, history, bookmarks and downloads.
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

### Private Tabs

- Each Profile can open **Private Tabs**. All of a Profile's Private Tabs share one temporary web data store, which is separate from the Profile's normal store.
- Private Tabs never write to history, are never restored after the app is killed, and block screenshots and recent-apps previews (`FLAG_SECURE`).
- Their storage is discarded once the last Private Tab of the Profile closes. Any leftover private storage is wiped at every cold start.

## Incoming Links

- When JRNY browser is the default browser, an **Incoming Link** opens as a new normal Tab in the Active Profile. It never opens in a Private Tab, even if the person left the app from one.
- A newly opened Incoming Link shows a one-tap "Move to Profile…" action, until it is dismissed or another Tab is shown.
- Only `http` and `https` links are accepted. Anything else another app sends is ignored.
- v1 does not provide a Custom Tabs service. Apps that ask for Custom Tabs fall back to opening a normal Tab.
- **Site Rules** come after v1 (see [ROADMAP.md](ROADMAP.md)).

## Layout

The layout depends on window width, not device type. It switches live when the window resizes (rotation, split screen, folding), and Tabs survive the switch.

### Sidebar (window width ≥ 840dp)

- An Arc-style vertical list that shows only the Active Profile's Tabs.
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
  - Deleting a Profile removes its history, its bookmarks and its list of downloads at once. Downloaded files stay on the device.
  - A page can only offer a file: it downloads once the person agrees. Android's download service fetches it with the Profile's cookies and saves it to the device's Downloads folder. Files a page makes itself (`blob:` and `data:` addresses) can't be downloaded yet, and a site that only answers a real browser may refuse the download service.
- Search: Google by default, and the engine can be changed in settings. Address-bar suggestions are on by default and can be turned off.
- Safe Browsing: on by default and can be turned off.
- Password managers work through the Android Autofill framework. JRNY browser does not store passwords itself.
- Passkeys are not supported until the app is approved as a privileged browser ([ADR 0001](adr/0001-system-webview-as-engine.md)).

## Data protection

- `android:allowBackup="false"`, and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer. Session cookies must never leave the device.
- The app has no telemetry and no crash-reporting SDK.
- Logs never contain addresses, page titles or cookies in release builds.

## Out of scope for v1

Sync, extensions, our own password manager, content blocking, per-Profile biometric lock, Site Rules, Custom Tabs, and passkeys.
