---
status: accepted
---

# Each Profile's web data store is named after its Profile ID

Each Profile keeps its web data in its own store, named `jrny-profile-<Profile ID>`: since [ADR 0006](0006-geckoview-as-engine.md), a GeckoView session context with that id. Its cookies, site storage, cache and service workers all live in that store, and the name is how the engine finds them again on disk. A Profile ID therefore never changes and is never reused. Renaming a Profile changes only its display name. Changing the store name later would orphan every sign-in in every Profile, so this is hard to reverse.

## Considered Options

- **Use the Profile's display name.** We rejected it because people rename Profiles, and a rename would sign them out of every Account.
- **Use the engine's default store for the first Profile.** We rejected it because anything loaded outside a Profile, such as a library's own web view, could then see that Profile's data. No Profile uses the default store.

## Consequences

- Profiles live in the Room table `profiles`, whose id is AUTOINCREMENT, so a deleted Profile's ID is never given to a new one. The first Profile, created on first launch, has the ID `1` and the store `jrny-profile-1`, as in Milestone 1, so the sign-ins made before Profiles moved to the database are kept.
- Deleting a Profile deletes its store by this name: at once, and again at the next cold start (ADR 0006).
