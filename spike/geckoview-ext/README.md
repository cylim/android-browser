# GeckoView extensions and Profiles spike (throwaway)

This app answers two questions from [the GeckoView extensions research](../../docs/research/geckoview-firefox-extensions.md) for a possible change to [ADR 0001](../../docs/adr/0001-system-webview-as-engine.md):

1. Can a GeckoView-based browser install and run Firefox extensions?
2. Can GeckoView keep JRNY **Profiles** isolated the way the WebView spike does, and how do extensions behave across Profiles?

It is a throwaway spike, like the WebView spike on the `spike/multi-profile-webview` branch, and is **never merged**. Only the written result goes to `main`.

The extension wiring follows Fenix and Android Components at `mozilla-firefox/firefox` `main` (2026-11-16): `GeckoProvider.kt` for the runtime settings, and `GeckoEngine.kt` and `GeckoWebExtension.kt` for the delegates. Everything is cut down to raw GeckoView calls in one activity. The Profile controls and tests mirror the WebView spike's.

The application ID is `app.jrny.spike.gvext`, so it never collides with the real app. The launcher shows it as "JRNY Gecko spike" with a gecko icon.

## How Profiles work here

Each Profile is a GeckoView **session context**: every `GeckoSession` built with `GeckoSessionSettings.Builder().contextId("jrny-a")` shares cookies and storage with other sessions that use the same ID, and is isolated from sessions with any other ID. A Private Tab adds `usePrivateMode(true)` on top of its Profile's context ID. All Profiles share one `GeckoRuntime`, because Android allows only one per process. Deleting a Profile calls `StorageController.clearDataForSessionContext`.

## What it uses

- GeckoView **160 release** (`160.0.20261104181552`). The release channel is deliberate: it enforces extension signing at build time, and the spike checks that.
- `compileSdk` 37.2, because GeckoView 160 requires it.
- Kotlin Gradle plugin 2.4.0, because GeckoView 160 is built with Kotlin 2.4 and AGP 9.5.1's bundled Kotlin (2.2) can't read it.
- `play-services-fido` is **excluded** from GeckoView's dependencies (ADR 0002 check). Build with `-PwithFido=true` to include it, so you can compare passkey behavior.
- `CREDENTIAL_MANAGER_SET_ORIGIN` and `CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS` permissions, which GeckoView needs to call Credential Manager for passkeys. Fenix declares the same two.

## Build and install

Requires JDK 21 and an Android SDK with platform 37.2. Run these from `spike/geckoview-ext/`:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # gitignored
./gradlew assembleDebug                         # x86_64, for the emulator
./gradlew assembleDebug -PgeckoAbi=arm64-v8a    # for a phone or tablet
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb -s <serial>` rather than `./gradlew installDebug`. The Gradle task installs on **every** connected device and emulator.

The app logs every step on screen, to logcat with the tag `JRNYGV`, and to a file. Some vendor builds (HONOR) drop app lines from logcat, so read the file:

```sh
adb -s <serial> shell run-as app.jrny.spike.gvext cat files/spike.log
```

## Controls

**Row 1: Profiles**

| Control | What it does |
|---|---|
| **A / B** | Switches Profile. Each Profile keeps its own live Tab. A Profile's first Tab opens the current address, which is the same behavior as a Move. The status bar shows the Profile in its color. The last normal Profile is restored at the next launch. |
| **Private** | Switches to the current Profile's Private Tab (`usePrivateMode` with the same context ID). |
| **Isolation test** | Each of the four contexts (A, B, A private, B private) stores a `jrny_iso_<name>` cookie and localStorage key. Then those four and GeckoView's default context report what they see. Pass means each sees only its own, and the default context sees nothing. |
| **Read storage** | The same check without writing. Use it after a restart or after **Delete other profile**. |
| **Set probe cookie / Cookies** | Site-level check through httpbin.org, the same as the WebView spike. |
| **Popup test** | Loads a page with an "Open popup" button that calls `window.open`. The popup must open in the opener's Profile and see that Profile's storage. |
| **Ext probe** | Asks the bundled extension what it can see: cookie stores, cookies per store, and every tab. |
| **Delete other profile** | Closes the other Profile's Tabs and calls `clearDataForSessionContext` while the app keeps running. |

The isolation checks run in a content script of the bundled extension (`assets/extensions/jrny-bridge/content.js`) on `https://httpbin.org/html`. GeckoView has no cookie API for apps like WebView's `CookieManager`. The content script reads the page's own `document.cookie` and `localStorage`, and reports through `document.title`.

