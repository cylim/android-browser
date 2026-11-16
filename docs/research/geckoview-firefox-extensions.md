# Can a GeckoView-based browser install Firefox extensions?

Research notes, written 2026-11-16. They feed a possible reconsideration of [ADR 0001](../adr/0001-system-webview-as-engine.md), which chose Android System WebView and lists "No browser extensions" as a consequence. They are checked against [ADR 0002](../adr/0002-no-proprietary-dependencies-or-telemetry.md).

**Versions these notes apply to.** GeckoView changes every 4 weeks, so every fact below is tied to a version:

- **Source code:** `mozilla-firefox/firefox` `main` at commit [`f898416`][gh-root] (2026-11-15), milestone `162.0a1`.
- **Published javadoc:** "GeckoView 162.0a1 API".
- **Latest Maven artifacts on 2026-11-16 ([release][mvn-release], [beta][mvn-beta], [nightly][mvn-nightly]):**

  | Channel | Version |
  | --- | --- |
  | Release | `geckoview` 160.0.20261104181552 |
  | Beta | `geckoview-beta` 161.0.20261113104103 |
  | Nightly | `geckoview-nightly` 162.0.20261115213519 |

## Short answer

**Yes, with conditions.** GeckoView has a public API (`WebExtensionController`) that installs, lists, enables, disables, updates and uninstalls ordinary Firefox WebExtensions (`.xpi`) for a third-party embedder. Several browsers that are not Mozilla's ship extension support on it today, including Iceraven, IronFox and Fennec F-Droid. The caveats:

1. **Mozilla must sign the extension.** GeckoView enforces this in release and beta builds, and an app cannot turn it off at runtime. Only Nightly builds, or a GeckoView you build yourself as a non-release milestone, honour `xpinstall.signatures.required`.
2. **GeckoView gives you an engine, not an add-on manager.** The app has to build the store or listing, the install and permission prompts, the toolbar or menu entries for extension actions, popups, tab creation and closing, and scheduled update checks. Mozilla's Android Components library (MPL-2.0) already does most of this, and every Fenix fork reuses it.
3. **Android supports a subset of the API.** There are no bookmarks, history, menus/contextMenus, commands, sessions, windows or sidebar APIs, and some tab and downloads functions are missing. `webRequest` (including blocking) and `declarativeNetRequest` are supported, which is what content blockers need.
4. **ADR 0002 conflicts.** Mozilla's prebuilt GeckoView AAR declares a dependency on Google's proprietary `play-services-fido`. The extension features also contact Mozilla servers: AMO for listings and updates, and Remote Settings for the add-on blocklist. Fennec F-Droid builds GeckoView from source to swap out the Play Services library.

---

## 1. The `WebExtensionController` API

Get it with `GeckoRuntime.getWebExtensionController()` ([api.txt][api-txt]). Every method below returns a `GeckoResult` and must be called on the handler (UI) thread (`@HandlerThread`).

**Signatures checked against:**

- the source at [`f898416`][wec-src]
- [`api.txt`][api-txt]
- the [published javadoc][jd-wec] for 162.0a1

**"Since" versions** come from the [GeckoView API changelog][changelog].

| Method (current signature) | Since | Notes |
| --- | --- | --- |
| `GeckoResult<WebExtension> install(@NonNull String uri, @Nullable String installationMethod)` | `install(uri)` in v73. The `installationMethod` overload in v122. | `uri` can be `https:`, `file:` or `resource:`. GeckoView downloads the file, checks the manifest and signature, then calls `PromptDelegate.onInstallPromptRequest`. The javadoc says these extensions "need to be signed by Mozilla" ([source][wec-install]). Calling `cancel()` on the result cancels the install. `installationMethod` takes one of these constants:<br>• `INSTALLATION_METHOD_MANAGER`<br>• `INSTALLATION_METHOD_FROM_FILE`<br>• `INSTALLATION_METHOD_ONBOARDING` (v133)<br>• `INSTALLATION_METHOD_RTAMO` (v152) |
| `GeckoResult<WebExtension> install(@NonNull String uri)` | v73 | Same as above with `installationMethod = null`. |
| `GeckoResult<WebExtension> installBuiltIn(@NonNull String uri)` | v78 | Accepts only `resource://android/...` folder URIs, meaning an unpacked folder inside the APK's `assets/` ([source][wec-builtin], [JS check][gvwe-validate]). |
| `GeckoResult<WebExtension> ensureBuiltIn(@NonNull String uri, @Nullable String id)` | v79 | Re-installs only when the bundled version differs from the installed one ([JS][gvwe-builtin]). |
| `GeckoResult<Void> uninstall(@NonNull WebExtension)` | v73 | Deletes the extension's data and closes its pages ([source][wec-uninstall]). |
| `GeckoResult<WebExtension> enable(@NonNull WebExtension, int source)` / `disable(...)` | v74 | `source` is `EnableSource.USER` or `EnableSource.APP` ([source][wec-enable]). |
| `GeckoResult<List<WebExtension>> list()` | v73 | Installs persist across restarts, so call `list()` at startup to attach delegates again ([source][wec-list]). |
| `GeckoResult<WebExtension> update(@NonNull WebExtension)` | v74 | Fetches `browser_specific_settings.gecko.update_url`, or AMO when there is none. Returns `null` when no update exists. Calls `onUpdatePrompt` if the new version needs new permissions ([source][wec-update], [design doc][design-mgmt]). |
| `setAllowedInPrivateBrowsing(WebExtension, boolean)` | v75 | |
| `addOptionalPermissions(String id, String[] perms, String[] origins, String[] dataCollection)` / `removeOptionalPermissions(...)` | v125. Data-collection argument added in v141. | |
| `setTabActive(GeckoSession, boolean)` | v74 | The embedder must call this when the user switches tabs. Without it, extensions cannot tell which tab is active ([source][wec-tabactive]). |
| `enableExtensionProcessSpawning()` / `disableExtensionProcessSpawning()` | v118 / v120 | Controls the separate extension process and its crash handling. |

