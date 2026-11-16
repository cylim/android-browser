# JRNY browser

An Android browser with separate Profiles, so you can stay signed in to a different account on the same site in each one. For example, work Google in one Profile and personal Google in another.

> **Status:** early development. You can create, switch, edit and delete Profiles, each with its own Tabs and its own sign-ins. Tabs come back after Android kills the app. Each Profile keeps its own history and bookmarks. A Tab can be moved to another Profile or opened in one, and links from other apps open in the Active Profile. This project is built in public. See the [roadmap](docs/ROADMAP.md).

## What it will do

- **Profiles**, each with its own cookies, site data, tabs, history, bookmarks and downloads.
- **Move a tab to another Profile** to open the same page signed in as a different account.
- **Links from other apps** open in the Profile you were last using.
- **Tablet layout** with an Arc-style sidebar of tabs and the Profile switcher at the bottom.
- **Phone layout** with a Safari-style bottom bar for search and switching Profiles.
- **No telemetry**, no Google Play Services, no trackers.

Requires Android 15 or later.

## Documentation

- [SPEC.md](docs/SPEC.md): what v1 does
- [ROADMAP.md](docs/ROADMAP.md): milestones
- [GLOSSARY.md](GLOSSARY.md): the project's vocabulary
- [docs/adr](docs/adr/): architecture decisions and why we made them

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). To report a security problem, follow [SECURITY.md](SECURITY.md). Never open a public issue for one.

## License

[MIT](LICENSE)
