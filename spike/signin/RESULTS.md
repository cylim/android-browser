# Milestone 0 results

> Never record email addresses, account names or cookie values here. Use "account 1" and "account 2".

## Environment

| | |
|---|---|
| Device / emulator | Emulator, Pixel 7 profile, `google_apis_playstore` x86_64 image |
| Android version | 17 (API 37) |
| WebView version (from Diagnostics) | com.google.android.webview 145.0.7632.218 |
| Features | MULTI_PROFILE, USER_AGENT_METADATA, WEB_AUTHENTICATION all supported |
| Date tested | 2026-11-16 (account-free checks) |
| Real-account device | HONOR YLE-W09 tablet, Android 17 (API 37), WebView 157.0.8103.38 |

## Isolation

| Check | Result |
|---|---|
| API isolation test (A/B/default) | ✅ PASS. No profile sees another profile's cookie, and the default profile sees neither. |
| httpbin probe cookie: A's cookie invisible in B | ✅ Each profile sees only its own `jrny_probe` |
| Cookies survive app restart | ⚠️ **Only after WebView's periodic flush.** A cookie set seconds before a force-stop was lost, and the same cookie survived after 40 s. The real app must call `CookieManager.flush()` on every loaded profile in `onStop`. The spike now does this. |
| Loaded profile can't be deleted while running | ✅ Confirmed: `IllegalStateException: Cannot delete in-use profile jrny-spike-a` |
| Profile never loaded in this process | ✅ `deleteProfile` succeeds immediately, and the recreated profile has no cookies |
| Pending profile deleted at cold start | ✅ After relaunch, profiles on disk are `[Default, jrny-spike-b]` |
| Signed-in state survives app restart | ✅ Tablet, profile B: still signed in to Google and Stack Overflow after an app reinstall and restart |

## Sign-in matrix

Result values: ✅ signed in · ⛔ blocked (quote the message) · ⚠️ works with caveats · — not tried

Account-free probe: entering an address with no Google account behind it. Every mode got as far as "Couldn't find this account", so Google does not block on the user agent **before** account lookup. The embedded-browser block is reported to appear after a real account is entered, so the account rows below are still open.

| Provider | Mode 1 default | Mode 2 UA | Mode 3 UA + hints | Mode 4 + Chrome brand | Two accounts at once (A/B) |
|---|---|---|---|---|---|
| Google (accounts.google.com), no-account probe | reaches lookup | reaches lookup | reaches lookup | reaches lookup | |
| Google (accounts.google.com), real account | ✅ (tablet) | not needed | not needed | not needed | ✅ different account in A and B (tablet, mode 1) |
| Google via third party (Stack Overflow), no-account probe | reaches "Sign in with Google · to continue to" with an email field. No `disallowed_useragent`. | | | | |
| Google via third party (Stack Overflow), real account | ✅ (tablet) | not needed | not needed | not needed | ✅ different Stack Overflow account in A and B (tablet, mode 1) |
| Microsoft personal | | | | | |
| Microsoft work | | | | | |
| GitHub | | | | | |
| Apple | | | | | |
| Passkey sign-in (GitHub "Sign in with a passkey") | ⛔ `NotAllowedError`. Google Play Services: "Origin is not being returned as the calling app did not match the privileged allowlist" | — | — | — | — |

Stack Overflow showed a Cloudflare "Verifying…" interstitial in mode 1 that passed by itself after about 20 s. On the tablet, Cloudflare Turnstile appeared on **every redirect** during sign-in. Investigation so far:

- Plain page loads on the tablet (mode 1, signed in): 4 of 4 loaded in about 2 s with no challenge. The challenges only happen during the sign-in redirect chain (Stack Overflow → Google → stackauth.com → Stack Overflow).
- After sign-in, the tablet's Stack Overflow cookies have `__cf_bm` and `__cflb` but no `cf_clearance`. On the emulator, a passed interstitial left a `cf_clearance` cookie and later loads were not challenged.
- Cloudflare's trace page shows the same residential IPv4 on every load from the tablet, with warp=off, so the clearance is not being lost to a changing IP.
- Still open: whether Chrome on the same tablet is also challenged on every sign-in redirect. If it is, this is Stack Overflow's policy on its login endpoints and nothing we can fix. If only WebView is challenged, compare with mode 3.