**Delegates on the controller:**

- **`PromptDelegate`** ([source][wec-prompt]). All three methods have defaults that return `null`, which GeckoView treats as deny. So **an embedder that sets no `PromptDelegate` cannot install anything that asks for permissions.**

  | Method | Returns | Since |
  | --- | --- | --- |
  | `onInstallPromptRequest(WebExtension, String[] permissions, String[] origins, String[] dataCollectionPermissions)` | `GeckoResult<WebExtension.PermissionPromptResponse>` | v133, replacing the older `onInstallPrompt`. The data-collection argument was added in the v141–v142 cycle, and the old signatures were removed in v143–v144. |
  | `onUpdatePrompt(WebExtension, String[] newPermissions, String[] newOrigins, String[] newDataCollectionPermissions)` | `GeckoResult<AllowOrDeny>` | |
  | `onOptionalPrompt(WebExtension, String[] permissions, String[] origins, String[] dataCollectionPermissions)` | `GeckoResult<AllowOrDeny>` | |

  The `PermissionPromptResponse(Boolean isPermissionsGranted, Boolean isPrivateModeGranted, Boolean isTechnicalAndInteractionDataGranted)` constructor carries the user's answer ([api.txt][api-txt]).
- **`AddonManagerDelegate`** (v113). Callbacks: `onInstalling`, `onInstalled`, `onUninstalling`, `onUninstalled`, `onEnabling`, `onEnabled`, `onDisabling`, `onDisabled`, `onInstallationFailed` (v118), `onReady` (v121) and `onOptionalPermissionsChanged` (v127). Since v121, `install()` no longer waits for the extension to start. `baseUrl` and `optionsPageUrl` stay empty until `onReady` ([changelog][changelog]).
- **`DebuggerDelegate.onExtensionListUpdated()`** fires for installs made with `web-ext` or the remote debugger.
- **Failures** surface as `WebExtension.InstallException` with an error code, for example:
  - `ERROR_SIGNEDSTATE_REQUIRED`
  - `ERROR_BLOCKLISTED`
  - `ERROR_SOFT_BLOCKED`
  - `ERROR_INCOMPATIBLE`
  - `ERROR_UNSUPPORTED_ADDON_TYPE`
  - `ERROR_USER_CANCELED`
  - `ERROR_NETWORK_FAILURE`

  The full list is in [api.txt][api-txt].

**API stability.** GeckoView keeps deprecated APIs for 3 releases before removing them, and ships a new major version every 4 weeks ([breaking-changes design doc][breaking]). The install-prompt signature alone changed in v133, v141/142 and v143/144 (see above). Expect this kind of churn.

## 2. Installing from AMO, a local `.xpi` or a remote `.xpi`

GeckoView covers the install mechanics. Everything the user sees is up to the app.

**From a URL the app already has.** Call `install("https://.../file.xpi")` and GeckoView downloads the file itself. Fenix gets these URLs from the AMO API (below), and IronFox installs uBlock Origin this way from `https://addons.mozilla.org/firefox/downloads/latest/uBlock0@raymondhill.net/latest.xpi` ([IronFox `IFAddonUtils.kt`][ironfox-addonutils]). Mozilla's minimal sample app also just calls `controller.install(uri, null)` ([geckoview_example][gv-example]).

**From a local file.** `install("file:///...")` works if the app can read the path. Android Components' `AddonFilePicker` opens the system picker for `application/x-xpinstall` or `application/zip`. It copies the chosen content URI into the app cache and calls `install()` with `INSTALLATION_METHOD_FROM_FILE` ([source][ac-filepicker]). Signature checks still apply. GeckoView does not accept `content://` URIs directly. *(Inferred: AC copies to a `file:` URI, and the javadoc lists only `https:`, `file:` and `resource:`.)*

**Links clicked on web pages.**

- **XPI links.** Gecko's `amContentHandler` handles navigations that return `application/x-xpinstall`. It requires a user gesture and passes the file to `AddonManager.installAddonFromWebpage` ([source][am-content-handler]). On Android:
  - **AMO links.** GeckoView allowlists `https://addons.mozilla.org` (`xpinstall.whitelist.add`, [geckoview-prefs.js][gv-prefs-whitelist]). These installs go straight to `PromptDelegate.onInstallPromptRequest`.
  - **Other sites.** Gecko raises an `addon-install-blocked` notification ([AddonManager][am-blocked]). I found no observer for that topic in GeckoView's Android code, and the desktop-only pref `extensions.postDownloadThirdPartyPrompt` ([firefox.js][ff-js-postdl]) is not set for Android. *(Inferred from source and not tested.)* This matches Extension Workshop, which says the `application/x-xpinstall` web-install method "doesn't work in Firefox for Android". Instead "the add-on is downloaded, and the user needs to follow the instructions for installing add-on from file" ([self-distribution][ew-selfdist]).
- **AMO's own install button.** Fenix turns on `GeckoRuntimeSettings.Builder.extensionsWebAPIEnabled(true)` ([Fenix GeckoProvider][fenix-geckoprovider], [setting][gvrs-webapi]). That exposes the `mozAddonManager` web API, but only to `https://addons.mozilla.org` ([host check][am-webapi-host]).
  - **What the API can do:** it lets AMO create installs, and it also lets AMO look up, enable or disable, and uninstall the user's add-ons ([WebIDL][am-webidl]).
  - **IronFox's change:** IronFox patches Gecko so AMO installs work without `mozAddonManager`, "without allowing Mozilla to enable, uninstall, and query a list of the user's installed add-ons" ([patch][ironfox-nomozam]).
  - **What breaks without it:** I did not verify how AMO's front end behaves in a GeckoView that leaves the web API off.

