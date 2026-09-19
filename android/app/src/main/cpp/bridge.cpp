// The JNI bridge between the launcher and the recompiled game library.
//
// The shell must build and run WITHOUT the game library (a development build
// ships no game), so every symbol of libnfsmw.so is resolved at probe time
// with dlsym and cached; input forwarding is a no-op (with a logged warning)
// when the game is not loaded. The game library is loaded by the Kotlin side
// with System.loadLibrary("nfsmw") before probeGame() is called, so RTLD
// DEFAULT finds its exports.
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#include <cstdio>
#include <cstring>
#include <string>

#include "nfsmw_android.h"

namespace {

constexpr char kTag[] = "nfsbridge";

#define BRIDGE_LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define BRIDGE_LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define BRIDGE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

using InitFn = int32_t (*)(const nfsmw_android_env*, nfsmw_android_log_fn);
using KeyFn = void (*)(int32_t, int32_t);
using TouchFn = void (*)(int32_t, int32_t, float, float);
using VoidFn = void (*)();
using ScaleFn = void (*)(float);
using LimitFn = void (*)(int32_t);
using ResizeFn = void (*)(int32_t, int32_t);

InitFn g_init = nullptr;
KeyFn g_key = nullptr;
TouchFn g_touch = nullptr;
VoidFn g_shutdown = nullptr;
VoidFn g_pause = nullptr;
VoidFn g_resume = nullptr;
VoidFn g_request_shutdown = nullptr;
ScaleFn g_set_render_scale = nullptr;
LimitFn g_set_frame_limit = nullptr;
ResizeFn g_resize = nullptr;

std::string g_last_error;
std::string g_game_dir;
std::string g_settings_path;
bool g_probed = false;
bool g_warned = false;

void logFn(int32_t level, const char* message) {
    int prio = ANDROID_LOG_INFO;
    if (level <= 0) prio = ANDROID_LOG_VERBOSE;
    else if (level == 2) prio = ANDROID_LOG_WARN;
    else if (level >= 3) prio = ANDROID_LOG_ERROR;
    __android_log_write(prio, "nfsmw-game", message ? message : "");
}

void* findSymbol(const char* name) {
    return dlsym(RTLD_DEFAULT, name);
}

void resolveAll() {
    std::string missing;
    g_init = static_cast<InitFn>(findSymbol("nfsmw_android_init"));
    g_key = static_cast<KeyFn>(findSymbol("nfsmw_android_key"));
    g_shutdown = static_cast<VoidFn>(findSymbol("nfsmw_android_shutdown"));
    g_pause = static_cast<VoidFn>(findSymbol("nfsmw_android_pause"));
    g_resume = static_cast<VoidFn>(findSymbol("nfsmw_android_resume"));
    g_touch = static_cast<TouchFn>(findSymbol("nfsmw_android_touch"));
    g_request_shutdown = static_cast<VoidFn>(findSymbol("nfsmw_android_request_shutdown"));
    g_set_render_scale = static_cast<ScaleFn>(findSymbol("nfsmw_android_set_render_scale"));
    g_set_frame_limit = static_cast<LimitFn>(findSymbol("nfsmw_android_set_frame_limit"));
    g_resize = static_cast<ResizeFn>(findSymbol("nfsmw_android_resize"));

    if (!g_init) missing += "nfsmw_android_init, ";
    if (!g_key) missing += "nfsmw_android_key";
    if (!missing.empty()) {
        g_last_error = "the game library is missing required symbols: " + missing;
        BRIDGE_LOGE("%s", g_last_error.c_str());
    }
}

void noteMissing(const char* what) {
    // A single warning is enough; these no-ops can fire on every input event.
    if (g_warned) return;
    g_warned = true;
    BRIDGE_LOGW("game symbol %s is not available; ignoring", what);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}

// Returns the NFSMW_SYM_* bitmask of the symbols the game library exports.
// Required symbols missing => -1 and lastError() explains why.
extern "C" JNIEXPORT jint JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_probeGame(JNIEnv*, jclass) {
    g_probed = true;
    g_warned = false;
    resolveAll();
    if (!g_init || !g_key) return -1;
    int mask = 0;
    if (g_init) mask |= NFSMW_SYM_INIT;
    if (g_key) mask |= NFSMW_SYM_KEY;
    if (g_shutdown) mask |= NFSMW_SYM_SHUTDOWN;
    if (g_pause) mask |= NFSMW_SYM_PAUSE;
    if (g_resume) mask |= NFSMW_SYM_RESUME;
    if (g_touch) mask |= NFSMW_SYM_TOUCH;
    if (g_request_shutdown) mask |= NFSMW_SYM_REQUEST_SHUTDOWN;
    if (g_set_render_scale) mask |= NFSMW_SYM_SET_RENDER_SCALE;
    if (g_set_frame_limit) mask |= NFSMW_SYM_SET_FRAME_LIMIT;
    if (g_resize) mask |= NFSMW_SYM_RESIZE;
    return mask;
}

// Runs on the game thread; blocks until the game exits.
extern "C" JNIEXPORT jint JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameStart(
        JNIEnv* env, jclass, jint width, jint height, jint dpi,
        jstring gameDir, jstring settingsPath) {
    if (!g_probed) {
        g_last_error = "gameStart called before probeGame";
        return -1;
    }
    if (!g_init) return -1;  // lastError is set by probeGame

    const char* dir = env->GetStringUTFChars(gameDir, nullptr);
    const char* settings = env->GetStringUTFChars(settingsPath, nullptr);
    g_game_dir = dir ? dir : "";
    g_settings_path = settings ? settings : "";
    env->ReleaseStringUTFChars(gameDir, dir);
    env->ReleaseStringUTFChars(settingsPath, settings);

    nfsmw_android_env e;
    e.abi_version = NFSMW_ANDROID_ABI_VERSION;
    e.screen_width = width;
    e.screen_height = height;
    e.screen_dpi = dpi;
    e.game_dir = g_game_dir.c_str();
    e.settings_path = g_settings_path.empty() ? nullptr : g_settings_path.c_str();

    BRIDGE_LOGI("starting the game: %d x %d @ %d dpi, dir %s",
                width, height, dpi, g_game_dir.c_str());
    int rc = g_init(&e, logFn);
    if (rc != 0) {
        char buf[128];
        std::snprintf(buf, sizeof buf, "nfsmw_android_init failed with code %d", rc);
        g_last_error = buf;
        BRIDGE_LOGE("%s", buf);
    } else {
        BRIDGE_LOGI("the game exited cleanly");
    }
    return rc;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameKey(JNIEnv*, jclass, jint code, jint down) {
    if (g_key) g_key(code, down);
    else noteMissing("nfsmw_android_key");
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameTouch(
        JNIEnv*, jclass, jint pointerId, jint action, jfloat x, jfloat y) {
    if (g_touch) g_touch(pointerId, action, x, y);
    else noteMissing("nfsmw_android_touch");
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gamePause(JNIEnv*, jclass) {
    if (g_pause) g_pause();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameResume(JNIEnv*, jclass) {
    if (g_resume) g_resume();
}

// 0 when the request was passed to the game, -1 when the game does not
// implement nfsmw_android_request_shutdown (the caller falls back to keys).
extern "C" JNIEXPORT jint JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameRequestShutdown(JNIEnv*, jclass) {
    if (g_request_shutdown) {
        g_request_shutdown();
        return 0;
    }
    return -1;
}

// The launcher calls this once after gameStart returns (cleanup hook).
extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameShutdown(JNIEnv*, jclass) {
    if (g_shutdown) g_shutdown();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameSetRenderScale(JNIEnv*, jclass, jfloat scale) {
    if (g_set_render_scale) g_set_render_scale(scale);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameSetFrameLimit(JNIEnv*, jclass, jint fps) {
    if (g_set_frame_limit) g_set_frame_limit(fps);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_gameResize(JNIEnv*, jclass, jint w, jint h) {
    if (g_resize) g_resize(w, h);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_recompkit_nfsmw_android_game_GameNative_lastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_last_error.c_str());
}
