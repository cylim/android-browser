# Profile engines: System WebView vs. GeckoView

This note compares the two throwaway spikes against the three requirements of Okapi, a browser by JRNY:

1. Several **Profiles**, each signed in to the same services with different **Accounts**.
2. **Site Rules**: send addresses that match a pattern, including wildcards such as `*.example.com`, to a chosen Profile.
3. Performance.

Sources:

- `spike/multi-profile-webview` (`cd041b2`): `spike/signin/`, WebView 145 on the emulator and 157 on the tablet.
- `spike/multi-profile-geckoview` (`7471db2`): `spike/geckoview-ext/`, GeckoView 160 release.
- New measurements in this note, taken on 2026-11-16.

## Summary

| | System WebView | GeckoView |
|---|---|---|
| Profile isolation | ✅ PASS | ✅ PASS, including a Private Tab store per Profile |
| Two Google Accounts at once | ✅ Tested on the tablet, default UA | Expected to work (Firefox UA), **not tested** |
| Microsoft, GitHub, Apple | Not tested | Not tested |
| Risk of being blocked as an embedded browser | **Real.** `X-Requested-With: <package>` is sent on every request and can't be turned off | Low. Looks like Firefox for Android |
| Site Rules | Possible. App logic plus `shouldOverrideUrlLoading` | Possible. App logic plus `onLoadRequest` |
| Cold start to first page painted (emulator) | **~1.7 s** | ~4.8 s |
| Memory, one Profile, one page (emulator) | **185 MB** | 562 MB |
| Memory, one Profile, Wikipedia article | **242 MB** | 685 MB |
| APK size | **3.6 MB** | 241 MB (x86_64), 227 MB (arm64), ~95 MB download |
| Delete a Profile while running | ❌ Deferred to next cold start | ✅ Immediate |
| Security updates | Google ships them through the Play Store | We ship a GeckoView update every 4 weeks |
| Extensions | None | ✅ AMO-signed (uBlock Origin works), shared by every Profile |

**Recommendation: stay on WebView (ADR 0001), and keep GeckoView as the fallback.** WebView wins on every performance number by about 3× and needs no engine maintenance. Site Rules work the same on both engines. The open risk is sign-in: the Microsoft, GitHub and Apple rows of the WebView Milestone 0 matrix are still untested. The maintainer kept WebView and started Milestone 1 with them open (ADR 0001). Switch to GeckoView only if one of them is blocked.

## 1. Multiple Accounts per service

Both engines isolate cookies and storage per Profile, keep popups in the opener's Profile, and keep sign-ins after a restart.

**WebView** (androidx.webkit `ProfileStore`):

- On the tablet, Google and "Google via Stack Overflow" signed in with a different Account in Profiles A and B. This needed **no** user-agent changes (mode 1).
- Every request carries `X-Requested-With: app.jrny.spike.signin`. The public API that used to remove it is now a no-op. Google's `disallowed_useragent` block didn't trigger in testing, but Google can start using this header at any time, and we can't hide it.
- Cookies are written to disk only periodically. A cookie set seconds before a force-stop was lost. The real app must call `CookieManager.flush()` for every loaded Profile in `onStop`.
- A Profile in use can't be deleted until the next cold start. That also limits Private Tabs: their store lingers on disk until the app restarts.

**GeckoView** (session `contextId`):