**Listing add-ons in the app.** GeckoView has no catalog API. Fenix uses Android Components' `AMOAddonsProvider`:

- **Which collection:** an AMO *collection*, by default `mozilla` / `Extensions-for-Android` in Fenix ([build.gradle][fenix-gradle-amo]).
- **Which endpoint:** `{server}/api/v4/accounts/account/{user}/collections/{name}/addons/?page_size=50&sort=...` on `https://services.addons.mozilla.org`. It also calls `.../api/v4/addons/search/?guid=...` for details on installed add-ons ([source][ac-amo-provider]).
- **API status:** AMO's API docs call v4 "frozen" and v5 the default. v5 is "stable" but "not frozen" ([addons-server API overview][amo-api-overview]).
- **Authentication:** the docs mark no authentication requirement on the collection add-ons endpoint, and public collections are readable ([collections API][amo-api-collections]).

## 3. Built-in (bundled) vs. user-installed extensions

| | Built-in (`installBuiltIn` / `ensureBuiltIn`) | User-installed (`install`) |
| --- | --- | --- |
| Source | Folder in the APK under `assets/`, addressed as `resource://android/assets/<dir>/` ([consumer guide][gv-consumer-ext]) | `.xpi` from `https:`, `file:` or `resource:` |
| Signing | "don't need to be signed" ([javadoc][wec-builtin]). The design doc says the same ([design doc][design-mgmt]). | Must be signed by Mozilla (AMO) on release and beta builds (section 4) |
| Privileges | Privileged. `XPIProvider` treats `builtIn` locations as privileged ([source][xpi-privileged]). | Not privileged unless signed with Mozilla's "privileged" certificate (`SignedStateFlags.PRIVILEGED`, [source][we-signedstate]) |
| Native messaging to the app | Yes, with the `geckoViewAddons` permission | No (below) |

**How the native-messaging gate works.** On Android, Gecko puts `geckoViewAddons`, `nativeMessagingFromContent` and `nativeMessaging` in its privileged-only permission set ([Extension.sys.mjs][ext-privperms]). `runtime.connectNative` and `sendNativeMessage` are sent to the app only when the extension has `geckoViewAddons` ([ExtensionParent.sys.mjs][ext-opennative]). MDN's compat data marks `runtime.connectNative` and `sendNativeMessage` as unsupported on Firefox for Android: "Only available to privileged extensions in GeckoView 68 and later" ([BCD runtime.json][bcd-runtime]). **So a user-installed AMO extension cannot talk to the embedding app.**

**App-side messaging** ([consumer guide][gv-consumer-ext], [javadoc][jd-we]):

- `WebExtension.setMessageDelegate(MessageDelegate, String nativeApp)` receives messages from background scripts.
- `GeckoSession.getWebExtensionController().setMessageDelegate(WebExtension, MessageDelegate, String nativeApp)` receives messages from content scripts and extension pages. Content scripts can send only if the extension also holds `nativeMessagingFromContent` (`Flags.ALLOW_CONTENT_MESSAGING`).
- `MessageDelegate.onMessage(...)` handles one-off `sendNativeMessage` calls. `MessageDelegate.onConnect(WebExtension.Port)` handles `connectNative`. The app sends back through `Port.postMessage(JSONObject)` and receives through `PortDelegate.onPortMessage` and `onDisconnect`.
- GeckoView needs no native-messaging host manifest. The `nativeApp` string is the identifier.

**Built-ins in practice.** A built-in extension is the supported way for an embedder to ship its own privileged JS glue. Fenix and IronFox use this mechanism ([IronFox `IFAddonUtils.kt`][ironfox-addonutils] installs a `resource://android/assets/extensions/...` built-in).

## 4. Signing

**Where enforcement is decided.** `AddonSettings.REQUIRE_SIGNING` sets it ([source][addonsettings]):

- If the build-time define `MOZ_REQUIRE_SIGNING` is set, `REQUIRE_SIGNING` becomes a non-writable constant `true`.
- If not, it follows the pref `xpinstall.signatures.required`.

**Which builds set the define.** `toolkit/moz.configure` sets `MOZ_REQUIRE_SIGNING` by default when the milestone "is release or beta and not ESR" ([source][moz-configure-signing]). Mozilla's `geckoview` (release) and `geckoview-beta` AARs therefore enforce signing, and the pref has no effect on them. *(Inferred from the build logic and the artifacts' version numbers. I did not install an unsigned `.xpi` against the 160 AAR to confirm.)*

**The pref's default.** GeckoView ships `pref("xpinstall.signatures.required", true)` ([geckoview-prefs.js][gv-prefs-sig]). Even where the pref is honoured (Nightly `162.0a1`, or a self-built non-release milestone), the embedder must actively set it to `false`.

**Ways to set prefs.** GeckoView has no `GeckoRuntimeSettings` option for signing. The routes are:

1. **`GeckoPreferenceController.setGeckoPref(...)`.** Marked `@ExperimentalGeckoViewApi` since v142 ([source][gv-prefctl]).
2. **A YAML config file.** Set the path with `GeckoRuntimeSettings.Builder.configFilePath(...)`. Without an explicit path, GeckoView reads `/data/local/tmp/<package>-geckoview-config.yaml` only for debuggable apps or the current debug app ([source][gvrs-config], [runtime][gv-runtime-config]).

**Extension Workshop's rule** ([signing overview][ew-signing], updated Aug 2024): signing is required in release and beta Firefox, and unsigned extensions can be installed in Developer Edition, Nightly and ESR after toggling `xpinstall.signatures.required`. This is consistent with the configure logic above. Note that `require_signing` excludes ESR.

