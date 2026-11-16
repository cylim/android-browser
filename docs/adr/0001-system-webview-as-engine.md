---
status: accepted
---

# Use Android System WebView as the rendering engine

JRNY browser renders pages with Android System WebView and keeps each Profile separate through androidx.webkit's `ProfileStore`. The minimum Android version is 15, so every supported device has a recent, Play-updated WebView with multi-profile support. We chose WebView because Google ships its security fixes, it gives Chrome-level site compatibility, and the app stays small. A solo, public project cannot reliably ship a browser engine update every month.

## Considered Options

- **GeckoView.** It is a fuller browser engine with extensions, and Google sign-in works because it looks like Firefox. We rejected it because the app would have to ship an engine security update every month, it adds 60–100 MB to the APK, and its API often changes in breaking ways. It is the fallback if milestone 0 fails.
- **Chromium fork.** We rejected it because Chromium has no supported way to be embedded (WebLayer was deprecated in M108), builds take hours, and the fork would need a rebase on every release.

## Consequences

- **Sign-in is the main risk.** Google blocks sign-in from embedded WebViews (`disallowed_useragent`), and other providers could do the same. Milestone 0 showed that Google sign-in works with WebView's own user agent, so the app sends WebView's default user agent and Client Hints. If a provider starts blocking it, the fallback is a Chrome-like user agent and Client Hints without the "Android WebView" brand, which the spike's modes 2 to 4 implement. That is unsupported, and Google can break it at any time. If sign-in can't be made reliable, we revisit this decision.
- **Deleting a Profile cannot finish while the app is running.** `ProfileStore.deleteProfile` refuses a profile that is already loaded. A deleted Profile disappears from the UI immediately, and its data is wiped at the next cold start. Private Tab storage is wiped the same way.
- **A WebView's profile is fixed before first use.** Moving a Tab always creates a new WebView in the target Profile (see ADR 0003).
- **Passkeys for any site** need `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`, and that needs approval as a privileged browser from Google Password Manager and from each other credential provider. Passkeys stay off until we have that approval. Password managers work through Android Autofill.
- **No browser extensions.** Content blocking, if we add it, will be our own request filtering.

## Milestone 0 result (2026-11-16)

**Decision kept: System WebView**, chosen by the maintainer on 2026-11-16 with the sign-in checks below still open. The sign-in spike (`spike/multi-profile-webview`) and the GeckoView spike (`spike/multi-profile-geckoview`) were compared on Profile isolation, sign-in and performance.

- Profiles are isolated, popups stay in their Profile, and sign-ins survive a restart. Two different Google Accounts were signed in at once, in two Profiles, with WebView's default user agent. No user-agent changes were needed.
- On the emulator, WebView painted the first page in about 1.7 s from a cold start, against 4.8 s for GeckoView. It used 185 MB against 562 MB with one page open, and its APK is 3.6 MB against 227 MB.
- WebView sends `X-Requested-With` with the app's package name on every request, and this can't be turned off. It is the most likely way for a site to block sign-in, so watch for it.
- **Open:** Microsoft (personal and work), GitHub and Apple sign-in with two Accounts each are untested, because they need real Accounts. They are tracked in the spike's RESULTS.md. If one of them blocks WebView, revisit this decision.
- WebView runs one renderer process for every Tab in every Profile. The app must handle `onRenderProcessGone`, or one renderer crash closes the app.
- Cookies reach disk only periodically, so the app calls `CookieManager.flush()` for each loaded Profile in `onStop`.
