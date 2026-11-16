---
status: accepted
---

# Use Android System WebView as the rendering engine

JRNY browser renders pages with Android System WebView and keeps each Profile separate through androidx.webkit's `ProfileStore`. The minimum Android version is 15, so every supported device has a recent, Play-updated WebView with multi-profile support. We chose WebView because Google ships its security fixes, it gives Chrome-level site compatibility, and the app stays small. A solo, public project cannot reliably ship a browser engine update every month.

## Considered Options

- **GeckoView.** It is a fuller browser engine with extensions, and Google sign-in works because it looks like Firefox. We rejected it because the app would have to ship an engine security update every month, it adds 60–100 MB to the APK, and its API often changes in breaking ways. It is the fallback if milestone 0 fails.
- **Chromium fork.** We rejected it because Chromium has no supported way to be embedded (WebLayer was deprecated in M108), builds take hours, and the fork would need a rebase on every release.

## Consequences

- **Google sign-in is the main risk.** Google blocks sign-in from embedded WebViews (`disallowed_useragent`). We present a Chrome-like user agent and User-Agent Client Hints. This is unsupported and Google can break it at any time. Milestone 0 is a throwaway check that signs in to Google, Microsoft, GitHub and Apple in two Profiles. If Google sign-in cannot be made reliable, we revisit this decision before building the app.
- **Deleting a Profile cannot finish while the app is running.** `ProfileStore.deleteProfile` refuses a profile that is already loaded. A deleted Profile disappears from the UI immediately, and its data is wiped at the next cold start. Private Tab storage is wiped the same way.
- **A WebView's profile is fixed before first use.** Moving a Tab always creates a new WebView in the target Profile (see ADR 0003).
- **Passkeys for any site** need `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`, and that needs approval as a privileged browser from Google Password Manager and from each other credential provider. Passkeys stay off until we have that approval. Password managers work through Android Autofill.
- **No browser extensions.** Content blocking, if we add it, will be our own request filtering.