**Other build-time switches.**

- `--with-unsigned-addon-scopes=app,system` ([moz.configure][moz-configure-signing]).
- Building with `MOZ_REQUIRE_SIGNING` unset.

Both require compiling GeckoView yourself (see costs in section 8).

**Self-distributed (unlisted) extensions.** These are still signed by AMO, just not listed publicly ([signing overview][ew-signing]). They install on Android through install-from-file, or through any `install()` call the app makes ([install self-distributed][ew-install-selfdist]).

**How Fenix exposes the developer escape hatches** (source at `f898416`):

- **Install add-on from file.** The settings entry is visible only when the hidden debug menu is unlocked for the session (`showSecretDebugMenuThisSession`, [SettingsFragment][fenix-settings-file]). Extension Workshop documents the unlock as tapping the Firefox logo in About five times ([install self-distributed][ew-install-selfdist]). The flow is `AddonFilePicker` → `install(file://…, INSTALLATION_METHOD_FROM_FILE)`. Signatures are still checked on release.
- **Custom add-on collection.** The setting stores an AMO user and collection name, and Fenix then builds `AMOAddonsProvider(collectionUser = ..., collectionName = ...)` instead of the default ([Components.kt][fenix-components-provider]). The feature flag is `customExtensionCollectionFeature = Config.channel.isNightlyOrDebug || Config.channel.isBeta` ([FeatureFlags.kt][fenix-featureflags]), so **in current source it is not available in Fenix release builds**. I did not check whether earlier releases exposed it. The collection only changes *which AMO-listed add-ons are shown*. Each add-on still has to be AMO-signed.

## 5. Which WebExtension APIs work on Android, and what the embedder must implement

**What Android registers.** Shared toolkit modules ([ext-toolkit.json][ext-toolkit-json]) work on Android unless noted. Examples: `alarms`, `cookies`, `declarativeNetRequest`, `dns`, `i18n`, `management`, `notifications`, `permissions`, `privacy`, `proxy`, `runtime`, `scripting`, `storage`, `userScripts`, `webNavigation` and `webRequest`. On top of those, Android registers only `browserAction`/`action`, `pageAction`, `tabs`, `browsingData` and `geckoViewAddons` ([ext-android.json][ext-android-json]).

**Desktop-only APIs.** These are registered only in [ext-browser.json][ext-browser-json], and MDN's compat data marks them unsupported on Firefox for Android ([BCD][bcd-root]):

- `bookmarks`
- `history`
- `menus`/`contextMenus`
- `commands`
- `sessions`
- `windows`
- `sidebarAction`
- `omnibox`
- `identity`
- `search`
- `topSites`
- `devtools`

**Supported on Android per MDN's compat data** ([BCD][bcd-root], checked at commit `3e04f2c`, 2026-11-15):

- `webRequest` since Firefox for Android 48, including blocking `onBeforeRequest` and `onHeadersReceived`. `filterResponseData` and `onAuthRequired` mirror desktop.
- `declarativeNetRequest`
- `cookies`
- `storage`, except `storage.managed`
- `browserAction` since 55
- `pageAction` since 50
- `tabs` since 54, with 25 subfeatures unsupported (for example `duplicate`, `move`, `discard`, `hide` and zoom)

**Android Manifest V3 limit.** Extension Workshop says "Background service workers aren't supported on Firefox for Android" and recommends Manifest V2, or event pages, for Android targets ([Developing for Firefox for Android][ew-android], updated Oct 2026).

**What the embedder must supply.** GeckoView forwards UI-bound extension calls to app delegates. Without them, the call either does nothing or is denied.

| WebExtension feature | GeckoView delegate the app must implement | Notes |
| --- | --- | --- |
| `browserAction` / `action` / `pageAction` (icon, title, badge, popup) | `WebExtension.ActionDelegate`: `onBrowserAction`, `onPageAction`, `onTogglePopup`, `onOpenPopup` ([javadoc][jd-we]). Set it per runtime with `WebExtension.setActionDelegate`, or per tab with `GeckoSession.getWebExtensionController().setActionDelegate(ext, ...)`. | The app draws the toolbar or menu entry. For popups it returns a `GeckoSession` that it renders itself. `Action.click()` triggers the action. |
| `tabs.create`, `runtime.openOptionsPage` | `WebExtension.TabDelegate`: `onNewTab(WebExtension, CreateTabDetails)` returns a `GeckoResult<GeckoSession>`. Also `onOpenOptionsPage(WebExtension)`. | Set per extension with `setTabDelegate` (v75+). |
| `tabs.update`, `tabs.remove` | `WebExtension.SessionTabDelegate`: `onUpdateTab(...)` and `onCloseTab(...)`, each returning `GeckoResult<AllowOrDeny>`. | Set per session with `SessionController.setTabDelegate(ext, ...)`. |
| `tabs.query({active:true})`, `tabs.onActivated` | `WebExtensionController.setTabActive(session, boolean)` | The app calls this on every tab switch ([geckoview_example][gv-example-tabactive]). |
| `downloads.download` | `WebExtension.DownloadDelegate.onDownload(...)` (v86) | On Android, `ext-downloads.js` is replaced by a GeckoView version that sends calls to this delegate ([jar.mn][gv-ext-jar]). Android Components does not wire this delegate. *(Inferred: no `setDownloadDelegate` call in `engine-gecko`.)* That matches MDN marking `downloads` as removed in Firefox for Android 79 ([BCD downloads.json][bcd-downloads]). |
| `browsingData` (history, downloads, form data, passwords) | `WebExtension.BrowsingDataDelegate` (v85) | Needed because the app, not Gecko, owns history and download storage. |
| `runtime.sendNativeMessage` / `connectNative` | `MessageDelegate` / `PortDelegate` | Built-in and privileged extensions only (section 3). |
| Install, update and optional-permission prompts | `WebExtensionController.PromptDelegate` | The defaults deny (section 1). |

