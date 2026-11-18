# Okapi brand

Okapi is a browser by JRNY. Its logo is the **peek** mark: the okapi's striped back curls into an "O", and its head peeks over the top right of the ring. The ring stays a whole circle, so it still reads as the O in Okapi.

The mark comes in two drawings of one design:

- **Peek full** (`okapi-peek/`) has four stripes, a slim head and fine detail. Use it at 64 px and up.
- **Peek compact** (`okapi-peek-compact/`) is the same okapi in the same pose and colours, redrawn for small sizes. The ring is thicker, the head is bigger, and it has three bold stripes and a larger eye. Use it below 64 px.

There is one logo. Don't use any other okapi drawing.

## Which drawing to use

| Where | Drawing | File |
| --- | --- | --- |
| Android launcher icon (adaptive) | Compact | `okapi-peek-compact/ic_launcher_foreground.svg` on `#F5EFE4` |
| Android themed icon | Compact | `okapi-peek-compact/ic_launcher_monochrome.svg` |
| Website favicon | Compact | `okapi-peek-compact/favicon.svg`, `favicon-dark.svg`, `favicon-32.png` |
| apple-touch-icon | Compact | `okapi-peek-compact/apple-touch-icon-180.png` |
| Google Play icon (512 px) | Compact | `okapi-peek-compact/icon-512.png` |
| Website header, 64 px tall | Full | `okapi-peek/mark.svg`, `mark-dark.svg` |
| Website hero | Full | `okapi-peek/mark.svg`, `mark-dark.svg` |
| Website footer, 24 px tall | Compact | `okapi-peek-compact/mark.svg`, `mark-dark.svg` |
| README banner | Full | `okapi-peek/lockup-horizontal.svg`, `lockup-horizontal-dark.svg` |
| Social card | Full | `okapi-peek/banner-1200x630.png` |
| Google Play feature graphic | Full | `okapi-peek/mark.svg` or a lockup, placed on a 1024 x 500 canvas |
| Name and logo together | Full | `okapi-peek/lockup-*.svg` |

The Play icon uses the compact drawing even though the file is 512 px. The store shows it at about 48 to 96 px, so the full drawing's detail would be lost.

## Minimum sizes

- Peek full: 64 px across the mark's square. Below that the eye glint is smaller than a pixel and the four stripes merge. Use compact.
- Peek compact: 16 px. The favicon still reads at 16 px, but only just.
- Horizontal lockup: 80 px tall. At that height the mark inside it is 64 px.
- Stacked lockup: 90 px wide, for the same reason.

## Clear space

Around the mark, keep empty space at least as wide as the ring's band, which is about one seventh of the mark's height. The mark files already include a margin of 72 units on their 1024-unit square. Clear space starts outside that margin.

The lockups have padding of half the height of the capital "O" in "Okapi" on every side. Count that padding as the clear space, and don't place anything inside it.

## Colours

Peek full on light surfaces (`mark.svg`):

| Part | Hex |
| --- | --- |
| Ring, radial gradient from top left | `#64302A` to `#36170F` |
| Neck and far ear | `#4A1F18` |
| Head, linear gradient | `#BE683F`, `#9E4C2D`, `#7C3622` |
| Ossicone | `#9E4C2D`, tip `#1E0D0A` |
| Near ear, gradient | `#8C3E2C` to `#64281E` |
| Near ear lining | `#C77A4A` |
| Face and stripes | `#F6EAD3` |
| Nose and eye | `#1E0D0A` |
| Eye glint | `#FFF6E6` |

On dark surfaces such as `#0f1115`, use `mark-dark.svg`. Its browns are lighter so the ring doesn't disappear into the page: ring `#B25B40` to `#8E4430`, neck `#8A3E2C`, head `#DC8A58`, `#C26A44`, `#A24F32`, ear `#B4573C` to `#923F2D`, lining `#E8A068`. The face and stripes stay `#F6EAD3`.

`mark-flat.svg` has one fill per part and no gradients, for print or for tools that drop gradients: ring and neck `#4C211A`, head `#A65233`, ear `#7A3326`.

`mark-mono.svg` is all `#1E0D0A`. `mark-mono-white.svg` is all `#FFFFFF`.

Lockup words: "Okapi" `#2A1411` and "by JRNY" `#8C3E2C` on light, and `#F6EAD3` and `#E0A27A` on dark.

Brand cream, used for the launcher background and the Play icon: `#F5EFE4`.

Peek compact uses the same colours. Its dark favicon has two changes for dark browser tab strips: the ring is `#B25B40`, and the nose and ossicone tip are `#7A3022` because `#1E0D0A` vanishes on a dark tab.

## Don'ts

- Don't recolour the stripes or the face. They are always cream, `#F6EAD3`, or cut out in the one-colour versions.
- Don't recolour the mark outside the palettes above. To show it in one colour, use `mark-mono.svg` or `mark-mono-white.svg`.
- Don't use peek full below 64 px.
- Don't use peek compact in banners, the hero, the feature graphic or the lockups.
- Don't stretch, squash, rotate or mirror the mark. The okapi looks right, toward the name.
- Don't add shadows, glows, outlines, bevels or other effects.
- Don't put the light mark on dark surfaces. Use `mark-dark.svg` or a `-dark` lockup.
- Don't move "Okapi" or "by JRNY" inside a lockup, or set the name in another typeface next to the mark. Use the lockup files.

