---
status: accepted
---

# Each Profile's web data store is named after its Profile ID

Each Profile keeps its web data in its own androidx.webkit profile, named `jrny-profile-<Profile ID>`. Its cookies, site storage, cache and service workers all live in that store, and the name is how WebView finds them again on disk. A Profile ID therefore never changes and is never reused. Renaming a Profile changes only its display name. Changing the store name later would orphan every sign-in in every Profile, so this is hard to reverse.

## Considered Options

- **Use the Profile's display name.** We rejected it because people rename Profiles, and a rename would sign them out of every Account.
- **Use WebView's built-in "Default" profile for the first Profile.** We rejected it because anything loaded outside a Profile, such as a library's own WebView, could then see that Profile's data. No Profile uses "Default".

## Consequences

- Profiles live in the Room table `profiles`, whose id is AUTOINCREMENT, so a deleted Profile's ID is never given to a new one. The first Profile, created on first launch, has the ID `1` and the store `jrny-profile-1`, as in Milestone 1, so the sign-ins made before Profiles moved to the database are kept.
- Deleting a Profile deletes its store by this name, at the next cold start (ADR 0001).
