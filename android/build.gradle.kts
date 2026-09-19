// The Android shell of the nfsmw-recomp port. The game itself is not built
// here: it ships as libnfsmw.so in the release zip (see docs/android.md) and
// is loaded at run time through src/main/cpp/bridge.cpp.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
