# Security policy

Okapi, a browser by JRNY, handles people's signed-in sessions, so we treat security reports as our highest priority.

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
- Malicious links or apps that make Okapi open content in a Profile the person did not choose.
- Secrets or signing material committed to this repository.

Bugs in GeckoView or Gecko itself should be reported to [Mozilla](https://www.mozilla.org/security/bug-bounty/) through [Bugzilla](https://bugzilla.mozilla.org/). Tell us as well if Okapi makes the bug worse, or ships a GeckoView with a known security hole.

## Supported versions

Only the latest release receives security fixes.