## Files

### `okapi-peek/`, the full drawing

| File | What it is |
| --- | --- |
| `mark.svg` | Full colour, for white and cream |
| `mark-dark.svg` | Full colour with lighter browns, for dark surfaces |
| `mark-flat.svg` | Flat colours, no gradients |
| `mark-mono.svg` | One colour, `#1E0D0A` |
| `mark-mono-white.svg` | One colour, white |
| `lockup-horizontal.svg`, `lockup-horizontal-dark.svg` | Mark, then "Okapi" with "by JRNY" under it. For headers |
| `lockup-stacked.svg`, `lockup-stacked-dark.svg` | Mark over the name, centred |
| `banner-1200x630.svg`, `.png` | Social card on cream |

### `okapi-peek-compact/`, the small-size drawing

| File | What it is |
| --- | --- |
| `mark.svg`, `mark-dark.svg` | Full colour, for light and dark surfaces |
| `mark-mono.svg`, `mark-mono-white.svg` | One colour, `#1E0D0A` and white |
| `ic_launcher_foreground.svg` | Adaptive icon foreground on a 108 dp canvas. The mark fits inside the 66 dp safe zone |
| `ic_launcher_monochrome.svg` | Android 13+ themed icon layer, same placement as the foreground |
| `favicon.svg`, `favicon-dark.svg` | Flat colours, cropped tight, for light and dark browser tabs |
| `favicon-32.png` | `favicon.svg` at 32 x 32, for browsers that don't take SVG favicons |
| `icon-square.svg` | The mark on a full-bleed `#F5EFE4` square. Source for the two PNGs below |
| `icon-512.png` | Google Play icon: 512 x 512, 32-bit RGBA, full bleed. Play rounds the corners itself |
| `apple-touch-icon-180.png` | The same square at 180 px. iOS rounds the corners itself |

The app's launcher icon is peek compact. `android/app/src/main/res/values/colors.xml` sets `ic_launcher_background` to the brand cream `#F5EFE4`. `android/app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` are VectorDrawables converted from `okapi-peek-compact/ic_launcher_foreground.svg` and `ic_launcher_monochrome.svg`. If you change either SVG, convert it again.

### Copies on the website

`website/public/assets/` holds copies of some of these files. Keep them in step with the sources here.

| Website file | Source | How it's made |
| --- | --- | --- |
| `mark.svg`, `mark-dark.svg` | `okapi-peek/mark.svg`, `mark-dark.svg` | Same drawing, with the `viewBox` cropped to the mark |
| `mark-compact.svg`, `mark-compact-dark.svg` | `okapi-peek-compact/mark.svg`, `mark-dark.svg` | Same drawing, with the `viewBox` cropped to the mark |
| `favicon.svg`, `favicon-dark.svg` | `okapi-peek-compact/favicon.svg`, `favicon-dark.svg` | Copied by `render.sh` |
| `favicon-32.png` | `okapi-peek-compact/favicon-32.png` | Copied by `render.sh` |
| `apple-touch-icon.png` | `okapi-peek-compact/apple-touch-icon-180.png` | Copied by `render.sh` |

`render.sh` doesn't touch the four cropped marks. If you redraw a mark, copy its paths into the website file and keep that file's `viewBox`.

Every SVG here uses only `path`, `circle`, `rect`, `g transform` and linear or radial gradients. None use `filter`, `mask`, `pattern`, `image` or `text`, and the words in the lockups and banner are outlines. The gradients use `gradientUnits="userSpaceOnUse"`, so Android Studio's Vector Asset tool can convert the launcher layers to VectorDrawables for API 24 and up.

The wordmark "Okapi" is drawn from circles, ellipses and straight strokes, so no font licence applies to it. "JRNY" is the JRNY wordmark. "by" and the banner text are outlines of Adwaita Sans, which is under the SIL Open Font License 1.1. That licence allows outlines in logos.

## Re-rendering the PNGs

The SVGs are the source. To rebuild the PNGs from them, run this from the repo root:

```sh
brand/render.sh
```

It renders `banner-1200x630.png`, `icon-512.png`, `apple-touch-icon-180.png` and `favicon-32.png`, then copies the favicons and the apple-touch-icon to `website/public/assets/`. It uses the first renderer it finds: `rsvg-convert`, then `magick` (ImageMagick 7), then headless Chromium. To choose one, set `RENDERER`:

```sh
RENDERER=chromium brand/render.sh
```

If ImageMagick is installed, the script writes every PNG as 32-bit RGBA, which Play requires, and strips timestamps and other metadata. It fails if `icon-512.png` is over Play's 1 MB limit. The three renderers differ by a few antialiased edge pixels. With `rsvg-convert` and ImageMagick, a run reproduces the committed PNGs byte for byte, so `git status` shows them as changed only when an SVG changed.

## Licence

The code in this repository is MIT licensed. That licence was written for code, and it doesn't settle who may use the Okapi name and the peek mark.

TODO(owner): choose a licence for the Okapi name and logo that is separate from the MIT code licence, for example a trademark policy that lets forks use the code but not the name and mark. Until then, treat the name and logo as not covered by the MIT licence.
