# Roadmap

Each milestone ends with something that can be demonstrated. The scope of v1 is defined in [SPEC.md](SPEC.md).

## Milestone 0: Sign-in check (go/no-go)

A throwaway app, kept on a separate branch and never merged, that answers one question: can a WebView-based browser reliably sign in to accounts on Android 15+?

- Two Profiles, each bound to its own androidx.webkit profile.
- In each Profile, sign in to Google, Microsoft, GitHub and Apple, each with a different Account.
- Confirm that the two Profiles cannot see each other's cookies.
- Record which user agent and Client Hints changes Google needs, and how stable they look.

**Exit:** a written result appended to [ADR 0001](adr/0001-system-webview-as-engine.md). If Google sign-in is not reliable, switch the engine decision to GeckoView before Milestone 1.

## Milestone 1: Skeleton

- Gradle/Compose project with application ID `app.jrny.browser`, minSdk 35.
- CI builds, lints and tests every pull request that changes the app.
- One Profile and one Tab: load a page, go back and forward, reload.
- Sidebar (≥ 840dp) and Bottom Bar (< 840dp) layouts for that Tab, switching live when the window resizes. The Sidebar can collapse to a rail.

## Milestone 2: Profiles

- Create, rename, recolor and delete Profiles, with data wiped at cold start.
- Each Profile keeps its own web data, which is verified by an instrumented test.
- Active Profile switching.

## Milestone 3: Tabs and layouts

- Several Tabs per Profile, with the 5-live-Tab limit and restore after the app is killed.
- The Tab list in the Sidebar and the Tab Overview in the Bottom Bar.

## Milestone 4: Moving between Profiles and Incoming Links

- Move and Open in Profile.
- Register as a browser. Incoming Links open in the Active Profile, with the "Move to Profile…" action.

## Milestone 5: Browsing essentials

- History, bookmarks and downloads, each per Profile.
- Private Tabs.
- Search engine choice, suggestions and the Safe Browsing setting.

## Before the first release

Added to v1 after Milestone 5:

- Site Rules: send Incoming Links for matching addresses to a specific Profile.
- Pinned Tabs and folders in the Sidebar, shared with each Profile's bookmarks.
- Styling: compact rows for the Tab list, a color and color strength per Profile, and emoji for Profiles.
- Everyday browsing, in four parts:
  1. Web capabilities: file upload, camera and microphone, location, fullscreen video, HTTP sign-in, and Site settings.
  2. Links and files: links to other apps, `blob:` and `data:` downloads, the link and image menu, Share, Copy link, and Print.
  3. Page tools: Find in page, Desktop site, text size, dark pages, and pull to refresh.
  4. Tabs and data: Reopen closed Tab, Clear browsing data, and Add to Home screen.
- Backup: export Okapi's own data to a file, encrypted by default, and import it by merging or replacing.
- Extensions, moved up from after 0.2: install them from addons.mozilla.org or a file, turn them on or off, allow them in Private Tabs, open their popups and settings, and keep them updated. They work in every Profile, and the UI says so ([ADR 0009](adr/0009-people-install-extensions.md)).
- Content blocking, moved up from 0.2: uBlock Origin installed by default on the first start, removable like any extension. It keeps its lists up to date from the lists' own servers, not from a service of ours ([ADR 0008](adr/0008-ublock-origin-as-content-blocker.md)).

## Before the first release: GeckoView

- Pages render in GeckoView instead of Android System WebView ([ADR 0006](adr/0006-geckoview-as-engine.md)), with page dialogs, error pages and engine-built downloads.
- Sign in with two Accounts each to Google, Microsoft (personal and work), GitHub and Apple, in two Profiles ([#33](https://github.com/cylim/android-browser/issues/33)).
- Capture and document what the engine itself sends over the network, for the privacy policy ([#34](https://github.com/cylim/android-browser/issues/34)).

## Milestone 6: First public release

- Signed release APK built by GitHub Actions and published on GitHub Releases, for `arm64-v8a` (`-PgeckoAbi=arm64-v8a`), as `okapi-arm64-v8a.apk`. The website's download button links to that name in the latest release ([#31](https://github.com/cylim/android-browser/issues/31)).
- Check that the build meets F-Droid's requirements. F-Droid usually builds GeckoView from source, and the build downloads uBlock Origin's release file.
- A routine for taking each GeckoView security release, about every 4 weeks, and each uBlock Origin release ([#32](https://github.com/cylim/android-browser/issues/32)).

## After the first release

Each item closes a gap between Okapi and Chrome or Firefox for Android. Items that add a network service need an ADR first ([ADR 0002](adr/0002-no-proprietary-dependencies-or-telemetry.md)).

- Web notifications, through GeckoView, without a Google push service ([#35](https://github.com/cylim/android-browser/issues/35)).
- Passkeys, after approval as a privileged browser ([#36](https://github.com/cylim/android-browser/issues/36)).
- Custom Tabs service ([#37](https://github.com/cylim/android-browser/issues/37)).
- Memory use and download size measured on real phones, with the live-Tab limit set from the device's memory ([#45](https://github.com/cylim/android-browser/issues/45)).
- Android versions older than 15. GeckoView supports Android 8 and later ([#44](https://github.com/cylim/android-browser/issues/44)).
- Extensions per Profile, if GeckoView allows it ([#43](https://github.com/cylim/android-browser/issues/43)).
- Sync between devices ([#38](https://github.com/cylim/android-browser/issues/38)).
- Page translation on the device ([#39](https://github.com/cylim/android-browser/issues/39)).
- Reader view ([#40](https://github.com/cylim/android-browser/issues/40)).
- QR code scanning ([#41](https://github.com/cylim/android-browser/issues/41)).
- Voice search ([#42](https://github.com/cylim/android-browser/issues/42)).
- Biometric lock per Profile.
- Bookmark export and import.
- Google Play release.
- A decision on a news or content feed for the Start Page ([#46](https://github.com/cylim/android-browser/issues/46)).
