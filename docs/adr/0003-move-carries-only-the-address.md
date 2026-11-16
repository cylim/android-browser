---
status: accepted
---

# Moving a Tab carries only its current address

When a Tab is moved to another Profile, only its current address goes with it. The page reloads under the target Profile's Accounts, and the back/forward history stays behind and is discarded with the original Tab. That history belongs to the source Profile. Carrying it over would leak browsing from one Profile into another, which is the opposite of what Profiles are for. Unsaved form input and pages reached by a form submission (POST) are lost, and the UI says so before the Move.

## Considered Options

- **Carry the back/forward history.** We rejected it because it leaks history between Profiles, and WebView cannot restore one WebView's history into another reliably anyway.
- **Copy instead of move.** We kept this as a separate action, Open in Profile.
