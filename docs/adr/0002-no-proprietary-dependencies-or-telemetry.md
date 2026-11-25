---
status: accepted
---

# No proprietary dependencies, telemetry or API keys

Okapi, a browser by JRNY, ships no analytics, crash-reporting SDKs, Google Play Services, Firebase, or any service that needs an API key. A browser sees everything a person does online. In a public repository, any key or secret the app needs is a key we might leak. With no secret, there is nothing to leak. This also keeps the app eligible for F-Droid. Crash reports come from people who open a GitHub issue and paste a log they have reviewed.

## Consequences

- Release signing keys exist only in encrypted GitHub Actions secrets and on the maintainer's machine. They are never in the repository.
- Any future feature that needs a hosted service (for example, sync) needs a new ADR.
- Safe Browsing is not affected. It is built into GeckoView, with Mozilla's own key for Google's lists, so the app needs no key of its own ([ADR 0006](0006-geckoview-as-engine.md)).
- GeckoView declares Google's proprietary `play-services-fido` library for passkeys. The build leaves it out.
