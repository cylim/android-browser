# JRNY browser

JRNY browser is an Android web browser that keeps several separate browsing identities on one device, so a person can be signed in to different accounts on the same site at the same time.

## Language

### Profiles and accounts

**Profile**:
An isolated browsing identity with its own web data, Tabs, history, bookmarks and downloads. Nothing in one Profile is visible from another.
_Avoid_: Space, container, persona, user

**Account**:
A person's sign-in on a website. An Account lives inside exactly one Profile, and the same site can hold a different Account in each Profile.
_Avoid_: Profile, login, user

**Active Profile**:
The Profile the person is browsing in, or was browsing in when they last left the app. Incoming Links open here. There is always exactly one.
_Avoid_: Default Profile, current profile, selected profile

### Tabs

**Tab**:
A single open page, owned by exactly one Profile.

**Private Tab**:
A Tab whose web data is discarded once the last Private Tab of its Profile closes, and which never writes to history.
_Avoid_: Incognito tab

**Move**:
Transferring a Tab to another Profile, carrying only its current address. The page reloads under the target Profile's Accounts, and the original Tab is closed.
_Avoid_: Transfer, send

**Open in Profile**:
Opening a Tab's current address as a new Tab in another Profile while keeping the original.
_Avoid_: Duplicate, copy

### Links

**Incoming Link**:
A link that another app hands to JRNY browser. It opens as a normal Tab in the Active Profile, never as a Private Tab.
_Avoid_: External link, intent

**Site Rule** (planned):
A person's rule that sends Incoming Links for matching addresses to a specific Profile instead of the Active Profile.
_Avoid_: AirControl, Air Traffic Control, routing rule

### Layout

**Sidebar**:
The wide-window layout: a vertical list of the Active Profile's Tabs, with the Profile switcher at its bottom.
_Avoid_: Drawer, tab strip

**Rail**:
The collapsed Sidebar: a narrow column with back, forward, reload and the Active Profile's avatar, so the page gets almost the full width.
_Avoid_: Mini sidebar, collapsed drawer

**Bottom Bar**:
The narrow-window layout: an address and search field along the bottom edge, with the Active Profile's avatar for switching Profiles.
_Avoid_: Toolbar, omnibox

**Tab Overview** (planned):
The Bottom Bar's full-screen view of all the Active Profile's Tabs, opened from a button on the bar.
_Avoid_: Tab switcher, tab grid
