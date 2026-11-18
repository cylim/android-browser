#!/usr/bin/env python3
"""Checks the website and copies it, ready to publish, into an output folder.

    python3 website/build.py [--base-path /android-browser] [--out website/_site]

Fails, and writes nothing, when:
- a page or stylesheet refers to another server (links to this repository on GitHub and the
  SVG namespace are the only outside addresses allowed), or
- a relative link or asset points to a file that isn't in public/.

The output is public/ with __BASE_PATH__ replaced by --base-path: the site's path on its
host, such as "/android-browser" for a GitHub Pages project site, or "" on its own domain.
Only the standard library is used, so it runs anywhere Python 3.9+ does.
"""

import argparse
import re
import shutil
import sys
from pathlib import Path
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
PUBLIC = HERE / "public"
PLACEHOLDER = "__BASE_PATH__"

ALLOWED = re.compile(r"(https://github\.com/cylim/android-browser(/\S*)?|http://www\.w3\.org/2000/svg)")
# Any absolute or protocol-relative address: "https://…", "http://…" or "//host…".
OUTSIDE = re.compile(r"""(?:https?:)?//[^\s"'()<>]+""")
# href="…" and src="…" in HTML, url(…) in CSS.
REFERENCE = re.compile(r"""(?:href|src)\s*=\s*["']([^"']*)["']|url\(\s*["']?([^"')]+)["']?\s*\)""")


def check(errors):
    for path in sorted(PUBLIC.rglob("*")):
        if path.suffix not in {".html", ".css", ".svg"}:
            continue
        text = path.read_text(encoding="utf-8")
        name = path.relative_to(HERE)
        for number, line in enumerate(text.splitlines(), 1):
            for match in OUTSIDE.finditer(line):
                if not ALLOWED.fullmatch(match.group(0)):
                    errors.append(f"{name}:{number}: refers to another server: {match.group(0)}")
            for match in REFERENCE.finditer(line):
                target = match.group(1) or match.group(2)
                problem = broken_reference(path, target)
                if problem:
                    errors.append(f"{name}:{number}: {problem}: {target}")


def broken_reference(page, target):
    """Why [target], referenced from [page], doesn't resolve to a file in public/, or None."""
    parts = urlsplit(target)
    if parts.scheme or target.startswith("//") or not parts.path:
        return None  # an outside address (checked above), or a "#fragment" on the same page
    path = parts.path
    if path.startswith(PLACEHOLDER):
        resolved = PUBLIC / path[len(PLACEHOLDER):].lstrip("/")
    elif path.startswith("/"):
        return f"a path from the host's root breaks under a subfolder; start it with {PLACEHOLDER}"
    else:
        resolved = page.parent / path
    resolved = resolved.resolve()
    if resolved.is_dir():
        resolved = resolved / "index.html"
    if PUBLIC not in resolved.parents or not resolved.is_file():
        return "no such file in public/"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--base-path", default="", help='e.g. "/android-browser", or "" (default)')
    parser.add_argument("--out", type=Path, help="folder to write the site to; leave out to only check")
    args = parser.parse_args()

    base = args.base_path.rstrip("/")
    if base and not re.fullmatch(r"(/[A-Za-z0-9._~-]+)+", base):
        parser.error(f"--base-path must look like /name, got {args.base_path!r}")

    errors = []
    check(errors)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"{len(errors)} problem(s) found; nothing written.", file=sys.stderr)
        return 1

    if args.out:
        out = args.out.resolve()
        # The output folder is emptied first, so it must not hold the sources, or be among them.
        if out == PUBLIC or PUBLIC in out.parents:
            parser.error("--out can't be inside public/")
        if out in PUBLIC.parents:
            parser.error(f"--out can't be a folder that holds the website's sources: {out}")
        shutil.rmtree(out, ignore_errors=True)
        shutil.copytree(PUBLIC, out)
        for path in out.rglob("*"):
            if path.suffix in {".html", ".css"}:
                text = path.read_text(encoding="utf-8")
                if PLACEHOLDER in text:
                    path.write_text(text.replace(PLACEHOLDER, base), encoding="utf-8")
        print(f"Wrote the site to {out} with base path {base or '(none)'}.")
    else:
        print("Website checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
