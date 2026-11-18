#!/usr/bin/env bash
# Re-render the PNGs in brand/ from their SVG sources, then copy the website's
# PNG icons into website/public/assets/.
#
#   brand/render.sh             use the first renderer found
#   RENDERER=chromium brand/render.sh
#
# Renderers, in order of preference: rsvg-convert, magick (ImageMagick 7),
# chromium (headless). If magick is installed, every PNG is then rewritten as
# 32-bit RGBA, which Google Play asks for, with no timestamps, so a run that
# changes no pixels leaves the files byte for byte the same.
set -euo pipefail

cd "$(dirname "$0")"

pick_renderer() {
  if [[ -n "${RENDERER:-}" ]]; then echo "$RENDERER"; return; fi
  for r in rsvg-convert magick chromium chromium-browser google-chrome; do
    if command -v "$r" >/dev/null 2>&1; then echo "$r"; return; fi
  done
  echo "render.sh: install rsvg-convert, ImageMagick 7 or Chromium" >&2
  exit 1
}
R=$(pick_renderer)

# render SVG WIDTH HEIGHT PNG
render() {
  local svg=$1 w=$2 h=$3 out=$4
  case "$R" in
    rsvg-convert)
      rsvg-convert -w "$w" -h "$h" "$svg" -o "$out" ;;
    magick)
      magick -background none -density 384 "$svg" -resize "${w}x${h}!" "$out" ;;
    chromium|chromium-browser|google-chrome)
      local tmp; tmp=$(mktemp -d)
      cp "$svg" "$tmp/in.svg"
      printf '<!doctype html><style>html,body{margin:0;background:transparent}img{display:block;width:%spx;height:%spx}</style><img src="in.svg">' \
        "$w" "$h" >"$tmp/page.html"
      "$R" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --default-background-color=00000000 --window-size="$w,$h" \
        --screenshot="$tmp/out.png" "file://$tmp/page.html" >/dev/null 2>&1
      mv "$tmp/out.png" "$out"
      rm -rf "$tmp" ;;
    *)
      echo "render.sh: unknown renderer $R" >&2; exit 1 ;;
  esac
  if command -v magick >/dev/null 2>&1; then
    magick "$out" -strip -define png:exclude-chunks=date,time \
      -define png:color-type=6 "PNG32:$out"
  fi
  echo "  $out (${w}x${h})"
}

echo "renderer: $R"
render okapi-peek/banner-1200x630.svg            1200 630 okapi-peek/banner-1200x630.png
render okapi-peek-compact/icon-square.svg        512  512 okapi-peek-compact/icon-512.png
render okapi-peek-compact/icon-square.svg        180  180 okapi-peek-compact/apple-touch-icon-180.png
render okapi-peek-compact/favicon.svg            32   32  okapi-peek-compact/favicon-32.png

# The website serves copies of the compact icons.
site=../website/public/assets
cp okapi-peek-compact/favicon.svg            "$site/favicon.svg"
cp okapi-peek-compact/favicon-dark.svg       "$site/favicon-dark.svg"
cp okapi-peek-compact/favicon-32.png         "$site/favicon-32.png"
cp okapi-peek-compact/apple-touch-icon-180.png "$site/apple-touch-icon.png"
echo "  copied favicon.svg, favicon-dark.svg, favicon-32.png, apple-touch-icon.png to website/public/assets/"

# Google Play rejects icons over 1 MB.
size=$(wc -c <okapi-peek-compact/icon-512.png)
if (( size > 1048576 )); then
  echo "render.sh: icon-512.png is $size bytes, over Play's 1 MB limit" >&2
  exit 1
fi
