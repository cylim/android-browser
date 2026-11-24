---
status: accepted
supersedes: 0001
---

# Use GeckoView as the rendering engine

Okapi, a browser by JRNY, renders pages with GeckoView, Mozilla's embeddable Gecko engine, shipped inside the APK. Each Profile is a GeckoView session context, named as in ADR 0004, and a Private Tab adds GeckoView's private mode to its session's own context. This replaces Android System WebView ([ADR 0001](0001-system-webview-as-engine.md)), decided by the maintainer on 2026-11-19, before the first release, so nobody's sign-ins are lost by the switch.

We chose GeckoView because Android System WebView can't be made into a full browser by an app that embeds it:

- WebView sends the app's package name with every request (`X-Requested-With`), and no API removes it. It is the most likely way for a sign-in provider to block an embedded browser. GeckoView sends nothing of the kind, and its user agent is Firefox for Android's.
- WebView leaves out what a browser owns: web notifications and push, extensions, and pages' own dialogs for some fields. GeckoView exposes them through its API, for the app to build on.
- A Profile in use can't be deleted in WebView. GeckoView deletes a session context while the app runs.
- WebView runs one renderer process for every Tab in every Profile. GeckoView runs several content processes.

## Considered Options

- **Keep Android System WebView** (ADR 0001). It is about 3.6 MB, Google ships its security fixes, and it uses about a third of GeckoView's memory. We rejected it for the limits above, which no app code can lift.
- **A Chromium fork.** Rejected for the same reasons as in ADR 0001: Chromium has no supported way to be embedded, and the fork would need a rebase on every release.

## Consequences

- **Okapi ships the engine's security fixes.** GeckoView has a release about every 4 weeks, with security fixes between them. Each one must be taken and released promptly, or people browse with publicly known holes. The version is in `android/gradle/libs.versions.toml`.
- **The APK is large.** About 95 MB to download and about 250 MB installed, for one ABI. The build picks the ABI with `-PgeckoAbi` (x86_64 by default, for the emulators); release builds use `arm64-v8a`.
- **Memory.** The spike measured about 3× WebView's memory for one page. The limit of 5 live Tabs stays, and is the first thing to lower if devices struggle.
- **Every GeckoView upgrade can force others.** GeckoView 160 needs `compileSdk` 37.2 and Kotlin 2.4.
- **Okapi builds what WebView used to show by itself:** page dialogs (`alert`, `confirm`, `prompt`, `<select>`, date, time and color fields), error pages, and print through a PDF.
- **Okapi talks to pages through a built-in extension** (`android/app/src/main/assets/okapi`). GeckoView has no way to run a script in a page. The extension's content script reports a page's site icon, darkens pages without a dark theme, and fetches an image the person saves or shares, with the page's own cookies. It runs in web pages only, and pages can't reach it.
- **Downloads come from GeckoView.** It fetches a file with the page's Profile, and Okapi writes the response into the Downloads folder once the person agrees. Android's download service is no longer used, so a download stops if Android ends the app's process.
- **Deleting a Profile wipes its data at once.** The cold-start wipe stays as a second pass, for data a page that was still on screen wrote meanwhile. GeckoView gives no word when a wipe is done, so the Profile is forgotten only once the second wipe has had time to finish; if the app ends first, the next start wipes again. A wipe waits only for Gecko to start, not for the built-in extension.
- **Logs.** GeckoView logs page addresses at debug level, and no setting stops it. Release builds strip debug and verbose log calls with R8 (`android/app/proguard-rules.pro`).
- **No `play-services-fido`.** GeckoView declares Google's FIDO library for passkeys. The build leaves it out (ADR 0002), so passkeys go through Android's Credential Manager only, once a provider approves Okapi as a privileged browser.
- **Sign-in is still to be checked** with real Accounts: Google, Microsoft (personal and work), GitHub and Apple, two Accounts each, as for ADR 0001.
- **Network traffic of the engine itself** (Mozilla's services for security lists, Safe Browsing updates) is not yet captured and documented. The privacy policy must say what it is before the first release.
