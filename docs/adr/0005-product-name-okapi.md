---
status: accepted
---

# The browser is called Okapi, by JRNY

The browser is called Okapi. JRNY stays the maker's name, so the full form is "Okapi, a browser by JRNY". Until now the launcher showed the maker's name followed by the generic word "browser". That names the maker, not this product, and it leaves no name for a later JRNY product.

A first round screened Lapis, Rupa, Ilka and other candidates, and the owner chose Pangolin. Pangolin was dropped the same day. Google Play already lists a "Pangolin Browser" (`com.Vsl.vbrowser`), and Fossorial ships a VPN and secure-access app called Pangolin. A second round screened 22 animal names for clashes on Google Play.

The owner chose Okapi from that round. It's short, and people say it the same way in most languages. The okapi is a rare, elusive forest animal of the DR Congo, related to the giraffe, and one of the last large mammals known to science. No browser or privacy app on Google Play uses the name.

## Considered Options

- **Takin or Shoebill.** The second round found no clash for these either. The owner chose Okapi over them.
- **Wombat.** Rejected: a crypto wallet with a dApp browser on Google Play already uses the name.
- **Pangolin.** Chosen first, then dropped for the clashes above.
- **Keep the maker's name plus "browser" as the app name.** We rejected it because JRNY names the maker. A later JRNY product would have no room, and "browser" alone says nothing about this one.
- **Change the application ID to match the new name.** We rejected it. Android treats a new application ID as a different app, so an installed copy wouldn't update to it. A person would have to uninstall first, and that erases every Profile, Tab, bookmark and sign-in, because Android backup leaves them all out. Google Play also never lets an app change its package name after the first upload.

## Consequences

- The display name is "Okapi". `app_name` in `android/app/src/main/res/values/strings.xml` sets it, and the launcher shows it.
- The Google Play title will be "Okapi Browser by JRNY", 21 of the 30 characters Play allows. "Browser" is capitalized there only, because store search matches on it. Everywhere else the prose says "Okapi", and "Okapi, a browser by JRNY" on first mention.
- Play search mixes "Okapi browser" with the many "OK Browser" apps. So store text always pairs the name with JRNY.
- The application ID stays `app.jrny.browser`. So do the package `app.jrny.browser`, the Kotlin class names such as `JrnyApplication` and `JrnyDatabase`, the Profile web data store names (ADR 0004), the GitHub repository and the website's address. People don't see these, and changing them would break installed copies or links for no gain.
- Sites still receive `app.jrny.browser` in WebView's `X-Requested-With` header. It names the maker, so it stays accurate.
- The trademark search isn't done yet. Before the Google Play release, search for "Okapi" in Nice classes 9 (software) and 42 (software services) at the USPTO, the EUIPO, WIPO's Global Brand Database and MyIPO. If a conflicting mark turns up, rename before the release, while only the display name and the listing have to change.
