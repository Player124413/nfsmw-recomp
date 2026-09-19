# android/ — the Android shell of nfsmw-recomp

The launcher, the game activity, the touch controls (with the Edit mode),
the JNI bridge and the asset stager. The recompiled game is **not** built
here: it arrives as `libnfsmw.so` + the game files in a release zip and is
staged into the APK by CI. The full picture, the zip layout and the ABI are
in [../docs/android.md](../docs/android.md).

## Build locally (no game needed)

```sh
cd android
gradle wrapper --gradle-version 8.9   # once; generates gradlew and the jar
./gradlew testReleaseUnitTest         # the layout/JSON unit tests (bare JVM)
./gradlew assembleDebug               # the development shell APK
```

Requires JDK 17 and the Android SDK (compileSdk 34, NDK 26.1.10909125 —
both auto-installed by Gradle once the licenses are accepted:
`yes | sdkmanager --licenses`). The development shell has no game library:
the launcher runs, the controls editor works, Play is disabled with a
reason. That is also what the push/PR check builds.

## Build a release (the normal path)

Don't build the APK by hand. Build the zip and let the pipeline do the rest:

```sh
# from the repository root, with a kit Android build in place
python3 tools/android/make_zip.py \
    --lib build/recomp/android/libnfsmw.so \
    --game-dir "/path/to/Need For Speed Most Wanted" \
    --out release.zip
```

Then run the **Android** workflow (Actions tab) with a link to `release.zip`.
It validates the zip, stages it, builds `assembleRelease`, signs it (from
the repository signing secrets, or the debug key without them) and uploads
the APK as an artifact — and as a GitHub Release when the run is asked to.

## Layout

| Path | What |
| --- | --- |
| `app/src/main/java/dev/recompkit_nfsmw/android/` | the launcher, settings, build info |
| `.../controls/` | the touch overlay and the edit panel |
| `.../game/` | the runtime, the JNI bindings, the asset stager |
| `.../layout/` | the control layout model, codec and JSON (pure Kotlin, JVM-tested) |
| `app/src/main/cpp/` | `bridge.cpp` (the JNI bridge) and `nfsmw_android.h` (the ABI) |
| `app/src/test/java/` | unit tests for the layout/JSON layer |
| `keystore/` | the release keystore, written by CI from secrets (git-ignored) |
| `app/src/main/assets/game/` | the staged game files (git-ignored, CI input) |
| `app/src/main/jniLibs/` | the staged game library (git-ignored, CI input) |

`BuildInfo.kt` is the one generated source: CI overwrites the committed
placeholder with the values of the uploaded zip.
