# Android

The Android port is a **shell** in this repository plus a **build pipeline**.
The recompiled game itself (the kit's work) is not in the repo — it ships as
one zip, and the pipeline turns that zip into an installable APK:

```
your kit build          this repository
─────────────           ─────────────
libnfsmw.so   ─┐
game dir      ─┴─> release.zip ──link──> Android workflow ──> APK
                                       (android/)  launcher, touch
                                                   controls, stager
```

- **The shell** (`android/`): a Kotlin launcher (status, Play, settings,
  about), the full-screen game activity, the touch-control overlay with the
  Edit mode, the JNI bridge (`src/main/cpp/bridge.cpp`) and the asset stager.
  It builds and runs **without** the game library (a development shell) —
  that is what the push/PR check enforces.
- **The pipeline** (`.github/workflows/android.yml`): run the **Android**
  workflow with a link to your zip; it validates the zip, stages the library
  and the game files into the APK, builds, signs and uploads the APK (and a
  GitHub Release, if you ask).
- **The game library**: built by the kit for `arm64-v8a` and exporting the
  ABI in `android/app/src/main/cpp/nfsmw_android.h`. Until the kit grows an
  Android host target, build that library with your own NDK setup against
  the same header; the launcher contract is the header, not the toolchain.

You still need your own copy of the game (see NOTICE); the zip contains
*your* files, and nothing here distributes them.

## The release zip

```
release.zip
├── lib/
│   └── arm64-v8a/
│       └── libnfsmw.so        # required: the recompiled game, aarch64 ELF
└── game/                      # required: the game directory
    ├── CARS/ FRONTEND/ GLOBAL/ SOUND/ TRACKS/ MOVIES/ ...
    └── (your installation minus [bundle].exclude from game.toml)
```

Rules the validator (`tools/android/validate_zip.py`) enforces:

- The library must be named `libnfsmw.so`; `lib/<abi>/libnfsmw.so` anywhere
  (root, or any directory) is accepted and `lib/arm64-v8a/` is preferred.
- It must be an **ELF64 little-endian aarch64** shared object. A Windows
  DLL renamed, an armeabi-v7a build, or a text file fails the run with a
  message.
- The game files live under a top-level `game/` directory (an `assets/`
  directory, or loose top-level directories, also work).
- Warnings (not failures): a game directory under ~50 MB, missing
  `SOUND/` `TRACKS/` `GLOBAL/`.

Build the zip locally from the repository root:

```sh
python3 tools/android/make_zip.py \
    --lib build/recomp/android/libnfsmw.so \
    --game-dir "/path/to/Need For Speed Most Wanted" \
    --out release.zip
```

`make_zip.py` applies `[bundle].exclude` from `game.toml` (the same rules the
iOS bundle uses: no uninstaller, no Windows DLLs, no saves, ...) plus any
`--exclude` patterns you add, and stores the library under
`lib/arm64-v8a/libnfsmw.so` whatever its local name.

## Building the APK

1. Upload `release.zip` anywhere that serves a plain link (a release asset,
   an HTTP server, ...).
2. Open the repository's **Actions → Android** and press **Run workflow**.
3. Paste the link (plus a bearer token if the link is private), a version
   name, and optionally tick **Publish a GitHub Release**.
4. The run downloads the zip, prints a validation report (library kind,
   file count, size, extension histogram, warnings), stages it, builds and
   uploads `nfsmw-android-v<version>-<build id>-arm64.apk` as an artifact —
   and as a release asset when asked. The build id is the first 12 hex of
   the zip's SHA-256; the launcher shows it in the About dialog.

**Size.** The APK is roughly the size of the game files in the zip (they
are stored uncompressed). GitHub caps workflow artifacts and release assets
at 2 GB, and a full install with `MOVIES/` in it goes over that. Two ways to
ship a large build: exclude the cinematics from the zip (the game runs
without them), or set the repository secrets `S3_BUCKET`, `S3_ACCESS_KEY_ID`,
`S3_SECRET_ACCESS_KEY` (and `S3_ENDPOINT_URL` for R2/MinIO) — builds over
2 GB are then uploaded to `s3://<bucket>/android/` and the run reports the
URI.

### Signing

Without signing secrets the APK is signed with the debug key: installable,
not for distribution. To sign properly, set the repository secrets:

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 release.keystore` (the whole file) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

The keystore is written to `android/keystore/release.keystore` (ignored) at
build time; nothing ever lands in the repo.

## The ABI (what `libnfsmw.so` must export)

The full contract is `android/app/src/main/cpp/nfsmw_android.h`; in short:

- **Required**: `nfsmw_android_init(env, log)` (blocks until the game exits)
  and `nfsmw_android_key(code, down)`.
- **Optional** (probed at start; missing ones are logged, not fatal):
  `nfsmw_android_shutdown`, `_pause`, `_resume`, `_touch` (raw screen
  touches the overlay lets through), `_request_shutdown` (the launcher's
  "Leave the game" uses it; without it the launcher sends ESC instead),
  `_set_render_scale`, `_set_frame_limit`, `_resize`.
- `env` carries screen size/dpi, the absolute path of the staged game
  directory and the path of the settings JSON.
- Key codes are the `NFSCODE_*` values in the header — the same vocabulary
  the smoke scripts use (`UP`, `DOWN`, `LEFT`, `RIGHT`, `RETURN`, ...). The
  host maps them onto the DirectInput keyboard state the game polls.
- Threading: `init` runs on the game thread; `key`/`touch`/settings calls
  come from the UI thread and the host must queue them.
- The settings JSON (written by the launcher, read at init):
  `{"render_scale": 1.0, "frame_limit": 0, "audio": true, "haptics": true}`
  — `render_scale` 0.5..2.0, `frame_limit` 0 = unlimited.

A game that does not export a required symbol, or whose `init` returns
nonzero, fails fast in the launcher with a dialog that shows the reason
(copyable) and the build info — no silent crash.

## The launcher

- **Status card**: game library (build id + library SHA), game files
  (staged / bundled, with size), device (ABI + Android version).
- **Play**: stages the bundled game files into internal storage on first
  launch (progress dialog; skipped on later launches and after updates —
  the staged tree is identified by the build's asset hash), then starts the
  game full-screen in landscape.
- **Touch controls**: opens the editor screen (below).
- **Settings**: render scale (0.5–2.0), frame limit (off/30/60/90/120),
  sound, vibration. Saved to `files/nfsmw-settings.json`, which the game
  reads through the ABI.
- **About**: the build the APK was made from — version, build id (the zip
  hash), library SHA, file count/size, the source link, the build time.

## Touch controls

Defaults (landscape, left thumb / right thumb):

| Button | Key | Default |
| --- | --- | --- |
| steer left / steer right | `LEFT` / `RIGHT` | bottom-left, 128 dp |
| handbrake | `SPACE` | above them, 108 dp |
| brake (and reverse) | `DOWN` | right of the steering, 112 dp |
| gas | `UP` | bottom-right, 150 dp |
| map / camera | `M` / `C` | top-left, 72 dp |
| menu (pause) | `ESC` | top-right, 76 dp |

Behaviour in game:

- A pointer that lands on a button drives it: the key goes down, the button
  stays down while the finger is held, and releases when the finger lifts
  **or slides clear** of the button (1.6× radius), so you can rest your
  thumb without losing control.
- Pointers that land on nothing are forwarded to the game untouched (the
  `nfsmw_android_touch` path) — menus keep working.
- Multi-touch is tracked per pointer, so gas + steering + handbrake work
  simultaneously.
- The Edit button (top-right, in game and on the editor screen) toggles
  **edit mode**:
  - **drag** a button to move it (8 dp grid, clamped to the screen),
  - **pinch** it (or use the size slider) to resize it (56–240 dp),
  - **tap** to select, **double-tap** to hide or show it,
  - the toolbar lists every button: tap a chip to select, long-press to
    hide/show (the eye icon shows the state),
  - **Reset** restores the defaults, **Done** leaves edit mode.
- The layout persists to `files/controls-layout.json` (versioned, tolerant
  of hand edits and unknown fields; a corrupt file is kept as
  `controls-layout.json.bad` and the defaults are used). It is re-clamped
  for every screen size, so the same layout survives updates and other
  devices.

## Development without a game

```sh
cd android
gradle wrapper --gradle-version 8.9   # once
./gradlew testReleaseUnitTest assembleDebug
```

This produces the development shell: the launcher runs, the controls editor
works, Play is disabled with a reason. No game files needed.

## Troubleshooting

- **"The zip does not contain libnfsmw.so"** — the library has a different
  name or is nested somewhere odd; `make_zip.py` produces the right layout.
- **"is a elf-other/arm32 binary"** — you uploaded the Windows build or an
  armv7 library; rebuild for `arm64-v8a`.
- **The game library is missing required symbols** — the library does not
  export `nfsmw_android_init` / `nfsmw_android_key` (or was built without C
  linkage). Check the export list: `aarch64-linux-gnu-nm -D libnfsmw.so |
  grep nfsmw_android`.
- **The APK is large** — it is: the game files ride in it (the kit's iOS
  bundle does the same). `MOVIES/` and `SOUND/` dominate.
- **The run fails at upload with a 2 GB error** — GitHub's artifact and
  release-asset limit. Either slim the zip (drop `MOVIES/` first — the game
  runs without cinematics) or configure the S3 secrets from the "Size" note
  above; the pipeline then uploads big builds to `s3://<bucket>/android/`.
- **A game crash kills the app** — by design the game runs in the app's
  process (like the iOS build, which compiles the game into the app). The
  error dialog's copyable log and `adb logcat` (tags `nfsbridge`,
  `nfsmw-game`) carry the details.
- **First launch takes a while** — that is the one-time staging of the game
  files into internal storage; it has a progress dialog and never repeats
  for the same build.
