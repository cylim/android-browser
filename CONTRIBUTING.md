# Contributing to JRNY browser

Thank you for helping. This project is built in public, and contributions of every size are welcome: bug reports, ideas, documentation and code.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- Read [SPEC.md](docs/SPEC.md) for what v1 does and [ROADMAP.md](docs/ROADMAP.md) for what comes next.
- Use the words in [GLOSSARY.md](GLOSSARY.md) in code, issues and pull requests. For example, write **Profile**, not "space" or "container", and **Move**, not "transfer".
- For anything larger than a small fix, open an issue first so we can agree on the approach before you write code.

## Security comes first

This is a public repository for a browser that holds people's signed-in sessions.

- **Report vulnerabilities privately.** Follow [SECURITY.md](SECURITY.md). Never use a public issue.
- **Never commit secrets.** That includes keystores (`*.jks`, `*.keystore`), `keystore.properties`, `local.properties`, `.env` files, API keys and tokens. `.gitignore` covers the common cases, but you are still responsible for what you commit.
- **Never commit personal data.** Logs, HAR files, heap dumps (`*.hprof`), screenshots and screen recordings can contain cookies, addresses or account names. Redact them before you attach them to an issue or pull request.
- **No new network services, SDKs or API keys** without an accepted ADR. See [ADR 0002](docs/adr/0002-no-proprietary-dependencies-or-telemetry.md).

### Install the secret-scanning hook

The repository includes a pre-commit hook that runs [gitleaks](https://github.com/gitleaks/gitleaks) on staged changes.

1. Install gitleaks 8.19 or later. See the gitleaks README for install options.
2. Turn on the repository hooks:

   ```sh
   git config core.hooksPath .githooks
   ```

CI runs the same scan on every pull request, so a leak that gets past your machine still blocks the merge. If gitleaks reports a false positive, explain it in your pull request. Don't add an allowlist entry yourself.

## Making a change

1. Fork the repository and create a branch from `main`.
2. Make your change. Keep each pull request focused on one thing.
3. Add or update tests for the behavior you changed.
4. If your change alters a decision recorded in [docs/adr](docs/adr/), or makes a new hard-to-reverse one, add an ADR in the same pull request.
5. If you introduce or redefine a project term, update [GLOSSARY.md](GLOSSARY.md).
6. Open a pull request and complete the template.

`main` is protected. Every change, including the maintainer's, goes through a pull request and needs CI to pass.

## Development setup

You need:

- JDK 21.
- The Android SDK with platform 37 (`platforms;android-37.0`).
- Two emulators or devices with Android 15 (API 35) or later and an up-to-date Android System WebView: a **phone** and a **tablet**. Every UI change is checked on both, because the app has a different layout for each (the Bottom Bar and the Sidebar). For Android 17 images, create the emulators with Command-line Tools 22 or later. Older `avdmanager` writes `target=android-0` into the emulator's `.ini` file, and the emulator then crashes in a loop:

  ```sh
  sdkmanager --install "system-images;android-37.0;google_apis_playstore;x86_64" "emulator"
  avdmanager create avd -n JRNY_Phone_API_37 -d pixel_7 -k "system-images;android-37.0;google_apis_playstore;x86_64"
  avdmanager create avd -n JRNY_Tablet_API_37 -d pixel_tablet -k "system-images;android-37.0;google_apis_playstore;x86_64"
  ```

Point Gradle at your SDK. `local.properties` is gitignored:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Build, lint and run the unit tests, the same as CI:

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Install the app and run the device tests on the phone, then on the tablet. CI does the same on every pull request. Run them on **one** device at a time: without `ANDROID_SERIAL`, `installDebug` and `connectedDebugAndroidTest` use every connected device and emulator.

```sh
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest
```

## Licensing of contributions

This project is licensed under the [MIT License](LICENSE). By submitting a contribution you agree that it is licensed under the same terms. No separate agreement or sign-off is needed.
