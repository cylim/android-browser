# Google Play store graphics

This folder holds the graphics for a future Google Play listing of Okapi, a browser by JRNY, and the shot list for the screenshots that still have to be captured. The [roadmap](../../ROADMAP.md) puts the Google Play release after v1, so nothing here is uploaded yet.

## Files

| File | What it is |
| --- | --- |
| `icon.svg` | Source of the Play icon: a copy of `brand/okapi-peek-compact/icon-square.svg`, the peek compact mark on the brand cream `#F5EFE4`. |
| `icon-512.png` | The Play icon, rendered from `icon.svg`. It is pixel for pixel the same as `brand/okapi-peek-compact/icon-512.png`. |
| `feature-graphic.svg` | Source of the feature graphic: the peek full mark, the name "Okapi" with "by JRNY" under it, and the website hero's headline "Sign in to the same site twice.", on the brand cream. |
| `feature-graphic.png` | The feature graphic, rendered from `feature-graphic.svg`. |
| `render.sh` | Renders both PNGs with `rsvg-convert` or headless Chromium, converts them with ImageMagick and prints their size, channels and bytes. |

## What Play asks for

Google's help page for preview assets gives these specs ([Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)). The "Here" column is the state of this folder.

| Asset | Required | Play's spec | Here |
| --- | --- | --- | --- |
| App icon | Yes | PNG, 32-bit with alpha, 512 x 512 px, 1024 KB or less. Must follow Play's icon design specifications. No badges or text that suggest rank, price or a Play category. | `icon-512.png`: 512 x 512, 8-bit RGBA, 34,221 bytes. |
| Feature graphic | Yes | JPEG or 24-bit PNG without alpha, 1024 x 500 px. Keep focal content centered and away from cutoff zones. Avoid pure white, black or dark gray backgrounds. Alt text up to 140 characters. | `feature-graphic.png`: 1024 x 500, 8-bit RGB, 60,513 bytes, on `#F5EFE4`. |
| Phone screenshots | At least 2 across all device types, up to 8 per device type | JPEG or 24-bit PNG without alpha. Each side 320 to 3840 px. The long side at most twice the short side. For larger-format promotion: at least 4 screenshots, 1080 px or more, at 9:16 (1080 x 1920) or 16:9 (1920 x 1080). The first 3 show real UI. Any tagline covers 20% of the image or less. | Not captured. See [Phone, portrait](#phone-portrait). |
| 7-inch tablet screenshots | TODO(owner): confirm in Play Console | At least 4. Each side 1080 to 7680 px, 16:9 landscape or 9:16 portrait. No text outside the app's own UI, because Play may crop it. | Not captured. See [7-inch tablet, landscape](#7-inch-tablet-landscape). |
| 10-inch tablet screenshots | TODO(owner): confirm in Play Console | Same as 7-inch tablet. The help page gives no separate spec for 7-inch and 10-inch tablets. | Not captured. See [10-inch tablet, landscape](#10-inch-tablet-landscape). |
| Chromebook screenshots | No | Same as tablets. | Not planned. Okapi targets phones and tablets. |
| Preview video | No | One public or unlisted YouTube URL, embeddable, no ads, no playlist, channel or timecode parameters. | None. |

The help page doesn't say whether Play Console requires separate 7-inch and 10-inch uploads. Check that in Play Console before you capture both sets.

## The app icon

The icon is the **peek compact** drawing of the Okapi mark, not peek full. Play shows the icon at about 48 to 96 px, and at that size the full drawing's four stripes and small eye blur together. The compact drawing has a thicker ring, a bigger head, three bold stripes and a larger eye. [brand/README.md](../../../brand/README.md) explains when to use each drawing.

`icon.svg` uses the viewBox `18 18 72 72`. That is the 72 x 72 area of the 108 x 108 adaptive icon that a launcher shows, so the mark has the same size and position as on the home screen. The brand cream `#F5EFE4` fills the whole square, with no rounded corners and no shadow, because Play applies its own mask.

The icon has an alpha channel because the spec asks for 32-bit PNG. Every pixel is opaque.

To change the icon, change `brand/okapi-peek-compact/icon-square.svg` first, then copy its content into `icon.svg`. Keep `width="512" height="512"` and the comment at the top.

## The feature graphic

The graphic uses the **peek full** drawing, because at 1024 x 500 the mark is about 350 px tall. Every shape is a path copied unchanged from `brand/okapi-peek/` and placed with a transform:

- The mark comes from `banner-1200x630.svg`. Its paths are the same as `mark.svg`.
- "Okapi" and "by JRNY" are the two word paths of `lockup-horizontal.svg`. They move as one unit, so the gap between them is the lockup's.
- "Sign in to the same site twice." is the hero line of `banner-1200x630.svg`. It matches the website's hero headline.

The SVG has no text elements, so it needs no fonts and renders the same on every machine. It has no filters, masks or images either. The background is the brand cream `#F5EFE4`, the same as the Play icon. The words are `#2A1411` and `#8C3E2C`, and the hero line is `#3A2420`, as on the banner.

Google gives no safe area in pixels. It asks for focal content centered and away from the edges. The mark and the text form one group, centered on the canvas. Measured on the PNG, everything sits inside x 133 to 889 and y 74 to 426. That is at least 133 px from each side and 73 px from the top and bottom.

Proposed alt text, 118 characters:

> The Okapi by JRNY logo, an okapi whose striped back curls into an O, beside the line "Sign in to the same site twice."

## Render and check the graphics

You need ImageMagick 7, and `rsvg-convert` or Chromium. The script uses `rsvg-convert` when it is installed. To use Chromium instead, run `RENDERER=chromium docs/playstore/graphics/render.sh`. The two renderers differ only in a few antialiased edge pixels.

1. Edit `icon.svg` or `feature-graphic.svg`.
2. Run the render script:

   ```sh
   docs/playstore/graphics/render.sh
   ```

   The script prints each file's size, channels and bytes:

   ```text
   icon-512.png: 512x512, srgba 4.0, 34221B
   feature-graphic.png: 1024x500, srgb  3.0, 60513B
   ```

3. Check that the icon reports `srgba` (it has alpha) and the feature graphic reports `srgb` (no alpha). The script stops with an error if the icon is over 1,048,576 bytes.
4. To check the feature graphic's margins, print the bounding box of everything that differs from the background by more than 8%:

   ```sh
   magick docs/playstore/graphics/feature-graphic.png -fuzz 8% -format '%@\n' info:
   ```

   The output for the current file is `757x353+133+74`.

## Screenshot shot list

Nobody has captured these yet. The commands in [Capture the screenshots](#capture-the-screenshots) produce them from the two emulators.

### Rules for every screenshot

- Use test Profiles and made-up content only. Open only `example.com`, `example.org` and `example.net` addresses, which [IANA reserves for documentation](https://www.iana.org/help/example-domains). Their pages are titled "Example Domain", and that is expected.
- Don't sign in to anything. No real Account, name, email address or photo may appear.
- Don't type search terms. Suggestions go to the search engine while you type (see [SPEC.md](../../SPEC.md), "Browsing features"). Open pages from their full address, or with the `am start` command below.
- Don't feature other companies' sites or logos. Show Okapi's own UI and the Start Page.
- Capture only while a normal Tab is showing. A Private Tab sets `FLAG_SECURE`, which blocks screenshots, so its Start Page can't be captured. Show a Private Tab through its row in the Tab list instead. The row shows the lock, and it doesn't name the page while a normal Tab is showing.
- Use the light theme and Color strength **Medium** for every Profile, so the sets match.
- Look at every PNG before you commit it. CONTRIBUTING.md warns that screenshots can hold personal data.

### Test content

Create this content on each emulator before you capture. "Personal" is the Profile the app creates on first launch.

| Profile | Color | Icon | Pinned Tabs | Other Tabs |
| --- | --- | --- | --- | --- |
| Personal | Blue | Person | `https://example.com/` | `https://example.net/` |
| Work | Teal | Build | `https://example.org/` at the top level, `https://example.com/reports` in a Folder named "Projects" | `https://example.org/inbox`, one Private Tab |
| Side project | Purple | Star | none | `https://example.net/notes` |

Add these Site Rules under **Settings > Site Rules**:

| Site Rule | Profile |
| --- | --- |
| `example.org` | Work |
| `*.example.net` | Side project |

Visit three or four pages in Work, so its Start Page has a "Recent" section.

### Phone, portrait

Device: `JRNY_Pixel_7_API_37` (Pixel 7, 1080 x 2400 at 420 dpi). The full screen is 2.22:1, more than Play's 2:1 limit, so the capture commands set the display to 1080 x 1920 (9:16). That is 411 x 731 dp, which shows the Bottom Bar layout.

| # | File | Shows | How |
| --- | --- | --- | --- |
| 1 | `01-start-page.png` | The Start Page of Work: its avatar and name, "Pinned" tiles with the "Projects" Folder, and "Recent". | Make Work the Active Profile. Open a new Tab. |
| 2 | `02-profile-switch.png` | The Profile sheet with Personal, Work and Side project. | Tap the avatar at the left of the address field. |
| 3 | `03-move.png` | The "Move to Profile" dialog over `example.org/inbox` in Work. | Open the page menu. Choose **Move to Profile…**. |
| 4 | `04-tab-overview.png` | The Tab Overview of Work: "Pinned", the "Projects" Folder, the other Tabs and the Private Tab's row with its lock. | Show a normal Tab. Tap the Tab Overview button on the Bottom Bar. |
| 5 | `05-incoming-link.png` | `example.org` opened from another app, with "Opened in Work by a rule" and **Move to Profile…**. | Make Personal the Active Profile. Send the link with `am start` (see step 7 of the capture commands). |
| 6 | `06-site-rules.png` | Settings > Site Rules with both rules and **Check a link**. | Open Settings, then **Site Rules**. |

### 10-inch tablet, landscape

Device: `JRNY_Pixel_Tablet_API_37` (Pixel Tablet, 2560 x 1600 at 320 dpi). 2560 x 1600 is 16:10, so the capture commands set the display to 2560 x 1440 (16:9). That is 1280 x 720 dp, which shows the Sidebar.

| # | File | Shows | How |
| --- | --- | --- | --- |
| 1 | `01-sidebar-start-page.png` | The Sidebar of Work with "Pinned", the "Projects" Folder, the other Tabs and the Private Tab's row, next to the Start Page. | Make Work the Active Profile. Open a new Tab. |
| 2 | `02-sidebar-page.png` | The Sidebar next to `example.org/inbox`. | Choose that Tab in the Sidebar. |
| 3 | `03-profile-switch.png` | The Profile switcher at the bottom of the Sidebar, with Personal active and Personal's Tabs in the list. | Choose Personal in the switcher. |
| 4 | `04-move.png` | The "Move to Profile" dialog over `example.org/inbox` in Work. | Open the page menu. Choose **Move to Profile…**. |
| 5 | `05-incoming-link.png` | `example.org` opened from another app, with "Opened in Work by a rule". | Make Personal the Active Profile. Send the link with `am start`. |
| 6 | `06-site-rules.png` | Site Rules beside the Sidebar, with both rules and **Check a link**. | Open Settings, then **Site Rules**. |

### 7-inch tablet, landscape

Device: `JRNY_Pixel_Tablet_API_37`, with the display set to 1920 x 1080 at 288 dpi. That is 1067 x 600 dp, the size of a 7-inch tablet in landscape, and it still shows the Sidebar. Capture the same six shots as the 10-inch tablet, with the same file names.

## Capture the screenshots

Run one emulator at a time. Without `-s <serial>`, `adb` and Gradle act on every connected device.

This guide uses the AVD names `JRNY_Pixel_7_API_37` and `JRNY_Pixel_Tablet_API_37`. CONTRIBUTING.md creates them as `JRNY_Phone_API_37` and `JRNY_Tablet_API_37`. If you created yours from CONTRIBUTING.md, use those names.

1. Build the debug APK. No release signing key exists yet, so screenshots come from the debug build.

   ```sh
   cd android
   ./gradlew assembleDebug
   ```

2. In a second terminal, start the emulator. The command keeps running until the emulator shuts down.

   ```sh
   "$HOME/Android/Sdk/emulator/emulator" -avd JRNY_Pixel_7_API_37
   ```

   Back in the first terminal, find the emulator's serial:

   ```sh
   adb devices
   ```

   For the tablet, start `JRNY_Pixel_Tablet_API_37` instead. In the commands below, replace `<serial>` with the serial `adb devices` prints, for example `emulator-5554`.

3. Install the app and start from empty app data. The second command erases Okapi's data on that emulator.

   ```sh
   adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <serial> shell pm clear app.jrny.browser
   ```

4. Check the display's real size. It should print 1080x2400 for the phone and 2560x1600 for the tablet.

   ```sh
   adb -s <serial> shell wm size
   ```

5. Set the display size for the device type:

   ```sh
   # Phone, 9:16
   adb -s <serial> shell wm size 1080x1920

   # 10-inch tablet, 16:9 landscape
   adb -s <serial> shell wm size 2560x1440
   adb -s <serial> shell cmd window user-rotation lock 0

   # 7-inch tablet, 16:9 landscape
   adb -s <serial> shell wm size 1920x1080
   adb -s <serial> shell wm density 288
   adb -s <serial> shell cmd window user-rotation lock 0
   ```

6. Turn on System UI demo mode, so the status bar shows a fixed clock and no notifications:

   ```sh
   adb -s <serial> shell settings put global sysui_demo_allowed 1
   adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command enter
   adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
   adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
   adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```

7. Create the [test content](#test-content) in the app. To open a page as an Incoming Link, send it from the shell:

   ```sh
   adb -s <serial> shell am start -a android.intent.action.VIEW -d https://example.org/ app.jrny.browser
   ```

8. For each shot, set up the screen, then capture it and remove the alpha channel. Use the folder `phone`, `tablet-7` or `tablet-10`:

   ```sh
   mkdir -p ../docs/playstore/graphics/screenshots/phone
   adb -s <serial> exec-out screencap -p > /tmp/shot.png
   magick /tmp/shot.png -alpha off PNG24:../docs/playstore/graphics/screenshots/phone/01-start-page.png
   ```

9. Check the sizes. Each phone shot must be 1080x1920, each 10-inch shot 2560x1440 and each 7-inch shot 1920x1080, all `srgb`:

   ```sh
   magick identify -format '%f %wx%h %[channels]\n' ../docs/playstore/graphics/screenshots/*/*.png
   ```

   If a capture comes out at the display's real size instead, `wm size` did not apply to the capture. Crop it to the target size from the top-left corner and check that no UI was cut off.

10. Put the emulator back and shut it down:

    ```sh
    adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command exit
    adb -s <serial> shell wm size reset
    adb -s <serial> shell wm density reset
    adb -s <serial> shell cmd window user-rotation free
    adb -s <serial> emu kill
    ```

## Open decisions for the owner

- TODO(owner): confirm in Play Console whether 7-inch and 10-inch tablet screenshots are separate required uploads.
- TODO(owner): confirm the feature graphic's alt text, or write another one of 140 characters or fewer.
