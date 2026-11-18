# Changelog

All notable changes to Okapi, a browser by JRNY, are listed here, grouped by version and then by pull request. Terms such as Profile, Tab and Move mean what [GLOSSARY.md](GLOSSARY.md) says they mean.

## 0.1.0 - not released yet

There is no release yet. Version 0.1.0 is what the app builds as today. It ships once Milestone 6 is done: a signed APK built by GitHub Actions and published on [GitHub Releases](https://github.com/cylim/android-browser/releases), and a check that the build meets F-Droid's requirements.

Everything below is new in 0.1.0, in the order it landed. The last four, the Start Page, the name Okapi, WebView usage metrics and the suggestion fix, aren't merged yet.

### Browsing in one Profile (Milestone 1, #1), 2026-11-16

- Load a page, go back and forward, reload, and stop loading.
- The address field loads an address or searches for the text. An address, or text shaped like credentials, is never sent to the search engine.
- Two layouts that follow the window's width: the Sidebar at 840dp and wider, which collapses to the Rail, and the Bottom Bar below that. They switch live when the window resizes.
- Sign-in popups open in the Tab's Profile, under a bar that shows the popup's own address.
- A Tab survives a crash of the page's renderer instead of closing the app.

### Profiles (Milestone 2, #2), 2026-11-16

- Create, switch, rename, recolor and delete Profiles. Each Profile keeps its own cookies, site storage, cache, site permissions and service workers.
- Deleting a Profile asks first and warns that every Account signed in within it will be signed out. The Profile disappears at once, and its web data is wiped at the next cold start. The last Profile can't be deleted.
- In the Sidebar, the Profile switcher sits at the bottom, and swiping across the Tab list switches Profiles. In the Bottom Bar, the Profile's avatar opens the Profile sheet.
- The Sidebar and the Bottom Bar take the Active Profile's color.

### Tabs (Milestone 3, #3), 2026-11-16

- Several Tabs per Profile. After Android kills the app, each Tab comes back with its address, title and scroll position.
- At most 5 Tabs are live at once, across all Profiles. The others reload when you open them.
- The Sidebar lists the Active Profile's Tabs. In the Bottom Bar, the Tab count opens the Tab Overview, and swiping along the bar switches Tabs.

### Move, Open in Profile and Incoming Links (Milestone 4, #4), 2026-11-16

- Move takes a Tab to another Profile. Only its address goes along, and the page reloads there. The Profile picker says what is lost before you choose.
- Open in Profile opens the Tab's address in another Profile and keeps the original Tab.
- A page menu ("More") in the Sidebar, the Rail and the Bottom Bar holds Move, Open in Profile and a new Tab.
- Okapi can be the default browser. An Incoming Link opens as a new Tab in the Active Profile, with a one-tap "Move to Profile…". Only `http` and `https` links are accepted.
- The menu offers "Set as default browser" while Okapi isn't.

### History and bookmarks (Milestone 5, #5), 2026-11-17

- Each Profile keeps its own history and bookmarks.
- History keeps the pages you go to, not reloads, Back, Forward or restored Tabs, and leaves out a redirect's stops on the way. A Profile keeps its latest 5,000 visits.
- Bookmarks and History open in the page's place: beside the Sidebar, or full screen on a phone. History can be cleared after a confirmation.

### Downloads (Milestone 5, #6), 2026-11-17

- A page can only offer a file. It downloads once you agree, in a dialog that names the file, the site and the size.
- Android's download service fetches the file with the Profile's cookies and saves it to the device's Downloads folder.
- Each Profile has its own list of downloads. A finished file opens, and a running one can be cancelled.
- A download that redirects to another site doesn't take the first site's cookies with it.

### Private Tabs (Milestone 5, #7), 2026-11-17

- Each Profile can open Private Tabs. They share one temporary web data store, apart from the Profile's own, and never write to history.
- When the last Private Tab of a Profile closes, its cookies and site storage are cleared. Private Tabs end when the app's window closes and are never restored.
- While a Private Tab shows, screenshots and the recent-apps preview are blocked. In the Tab list it carries a lock.
- A Private Tab can't be Moved or opened in another Profile, and never takes an Incoming Link.

### Settings (Milestone 5, #8), 2026-11-17

- Search engine: Google, Bing, DuckDuckGo or Brave Search.
- Suggestions while you type: pages from the Active Profile's bookmarks and history first, then the search engine's suggestions. Suggestion requests carry no cookies, and a Private Tab offers none.
- Safe Browsing can be turned off, and the change reaches pages that are already open.
- Theme: the device's, light or dark.

### Fixed (#9), 2026-11-17

- On a tablet in landscape, the on-screen keyboard no longer covers the New Profile dialog's buttons.
- With the keyboard up, the Sidebar's suggestions and Tab list end above it and scroll.

### Site Rules (#10), 2026-11-17

- A Site Rule sends Incoming Links for a site, optionally with a path, to the Profile you chose. `*` stands for anything.
- Site Rules live under Settings → Site Rules, where "Check a link" says which Profile a link would open in.
- After you Move an Incoming Link to another Profile, the page offers a rule with one tap ("Always").

### Pinned Tabs and Folders (#11), 2026-11-17

- A Profile's bookmarks are its Pinned Tabs, listed under "Pinned" above its other Tabs, in the Sidebar and the Tab Overview.
- A Pinned Tab is open while a Tab shows it and dimmed otherwise. Closing it keeps the bookmark.
- Folders group Pinned Tabs, one level deep. A Folder can be collapsed, renamed and deleted, and deleting it keeps its bookmarks.

### Styling (#12), 2026-11-17

- Each Profile picks one of 12 colors and a color strength: soft, medium or strong. While it is active, the Sidebar, the Bottom Bar and the Tab Overview are tinted with it, and switching Profiles glides from one color to the next.
- A Profile's avatar shows one of 8 icons or any one emoji.
- "Compact rows" in Settings makes every row of the Tab list one line.

### Site icons (#13), 2026-11-17

- Every row of the Tab list starts with its site's own icon, or the first letter of the site's name until the Profile has shown a page of that site.
- Icons are kept per Profile on the device. A Private Tab's icons are never kept, and clearing a Profile's history removes the icons it no longer needs.

### Drag to reorder Pinned Tabs (#14), 2026-11-17

- Hold a Pinned Tab or a Folder and drag it to a new place. Let a Pinned Tab go on a Folder to put it in, or drag it past its Folder's ends to take it out.
- Held without dragging, a row opens its menu, where "Move up" and "Move down" do the same.

### Start Page (not merged yet)

- A new Tab opens on a Start Page built into the app, with the Profile's Pinned Tabs and recent pages. In a new Private Tab, it says what Private Tabs keep apart and what they erase.

### The name Okapi (not merged yet)

- The browser is now called Okapi, after Pangolin was considered first and dropped. JRNY stays the maker's name. The launcher shows "Okapi", and the Google Play title will be "Okapi Browser by JRNY", always paired with JRNY in store text. The application ID stays `app.jrny.browser`, so an installed copy updates in place and keeps its Profiles. [ADR 0005](docs/adr/0005-product-name-okapi.md) says why.

### WebView usage metrics are turned off (not merged yet)

- Okapi tells Android System WebView not to send its usage statistics to Google, whatever the device's usage and diagnostics setting is. WebView's crash reports still follow that setting and contain no page addresses.

### Fixed: suggestions and passwords typed before the `@` (not merged yet)

- While you type, text with a colon before its first `/`, `?` or `#`, such as `admin:hunter2`, is no longer sent to the search engine for suggestions, because an `@` typed next would make it credentials. Before, a pause before the `@` could send the password.
- A search with an early colon, such as `python: list comprehension`, now gets only bookmark and history suggestions while you type. Go still searches for it.

### Web capabilities (not merged yet)

- A site can ask for the camera, the microphone or your location. Okapi names the site and offers "Allow once", "Always allow" or "Block". Each Profile keeps its own answers, so allowing a site in one Profile doesn't allow it in another. A Private Tab only offers "Allow" and "Block", and forgets them.
- Android asks for its own permission only the first time a site you allowed needs it. If Android refuses, Okapi says so and opens Android's settings for it.
- Settings → Site settings lists the Active Profile's sites with what each may use, where you can allow, block or reset each one, or reset them all.
- File fields work: Android's file picker opens with the kinds of file the page accepts, and a field that takes photos or videos also offers "Take photo" or "Record video".
- A video's fullscreen button fills the screen, hides the system bars and turns the screen to match the video. Back leaves fullscreen.
- A site that asks for a username and password before its page (HTTP sign-in) gets a dialog that a password manager can fill. Okapi keeps nothing typed there.

### Links and files (not merged yet)

- A tapped phone, mail, message or map link opens its app. Any other link for an app, such as `intent:` or `market:`, shows a bar under the page with "Open", and nothing leaves the browser before you tap it. In a Private Tab every such link waits for "Open". Only the link goes to the app.
- Files a page makes itself (`blob:` and `data:` addresses), such as an exported CSV or PDF, are saved to the Downloads folder and listed with the Profile's downloads.
- Hold a link or an image for its menu: Open in new Tab, Open in Private Tab, Open in Profile…, Copy link and Share link; for an image, Open image in new Tab, Save image, Copy image and Share image. A Private Tab opens links only in Private Tabs.
- The page menu starts with a row of icons (Forward on narrow phones, Bookmark, Share, Reload), then Page (Copy link, Print), Profile (Move, Open in Profile) and Browser sections.
- Print opens Android's print dialog, which can also save the page as a PDF.

### Page tools (not merged yet)

- Find in page, from the page menu: it counts the matches and moves between them. On a phone it takes the address bar's place; on a tablet it runs across the top of the page.
- Desktop site, a switch in the page menu, asks a site for its desktop version and lays the page out at desktop width. Each Profile remembers it for the site, and it shows in Site settings. A Private Tab keeps it until it closes.
- Settings → Pages: text size for every page (50% to 200%), "Darken pages without a dark theme" while Okapi's theme is dark, and pull to refresh, which is on by default.

### Known limits

- Passkeys don't work until Okapi is approved as a privileged browser. Password managers work through Android Autofill.
- There is no Custom Tabs service. An app that asks for a Custom Tab gets a normal Tab.
- A site that only answers a real browser may refuse the download service. A page that drops a file it made in the same moment it offers it leaves nothing to save.
- Back/forward history isn't restored after Android kills the app.
- A Move loses unsaved form input and pages reached by submitting a form.
- A deleted Profile's web data stays on the device until the next cold start.
- Sign-in with two Accounts at once is confirmed for Google. Microsoft, GitHub and Apple are untested.
- Android System WebView sends the app's package name with every request (`X-Requested-With`), and this can't be turned off. A site could use it to block sign-in.
- Not in v1: sync, extensions, a built-in password manager, content blocking and a lock per Profile.
