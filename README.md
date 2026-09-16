# NFS Most Wanted Recomp

[Build & contribute](CONTRIBUTING.md) · [Port analysis](docs/analysis.md) ·
[Testing](docs/testing.md) · [Changelog](CHANGELOG.md)

A native macOS and iPad recompilation of **Need for Speed: Most Wanted**
(the 2005 PC Black Edition), in progress. Original game instructions are
translated to C ahead of time and compiled with the native host, the way
[populous-recomp](https://github.com/veritr1x/populous-recomp) does it.

The runtime, translator, hosts and mod foundation are
[recomp-kit](https://github.com/veritr1x/recomp-kit), pulled in as the git
submodule `kit/`. This repository holds what is Most Wanted's: `game.toml`
and `globals.toml` (identity, addresses, curated symbols), `tests/` (the
config's contract with the kit), `tools/analyze.py` (this game's listing
export) and docs. The kit is private at the moment, so the submodule needs
access to it.

**You need your own copy of the game.** Game executables, artwork, sound,
tracks, cinematics, generated game code and replacement packs are prepared
locally and are not included. See [NOTICE](NOTICE) for ownership and
dependency credits.

## Status: analysis, not playable

The executable is pinned, hashed and measured against the kit; the config
renders and its tests pass; the kit configures against it; Ghidra exports
25,768 functions and the kit's translator emits code for all but 40 of
them, which use MMX, SSE2 and four rarer instructions the kit does not
model yet. Nothing runs yet. [docs/analysis.md](docs/analysis.md) records the executable's import
surface, its graphics path and the kit work each needs. The short version:

- Most Wanted renders through **Direct3D 9 with D3DX effect shaders**
  (31 `.fx` effects compiled into the executable; vertex shader 1.1, pixel
  shader 1.1 through 2.0). The kit models DirectDraw and fixed-function
  Direct3D up to version 7; shader-model Direct3D is outside its supported
  envelope today. This is the blocking item.
- Audio goes through `winmm` wave output and DirectSound; input through
  DirectInput 8; the kit shims older versions of both.
- Online play (`ws2_32`, `tapi32`, `netapi32`, the bundled `server.dll`) is
  out of scope and needs stubs that fail cleanly.
- The Windows-only extras in the folder (the ASI loader `dinput8.dll` and the
  widescreen fix) are not part of the port; their fixes become native host
  behaviour.

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

## Play on an iPad

Not yet. When the macOS build runs, `tools/build.py --target ios --console`
builds, signs and installs the app exactly as it does for Populous, staging
the game directory into the app minus `[bundle].exclude` in `game.toml`
(cinematics, the uninstaller, the Windows-only DLLs). The touch map will
need a racing layout, not Populous's pointer gestures.

## Check a change

```sh
.venv/bin/python tools/test.py              # the kit's portable suites
.venv/bin/python -m pytest -q tests         # this game's config
.venv/bin/python tools/build.py --stub      # the kit configures against this config, no game code
```

Changes to the runtime, hosts or tools belong in the kit's repository; bump
the submodule here once they land.
