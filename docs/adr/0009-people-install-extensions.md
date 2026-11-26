---
status: accepted
---

# People install extensions from addons.mozilla.org, for every Profile at once

People can add any extension that Mozilla has signed and that works in Firefox for Android: from addons.mozilla.org in a Tab, or from a `.xpi` file. They can turn each one off, let it run in Private Tabs or not, open its settings and remove it, in Settings → Extensions. The page menu opens each extension's popup. GeckoView runs them: one set of extensions per engine, so every extension works in every Profile. The maintainer decided this on 2026-11-26, before the first release.

## How it works

- addons.mozilla.org's "Add to Firefox" button links to the extension's file. A Tab that gets a response of type `application/x-xpinstall` hands its address to the engine's installer instead of offering a download. The engine downloads the file again and checks Mozilla's signature. Pages get no install API: `extensionsWebAPIEnabled` stays off.
- Before anything is installed, Okapi asks. Its dialog lists what the extension can do, in the person's words, says that it works in every Profile and can see the pages open in all of them, and offers Run in Private Tabs, off by default. An update that asks for more permissions, and an extension that asks for an optional one, also ask first.
- An extension's own pages, its popup and its settings, open in Okapi's sheet and panel, with Okapi's page dialogs and Android's file picker. A web page an extension opens goes to a new Tab of the Active Profile.
- Okapi asks addons.mozilla.org for updates once a day, at start, and when the person taps "Check for updates". GeckoView sends the extension's id and version to Mozilla's update service.

## Considered options

- **A catalog inside Okapi**, as Firefox for Android has. We rejected it. It would ask Mozilla's API for lists Okapi doesn't need, and addons.mozilla.org's own site already works in a Tab.
- **Extensions per Profile.** GeckoView can't do it: extensions belong to the runtime, and Okapi has one runtime for every Profile ([ADR 0006](0006-geckoview-as-engine.md)). Okapi says so wherever an extension is added or shown.
- **Unsigned extensions.** We rejected them. GeckoView's release builds refuse them, and a signature is what lets Mozilla block a harmful extension later.
- **No automatic updates.** We rejected it. An extension with every site's data needs its security fixes as soon as they are out.

## Consequences

- An extension with the `tabs` permission or access to every site can read what every Profile has open. That weakens the separation between Profiles, so the add dialog and the Extensions panel say it plainly.
- Okapi contacts addons.mozilla.org: the daily update check, and installs the person starts. Both are Mozilla services, not ours, in line with [ADR 0002](0002-no-proprietary-dependencies-or-telemetry.md). The privacy policy says what they receive.
- Each extension's own network traffic is its developer's. The add dialog shows what the extension says it sends its developer, from Firefox's data collection permissions.
- Okapi's backup holds no extensions or their data. People add them again after an import.
- uBlock Origin is installed by default, and nothing else is ([ADR 0008](0008-ublock-origin-as-content-blocker.md)).
