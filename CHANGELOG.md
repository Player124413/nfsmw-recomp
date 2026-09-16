# Changelog

## Unreleased

- The translation compiles and the game boots as far as its first Direct3D 9
  call. `game.toml` names two CRT helper entry points the Ghidra listing
  lacks; everything else was kit work (see the kit's changelog): MMX/SSE2
  traps, `XADD`, `CMPXCHG`, `LAHF`, the x87 constants and environment ops,
  `INT3` as a block terminator, `GetModuleHandleA` for served modules, 19
  kernel32 shims and stdcall pop counts for the unshimmed imports. Recorded
  in `docs/analysis.md`. The window path works too: `RegisterClassExA`,
  `AdjustWindowRect` and `GlobalMemoryStatusEx` landed in the kit, and the
  guest stack no longer drifts, so the only remaining blocker is Direct3D 9.
- Re-pin the kit to `main` 4574a35, the commit the other game repositories
  pin; `game.toml` gains the `entry_points` key and CI takes the current
  three-platform shape. On this kit the translator clears discovery and
  emits code for all but 40 of the 25,768 functions; the 40 need MMX, SSE2,
  `STMXCSR`, two x87 constants, `FNSTENV` and `LAHF` in the kit's
  translator. Recorded in `docs/analysis.md`.
- New game repository for Need for Speed: Most Wanted (PC Black Edition,
  `speed.exe` SHA-256 `80774c2e…d253c`) in the shape of populous-recomp: the
  kit as the submodule `kit/`, `game.toml` and `globals.toml`, thin
  `tools/*.py` wrappers, config tests and CI.
- `game.toml` carries the measured identity of the executable (image base
  `0x00400000`, entry point `0x007c4040`, guest root, required data
  directories, iOS bundle exclusions). The Populous-shaped hooks and globals
  the kit compiles against are sentinels in the executable's unused section
  padding until the bring-up identifies them; `tests/test_game_config.py`
  enforces that.
- `tools/analyze.py`: listing export with Ghidra's own analyzers, because the
  kit's setup expects a curated annotation set this game does not have.
- First pipeline run recorded in `docs/analysis.md`: Ghidra exports 25,768
  functions; the kit's translator parses them all and stops at its discovery
  gates (638 dispatch targets outside any listing, mostly fall-throughs after
  calls Ghidra marks non-returning). No translation compiles yet.
- `docs/analysis.md`: the executable's import surface, graphics path and the
  kit work each needs. The blocking item is shader-model Direct3D 9 through
  D3DX effects, outside the kit's supported envelope today.
