# Working on NFS Most Wanted Recomp

Read README.md, CONTRIBUTING.md and docs/analysis.md before a broad change.
This repository holds only what is Need for Speed: Most Wanted's: config,
curated symbols, game headers, docs and, once they exist, mods and smoke
scripts. The runtime, translator, hosts and tools are the kit in `kit/` (a git
submodule of recomp-kit); edit those in the kit's own repository and bump the
submodule here. Game files and translations are private local inputs under
ignored `original/`, `analysis/` and `build/`.

- The port is at the analysis stage. docs/analysis.md records what the
  executable needs and what the kit lacks; keep it current rather than
  claiming progress in README.md.
- Keep changes focused; preserve unrelated local work and player profiles.
- Never replace 32-bit guest addresses with host pointers. Addresses belong
  in `game.toml` `[hooks]` and `globals.toml`, never in kit code. A sentinel
  address (see game.toml) is replaced only by an address verified in the
  listings; `tests/test_game_config.py` keeps the unverified ones in the
  sentinel range.
- Edit translation rules in the kit, not `build/recomp/gen/`. Regenerate with
  `tools/build.py --regenerate` after changing the translator.
- Native code builds only through `tools/build.py` and `tools/test.py`, never
  by invoking compilers directly. Format kit sources with
  `.venv/bin/python kit/tools/format.py --write`.
- Run relevant suites from docs/testing.md and report exactly which checks
  ran; compilation and translator coverage do not establish a playable game.
- Do not commit game assets, generated code, binaries, credentials, personal
  saves or run logs.
