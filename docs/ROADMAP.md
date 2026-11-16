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
- Passkeys, after approval as a privileged browser. Until then the app tells sites that passkeys are unsupported, so they fall back to passwords. Once the app is live:
  1. Submit Google's [privileged-browser request form](https://docs.google.com/forms/d/e/1FAIpQLScRuk9tTg0QPfSWl9SbpbIorX8xx2FCXlmoUYftCX2MxG4qyg/viewform), linked from [Make Credential Manager calls on behalf of other parties](https://developer.android.com/identity/sign-in/privileged-apps). Approval adds the app to [Google Password Manager's allowlist](https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json).
  2. List the package name with the SHA-256 fingerprint of **every** release signing certificate: our GitHub Releases key, and F-Droid's key if F-Droid signs its build.
  3. Ask other credential providers to add the app to their own allowlists, for both passkeys and autofill. 1Password only fills in [browsers it recognizes](https://support.1password.com/android-autofill/). Bitwarden keeps [`fido2_privileged_community.json`](https://github.com/bitwarden/android/blob/main/app/src/main/assets/fido2_privileged_community.json).
  4. Remove the "passkeys unsupported" behavior, then test passkey sign-in in two Profiles.
- Custom Tabs service.
- Biometric lock per Profile.
- Content blocking.
- Bookmark export and import.
- Google Play release.
