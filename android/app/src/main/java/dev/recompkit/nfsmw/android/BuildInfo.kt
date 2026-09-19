package dev.recompkit_nfsmw.android

// Build information for the APK. The committed placeholder keeps the repo
// buildable on its own (a development shell with no game library); CI
// (tools/android/validate_zip.py --stage) overwrites this file with the
// values of the uploaded zip.
object BuildInfo {
    const val VERSION_NAME = "0.0.0-dev"
    const val BUILD_ID = "dev"
    const val ASSET_SHA = ""
    const val ASSET_COUNT = 0
    const val ASSET_BYTES = 0L
    const val LIB_SHA = ""
    const val SOURCE_URL = ""
    const val BUILT_AT = ""

    val hasGame: Boolean get() = LIB_SHA.isNotEmpty()
    val hasAssets: Boolean get() = ASSET_COUNT > 0
    val isReleaseBuild: Boolean get() = BUILD_ID != "dev"
}
