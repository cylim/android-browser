# Okapi website

The product page for Okapi, a browser by JRNY. It is a static site with no JavaScript, no dependencies and no requests to other servers. GitHub Pages publishes it.

| Path | What it is |
| --- | --- |
| `public/index.html`, `public/styles.css`, `public/assets/` | The page. Its paths are relative, so it works from any folder. |
| `public/404.html` | Shown for any address that has no file. Its paths start with `__BASE_PATH__`, because it can be shown at any depth. |
| `build.py` | Checks the site and copies it, with `__BASE_PATH__` filled in, into a folder ready to publish. Python 3.9+ standard library only. |

## Preview it locally

```sh
python3 website/build.py --out website/_site
python3 -m http.server 8000 --directory website/_site
```

Then open <http://localhost:8000/>. `_site/` is gitignored.

## Publish

[`.github/workflows/pages.yml`](../.github/workflows/pages.yml) publishes the site whenever a push to `main` changes `website/`. You can also run it by hand from the Actions tab ("Pages" → "Run workflow").

One-time setup: in the repository's **Settings** → **Pages** → **Build and deployment**, set **Source** to **GitHub Actions**.

The site goes live at `https://cy.my/android-browser/`. GitHub serves a repository's Pages under the domain of the owner's user site, which is `cy.my`. To give it a domain of its own instead:

1. Add a DNS record for the domain. For a subdomain such as `browser.jrny.app`, that's a `CNAME` record pointing to `cylim.github.io`.
2. Enter the domain under **Settings** → **Pages** → **Custom domain**, and turn on **Enforce HTTPS** once the certificate is issued.

The workflow reads the site's path from GitHub, so the 404 page keeps working when the domain changes.

## Checks

`python3 website/build.py` fails when a page or stylesheet refers to another server, when a relative link or asset points to a file that doesn't exist, or when a path starts at the host's root without `__BASE_PATH__`. CI runs it on every pull request that changes `website/`, and the Pages workflow runs it again before publishing.

The "Website" CI check isn't one of `main`'s required checks. To stop a failing site from being merged, add it under **Settings** → **Branches**. A pull request that doesn't touch `website/` skips the job, and a skipped job counts as passing.

## Security

GitHub Pages can't send custom headers. Each page sets its Content Security Policy in a `<meta>` tag instead: only the site's own files load. A `<meta>` policy can't stop other sites from framing the page, and the page has no forms or scripts to abuse in a frame.

## Keep it true

The page only says what the repository's documents support: [README.md](../README.md), [docs/SPEC.md](../docs/SPEC.md), [docs/ROADMAP.md](../docs/ROADMAP.md), [GLOSSARY.md](../GLOSSARY.md) and [docs/adr](../docs/adr/). The changelog section mirrors [CHANGELOG.md](../CHANGELOG.md). Update both together.

Don't add fonts, scripts, analytics or embeds from other servers. Links to this repository on GitHub are the only outside addresses on the page.