**Row 2: Extensions**

| Control | What it does |
|---|---|
| **Install uBO** | `WebExtensionController.install()` with AMO's `latest.xpi` URL for uBlock Origin, the way IronFox does it. Shows the permission prompt. |
| **Install URL** | Installs the `.xpi` URL in the address bar with `WebExtensionController.install()`, the way an add-on screen fed by the AMO API would. Used for iCloud Passwords. |
| **Unsigned .xpi** | Builds an unsigned extension in the app cache and installs it from `file://`. Release GeckoView should refuse it with `ERROR_SIGNEDSTATE_REQUIRED`. |
| **Built-in bridge** | `ensureBuiltIn()` on `assets/extensions/jrny-bridge/`. This also runs at every start, the way Fenix handles its built-ins. Its background script opens a native port to the app. |
| **Ad site (CNN)** | Opens an ad-heavy page. Then tap **Popup** to read uBlock Origin's blocked count. |
| **AMO** | Opens `addons.mozilla.org` to test the site's own "Add to Firefox" button (`extensionsWebAPIEnabled` is on). |
| **Popup** | Clicks the first registered browser action. The popup opens in a dialog. |
| **Extensions** | Lists installed extensions. Tap one to open its popup or options page, enable or disable it, check for an update, or uninstall it. |

**Passkeys are reported as unsupported.** The bundled extension's `passkeys-unsupported.js` rejects every WebAuthn passkey call with `NotSupportedError`, and reports that no authenticator is available, so sites fall back to passwords. See RESULTS.md, "Passkeys".

**Row 3: Accounts and passkeys**

| Control | What it does |
|---|---|
| **Google … Apple, Google via Stack Overflow** | The WebView spike's sign-in pages. Sign-in popups (`window.open`) open over the page that opened them, which stays alive behind them. Back or `window.close()` returns to that page. |
| **Headers** | `httpbin.org/headers`, to check the user agent the sites receive |
| **Passkey probe** | Injects two buttons into `httpbin.org/html` that call `navigator.credentials.get()` and `create()`. The result, or the error's name and message, appears in the log as `passkey: {…}`. |
| **Passkey: webauthn.io / GitHub** | Real passkey pages |

## Test protocol

### Extensions

1. **Startup.** The log shows the GeckoView version, the installed extensions and `bridge: port connected`.
2. **Install uBO.** Accept the prompt. Expect `install OK`, `onInstalled`, `onReady` and a `browserAction` line.
3. **Ad site, then Popup.** uBlock Origin's popup shows a non-zero "Blocked on this page" count.
4. **Open the dashboard** from the popup. Expect `onNewTab`, `new tab shown` and the dashboard.
5. **Unsigned .xpi.** Expect `refused: … ERROR_SIGNEDSTATE_REQUIRED`.
6. **AMO install button.** Open an extension's AMO page and tap "Add to Firefox". Expect the prompt and `onInstalled`.
7. **Extensions list:** check for an update, disable, enable, uninstall.

### Profiles

1. **Isolation test.** Expect `PASS`.
2. **Set probe cookie** in A, switch to B, **Set probe cookie**, then **Cookies**. B shows only its own `jrny_probe`.
3. **Ext probe.** Record which stores, cookies and tabs the extension sees.
4. **Ad site, then Popup** in B and in B private. Record whether uBlock Origin blocks in each.
5. **Popup test** in A and in B. The popup's `ISO` log line shows the opener Profile's `jrny_iso_*` keys.
6. **Restart.** Force-stop and relaunch, or reinstall with `adb install -r`. The last normal Profile is restored. **Read storage** shows A and B kept their data and the private contexts are empty.
7. **Delete other profile** from A, then **Read storage**. B is empty, A is intact, and no restart is needed.

### Accounts and passkeys

1. In Profile A, sign in to each provider with account 1. Switch to B and sign in with account 2. Switch back to A and reload: it must still be account 1.
2. Force-stop and relaunch. Both Profiles must still be signed in.
3. **Passkey probe**: tap **get** and **create**, and record the `passkey:` log lines. If a system passkey sheet appears, cancel it unless you mean to save a test passkey.

Record the results in `RESULTS.md`. Never put account names or personal data in it.
