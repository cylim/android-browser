---
status: accepted
---

# uBlock Origin is the one extension Okapi installs by default

Okapi blocks ads and trackers with uBlock Origin, an extension that Okapi installs on the first start. The build downloads uBlock Origin's signed Firefox release file from its GitHub release page, checks it against a SHA-256 hash in `android/app/build.gradle.kts`, and puts the file, unchanged, into the APK's assets. On the first start, `Extensions` installs it from there as an ordinary extension, with Mozilla's signature checked, and lets it run in Private Tabs. After that it is an extension like any other ([ADR 0009](0009-people-install-extensions.md)). The person can turn it off or remove it, and a removed one isn't added again. It updates from addons.mozilla.org. The maintainer chose uBlock Origin on 2026-11-26, before the first release.

People control it with uBlock Origin's own pages, as in Firefox for Android. The page menu opens its popup for the shown Tab, which has the per-site switch, the element picker and the blocked count. Its settings page holds its settings, filter lists, own filters and trusted sites.

## Considered options

- **GeckoView's Enhanced Tracking Protection.** It knows each Profile, so a per-site exception could be per Profile. We rejected it because it blocks trackers only, not ads, and its lists come from Mozilla's servers.
- **Okapi's own filtering.** We rejected it. Writing and maintaining a filter engine as good as uBlock Origin's is a project of its own, and the filter lists are written for uBlock Origin's syntax.
- **uBlock Origin built in (`ensureBuiltIn`), unchanged and fixed.** We tried it first. We rejected it once people could install extensions: a built-in extension can't be removed, and it updates only with the APK.
- **uBlock Origin with a small added script** that lets Okapi set trusted sites and show the switch in Site settings. We rejected it. Every update of uBlock Origin would need the script checked again, and Mozilla's signature wouldn't cover the changed file.

## Which other extensions to install by default

We looked at the extensions people most often add for privacy and comfort. None is installed by default, because each would see every Profile's Tabs, add its own network contacts and updates, and use memory. Each also repeats something Okapi or uBlock Origin already does:

- **ClearURLs**, which strips tracking parameters from addresses. uBlock Origin's default privacy list already strips them: 223 rules in 1.77.2, among them `gclid` and `fbclid`. Its optional "AdGuard/uBO – URL Tracking Protection" list adds more.
- **Privacy Badger.** uBlock Origin's EasyPrivacy and privacy lists block the same trackers by name.
- **Cookie notice blockers**, such as Consent-O-Matic. uBlock Origin's dashboard offers four cookie notice lists, off by default. GeckoView also has its own cookie banner handling, which Okapi could offer as a setting later.
- **Dark Reader.** Okapi's "Darken pages without a dark theme" does this ([SPEC](../SPEC.md), "Dark pages").
- **Password managers**, such as Bitwarden. Android Autofill fills passwords from any password manager app.

People who want any of them can add them from addons.mozilla.org.

## Consequences

- uBlock Origin can't tell which Profile a request comes from. Its trusted sites and settings apply in every Profile, and its popup and settings say so under their title.
- uBlock Origin downloads list updates from the lists' own servers, not from a server of ours, in line with [ADR 0002](0002-no-proprietary-dependencies-or-telemetry.md). The privacy policy names those servers.
- uBlock Origin is GPL-3.0. Its release file ships unchanged inside the APK as a separate program, with its own `LICENSE.txt`, and its source is its GitHub release. Okapi's own code stays MIT.
- The copy in the APK only serves the first start. A new APK with a newer copy doesn't update an installed uBlock Origin. addons.mozilla.org does. Each uBlock Origin release still needs a new version and hash in `android/app/build.gradle.kts`, so a first start gets a recent one.
- A page that loads in the first seconds after the app's first install can get through unblocked while uBlock Origin installs and reads its lists. After any later start, uBlock Origin holds requests until it is ready. If the first install fails, Okapi tries again at each start, and pages load unblocked until one succeeds.
- F-Droid builds from source. Whether it accepts a release file downloaded at build time is checked in Milestone 6.