Mozilla's `geckoview_example` app shows the minimum wiring: `PromptDelegate`, `ActionDelegate`, `TabDelegate`, `SessionTabDelegate` and `setTabActive` ([GeckoViewActivity.java][gv-example]).

## 6. What Android Components and Fenix add on top of GeckoView

**What Android Components (AC) provides.** AC is "a collection of Android libraries to build browsers or browser-like applications", published on `maven.mozilla.org` and licensed MPL-2.0 ([README][ac-readme]). The extension-related modules, versioned with Firefox (latest `161.0b3` on 2026-11-16):

| Module | What it does |
| --- | --- |
| `concept-engine` | An engine-neutral `WebExtension` / `WebExtensionRuntime` / `WebExtensionDelegate` abstraction ([source][ac-concept-webext]). |
| `browser-engine-gecko` | Implements it on GeckoView. It installs a `WebExtensionController.PromptDelegate`, `AddonManagerDelegate`, `DebuggerDelegate` and `ExtensionProcessDelegate` and maps them to the abstraction ([GeckoEngine.kt][ac-geckoengine-prompt]). |
| `support-webextensions` | `WebExtensionSupport` keeps installed extensions in sync with AC's browser state. It handles new-tab and close-tab requests, popups and permission requests. |
| `feature-addons` | The add-on manager: `AddonManager` with install, uninstall, enable, disable, private-browsing access, optional permissions and update. Also `AMOAddonsProvider` (AMO collection client with disk cache), `AddonFilePicker`, permission dialogs, a supported-add-ons checker, and `DefaultAddonUpdater`, which schedules `update()` through AndroidX WorkManager ([source][ac-addon-updater]). Fenix runs the updater every 12 hours ([Components.kt][fenix-components-updater]). |

**Gecko's own background update is off on Android.** `extensions.manifest` registers the add-on update timer only `#ifndef MOZ_WIDGET_ANDROID` ([source][am-manifest]). The design doc says "Gecko will not update any extension or blocklist state without the embedder's input" ([design doc][design-mgmt]). Without AC, the app has to schedule `update()` itself.

**Can a third-party app reuse it?**

- **License:** the MPL-2.0 file-level copyleft applies. Modified MPL files stay MPL. The rest of the app can stay MIT. *(General MPL-2.0 reading, not legal advice.)*
- **Coupling:** the modules are built around AC's `BrowserStore`/`browser-state` architecture. `feature-addons` pulls in `browser-state`, Room, WorkManager, Compose, Material and others. `browser-engine-gecko` depends on `geckoview-*-omni` and AC's `service-nimbus`, which is Mozilla's experimentation SDK from application-services (dependency lists from the [Maven POMs][mvn-ac]). Reusing `feature-addons` effectively means adopting AC's engine and state layers. Every Fenix fork in section 7 does this. I did not find a non-Fenix app that uses `feature-addons` on its own.
- **AMO API terms:** the addons-server API docs I read state no terms of use, no rate limits and no requirement to authenticate for public collection reads ([overview][amo-api-overview], [collections][amo-api-collections]). Any terms Mozilla applies to AMO as a service sit outside those docs. I did not locate or review them, so this is **unverified**.

## 7. Third-party GeckoView browsers with extension support

| Project | Status (checked 2026-11-16) | Extension support, per its own repo |
| --- | --- | --- |
| **Iceraven** ([GitHub][iceraven]) | Active. Last push 2026-11-15, release `iceraven-2.53.0` on 2026-11-15. | Fork of Fenix and Android Components. The README says it lets users attempt to install "a much longer list of add-ons", queried from AMO collection `16201230/What-I-want-on-Fenix`, and warns "Most of them will not work". The README also says it ships "the stable version of GeckoView", so AMO signing applies. |
| **IronFox** ([GitLab][ironfox]) | Active. Last activity 2026-11-11. | Describes itself as a fork of Mull. Bundles uBlock Origin by installing AMO's signed `latest.xpi` at onboarding ([IFAddonUtils.kt][ironfox-addonutils]). Turns off AMO featured-collection fetching ([patch][ironfox-nocollections]). Ships `xpinstall.enabled` off by default with a UI toggle ([ironfox.cfg][ironfox-cfg]). Patches AMO installs to work without `mozAddonManager` ([patch][ironfox-nomozam]). |
| **Fennec F-Droid** ([GitLab][fennec]) | Active. Last commit 2026-11-10. | Builds Fenix and GeckoView from source. Replaces GeckoView's `play-services-fido` dependency with microG's client library ([prebuild.sh][fennec-prebuild-gms], [gecko-liberate.patch][fennec-liberate]) and turns off telemetry in the Fenix build (`/TELEMETRY/s/true/false/`, [prebuild.sh][fennec-prebuild-telemetry]). Its scripts do not change add-on signing. *(I searched `prebuild.sh` for signing and xpinstall settings and found none.)* Add-on support is Fenix's. |
| **Mull** ([GitLab][mull]) | Inactive. Last repo activity 2024-12-10. | A Fennec F-Droid-derived Fenix build. IronFox presents itself as continuing Mull. I found no discontinuation notice in the Mull repo itself, so the status is inferred from inactivity. |

**What these examples share.** Every working example above is a Fenix fork that reuses Android Components. I did not find, and so cannot cite, a GeckoView browser that built extension support from scratch without AC. Mozilla's `geckoview_example` is the only non-AC reference implementation I found ([source][gv-example]).

## 8. Practical costs for this project

**APK size.** Mozilla Maven content lengths for GeckoView release `160.0.20261104181552`, checked 2026-11-16:

| Artifact | Size |
| --- | --- |
| `geckoview-arm64-v8a` AAR | 91,494,920 bytes (about 87 MiB) |
| Multi-ABI `geckoview` AAR | 244,581,941 bytes (about 233 MiB) |

Per-ABI splits are mandatory in practice. I did not measure the size added to a final APK. ADR 0001's "60–100 MB" estimate is in line with the arm64 AAR.

**Engine updates.**

- Security fixes ship with each 4-week major release. API breaks follow the 3-release deprecation policy ([breaking-changes][breaking]).
- The embedder updates the dependency at least every 4 weeks, and also rewrites prompt and delegate code when signatures change (section 1).

**Extension updates.** The app must schedule `update()` itself (section 6). Update checks go to the extension's `update_url`, which is AMO for listed add-ons ([design doc][design-mgmt]). AMO's update endpoint is `versioncheck.addons.mozilla.org`. The query string carries the add-on ID and version, app ID and version, OS, ABI and locale ([geckoview-prefs.js][gv-prefs-update]).

**AMO usage.**

- **Signing:** every user-installable extension depends on AMO signing.
- **Discovery:** an in-app catalog depends on AMO's public API (v4 frozen, v5 changing).
- **AMO account:** a custom catalog needs an AMO collection, which needs an AMO account to create. *(Inferred: the collection endpoints in the docs belong to user accounts.)*
- **Pref defaults:** `extensions.getAddons.*` defaults also point to `services.addons.mozilla.org` ([geckoview-prefs.js][gv-prefs-update]).

**Blocklist.** `extensions.blocklist.enabled` is `true` by default ([all.js][all-js-blocklist]). The add-on blocklist comes from Mozilla's Remote Settings service (`RemoteSettings("addons")`, [Blocklist.sys.mjs][blocklist-rs]). This is a periodic request to a Mozilla server, not telemetry. Turning it off would mean keeping known-malicious add-ons enabled.

**Conflicts with ADR 0002 ("no proprietary dependencies, telemetry or API keys").**

1. **Google Play Services.** The official GeckoView AAR POM declares `com.google.android.gms:play-services-fido:21.3.1` ([build.gradle][gv-build-fido], [Maven POM][mvn-pom]). This conflicts with ADR 0002 and with F-Droid eligibility. The proven fix is Fennec F-Droid's: build GeckoView from source with microG's FIDO client. Excluding the dependency in Gradle is another option, but I did not test what breaks; WebAuthn presumably would.
2. **Glean telemetry.** Gecko records add-on metrics through Glean, including a daily `addons` ping timer registered on all platforms ([extensions.manifest][am-manifest]). The `geckoview-omni` artifact bundles the Glean native library ([architecture doc][gv-arch-omni]), and AC's `browser-engine-gecko` depends on the omni variant. **Unverified:** I could not find a primary source saying whether GeckoView uploads anything when the embedding app never initialises the Glean SDK. Check this with a network capture before relying on it.
3. **API keys.** None of the extension APIs described here need an API key. AMO's public read endpoints and Remote Settings need none.
4. **Third-party network services.** These Mozilla services are required for a working extension ecosystem:
   - AMO for signing, listings and updates
   - Remote Settings for the blocklist

   ADR 0002 already requires a new ADR for any feature that depends on a hosted service.

**Interaction with the Profile model (not researched in depth).** GeckoView installs extensions per `GeckoRuntime`, and Android allows only one runtime per process. Extensions would therefore be shared across all JRNY Profiles, not per-Profile. *(Inferred from `WebExtensionController` being runtime-scoped. Not researched further.)*

## Claims I could not verify

- Whether Mozilla's release GeckoView AAR rejects an unsigned `.xpi` in practice. This is inferred from `toolkit/moz.configure` and `AddonSettings.sys.mjs`, not tested.
- How AMO's website install button behaves in a GeckoView app that leaves `extensionsWebAPIEnabled` off.
- Whether GeckoView sends any Glean data when the embedder does not initialise Glean.
- Whether Fenix release builds ever exposed "Custom add-on collection". Current source limits it to Nightly, Beta and Debug.
- Any AMO terms of service or rate limits that apply to third-party API clients.
- The exact APK size added by GeckoView after R8 and resource shrinking.
- Whether any non-Fenix GeckoView browser ships extension support.

## Sources

All source-code links are pinned to `mozilla-firefox/firefox` commit `f898416c560977636760a1fdab1d3f468cb62486` (2026-11-15), so they keep pointing at the code these notes describe.

**GeckoView API and docs**
- [GeckoView javadoc: WebExtensionController (162.0a1)][jd-wec]
- [GeckoView javadoc: WebExtension][jd-we]
- [GeckoView `api.txt`][api-txt]
- [GeckoView API changelog][changelog]
- [`WebExtensionController.java`][wec-src]
- [`WebExtension.java`, SignedStateFlags][we-signedstate]
- [`GeckoRuntimeSettings.java`: `extensionsWebAPIEnabled`][gvrs-webapi], [`configFilePath`][gvrs-config]
- [`GeckoRuntime.java`: config file loading][gv-runtime-config]
- [`GeckoPreferenceController.java`][gv-prefctl]
- [GeckoView consumer guide: Interacting with Web content and WebExtensions][gv-consumer-ext]
- [GeckoView design doc: Extension Managing API (Nov 2019)][design-mgmt]
- [GeckoView design doc: Breaking changes / deprecation policy][breaking]
- [GeckoView architecture: Lite and Omni builds][gv-arch-omni]
- [`geckoview_example` GeckoViewActivity.java][gv-example]

