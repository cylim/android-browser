# Okapi

Okapi, a browser by JRNY, is an Android web browser that keeps several separate browsing identities on one device, so a person can be signed in to different accounts on the same site at the same time.

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

**Start Page**:
What a Tab shows while it has no web page: the Profile's Pinned Tabs and recent pages, or, in a Private Tab, what a Private Tab keeps and discards. It belongs to the Tab's Profile and is drawn by the browser, not loaded from the web.
_Avoid_: New Tab Page, home page, speed dial

**Private Tab**:
A Tab whose web data is discarded once the last Private Tab of its Profile closes, and which never writes to history.
_Avoid_: Incognito tab

**Bookmark**:
An address a person saved in a Profile, with a name, at the top level or in one Folder. A Profile's bookmarks are its Pinned Tabs.
_Avoid_: Favorite, shortcut

**Pinned Tab**:
A bookmark as the Tab list shows it, under "Pinned" above the Profile's other Tabs. It is open while a Tab shows it and closed otherwise. Closing its Tab keeps the bookmark.
_Avoid_: Favorite, pinned site, essential

**Folder**:
A named group of a Profile's bookmarks, which the Tab list can show or hide. Folders don't nest.
_Avoid_: Group, collection

**Move**:
Transferring a Tab to another Profile, carrying only its current address. The page reloads under the target Profile's Accounts, and the original Tab is closed.
_Avoid_: Transfer, send

**Open in Profile**:
Opening a Tab's current address as a new Tab in another Profile while keeping the original.
_Avoid_: Duplicate, copy

**Backup**:
A file the person exports from Okapi with every Profile's bookmarks, history, open Tabs, Site Rules and Site settings, the app settings and the person's changes to the Link Wrappers, but no web data, so no Account. Importing one either merges it, adding only what is missing, or replaces every Profile on the device with its own.
_Avoid_: Sync, export (as a noun), archive

### Links

**Incoming Link**:
A link that another app hands to Okapi. It opens as a normal Tab in the Active Profile, or in the Profile a Site Rule names, and never as a Private Tab.
_Avoid_: External link, intent

**Handoff**:
Passing a link a page opens to another app, such as a `tel:` link to the phone app. Only the link goes, never the Profile or its cookies.
_Avoid_: Outgoing link, external app

**Home Screen Shortcut**:
A page pinned to the launcher from a Profile. It opens in that Profile, whatever the Active Profile is, and stops working when the Profile is deleted.
_Avoid_: Bookmark, web app, PWA

**Site Rule**:
A person's rule that sends Incoming Links for matching addresses to a specific Profile instead of the Active Profile. It names a site, optionally with a path, and `*` stands for anything.
_Avoid_: AirControl, Air Traffic Control, routing rule

**Link Wrapper**:
A site whose links carry the address they pass the person on to in one of their query parameters, such as Outlook's Safe Links (`?url=`). A site has one Link Wrapper, which can name several parameters. A Site Rule judges a link through a Link Wrapper by the address it carries. Okapi has a default list; it keeps only the person's changes to it, which "Use default list" undoes.
_Avoid_: Redirector, tracker, unwrapper, link shortener

### Sites

**Site settings**:
What a Profile remembers about each site: the permissions it was given or refused (camera, microphone, location) and whether it gets its desktop version. Each Profile has its own.
_Avoid_: Site permissions (for the whole list), content settings

**Extension**:
A program from addons.mozilla.org, signed by Mozilla, that the engine runs for every Profile at once. It can see and change the pages that its permissions allow, in every Profile, and in Private Tabs only when the person allows it.
_Avoid_: Add-on (except when naming addons.mozilla.org), plugin

**Content blocker**:
uBlock Origin, the Extension Okapi installs by default, which stops pages from loading what its filter lists name, such as ads and trackers. Its settings and trusted sites apply in every Profile.
_Avoid_: Ad blocker, tracking protection (the engine's own feature, which is off)

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

**Tab Overview**:
The Bottom Bar's full-screen view of all the Active Profile's Tabs, opened from a button on the bar.
_Avoid_: Tab switcher, tab grid
