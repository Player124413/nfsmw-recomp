# NFS Most Wanted Recomp

[Build & contribute](CONTRIBUTING.md) · [Port analysis](docs/analysis.md) ·
[Testing](docs/testing.md) · [Changelog](CHANGELOG.md)

A recompilation of **Need for Speed: Most Wanted** (the 2005 PC Black
Edition) for macOS, iPad, Linux, Windows and the browser, in progress.
Original game instructions are translated to C ahead of time and compiled
with the native host, the way
[populous-recomp](https://github.com/veritr1x/populous-recomp) does it.

The runtime, translator, hosts and mod foundation are
[recomp-kit](https://github.com/veritr1x/recomp-kit), pulled in as the git
submodule `kit/`. This repository holds what is Most Wanted's: `game.toml`
and `globals.toml` (identity, addresses, curated symbols), `tests/` (the
config's contract with the kit), `tools/analyze.py` (this game's listing
export) and docs.

**You need your own copy of the game.** Game executables, artwork, sound,
tracks, cinematics, generated game code and replacement packs are prepared
locally and are not included. See [NOTICE](NOTICE) for ownership and
dependency credits.

## Status: playable

The game plays. Direct3D 9 is translated through one shader generator and
renders on Metal (macOS, iPad), Vulkan (Linux, Windows, and macOS through
MoltenVK) and WebGPU (the browser).

- **macOS.** Up to 4K with the render scale following the window, 4x
  multisampling, depth-texture shadow maps and occlusion queries. A pinned 4K
  run with every option at maximum holds 110-123 fps.
- **iPad.** Plays by touch; the core mods are compiled into the app, which a
  stock device needs because it loads no plugins.
- **Linux.** Renders the race, including under software Vulkan (lavapipe).
- **Windows.** Cross-compiled with llvm-mingw; runs the whole test script at
  100-170 fps under CrossOver. A run on Windows hardware is still untested.
- **The browser.** WebGPU, tested in Chrome and Safari: the game runs on a
  worker, reads its files from the browser's private storage, and a race runs
  at 113-166 fps. Reading a render target back is not supported there.

The simulation runs at 120 Hz and the widescreen fix (FOV, HUD, minimap) is
ported, both in the `core.nfsmw` mod. The audio-bank heap routines run in a
small x86 interpreter checked against Unicorn. Online play (`ws2_32`,
`tapi32`, `netapi32`, the bundled `server.dll`) is out of scope and stubbed to
fail cleanly, and the Windows-only extras in the game folder (the ASI loader
and the widescreen fix) are not part of the port; their fixes are native host
behaviour instead. [docs/analysis.md](docs/analysis.md) records the
executable's graphics path, its effect shaders and each bring-up step.

## Build on macOS

The steps are the kit's; they run today up to the translation, which is
where the bring-up stands.

```sh
git clone --recurse-submodules https://github.com/veritr1x/nfsmw-recomp.git
cd nfsmw-recomp
python3 -m venv .venv
.venv/bin/python -m pip install -r kit/requirements-dev.txt
.venv/bin/python tools/setup.py --install "/path/to/Need For Speed Most Wanted Black Edition" --link-only
.venv/bin/python tools/analyze.py --ghidra-home /path/to/ghidra_12.1.3_PUBLIC
.venv/bin/python tools/build.py --regenerate --allow-table-gaps "MSVC 7.1 switch shapes; see docs/analysis.md"
```

`tools/setup.py`, `tools/build.py`, `tools/test.py` and `tools/ios_logs.py`
are four-line wrappers around the kit's tools; every option is the kit's
(`--help` lists them). `tools/analyze.py` is this game's own: the kit's setup
exports listings from a curated annotation set, and none exists for
`speed.exe`, so this script runs Ghidra's analyzers instead. Outputs (the
translation, the apps, the logs) live under ignored `build/`; your
installation is linked at ignored `original/retail` and the Ghidra listings
live in ignored `analysis/`.

## Build for the other platforms

Linux and Windows builds are the kit's `--target app` (Windows cross-compiles
with llvm-mingw), and `tools/build.py --target web` writes a servable site for
the browser. The kit's [README](kit/README.md) has the prerequisites and the
serving headers the web build needs.

## Play on an iPad

`tools/build.py --target ios --console` builds, signs and installs the app and
streams its console, staging the game directory into the app minus
`[bundle].exclude` in `game.toml` (the uninstaller, the Windows-only DLLs).
The core mods are compiled in, which a stock device needs because it loads no
plugins.

## Check a change

```sh
.venv/bin/python tools/test.py              # the kit's portable suites
.venv/bin/python -m pytest -q tests         # this game's config
.venv/bin/python tools/build.py --stub      # the kit configures against this config, no game code
```

Changes to the runtime, hosts or tools belong in the kit's repository; bump
the submodule here once they land.
