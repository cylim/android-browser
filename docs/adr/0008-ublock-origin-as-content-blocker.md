---
status: accepted
---

# uBlock Origin, built in and unchanged, blocks ads and trackers

Okapi blocks ads and trackers with uBlock Origin, which runs in GeckoView as a built-in extension. The build downloads uBlock Origin's signed Firefox release from its GitHub release page, checks it against a SHA-256 hash in `android/app/build.gradle.kts`, and puts it unpacked and unchanged into the APK's assets. Engine installs it at every start (`ensureBuiltIn`), so a new APK updates it. It blocks in every Profile and in Private Tabs. The maintainer chose uBlock Origin on 2026-11-26, before the first release.

People control it with uBlock Origin's own pages, not Okapi's. The page menu opens its popup for the shown Tab, which has the per-site switch, the element picker and the blocked count. Settings → **Ad and tracker blocking** opens its dashboard, which has its settings, filter lists, own filters and trusted sites.

## Considered options

- **GeckoView's Enhanced Tracking Protection.** It knows each Profile, so a per-site exception could be per Profile. We rejected it because it blocks trackers only, not ads, and its lists come from Mozilla's servers.
- **Okapi's own filtering.** We rejected it. Writing and maintaining a filter engine as good as uBlock Origin's is a project of its own, and the filter lists are written for uBlock Origin's syntax.
- **uBlock Origin with a small added script** that lets Okapi set trusted sites and show the switch in Site settings. We rejected it. Every update of uBlock Origin would need the script checked again. Its trusted sites would still apply in every Profile.
- **Installing uBlock Origin from addons.mozilla.org.** We rejected it. Okapi would contact Mozilla's add-on service and would need its own update checks. A built-in extension updates with the APK.

## Consequences

- uBlock Origin can't tell which Profile a request comes from. Its trusted sites and settings apply in every Profile, and its popup and dashboard say so under their title.
- uBlock Origin downloads list updates from the lists' own servers, not from a server of ours, in line with [ADR 0002](0002-no-proprietary-dependencies-or-telemetry.md). The privacy policy names those servers.
- uBlock Origin is GPL-3.0. It ships as a separate program inside the APK, with its own `LICENSE.txt`, and its source is its GitHub release. Okapi's own code stays MIT.
- The asset ignore pattern in `android/app/build.gradle.kts` keeps folders whose names start with `_`. The default pattern drops them, and uBlock Origin doesn't load without `_locales`.
- Each uBlock Origin release needs a new version and hash in `android/app/build.gradle.kts`. The GeckoView update routine in Milestone 6 covers both.
- A page that loads in the first few seconds after the app's first install can get through unblocked while uBlock Origin reads its lists. After any later start, uBlock Origin holds requests until it is ready. Its popup then says that the page from the start may not have been filtered.
- F-Droid builds from source. Whether it accepts a release file downloaded at build time is checked in Milestone 6.
- This supersedes the content blocking part of [ADR 0001](0001-system-webview-as-engine.md), which was already superseded by [ADR 0006](0006-geckoview-as-engine.md). Okapi still offers no way to install other extensions.
