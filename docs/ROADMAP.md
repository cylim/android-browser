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
- CI builds, lints and tests every pull request.
- One Profile and one Tab: load a page, go back and forward, reload.

## Milestone 2: Profiles

- Create, rename, recolor and delete Profiles, with data wiped at cold start.
- Each Profile keeps its own web data, which is verified by an instrumented test.
- Active Profile switching.

## Milestone 3: Tabs and layouts

- Several Tabs per Profile, with the 5-live-Tab limit and restore after the app is killed.
- Sidebar (≥ 840dp) and Bottom Bar (< 840dp), switching live when the window resizes.

## Milestone 4: Moving between Profiles and Incoming Links

- Move and Open in Profile.
- Register as a browser. Incoming Links open in the Active Profile, with the "Move to Profile…" action.

## Milestone 5: Browsing essentials

- History, bookmarks and downloads, each per Profile.
- Private Tabs.
- Search engine choice, suggestions and the Safe Browsing setting.

## Milestone 6: First public release

- Signed release APK built by GitHub Actions and published on GitHub Releases.
- Check that the build meets F-Droid's requirements.

## After v1

- Site Rules: send Incoming Links for matching addresses to a specific Profile.
- Passkeys, after approval as a privileged browser.
- Custom Tabs service.
- Biometric lock per Profile.
- Content blocking.
- Bookmark export and import.
- Google Play release.
