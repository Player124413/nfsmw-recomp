# tools/android

Helpers for the Android port (see `docs/android.md` for the whole picture).

- **`validate_zip.py`** — CI's front door. Validates a release zip (library
  present, an aarch64 ELF; game files present) and, with
  `--stage <src/main>`, stages the library into
  `jniLibs/arm64-v8a/`, the game files into `assets/game/`, writes
  `assets/game/.manifest.json` and the generated
  `BuildInfo.kt`. Exit 0 = ok, 2 = invalid zip (the reason is on stderr).
  Run it locally to pre-flight a zip before uploading:

  ```sh
  python3 tools/android/validate_zip.py release.zip
  ```

- **`make_zip.py`** — builds the release zip from a local kit build and your
  game directory, applying `[bundle].exclude` from `game.toml` by default:

  ```sh
  python3 tools/android/make_zip.py \
      --lib build/recomp/android/libnfsmw.so \
      --game-dir "/path/to/Need For Speed Most Wanted" \
      --out release.zip
  ```

Neither script needs anything beyond the Python 3.11+ standard library.
