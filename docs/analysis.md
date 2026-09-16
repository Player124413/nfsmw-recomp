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

#### 2026-09-17: what a rasterizer would draw, and why it would still be black

The draw path now decodes each vertex declaration and prints the vertices.
The seven opening draws are three kinds of quad:

| Draws | Layout | Where | What it is |
| --- | --- | --- | --- |
| 1-5 | `POSITION` float3, `COLOR`, four `TEXCOORD` pairs with sub-texel offsets | 160x120 off-screen targets | a separable filter chain: blur or downsample |
| 6 | `POSITION` float4, one `TEXCOORD` | the back buffer, full screen | the final composite |
| 7 | `POSITION` float3, `COLOR` `0x80808080`, one `TEXCOORD` | the back buffer, a 16x32-pixel corner | a small textured indicator |

All positions are in clip space, from -1 to 1, so the effects' vertex stages
pass them through and a viewport transform is all a rasterizer needs for
these. That part would be easy.

It would not produce a picture. Every target those passes read was cleared to
black and no scene has been drawn into any of them, so the filter chain and
the composite are black in, black out. Only draw 7 could light pixels, and
its texture, like every texture in these draws, is bound through
`ID3DXEffect::SetTexture`. The effect is accepted without being parsed, so
the device has no idea what is bound: every draw reports texture 0.

This settles the order of the remaining work, and it is not the order a
quick look suggested. A quad filler first would show, at best, a grey square
in a corner. The first real image needs:

1. the compiled effect format parsed, so parameters, techniques, passes,
   sampler bindings and render states are known;
2. `SetTexture`, `SetMatrix` and the rest of the parameter calls recorded
   against those parameters;
3. each pass's vertex and pixel shader bytecode translated and executed,
   on Metal or on the CPU;
4. the world itself, which the game has not started drawing in this part of
   the run.

That is the large piece this port was always going to need, and it is now the
only piece left in front of a picture.

#### 2026-09-17: the first presented frames, and why they are black

The presenter now accepts 32-bit frames, and the Direct3D 9 device clears,
copies and presents for real. Two frames reach the host and the frame dump
writes both. Both are black, and the run says that is correct:

```text
clear 1:  flags 7 colour 00000000, the back buffer
present:  back buffer, 0 of 3168 sampled pixels lit
clear 2-7:  colour 00000000, off-screen targets of 640x480, 160x120 and 20x15
clear 8-13: colour ff000000, six 16x16 targets
StretchRect: back buffer -> off-screen 640x480
draws 1-7:  seven screen-space quads
present:  back buffer, 0 of 3168 sampled pixels lit
```

Every clear is black. The one copy goes from the back buffer to an off-screen
target, which is a post-processing grab rather than a composition onto the
screen. So the only thing in these frames that would put a non-black pixel on
the back buffer is the seven quads, and nothing draws them yet.

This closes the question of the presentation path: it works, and a black
window is the honest output of the port as it stands. The first visible
image now depends entirely on rasterizing those quads, which needs the
vertex layouts decoded from the declarations, the bound textures sampled,
and the effect's pixel stage reproduced closely enough to give the right
colour.

#### 2026-09-16: what the draws contain

The draw entry points now report their arguments. Every draw in the opening
of the game is the same shape:

```text
draw 1: triangle fan, 2 primitives, 48-byte vertices inline at 0efff294
draw 2: triangle fan, 2 primitives, 48-byte vertices inline at 0efff290
draw 3: triangle fan, 2 primitives, 48-byte vertices inline at 0efff27c
draw 4: triangle fan, 2 primitives, 48-byte vertices inline at 0efff290
draw 5: triangle fan, 2 primitives, 48-byte vertices inline at 0efff27c
draw 6: triangle fan, 2 primitives, 24-byte vertices inline at 0efff368
draw 7: triangle fan, 2 primitives, 32-byte vertices inline at 0efffa10
```

Two primitives as a fan is four vertices: a quad. The addresses are in the
guest stack, so each quad is built as a local and handed to the call. Strides
of 48, 24 and 32 bytes are three different vertex layouts, which matches the
12 vertex declarations created earlier.

This says something useful about the order of the remaining work. These are
screen-space quads - a front end, a loading screen, a fade - not the world.
A first visible image therefore does not need the 31 compiled effects
translated to Metal. It needs a swap chain backed by a drawable, the render
target bound to it, one textured-quad pipeline, and the inline vertices
uploaded. The world, its shaders and its shadow cube can come afterwards.

