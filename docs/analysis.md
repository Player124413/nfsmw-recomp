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

300 imports across 14 DLLs. Measured against the shims registered in kit
`main` at 4574a35 (2026-09-14): 163 of 300 have a shim. Buckets follow the
kit design's implemented / auto-stub / unsupported split.

| DLL | Shimmed | Bucket | What is missing |
| --- | --- | --- | --- |
| `KERNEL32` | 115 / 157 | implemented, partly | toolhelp snapshots, priority and affinity, `IsDebuggerPresent`, `GlobalMemoryStatusEx`, file-time conversion, the serial `*Comm*` family, `CreateProcessA`, waitable timers, `QueueUserAPC`, `VirtualProtect`, `VirtualQuery`, `SleepEx`, `DuplicateHandle`, `TerminateThread`, date and time formatting |
| `USER32` | 30 / 41 | implemented, partly | `SetCapture`, `ReleaseCapture`, `RegisterClassExA`, `AdjustWindowRect`, `GetDesktopWindow`, `MapVirtualKeyA`, `MapVirtualKeyExA`, `ToUnicode`, `SendInput`, `PostThreadMessageA`, `wsprintfA` |
| `GDI32` | 8 / 12 | auto-stub | `CreateFontA`, `ExtTextOutA`, `CreateBitmap`, `GetPixel`: text rendered into a bitmap, likely the debug text path |
| `ADVAPI32` | 5 / 6 | implemented | `RegCreateKeyA`; the game keeps settings, CD key and language in the registry |
| `SHELL32`, `SHFOLDER` | 1 / 1, 0 / 1 | implemented, auto-stub | `SHGetFolderPathA` for the profile directory |
| `d3d9` | 0 / 1 | **unsupported** | `Direct3DCreate9` |
| `d3dx9_26` | 0 / 13 | **unsupported** | `D3DXCreateEffectFromResourceA`, `D3DXCreateEffectPool` and eleven matrix and vector helpers |
| `DINPUT8` | 0 / 1 | unsupported today | `DirectInput8Create`; the kit shims the `DINPUT.dll` generation |
| `DSOUND` | 1 / 2 | partly | ordinal 6 `DirectSoundCaptureCreate` (voice chat) needs a stub |
| `WINMM` | 3 / 24 | unsupported today | the kit has the timer family; every `waveOut*` and `waveIn*` streaming call is missing |
| `WS2_32` | 0 / 31 | auto-stub, fail cleanly | online play; the kit shims `WSOCK32.dll`, not `WS2_32.dll` |
| `TAPI32` | 0 / 9 | auto-stub, fail cleanly | modem play |
| `NETAPI32` | 0 / 1 | auto-stub | `Netbios` |

The loader has no auto-stub generator yet (kit milestone M2): an import
with no shim is what the first boot's run report will list.

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

#### 2026-09-16: the translation compiles and the game boots to the Direct3D 9 wall

Kit work on branch `nfsmw` of recomp-kit, cut from `main` 4574a35.

**Translator.** Seven gaps closed, and the whole executable now translates:

| Change | Why |
| --- | --- |
| MMX, SSE and SSE2 become a `recomp_unmodelled` trap | `recomp_cpuid` advertises none of them, so a guest that checks CPUID never runs one; the 30 MMX blitters and the CRT's SSE2 math are dead code behind `__sse2_available` at `0x009c5310` |
| the trap is tested before the string instructions | `MOVSD` and `CMPSD` name both a string instruction and an SSE2 scalar-double one, and only the operands tell them apart; the string emitter was silently claiming the SSE2 form |
| `XADD` and `CMPXCHG` | lock-prefixed atomics in the CRT's reference counting |
| `LAHF` | one CRT flag inspection |
| `FLDLN2`, `FLDL2E`, `FLDLG2`, `FLDL2T` | the x87 constant loads `exp` and `log` use |
| `FNSTENV` and `FLDENV` | the 28-byte x87 environment, masking exceptions as hardware does |
| `INT3` ends a block | MSVC pads between functions with it; a listing whose tail is a call Ghidra marks non-returning ran that padding into the next function |

The Unicorn differential in the kit earned its place immediately: it caught
`FLDL2E` written as log10(e) instead of log2(e).

All 25,768 functions now translate. The archive is 221 MB from 188 chunks,
and `build/SpeedRecomp.app` links.

**Entry points.** `007f0e13` jumps through a `.data` slot holding `007f10e8`,
a CRT helper Ghidra never listed. The translator's pointer scan only accepts
16-byte-aligned destinations and these are not, so `game.toml` names them in
`[translate] entry_points`. Relaxing the alignment rule instead would be
wrong: 8,344 dwords in this image point into `.text` at 4-byte alignment and
decode as instructions, nearly all of them coincidence.

**Runtime.** `GetModuleHandleA` now hands out a handle for a DLL the runtime
serves, instead of reporting it missing. The CRT's `__mtinit` asks for
kernel32, and a null handle made it skip the block that fills in its own TLS
function pointers, leaving it to call one that was still zero. Nineteen
kernel32 shims were added (system and file times, process id, handle
duplication, `SleepEx`, waitable timers, priority and affinity, toolhelp
reporting no processes, `IsDebuggerPresent`). Unshimmed imports across
`d3d9`, `d3dx9_26`, `DINPUT8`, `SHFOLDER`, `WINMM` wave, `WS2_32`,
`NETAPI32`, `USER32` and `GDI32` now declare their stdcall pop counts, so a
call the runtime answers with zero still leaves the guest stack where the
callee would have.

