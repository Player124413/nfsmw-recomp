#!/usr/bin/env python3
"""Build a release zip for the Android workflow from a local kit build.

Example (from the repository root, with the kit's Android build done):

  python3 tools/android/make_zip.py \
      --lib build/recomp/android/libnfsmw.so \
      --game-dir "/path/to/Need For Speed Most Wanted" \
      --out release.zip

The zip is then uploaded anywhere that serves a plain link (a release asset,
an HTTP server, ...), and the "Android" workflow is run with that link.

Exclusions default to ``[bundle].exclude`` from the repository's game.toml
(the same rules the iOS bundle uses); ``--exclude`` adds more patterns
(basename globs, matched against files and directories) and
``--no-bundle-excludes`` skips the game.toml rules.
"""
import argparse
import fnmatch
import os
import sys
import zipfile


def find_game_toml():
    """The nearest game.toml walking up from the current directory."""
    d = os.getcwd()
    while True:
        p = os.path.join(d, "game.toml")
        if os.path.isfile(p):
            return p
        if os.path.isdir(os.path.join(d, ".git")):
            return None
        parent = os.path.dirname(d)
        if parent == d:
            return None
        d = parent


def bundle_excludes(toml_path):
    if not toml_path:
        return []
    try:
        import tomllib  # Python 3.11+
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)
        return list(data.get("bundle", {}).get("exclude", []))
    except ModuleNotFoundError:
        pass
    except Exception as e:
        print("warning: could not read [bundle].exclude: %s" % e, file=sys.stderr)
        return []
    # Fallback for Python < 3.11: pull the exclude list out of the text.
    try:
        text = open(toml_path, "r", encoding="utf-8").read()
        section = text.split("[bundle]", 1)[-1].split("[", 1)[0]
        line = next(l for l in section.splitlines() if l.strip().startswith("exclude"))
        bracket = line[line.index("["):line.rindex("]") + 1]
        import ast
        return [str(x) for x in ast.literal_eval(bracket)]
    except Exception as e:
        print("warning: could not parse [bundle].exclude: %s" % e, file=sys.stderr)
        return []


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lib", required=True, help="the recompiled game library (aarch64)")
    ap.add_argument("--game-dir", required=True, help="your game installation directory")
    ap.add_argument("--out", required=True, help="output zip path")
    ap.add_argument("--exclude", action="append", default=[],
                    help="extra exclusion pattern (basename glob), repeatable")
    ap.add_argument("--no-bundle-excludes", action="store_true",
                    help="do not apply [bundle].exclude from game.toml")
    args = ap.parse_args()

    if not os.path.isfile(args.lib):
        sys.exit("lib not found: " + args.lib)
    if not os.path.isdir(args.game_dir):
        sys.exit("game dir not found: " + args.game_dir)

    excludes = [] if args.no_bundle_excludes else bundle_excludes(find_game_toml())
    excludes += args.exclude

    def excluded(name):
        return any(fnmatch.fnmatch(name, pat) for pat in excludes)

    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)

    renamed = ""
    if os.path.basename(args.lib) != "libnfsmw.so":
        renamed = " (stored as libnfsmw.so)"

    count = 0
    size = 0
    with zipfile.ZipFile(args.out, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.write(args.lib, "lib/arm64-v8a/libnfsmw.so")
        for root, dirs, files in os.walk(args.game_dir):
            dirs[:] = sorted(d for d in dirs if not d.startswith(".") and not excluded(d))
            for name in sorted(files):
                if name.startswith(".") or excluded(name):
                    continue
                full = os.path.join(root, name)
                rel = os.path.relpath(full, args.game_dir).replace(os.sep, "/")
                zf.write(full, "game/" + rel)
                count += 1
                size += os.path.getsize(full)

    print("wrote %s: %d game files (%d MB)%s + libnfsmw.so"
          % (args.out, count, size // (1024 * 1024), renamed))
    if excludes:
        print("excluded by patterns: %s" % ", ".join(excludes))


if __name__ == "__main__":
    main()