That is the smallest honest next milestone: one quad on screen, drawn from
the game's own vertices and its own texture.

#### 2026-09-16: correction, the game is drawing

The previous entry says no draw call has been reached. That is wrong, and the
error was in how I counted rather than in the run. The census tested for
`DrawPrimitive` and `DrawIndexedPrimitive`; this game draws with
`DrawPrimitiveUP`, which matches neither test. Run 19 contains seven draw
calls, and they sit exactly where draws belong:

```text
ID3DXEffect::Begin          12
ID3DXEffect::BeginPass       7
ID3DXEffect::CommitChanges   7
IDirect3DDevice9::DrawPrimitiveUP   7
ID3DXEffect::EndPass         7
ID3DXEffect::End            12
```

`UP` means user pointer: the geometry is passed inline with the call rather
than from a bound vertex buffer, which is why `SetStreamSource` never appears
while `CreateVertexBuffer` and its locks do.

So the full shape of a frame is present: render target and depth surface
selected, viewport and transforms set, render states and texture stage states
applied, an effect technique begun, a pass begun, parameters committed,
geometry drawn, the pass and effect ended, and the frame presented. Fifty
`SetRenderState` calls, 26 render-target switches, 20 texture-stage settings
and 14 viewport changes say the same thing.

Nothing is unresolved and nothing is rasterized. The kit records these draws
and produces no pixels, so the window stays empty. What stands between here
and an image is the GPU work itself: a swap chain and render targets backed by
Metal textures, vertex and index data uploaded, and the shader programs the
effects carry translated to Metal. That is the large remaining piece, and it
is unchanged by today.

#### 2026-09-16: nothing unresolved

`IDirect3DTexture9::LockRect` takes five dwords with `this` and the cube form
six; both were declared one short. The game locks a texture, copies pixels in
with `rep movsd`, and unlocks it by reloading the texture pointer from the
stack - so the missing pop moved that slot and the unlock called through zero.

The run after the correction is the cleanest of the bring-up:

| | Run 18 | Run 19 |
| --- | --- | --- |
| calls into nothing | 1 | **0** |
| `LockRect` / `UnlockRect` | 2 / 1 | 330 / 330 |
| textures created | 13 | 145 |
| `SetTexture` | 0 | 33 |
| `SetVertexDeclaration` | 0 | 12 |
| `Clear` | 7 | 14 |
| frames presented | 1 | 2 |
| draw calls | 0 | 0 |
| log lines | 3,142 | 8,095 |

Every guest call now reaches something that answers it. The game uploads its
texture set, binds textures, describes vertex formats, clears its targets and
presents frames. It has not issued a draw.

Three of the last four defects were the same mistake in different places: a
vtable slot declaring fewer arguments than it takes. The pop count is part of
the interface, and getting it wrong corrupts the caller rather than the
callee, which is why each one surfaced as a crash or a null call somewhere
unrelated.

#### 2026-09-16: six cube faces, and two runs that proved nothing

The cube texture was still being thrown away after one face. The reason was
mine and it is a COM rule: `GetCubeMapSurface` and `GetSurfaceLevel` hand out
a view **into** a texture, and taking one references the texture. The game
does the ordinary thing - take face zero, release the cube, keep using the
cube for the other five faces - and my standalone surfaces held no reference,
so that release destroyed it and face one was fetched through a dead pointer.
Views now reference their container and record it, so `GetContainer` answers
too. The game walks all six faces.

| | Run 15 | Run 18 |
| --- | --- | --- |
| `GetCubeMapSurface` | 1 | 6 |
| `IDirect3DCubeTexture9::Release` | 1 | 6 |
| calls into nothing | 1 | 1, and in a different place |
| draw calls | 0 | 0 |

Runs 16 and 17 are worth recording because they proved nothing and looked as
though they did. In the first, the edit script hit a failed assertion and
exited before writing, so the build compiled and ran unmodified code. In the
second, an anchor matched the wrong function, the compile failed, and the run
executed the previous binary. Both printed a plausible census, and both
reported success at the level of exit codes. A run only means something if
the change is verifiably in the binary that produced it.

The surviving call into nothing has moved, which is the point of fixing these
one at a time: it used to be in the cube loop and is now in the texture upload
path, right after `CreateTexture` and `IDirect3DTexture9::LockRect`.

#### 2026-09-16: what the game actually asks for, counted

