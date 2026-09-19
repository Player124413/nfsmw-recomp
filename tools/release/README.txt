Need for Speed: Most Wanted - native port (SpeedRecomp)

There is no full release yet. This port is at the analysis stage: the
executable has been measured against the recomp kit and the work it needs is
listed in docs/analysis.md. When a build exists, this file will describe how
to point it at your own copy of the game, as populous-recomp's release notes
do.

Android: the pipeline already builds the APK from a zip. Run the "Android"
workflow with a link to a zip holding lib/arm64-v8a/libnfsmw.so (the
recompiled game, aarch64) and the game files under game/; see
docs/android.md. `tools/android/make_zip.py` builds the zip locally from a
kit build and your game directory.

You need your own copy of the game: the PC Black Edition whose speed.exe has
SHA-256 80774c2e5d619b4f120b48d4462896fd504c263399d203a238769cffde1d253c.
No game files are distributed with this project.