- Isolation, persistence across a reinstall, and popups all pass. Private Tabs per Profile (`usePrivateMode` plus the Profile's `contextId`) are isolated and empty after a restart.
- Real sign-ins in two Profiles were **not run**. The UA is plain Firefox for Android, which Google, Microsoft, GitHub and Apple all support, so the risk is low. It still needs testing.
- `clearDataForSessionContext` deletes a Profile while the app runs.
- Extensions see every Profile. Any extension with the `tabs` permission can read the addresses open in all Profiles. That weakens isolation if Okapi ever allows extensions.

**Both:** passkeys need approval as a privileged browser, which is tied to the signing certificate. Password managers fill through Android Autofill, and 1Password only recognizes browsers on its own list.

## 2. Site Rules

Neither spike builds Site Rules, but the engine APIs needed are equivalent. The matching (`*.atlassian.net` → Work) is plain app code and doesn't depend on the engine.

| Hook | WebView | GeckoView |
|---|---|---|
| Incoming Links | `ACTION_VIEW` intent, app chooses the Profile | Same |
| Navigation inside a Tab | `WebViewClient.shouldOverrideUrlLoading`: synchronous. It isn't called for POST or `loadUrl` | `NavigationDelegate.onLoadRequest`: returns `GeckoResult<AllowOrDeny>`, so the answer can be async |
| Signals to avoid breaking sign-in | `isRedirect`, `hasGesture`, `isForMainFrame` | `isRedirect`, `hasUserGesture`, `isDirectNavigation`, `triggerUri` |
| Open the target in another Profile | New `WebView` with `setProfile` (ADR 0003) | New `GeckoSession` with the target `contextId` |

Design points that apply to either engine:

- **Never apply a Site Rule to a redirect, or to a navigation without a user gesture.** Sign-in chains cross domains: Stack Overflow → accounts.google.com → stackauth.com → Stack Overflow. If a Site Rule moved the `accounts.google.com` step, the sign-in would land in the wrong Profile. Exempt identity providers by default: `accounts.google.com`, `login.microsoftonline.com`, `login.live.com`, `appleid.apple.com` and `github.com/login/oauth`.
- **A rule can't tell Accounts apart on a shared domain.** Work and personal Gmail are both `mail.google.com`. Rules work for services where the address differs per Account, such as `yourco.atlassian.net` or `yourco.slack.com`.
- **Wildcards need the Public Suffix List.** Without it, `*.co.uk` would mean "every UK site". Bundle the list (as Firefox and Chromium do) or reject rules above the registrable domain.
- SPEC currently limits Site Rules to Incoming Links. Applying Site Rules to links clicked *inside* a Tab is a bigger feature. It needs the gesture rules above and a decision about what happens to the original Tab.

## 3. Performance

### Measured

Emulator `JRNY_Pixel_7_API_37` (Android 17, x86_64, 4 GB RAM), WebView 145.0.7632.218 and GeckoView 160.0. Both spike APKs are debug builds, but each engine's native code is a release build. The installed APKs matched the branch builds by MD5.

**Cold start** (`am start -W -S` with a `VIEW` intent for `https://example.com/`, 6 runs):

| | WebView | GeckoView |
|---|---|---|
| Activity's first frame (`TotalTime`) | 818–1089 ms, median ~880 | 780–894 ms, median ~835 |
| First frame with the page painted (5 runs) | 1551–1856 ms, **median 1675** | 4540–5065 ms, **median 4778** |

How "painted" was measured: a hash of a band of the screen where example.com's text appears, captured about every 220 ms. Each result is the first capture that matched the final screen, so it's accurate to about ±220 ms. In the Gecko spike's log, about 2.1 s passes between creating the runtime and `WebExtensionController.list()` returning, which is Gecko's engine boot. The spike waits for that, plus about 40 ms for `ensureBuiltIn`, before loading the first page. A real app that skips extensions would save little of that time.

**Memory** (`dumpsys meminfo` total PSS, every process of the app, 20 s after the page loaded, one Profile):

| | WebView | GeckoView |
|---|---|---|
| example.com | **185 MB** (app 110 + renderer 75) | 562 MB (main 206, GPU 53, crash helper 23, three content and extension processes 280) |
| Then a Wikipedia article in the same Tab | **242 MB** (app 116 + renderer 125) | 685 MB (a fourth content process started) |

The Gecko spike still had two extensions installed: the built-in bridge and the 1Password extension, which runs a background page. These sit in the extensions process, so remove one of the ~95–120 MB processes for a rough engine-only figure: still about 440–470 MB, more than double WebView's.

### Why the numbers differ

- **WebView's code is shared with the system.** The engine library is preloaded and shared across apps through `webview_zygote`, so less of it is charged to Okapi. GeckoView loads its own 155 MB `libxul.so` in every process.
- **Process model.** WebView ran **one renderer process for every Profile and Tab**. Extra Profiles cost little memory, but one renderer crash (`onRenderProcessGone`) takes down every Tab in every Profile. GeckoView runs a parent, a GPU process and several content processes, and starts more as sites open. That uses more memory and gives better crash isolation.
- **APK and install size.** 3.6 MB vs. 227 MB installed per ABI. Updates of that size every 4 weeks also cost users storage and data.

### Not measured

- **JavaScript and rendering speed (Speedometer 3.1).** This needs the device UI, and another thread holds the emulator's device session.
- **Marginal cost of a second Profile.** The WebView spike keeps one WebView and replaces it on a Profile switch, so it can't show two live Profiles.
- **Real arm64 hardware.** The emulator runs on a desktop CPU. Absolute times will be slower on a phone. The ratios are probably similar, but that is not verified.

## 4. Viability and maintenance

| | WebView | GeckoView |
|---|---|---|
| Security fixes | Google ships them through the Play Store, so we do nothing | We update the dependency and ship a release at least every 4 weeks |
| API churn | androidx.webkit is additive. New features are gated by `WebViewFeature` | A 3-release deprecation policy. The install-prompt signature alone changed three times in v133–v144. GeckoView 160 forced `compileSdk` 37.2 and Kotlin 2.4 |
| Who controls the risks | Google: the `disallowed_useragent` policy, and the WebView feature set | Mozilla: AMO signing, and the release cadence |
| ADR 0002 (no Google libraries) | ✅ | ✅ with `play-services-fido` excluded. Passkeys use Credential Manager |
| F-Droid | Simple, a small app | Possible, but F-Droid usually wants GeckoView built from source (Fennec F-Droid and IronFox do this). Check before relying on it |

For a solo maintainer, the 4-week GeckoView cycle is the main cost. A missed update leaves users on a browser engine with publicly known security holes.

## 5. Future development

| Feature | WebView | GeckoView |
|---|---|---|
| Content blocking | Our own code in `shouldInterceptRequest` (synchronous, with no access to some request types) | uBlock Origin works today, in every Profile |
| Extensions | Never | Yes, but shared by all Profiles. Per-Profile extensions would need a runtime per process, so they're effectively impossible |
| Web notifications and push (Gmail, Slack) | WebView doesn't implement the Notification or Push APIs | `WebNotificationDelegate` exists. Push needs a push service: Mozilla's uses Firebase, which conflicts with ADR 0002, so it would need UnifiedPush or similar |
| Private Tabs per Profile | Workable, with the store wiped at cold start | ✅ Built in (`usePrivateMode`) |
| Live Profile deletion | ❌ | ✅ |
| Custom Tabs service | App-built on either engine | App-built on either engine |
| Changing the user agent and engine prefs | UA and Client Hints only | Full prefs (`about:config` equivalents) |
| Site compatibility | Chromium. Some sites only fully support Chrome | Firefox. A small number of sites degrade |

If notifications for multi-account web apps (two Gmails, two Slacks) become a core feature, that is the strongest argument for GeckoView. Revisit this note at that point.

## Next steps

1. Finish the WebView Milestone 0 sign-in matrix: Microsoft personal and work, GitHub and Apple, with two Accounts each. A block there reopens ADR 0001.
2. Run Speedometer 3.1 in both spikes once the emulator's device session is free, and measure two live Profiles.
3. When Site Rules are designed, write the gesture and redirect rules and the identity-provider exemptions above into SPEC.