**Gecko internals**
- [`GeckoViewWebExtension.sys.mjs`][gvwe-builtin]
- [`ext-android.json`][ext-android-json], [`ext-toolkit.json`][ext-toolkit-json], [`ext-browser.json`][ext-browser-json], [mobile `jar.mn`][gv-ext-jar]
- [`Extension.sys.mjs`: Android privileged permissions][ext-privperms]
- [`ExtensionParent.sys.mjs`: `openNative`][ext-opennative]
- [`AddonSettings.sys.mjs`][addonsettings]
- [`toolkit/moz.configure`: `require_signing`][moz-configure-signing]
- [`geckoview-prefs.js`: signatures][gv-prefs-sig], [whitelist][gv-prefs-whitelist], [update URLs][gv-prefs-update]
- [`amContentHandler.sys.mjs`][am-content-handler]
- [`AddonManager.sys.mjs`: web install blocking][am-blocked]
- [`AddonManagerWebAPI.cpp`: host check][am-webapi-host], [`AddonManager.webidl`][am-webidl]
- [`extensions.manifest`][am-manifest]
- [`XPIProvider.sys.mjs`: `isPrivileged`][xpi-privileged]
- [`Blocklist.sys.mjs`][blocklist-rs], [`all.js`: blocklist pref][all-js-blocklist]
- [`browser/app/profile/firefox.js`][ff-js-postdl]
- [`geckoview/build.gradle`: play-services-fido][gv-build-fido]

**Android Components and Fenix**
- [Android Components README][ac-readme]
- [`concept-engine` webextension package][ac-concept-webext]
- [`GeckoEngine.kt` PromptDelegate][ac-geckoengine-prompt]
- [`AMOAddonsProvider.kt`][ac-amo-provider]
- [`AddonFilePicker.kt`][ac-filepicker]
- [`AddonUpdater.kt`][ac-addon-updater]
- [Fenix `Components.kt`: addonsProvider][fenix-components-provider], [addonUpdater][fenix-components-updater]
- [Fenix `FeatureFlags.kt`][fenix-featureflags]
- [Fenix `SettingsFragment.kt`][fenix-settings-file]
- [Fenix `GeckoProvider.kt`][fenix-geckoprovider]
- [Fenix `build.gradle` AMO collection][fenix-gradle-amo]

**Maven (checked 2026-11-16)**
- [geckoview maven-metadata][mvn-release], [geckoview-beta][mvn-beta], [geckoview-nightly][mvn-nightly]
- [geckoview 160 POM][mvn-pom]
- [Android Components on maven.mozilla.org][mvn-ac]

**AMO, Extension Workshop, MDN**
- [addons-server API overview][amo-api-overview]
- [addons-server Collections API][amo-api-collections]
- [Extension Workshop: Signing and distribution overview][ew-signing]
- [Extension Workshop: Distributing an add-on yourself][ew-selfdist]
- [Extension Workshop: Installing self-distributed extensions][ew-install-selfdist]
- [Extension Workshop: Developing extensions for Firefox for Android][ew-android]
- [MDN browser-compat-data, `webextensions/api` (commit 3e04f2c)][bcd-root], [runtime.json][bcd-runtime], [downloads.json][bcd-downloads]

**Third-party browsers**
- [Iceraven README][iceraven]
- [IronFox repository][ironfox], [`IFAddonUtils.kt`][ironfox-addonutils], [`a-c-disable-amo-collections.patch`][ironfox-nocollections], [`gecko-support-addon-installation-without-mozaddonmanager.patch`][ironfox-nomozam], [`ironfox.cfg`][ironfox-cfg]
- [Fennec F-Droid build scripts][fennec], [`prebuild.sh`][fennec-prebuild-gms], [`gecko-liberate.patch`][fennec-liberate]
- [Mull build scripts][mull]