**Boot progression**, each line a run of the built app:

| Run | How far | Stopped at |
| --- | --- | --- |
| 1 | CRT entry | `__mtinit` calling a null `FlsAlloc` pointer |
| 2 | CRT complete, into the game's own start-up | the indirect jump to the unlisted CRT helper `007f10e8` |
| 3 | window class, window, `Direct3DCreate9`, `D3DXCreateEffectPool`, `D3DXCreateEffectFromResourceA` | `006c1527`, `call dword ptr [ecx + 0x38]`: a COM method on the `IDirect3D9` that `Direct3DCreate9` could not return |

That last line is the wall this port was always going to hit, and it is now
the only thing between the game and a frame. Everything before it works:
the loader, the CRT, the file system, the registry, the window, and the
game's own start-up code up to the point where it asks for a device.

**Native suites** (`tools/test.py --native`): 13 of 15 pass. `runtime_tests`
and `host_tests` fail on expectations written for Populous (its entry point,
IAT slot count, `weanetr` data imports, `data\VCONFIG0.*`, its frame-clock
addresses) and on the DirectInput mouse clamp, which cannot work while
`mouse_vtable` is a sentinel. The checks added for the module-handle change
pass, and Populous's own binary still reports 433 checks and 0 failures.

**Next**, in order: `IDirect3D9` and `IDirect3DDevice9`, the D3DX effect
runtime, and Metal translations of the 31 compiled effects; then
`DirectInput8Create`; then `waveOut` streaming. The graphics layer is the
bulk of the remaining work and is unchanged in size by today.

#### 2026-09-16: kit main 4574a35 clears discovery; 40 functions need MMX, SSE2 and four rarer ops

Re-pinned the kit from the `game-dir-wip` snapshot to `main` at 4574a35
(2026-09-14), the commit the majesty, pharaoh and siege repositories pin.
Since the snapshot the kit landed the translator's non-returning calls,
`--allow-table-gaps` through `tools/build.py`, `[translate] entry_points`,
`[game] heap_base`, winmm timers, gdi32, advapi32, version, wsock32, Miles,
Bink through FFmpeg, DirectShow, and Android, Linux and Windows packaging.
The config test passes on the new schema; the import table above is
measured against this commit (163 of 300 imports shimmed).

`tools/build.py --regenerate --target gen --allow-table-gaps "..."` on the
same listings: the non-returning-call rule removes every fall-through
target of the previous run. Discovery now completes, the translator parses
all 25,768 functions and emits code for all but 40. Those 40 fail at the
instruction level, and the 73 literal dispatch targets the final gate still
reports are, with one exception, direct calls into them (`0x007c45f0`, the
`STMXCSR` routine, is called from 19 sites; `0x006e9020`, the `FLDLN2`
routine, from two). The exception is `fn_00666590` jumping to `0x00666583`,
an address one byte inside the listing's previous instruction.

| Instructions the translator lacks | Functions | Notes |
| --- | --- | --- |
| MMX: `MOVQ`, `PXOR`, `MOVD`, `EMMS` on `MM0`-`MM7` | 30 | a block of blitters and converters around `0x00811000`-`0x00818000`, one `EMMS` at `0x007f9fc2` |
| SSE2: `MOVAPD`, `MOVLPD`, `PXOR` on `XMM` | 3 | the CRT's floating-point helpers at `0x007ce670`, `0x007ce719`, `0x008128fe` |
| `STMXCSR` | 3 | the CRT's floating-point control routines, `0x007c45f0` among them |
| x87 constants `FLDL2E`, `FLDLN2` | 2 | `exp` and `log` helpers |
| `FNSTENV`, `LAHF` | 2 | CRT floating-point exception and flag inspection |

So the translator gap for this game is small and bounded: an `MM` register
file with the four MMX moves and `PXOR`, `XMM` operands for two moves and
`PXOR`, `STMXCSR` (store the default control word), two x87 constant loads,
and `FNSTENV` and `LAHF`. No unmodelled mnemonic appears outside those
forty functions. The 20 undecoded jump-table sites are accepted for now
through `--allow-table-gaps`; their switch shapes still need the decoder.
The compile of the emitted code has not run yet, because the gate stops
the build before it stages `build/recomp/gen`.

Order of work from here, all of it in the kit:

1. Translator: the MMX and SSE2 forms above, `STMXCSR`, the two x87
   constants, `FNSTENV`, `LAHF`; then the MSVC 7.1 jump-table shapes at the
   20 accepted sites. Then compile the translation and size it.
2. Shims the boot path needs: `DirectInput8Create`, `SHGetFolderPathA`,
   `RegCreateKeyA`, the missing user32 window-class and capture calls, then
   the run report for the rest of kernel32; `WS2_32`, `TAPI32`, `NETAPI32`
   and DirectSound capture as clean failures.
3. winmm `waveOut` streaming over the kit's mixer.
4. Direct3D 9 and the D3DX effect shim, the Metal ports of the 31 effects.
   The kit's `siege-delphi` branch (2026-09-15) starts DXGI, Direct3D 11
   and `D3DCompile` shims for a shader-model game; that is the nearest
   prior art in the kit for this layer.
5. Only then the hooks: replace the sentinels in `game.toml`.

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
