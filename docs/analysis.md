# Port analysis

What `speed.exe` needs from the kit, measured on 2026-09-13 from the pinned
executable. This is the `analyze` stage of the kit's design (its section 3.2)
done by hand, since the kit's `analyze` command is milestone M2 work.
Keep this file true as the bring-up moves.

## The executable

| | |
| --- | --- |
| File | `speed.exe`, 6,029,312 bytes |
| SHA-256 | `80774c2e5d619b4f120b48d4462896fd504c263399d203a238769cffde1d253c` |
| Linker | Microsoft 7.10 (Visual C++ .NET 2003), link timestamp 2005-12-01 |
| Image base, entry point | `0x00400000`, `0x007c4040` |
| Sections | `.text` 4.6 MB code, `.rdata`, `.data` (0xdce10 bytes, 0xb8e10 of them uninitialised), `.rsrc`, and one unnamed discardable section |
| Relocations, TLS | none, none: the image loads at its preferred base only |
| Packing, protection | none found: section entropy is that of plain code and data, and no SafeDisc or SecuROM markers are present. The retail disc check is absent from this build. |
| Version resource | none; the patch level is not recorded in the file |

The unnamed fifth section (`0x00a38000`, 0x40e4e bytes) is data the game
references; the page of zero padding before it, after `.rsrc` ends at
`0x00a3764e`, is where `game.toml` parks its unidentified hooks.

## Import surface

300 imports across 14 DLLs. Buckets follow the kit design's
implemented / auto-stub / unsupported split, judged against the kit's
shims as of its `game-dir-wip` snapshot.