[gh-root]: https://github.com/mozilla-firefox/firefox/tree/f898416c560977636760a1fdab1d3f468cb62486
[api-txt]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/api.txt
[changelog]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/doc-files/CHANGELOG.md
[jd-wec]: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html
[jd-we]: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.html
[wec-src]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java
[wec-prompt]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L255
[wec-install]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L620-L672
[wec-builtin]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L820-L852
[wec-uninstall]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L890-L909
[wec-enable]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L944-L990
[wec-list]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L1002-L1018
[wec-update]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L1020-L1052
[wec-tabactive]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtensionController.java#L1651
[we-signedstate]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/WebExtension.java#L1825
[gvrs-webapi]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntimeSettings.java#L563-L572
[gvrs-config]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntimeSettings.java#L77-L88
[gv-runtime-config]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntime.java#L511-L535
[gv-prefctl]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoPreferenceController.java#L24-L31
[gv-consumer-ext]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html
[design-mgmt]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/managing-extensions.html
[breaking]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/breaking-changes.html
[gv-arch-omni]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html#lite-and-omni-builds
[gv-example]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview_example/src/main/java/org/mozilla/geckoview_example/GeckoViewActivity.java#L1525-L1540
[gv-example-tabactive]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview_example/src/main/java/org/mozilla/geckoview_example/GeckoViewActivity.java#L1965-L1987
[gvwe-builtin]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/shared/modules/geckoview/GeckoViewWebExtension.sys.mjs#L966-L1008
[gvwe-validate]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/shared/modules/geckoview/GeckoViewWebExtension.sys.mjs#L1160-L1180
[ext-android-json]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/shared/components/extensions/ext-android.json
[ext-toolkit-json]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/components/extensions/ext-toolkit.json
[ext-browser-json]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/browser/components/extensions/ext-browser.json
[gv-ext-jar]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/shared/components/extensions/jar.mn#L14
[ext-privperms]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/components/extensions/Extension.sys.mjs#L189-L208
[ext-opennative]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/components/extensions/ExtensionParent.sys.mjs#L264-L278
[addonsettings]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/internal/AddonSettings.sys.mjs#L32-L48
[moz-configure-signing]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/moz.configure#L2079-L2100
[gv-prefs-sig]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/app/geckoview-prefs.js#L381
[gv-prefs-whitelist]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/app/geckoview-prefs.js#L383-L384
[gv-prefs-update]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/app/geckoview-prefs.js#L204-L233
[am-content-handler]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/amContentHandler.sys.mjs#L35-L125
[am-blocked]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/AddonManager.sys.mjs#L2590-L2625
[ff-js-postdl]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/browser/app/profile/firefox.js#L36
[am-webapi-host]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/AddonManagerWebAPI.cpp#L33-L56
[am-webidl]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/dom/webidl/AddonManager.webidl
[am-manifest]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/extensions.manifest
[xpi-privileged]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/internal/XPIProvider.sys.mjs#L605-L612
[blocklist-rs]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/toolkit/mozapps/extensions/Blocklist.sys.mjs#L763
[all-js-blocklist]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/modules/libpref/init/all.js#L1743
[gv-build-fido]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/geckoview/build.gradle#L196
[ac-readme]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/README.md
[ac-concept-webext]: https://github.com/mozilla-firefox/firefox/tree/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/components/concept/engine/src/main/java/mozilla/components/concept/engine/webextension
[ac-geckoengine-prompt]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/components/browser/engine-gecko/src/main/java/mozilla/components/browser/engine/gecko/GeckoEngine.kt#L491
[ac-amo-provider]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/components/feature/addons/src/main/java/mozilla/components/feature/addons/amo/AMOAddonsProvider.kt#L42-L76
[ac-filepicker]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/components/feature/addons/src/main/java/mozilla/components/feature/addons/ui/AddonFilePicker.kt
[ac-addon-updater]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/android-components/components/feature/addons/src/main/java/mozilla/components/feature/addons/update/AddonUpdater.kt#L130-L160
[fenix-components-provider]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/components/Components.kt#L199-L224
[fenix-components-updater]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/components/Components.kt#L226-L229
[fenix-featureflags]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/FeatureFlags.kt#L14
[fenix-settings-file]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/settings/SettingsFragment.kt#L746-L902
[fenix-geckoprovider]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/gecko/GeckoProvider.kt#L134
[fenix-gradle-amo]: https://github.com/mozilla-firefox/firefox/blob/f898416c560977636760a1fdab1d3f468cb62486/mobile/android/fenix/app/build.gradle#L65-L68
[mvn-release]: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml
[mvn-beta]: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-beta/maven-metadata.xml
[mvn-nightly]: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-nightly/maven-metadata.xml
[mvn-pom]: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/160.0.20261104181552/geckoview-160.0.20261104181552.pom
[mvn-ac]: https://maven.mozilla.org/maven2/org/mozilla/components/
[amo-api-overview]: https://mozilla.github.io/addons-server/topics/api/overview.html
[amo-api-collections]: https://mozilla.github.io/addons-server/topics/api/collections.html
[ew-signing]: https://extensionworkshop.com/documentation/publish/signing-and-distribution-overview/
[ew-selfdist]: https://extensionworkshop.com/documentation/publish/self-distribution/
[ew-install-selfdist]: https://extensionworkshop.com/documentation/publish/install-self-distributed/
[ew-android]: https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/
[bcd-root]: https://github.com/mdn/browser-compat-data/tree/3e04f2cccb57c3857f3ff946635edff497437e26/webextensions/api
[bcd-runtime]: https://github.com/mdn/browser-compat-data/blob/3e04f2cccb57c3857f3ff946635edff497437e26/webextensions/api/runtime.json
[bcd-downloads]: https://github.com/mdn/browser-compat-data/blob/3e04f2cccb57c3857f3ff946635edff497437e26/webextensions/api/downloads.json
[iceraven]: https://github.com/fork-maintainers/iceraven-browser/blob/d325cfb7546bd62dccc817db53d3fc831cad065d/README.md
[ironfox]: https://gitlab.com/ironfox-oss/IronFox/-/tree/b9197ebc40d0f0f32a982faa70280ab78a59fe42
[ironfox-addonutils]: https://gitlab.com/ironfox-oss/IronFox/-/blob/b9197ebc40d0f0f32a982faa70280ab78a59fe42/patches/fenix-overlay/app/src/main/java/org/ironfoxoss/ironfox/utils/IFAddonUtils.kt#L20-L50
[ironfox-nocollections]: https://gitlab.com/ironfox-oss/IronFox/-/blob/b9197ebc40d0f0f32a982faa70280ab78a59fe42/patches/a-c-disable-amo-collections.patch
[ironfox-nomozam]: https://gitlab.com/ironfox-oss/IronFox/-/blob/b9197ebc40d0f0f32a982faa70280ab78a59fe42/patches/gecko-support-addon-installation-without-mozaddonmanager.patch
[ironfox-cfg]: https://gitlab.com/ironfox-oss/IronFox/-/blob/b9197ebc40d0f0f32a982faa70280ab78a59fe42/configs/phoenix/ironfox.cfg#L96
[fennec]: https://gitlab.com/relan/fennecbuild/-/tree/5b374901e0fd5073ba73f94e30cdcb373b37f5ce
[fennec-prebuild-gms]: https://gitlab.com/relan/fennecbuild/-/blob/5b374901e0fd5073ba73f94e30cdcb373b37f5ce/prebuild.sh#L245-L247
[fennec-prebuild-telemetry]: https://gitlab.com/relan/fennecbuild/-/blob/5b374901e0fd5073ba73f94e30cdcb373b37f5ce/prebuild.sh#L70
[fennec-liberate]: https://gitlab.com/relan/fennecbuild/-/blob/5b374901e0fd5073ba73f94e30cdcb373b37f5ce/gecko-liberate.patch
[mull]: https://gitlab.com/divested-mobile/mull-fenix
