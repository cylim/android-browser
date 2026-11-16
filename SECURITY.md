# Security policy

JRNY browser handles people's signed-in sessions, so we treat security reports as our highest priority.

## Reporting a vulnerability

**Do not open a public issue, discussion or pull request for a security problem.**

Report it privately through GitHub:

1. Go to the repository's [Security tab](https://github.com/cylim/android-browser/security).
2. Select **Report a vulnerability**.
3. Describe the problem, the affected version, and the steps to reproduce it.

You can expect:

- an acknowledgement within 7 days,
- an assessment and a planned fix date within 30 days,
- credit in the release notes, unless you prefer to stay anonymous.

## What is in scope

- Data leaking between Profiles: cookies, storage, history, Tabs or downloads that are visible from another Profile.
- Private Tab data that survives after it should have been discarded.
- Session data that leaves the device, for example through backups or logs.
- Malicious links or apps that make JRNY browser open content in a Profile the person did not choose.
- Secrets or signing material committed to this repository.

Bugs in Android System WebView itself should be reported to the [Chromium project](https://issues.chromium.org/). Tell us as well if JRNY browser makes the bug worse.

## Supported versions

Only the latest release receives security fixes.