A verbose run (`RECOMP_LOG=2`) for forty seconds, with no crash in it, made
148 distinct guest API calls. The graphics half of that census is the clearest
statement of where this port is:

| Call | Times | What it says |
| --- | --- | --- |
| `D3DXCreateEffectFromResourceA` | 62 | it loads every effect it has |
| `ID3DXEffect::GetDesc` | 93 | and inspects each one |
| `SetTechnique`, `ValidateTechnique`, `OnLostDevice`, `Release` | 31 each | it picks a technique per effect, then discards half of them |
| `CreateVertexDeclaration` | 33 | it describes 33 vertex layouts |
| `CreateTexture` | 13 | and starts filling textures |
| `IDirect3DTexture9::LockRect` / `UnlockRect` | 2 / 1 | uploading image data |
| `CreateDepthStencilSurface` | 8 | shadow and depth targets |
| `SetRenderTarget`, `SetDepthStencilSurface` | 6 each | it is switching render passes |
| `Clear` | 7 | clearing them |
| `BeginScene`, `EndScene`, `Present` | 1 each | one frame boundary reached |
| `CreateCubeTexture` | 2 | the shadow cube |
| `GetCubeMapSurface`, then `Release` | 1, 1 | it asked for a face, got nothing, and threw the cube away |

That last row was the remaining defect, and it is the same mistake as the
effect descriptors: a method that returns success without writing what the
caller asked for. `GetCubeMapSurface` is now real, each face a surface of its
own kept on the texture. `GetLevelCount` was worse than a stub - it returns a
count rather than an HRESULT, so returning `D3D_OK` told the game its textures
had no levels at all; it now returns 1. `GetLevelDesc` fills its structure.

No `DrawPrimitive` has been reached. The game is still setting up: it is
loading effects, describing vertex formats, allocating targets and clearing
them. A draw call is the next milestone, and pixels need the whole Metal path
behind it, which does not exist yet.

#### 2026-09-16: four pop counts, and a run with no crash in it

Run 11 died at `EIP=0` immediately after `IDirect3DDevice9::CreateTexture`.
The cause was mine and it was arithmetic: a vtable slot's argument count is
part of the interface, and four of mine were one short.

| Slot | Declared | Actual |
| --- | --- | --- |
| `IDirect3DDevice9::CreateTexture` | 8 | 9 |
| `IDirect3DDevice9::CreateVolumeTexture` | 9 | 10 |
| `IDirect3DDevice9::ProcessVertices` | 6 | 7 |
| `ID3DXEffect::GetParameterBySemantic` | 4 | 3 |

Each call through one of them left four bytes on the guest stack, so the
caller returned into whatever followed. With the counts corrected, run 12 has
no SIGBUS in it at all: the game boots, creates its device and resources,
loads its effects, streams audio through 2,815 sink pulls without a single
late or starved buffer, and runs until it is stopped.

One call into nothing remains, the cube-texture global described above. It is
the next thing to chase, and it is a shim question rather than a translation
one. No draw call has been reached yet.

The whole effect vtable was audited against the SDK signatures afterwards;
the remaining seventy-odd slots are right.

#### 2026-09-16: device resources, and the last call into nothing

The device now creates what the game asks for: textures, cube textures,
surfaces, vertex and index buffers, vertex declarations and queries, each an
object over real guest memory, so a `Lock` hands back storage the game can
fill. The device keeps its own back buffer and depth buffer and answers
`GetBackBuffer` and `GetDepthStencilSurface` with them.

| Run | Calls into nothing | How it ended |
| --- | --- | --- |
| 9, with resources | 3 | ran the full 90 seconds, stopped by hand |
| 10, plus two entry points | 1 | SIGBUS in a guest worker thread |

Two of the three were not graphics at all. A vtable slot call at `007ee9fe`
and a function-pointer call at `00823380` each named a `.text` address no
listing owned, the same case as the CRT helpers; naming them in
`[translate] entry_points` removed both.

The survivor is the interesting one, and it is mine. `FUN_006bd4b0` sets up
the game's shadow-map cube:

```text
CreateCubeTexture(device, edge, 1, 1, format, 0, &g_cube, 0)   device slot 25
  then, per face: g_cube->GetCubeMapSurface(face, 0, &g_faces[i])  slot 18
  and            CreateDepthStencilSurface(...)                    slot 29
```