## Headers seen by httpbin.org/headers

On the tablet with WebView 157 in mode 1, the `User-Agent` has no `Mobile` token, `Sec-CH-UA-Mobile` is `?0`, and the brands are still Not.A/Brand, Android WebView and Chromium. The table below is from the emulator.

| Mode | `User-Agent` | `Sec-CH-UA` brands | `X-Requested-With` |
|---|---|---|---|
| 1 | `Linux; Android 10; K; wv … Version/4.0 Chrome/145.0.0.0 Mobile` | Not:A-Brand, **Android WebView**, Chromium | `app.jrny.spike.signin` |
| 2 | `Linux; Android 10; K … Chrome/145.0.0.0 Mobile` (clean) | Not:A-Brand, **Android WebView**, Chromium (inconsistent with the UA) | `app.jrny.spike.signin` |
| 3 | clean | Not:A-Brand, Chromium | `app.jrny.spike.signin` |
| 4 | clean | Not:A-Brand, Chromium, Google Chrome | `app.jrny.spike.signin` |

**`X-Requested-With` is sent in every mode**, on both WebView 145 (emulator) and WebView 157 (tablet). It reveals an embedded WebView and the app's package name, and the public API for suppressing it (`setRequestedWithHeaderOriginAllowList`) is now a no-op. If Google blocks sign-in in modes 3 and 4, this header is the likely reason, and nothing in the user agent can fix it.

## Verdict

- [ ] **Go:** WebView works. Record the minimum UA mode in ADR 0001.
- [ ] **No-go:** switch ADR 0001 to GeckoView.

Pending real-account tests (see the README test protocol).

Notes:
- Sign-in popups: ✅ A `window.open` popup gets a WebView bound to the opener's profile. From A it sees A's cookie, and from B it sees B's but not A's (emulator, local test page at `https://jrny.test/popup`). Real sites tested (Stack Overflow, Vercel, Figma, on both phone and tablet) used full-page redirects, not popups.
- Profile A after restart: ✅ still signed in to Google and Stack Overflow (tablet).
- Bug in the spike, now fixed: switching profiles reloaded the address without its query string, and Google returned 400 on its sign-in page. A Move in the real app must keep the full address.

## Passkeys

| Step | Result |
|---|---|
| WebAuthn off (default) | GitHub hides "Sign in with a passkey" |
| `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER` | GitHub shows the button. Sites see `PublicKeyCredential` and `isUserVerifyingPlatformAuthenticatorAvailable() = true`, and `isConditionalMediationAvailable() = false` (no passkey autofill in the username field) |
| Tapping it without `CREDENTIAL_MANAGER_SET_ORIGIN` | 💥 **App crash.** WebView calls Credential Manager with `setOrigin`, Android throws `SecurityException: … does not have android.permission.CREDENTIAL_MANAGER_SET_ORIGIN`, and WebView doesn't catch it. The permission is `prot=normal`, so declaring it in the manifest fixes the crash. |
| With the permission | No crash. Google Play Services refuses: "calling app did not match the privileged allowlist". GitHub shows "Authentication failed." |

**To make passkeys work:** each credential provider has to allow the app as a privileged browser, keyed on **package name and signing-certificate SHA-256**:
- Google Password Manager: Google's request form (see ADR 0001).
- Bitwarden: checks Google's list, then its own community list (`fido2_privileged_community.json`, added through a PR to bitwarden/android).
- 1Password, Dashlane, Samsung Pass and others each have their own process.

Because the fingerprint of the release signing key is what gets allowlisted, the final release key must exist before we apply. An F-Droid build signed with F-Droid's key would not match, unless F-Droid publishes our signed build reproducibly.