| DLL | Imports | Bucket | Notes |
| --- | --- | --- | --- |
| `KERNEL32` | 157 | implemented, partly | files, heap, threads, `GetTickCount`, `Sleep`, toolhelp snapshots, affinity, `IsDebuggerPresent`, `GlobalMemoryStatusEx`, drive enumeration. Each import the kit's `kernel32.cpp` lacks is a run-report item, not a design problem. |
| `USER32` | 41 | implemented, partly | one window, message pump, `SendInput`, `ToUnicode`, `GetKeyboardLayout`, `MessageBoxA` |
| `GDI32` | 12 | auto-stub | `CreateFontA`, `ExtTextOutA`, `BitBlt`, `GetPixel`: font rendering into a bitmap, likely the debug text path |
| `ADVAPI32` | 6 | implemented | registry: settings, CD key, language (the widescreen fix's `WriteSettingsToFile` shows what the game keeps there) |
| `SHFOLDER`, `SHELL32` | 1, 1 | implemented | `SHGetFolderPathA` for the profile directory, `ShellExecuteA` |
| `d3d9` | 1 | **unsupported** | `Direct3DCreate9` |
| `d3dx9_26` | 13 | **unsupported** | `D3DXCreateEffectFromResourceA`, `D3DXCreateEffectPool` and eleven matrix/vector helpers |
| `DINPUT8` | 1 | unsupported today | `DirectInput8Create`; the kit shims the DirectInput the Populous era used |
| `DSOUND` | 2 | partly | ordinal 1 `DirectSoundCreate` (kit shim exists) and ordinal 6 `DirectSoundCaptureCreate` (voice chat; stub) |
| `WINMM` | 24 | unsupported today | `waveOut*` and `waveIn*` streaming, `timeGetTime`, `timeBeginPeriod`; the kit design schedules winmm for M3 |
| `WS2_32` | 31 | auto-stub, fail cleanly | online play |
| `TAPI32` | 9 | auto-stub, fail cleanly | modem play |
| `NETAPI32` | 1 | auto-stub | `Netbios` |

## Graphics: the blocking item

The game renders with Direct3D 9 through D3DX effects. Thirty-one `.fx`
effects are compiled into the executable as RCDATA resources
(`IDI_WORLD_FX`, `IDI_CAR_FX`, `IDI_SKYBOX_FX`, `IDI_SHADOW_MAP_MESH_FX`,
`IDI_VISUALTREATMENT_FX`, `IDI_PARTICLES_FX`, `IDI_RAIN_DROP_FX`, …) and
loaded with `D3DXCreateEffectFromResourceA`; the effect text names shader
profiles `vs_1_1`, `ps_1_1`, `ps_1_4` and `ps_2_0`. Every draw goes through
`IDirect3DDevice9` with vertex and pixel shaders bound by the effect
framework.

The kit's `dx/` models DirectDraw through version 4 and fixed-function
Direct3D 2; its design lists shader-model Direct3D 8 and 9 as a non-goal for
the first version and fixed-function Direct3D 8 and 9 as milestone M5.
Bringing this game up therefore needs, in the kit:

1. `IDirect3D9` and `IDirect3DDevice9` shims: device, swap chain, textures,
   vertex and index buffers, declarations, render states, render targets and
   depth-stencil surfaces (the game's shadow maps use `D24S8`).
2. A D3DX effect shim: parse the compiled effect (the `.fx` resources are
   text, so a compiler or a hand translation per effect), map techniques and
   passes to Metal pipeline states, and expose the parameter table the game
   sets by handle.
3. Shader translation: the profiles are simple enough (SM 1.1 to 2.0) that a
   per-effect Metal Shading Language port is feasible; 31 effects is a bounded
   amount of work, and the kit's Metal backend already has the presentation
   and texture plumbing.

Nothing in the runtime, loader or translator is affected by this; it is
host and `dx/` work.

## Audio, input, time

- Audio streams through `waveOutWrite` (24 winmm imports) and DirectSound;
  the SDL3 audio sink behind the kit's mixer can back a waveOut shim.
  `SOUND/` is 918 MB of EA's own formats, decoded by the game itself.
- `DirectInput8Create` gives the game keyboard, mouse and gamepad state;
  the kit's input gate and touch mapper attach to a DirectInput device
  object, whose vtable `game.toml` currently names with a sentinel.
- `timeGetTime` and `timeBeginPeriod` are the game's clock, not
  `GetTickCount` alone; the kit's frame-clock hook identifies the draw-loop
  waits by `GetTickCount` return addresses. Whether the game's frame limiter
  is reachable through that hook, or needs a `timeGetTime` hook in the kit,
  is a question for the listings.
- Cinematics are 856 MB of VP6 video under `MOVIES/` (32 files), decoded by
  the game's own code, not by a Bink DLL. Nothing to shim; they are excluded
  from the iOS bundle until playback is proven.

## Data

| Directory | Size | |
| --- | --- | --- |
| `SOUND` | 918 MB | engine, speech, streams, music |
| `MOVIES` | 856 MB | VP6 cinematics, excluded from bundles |
| `TRACKS` | 609 MB | the city and its streams |
| `CARS` | 373 MB | geometry, textures, vinyls per car |
| `FRONTEND`, `GLOBAL`, `NIS`, `LANGUAGES` | 154 MB | UI, shared bundles, scripted scenes, text |

Paths inside the executable use the game directory relative to
`guest_root`, with mixed case (`TRACKS\L2RA\TrackMaps.bin`, `frontend.bin`,
`GLOBAL\GlobalMemoryFile.bin`); the kit's case-folded path index handles
that. Saves go under the profile directory `SHGetFolderPathA` returns.

## Modules that are not the game

| File | What it is | Fate |
| --- | --- | --- |
| `dinput8.dll` | a 2023 ASI loader (1,180 exports, its own `Direct3DCreate9` import) | not part of the port |
| `scripts/NFSMostWanted.WidescreenFix.asi` and `.ini` | the widescreen fix: resolution, HUD and FOV correction, windowed mode, sim rate, shadow resolution | not loaded; its behaviours become native host options |
| `server.dll` | the LAN server (`StartServer`, `StopServer`, `IsServerRunning`) | not loaded; multiplayer is out of scope |
| `FirewallInstallHelper.dll`, `GameuxInstallHelper.dll`, `*_inst.exe` | installer helpers | excluded |

## Translation

The kit's translator reads Ghidra listings and emits one C function per
original function. `tools/analyze.py` exports them with Ghidra's default
analyzers (`analysis/decompiled/speed.exe/summary.txt` gives the count).
The translator's own coverage report, `build/recomp/translate-report.json`,
is the record of unsupported instructions and unresolved indirect targets;
see the section below once a run has been recorded.

### Run log

Recorded runs of the pipeline against this executable, newest first.

#### 2026-09-13: listings exported, translation stops at discovery

Kit at `game-dir-wip` (81594e8). `tools/analyze.py` with Ghidra 12.1.3's
default analyzers: 25,768 functions discovered, all 25,768 decompiled, 0
failed, 216 MB of listings in about four minutes. The stub configure links
`build/stub/SpeedRecomp.app` against this config, and the kit's portable
suites pass through the wrappers (56 passed, 3 skipped).

`tools/build.py --regenerate --target gen` parses every listing, then stops
at the translator's discovery gates before emitting any C:

| Gate | Count | What it is |
| --- | --- | --- |
| jump-table sites that decoded no entries | 20 | `switch` dispatches whose table the translator's decoder does not recognise in this compiler's output, for example `0x004e9c2d`, `0x006a8a14`, `0x007c488d` |
| jump-table entries with no block entry | 4 of 8 at `0x007f967f` | table slots naming addresses no listing covers |
| literal dispatch targets that are not entry points | 638 | direct calls and jumps whose target no listing owns |

The third gate is the real one; `--allow-table-gaps` only reaches it. Of
the twenty examples the translator prints, nineteen are the fall-through
after a `CALL`: Ghidra ends the function at a call it classifies as
non-returning (`fn_00425178` ends at `CALL 0x007c56b0`), and the translator
emits a jump to the byte after it, which no listing owns. The kit's
translator was tuned on curated listings that carry no such truncations.
Honouring Ghidra's non-returning calls (or re-listing with that analyzer
off) is the next translator change, in the kit; until it lands nothing
compiles and no run report exists. The instruction-level coverage
(unsupported mnemonics) is therefore still unmeasured: the translator gates
on control flow before it emits code.

Order of work from here, all of it in the kit:

1. Translator: non-returning calls and this compiler's jump-table shapes,
   until `--regenerate` emits and compiles the translation.
2. Run report on the stub hosts: which `kernel32`, `user32`, `winmm` and
   `dinput8` calls the boot path makes, and stubs for the network DLLs.
3. Direct3D 9 and the D3DX effect shim, the Metal ports of the 31 effects.
4. Only then the hooks: replace the sentinels in `game.toml` with the frame
   clock, input device and camera addresses the listings name.