The call returned success and `g_cube` stayed null, so the game called
`GetCubeMapSurface` through a null pointer. Guest address 0 is mapped, so
reading a vtable through it yields zero and the call goes to nothing rather
than faulting, which is why this looked like a missing translation. The cause
was in the shim: every creator reported `D3D_OK` whether or not the interface
view was actually made. They now report the failure instead, which is both
correct and the only way the game can take its own fallback path.

Run 10 got further than run 9 and died sooner, which is the normal shape of
this work: each fix moves the failure later.

#### 2026-09-16 (later still): the game runs

With the effect descriptions filled in, the game stopped hanging and simply
ran. It was stopped by hand after about three minutes; it had not crashed,
and it was using a full core.

What it did in that time:

| | |
| --- | --- |
| Direct3D | created its device, cleared, presented, and asked for the resources it wants to draw with |
| Effects | loaded `IDI_WORLD_FX`, walked its parameters, validated a technique, set values on it |
| Sound | created DirectSound buffers and streamed them: 2,815 sink pulls, none late, none starved |
| Threads | guest worker threads reached the host and were serviced |

The resources it asks the device for, and does not get, are now a list rather
than a guess:

```text
CreateTexture            CreateCubeTexture        CreateVertexBuffer
CreateIndexBuffer        CreateVertexDeclaration  CreateDepthStencilSurface
CreateQuery              GetBackBuffer            GetDepthStencilSurface
SetRenderTarget          SetDepthStencilSurface
```

and from the effect: `SetInt`, `ValidateTechnique`, `GetAnnotationByName`,
`OnLostDevice`. The seven remaining "call to unknown target" lines are the
game calling methods on the interfaces those creators never returned.

**What this is and is not.** The game boots, runs, streams its audio and
drives its own render loop without crashing. It draws nothing: there is no
render target, no swap chain, no texture or buffer storage, and no shader
translation, so the window stays empty. Sound is real; the picture is not.

The next piece is device resources, which is ordinary work: textures, vertex
and index buffers, surfaces, and a swap chain wired to the kit's Metal
backend. After that comes the part that is not ordinary: translating the
game's compiled shader bytecode, about 120 programs across 31 effects, into
Metal. That remains the bulk of the port.

#### 2026-09-16 (later): the game creates a device and enters its render loop

Two new kit modules, both on the `nfsmw` branch.

`dx/d3d9.cpp` is Direct3D 9: the factory object, the adapter and format
queries a game makes before it commits to a device, and `CreateDevice`. The
device's vtable is complete and in interface order, since a guest calls these
by slot index, but only a few slots do anything; the rest report themselves
once and return `D3D_OK`. `dx/d3dx9.cpp` is D3DX 9: the matrix and vector
maths for real, an effect pool, and effect objects for the effects compiled
into the executable's resources.

What the game did with them, in one run:

| Step | Evidence |
| --- | --- |
| asked for the library | `Direct3DCreate9(SDK 32)` returned an interface |
| created its device | `CreateDevice 640x480, window 00020004` |
| entered its render loop | `Clear`, then `Present` |
| started describing geometry | `CreateVertexDeclaration` |
| loaded its first effect | `IDI_WORLD_FX`, the world shader |
| walked the effect's parameters | `GetDesc`, `GetParameter`, `GetParameterDesc` |

It then hung. The descriptor methods returned success without filling the
structures they were handed, so the game read whatever was already in that
memory as its parameter and technique counts and iterated on it. The four
descriptors now zero their structures and report one technique of one pass
and no parameters, which keeps the game's own loops finite. The process had
to be killed; it was not a crash.

This is the furthest the port has run. The game is inside its rendering code,
asking for shader parameters by name. Nothing draws: no render target, no
swap chain, no shader translation, and the 31 compiled effects are accepted
without being parsed. Those are the next pieces, and they are the large ones.

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

**Two more runs the same day.** Declaring stdcall pop counts removed every
stack-drift warning (run 4: zero, against eleven in run 3), and the game got
as far as asking for its window. `RegisterClassExA` had no implementation, so
`CreateWindowExA` found no class and failed silently, and the game carried on
with no window at all; the kit now registers the Ex structure, and run 5
creates the window cleanly. Both runs then end the same way: `Direct3DCreate9`
returns zero, the game calls a method on the interface it did not get, and
what follows is garbage (`call to unknown target 7972656c` is four bytes of a
string read as a function pointer). The last abort is an indirect jump to zero
at `007e7d76`.

Nothing is left between the game and a frame except Direct3D 9 itself.

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
