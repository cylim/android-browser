# Milestone 0: sign-in spike (throwaway)

This app answers one question from [ADR 0001](../../docs/adr/0001-system-webview-as-engine.md): can a System WebView browser reliably sign in to accounts in two isolated profiles on Android 15+? It lives on the `spike/m0-signin` branch and is **never merged**. Only the written result goes to `main`.

The application ID is `app.jrny.spike.signin`, so it never collides with the real app.

## Build and install

Requires JDK 21 and an Android SDK with platform 37. Run these from `spike/signin/`:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # gitignored
./gradlew assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb -s <serial>` rather than `./gradlew installDebug`. The Gradle task installs on **every** connected device and emulator.

### Android 17 emulator

Android 17 (API 37) emulator images crash in a loop (`surfaceflinger` aborts with `!rcEnc->featureInfo()->hasReadColorBufferDma`) when the AVD was created with Command-line Tools older than 22. Those tools write `target=android-0` to the AVD's `.ini` file. Recreate the AVD with Command-line Tools 22 or later and check that its `.ini` file says `target=android-37.0`. Upstream bug: issuetracker.google.com/issues/557246813.

## Controls

| Control | What it does |
|---|---|
| **A / B** | Switches profile. The WebView is recreated in the new profile and reloads the same address without its query string, which is the same behavior as a Move. |
| **UA mode** | 1: WebView default. 2: Chrome-like UA string (no `; wv`, no `Version/4.0`). 3: mode 2 plus Client Hints without the "Android WebView" brand. 4: mode 3 plus a "Google Chrome" brand. |
| **Passkeys** | Sets `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`. Expect this to fail until the app is approved as a privileged browser. |
| **Isolation test** | Writes a cookie through each profile's API and checks the other profile and the default profile can't see it. |
| **Set probe cookie / Cookies** | Site-level isolation check through httpbin.org. |
| **Headers** | Shows the headers the site receives (`User-Agent`, `Sec-CH-UA`, `X-Requested-With`). |
| **Diagnostics** | Shows the WebView version, supported features, `navigator.userAgentData`, and the **names** of the cookies set for each provider. Values are never shown. |
| **Delete other profile** | Checks that a loaded profile can't be deleted while the app runs, and that deleting it at the next cold start works. |

## Test protocol

Use test accounts or your own, but **never put an email address, account name, cookie value or screenshot of an account page into RESULTS.md or a commit.** Write "Google account 1" and "Google account 2".

1. Run **Isolation test**. It must say PASS.
2. For each UA mode, starting at 1 and stopping at the first mode that works:
   1. In profile A, sign in to Google account 1.
   2. Switch to B, sign in to Google account 2.
   3. Switch back to A and reload. It must still be account 1.
   4. Record the result in RESULTS.md.
3. Repeat step 2 for Microsoft (personal and work), GitHub and Apple.
4. Repeat step 2 for "Google via Stack Overflow", which tests the third-party "Sign in with Google" flow.
5. Force-stop and relaunch the app. Both profiles must still be signed in.
6. Run **Delete other profile**, then force-stop and relaunch, and record what happened.
