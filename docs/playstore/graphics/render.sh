#!/usr/bin/env bash
# Renders the Play Store graphics from their SVG sources and checks them against Play's specs.
# Needs: ImageMagick 7 (magick), and rsvg-convert or Chromium. Run from any folder.
#
#   docs/playstore/graphics/render.sh
#   RENDERER=chromium docs/playstore/graphics/render.sh
set -euo pipefail
cd "$(dirname "$0")"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pick_renderer() {
	if [[ -n "${RENDERER:-}" ]]; then echo "$RENDERER"; return; fi
	for r in rsvg-convert chromium chromium-browser google-chrome; do
		if command -v "$r" >/dev/null 2>&1; then echo "$r"; return; fi
	done
	echo "render.sh: install rsvg-convert or Chromium" >&2
	exit 1
}
R=$(pick_renderer)

render() { # render <svg> <width> <height> <out.png>
	case "$R" in
	rsvg-convert)
		rsvg-convert -w "$2" -h "$3" "$1" -o "$4" ;;
	chromium | chromium-browser | google-chrome)
		cp "$1" "$tmp/in.svg"
		printf '<!doctype html><style>html,body{margin:0}img{display:block;width:%spx;height:%spx}</style><img src="in.svg">' \
			"$2" "$3" >"$tmp/page.html"
		"$R" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
			--window-size="$2,$3" --screenshot="$4" "file://$tmp/page.html" >/dev/null 2>&1 ;;
	*)
		echo "render.sh: unknown renderer $R" >&2
		exit 1 ;;
	esac
}

# App icon: 512 x 512, 32-bit PNG with alpha (RGBA), 1024 KB or less. Every pixel is opaque.
render icon.svg 512 512 "$tmp/icon.png"
magick "$tmp/icon.png" -strip -alpha set PNG32:icon-512.png

# Feature graphic: 1024 x 500, 24-bit PNG without alpha (RGB).
render feature-graphic.svg 1024 500 "$tmp/feature.png"
magick "$tmp/feature.png" -strip -background '#F5EFE4' -alpha remove -alpha off PNG24:feature-graphic.png

magick identify -format '%f: %wx%h, %[channels], %b\n' icon-512.png feature-graphic.png

# Google Play rejects icons over 1 MB.
size=$(wc -c <icon-512.png)
if ((size > 1048576)); then
	echo "render.sh: icon-512.png is $size bytes, over Play's 1 MB limit" >&2
	exit 1
fi
