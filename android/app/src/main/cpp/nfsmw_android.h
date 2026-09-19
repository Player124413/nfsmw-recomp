// nfsmw_android.h - the ABI between the Android launcher shell and the
// recompiled game library (libnfsmw.so).
//
// The game library must be built for arm64-v8a (aarch64 ELF shared object)
// and export, with C linkage:
//
//   REQUIRED   nfsmw_android_init, nfsmw_android_key
//   OPTIONAL   everything else - the launcher probes for the rest and
//              degrades cleanly (a missing optional symbol is logged, not fatal).
//
// Threading:
//   - nfsmw_android_init runs on the game thread and BLOCKS until the game
//     exits (its own quit flow, or nfsmw_android_request_shutdown honoured).
//   - nfsmw_android_key / nfsmw_android_touch may be called from the UI
//     thread at any time once init has been called; the host must queue them.
//   - Settings callbacks may be called from the UI thread at any time.
//
// The launcher passes the absolute path of the staged game directory (the
// game installation minus [bundle].exclude, see docs/android.md) and an
// optional settings JSON written by the launcher:
//   { "render_scale": 1.0, "frame_limit": 0, "audio": true, "haptics": true }
// (render_scale 0.5..2.0, frame_limit 0 = unlimited).
//
// See docs/android.md for the whole contract and tools/android/validate_zip.py
// for the release zip layout this library ships in.
#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define NFSMW_ANDROID_ABI_VERSION 1

// Symbol bitmask the launcher's probe returns, one bit per exported symbol.
#define NFSMW_SYM_INIT              (1 << 0)
#define NFSMW_SYM_KEY               (1 << 1)
#define NFSMW_SYM_SHUTDOWN          (1 << 2)
#define NFSMW_SYM_PAUSE             (1 << 3)
#define NFSMW_SYM_RESUME            (1 << 4)
#define NFSMW_SYM_TOUCH             (1 << 5)
#define NFSMW_SYM_REQUEST_SHUTDOWN  (1 << 6)
#define NFSMW_SYM_SET_RENDER_SCALE  (1 << 7)
#define NFSMW_SYM_SET_FRAME_LIMIT   (1 << 8)
#define NFSMW_SYM_RESIZE            (1 << 9)

// Key codes the launcher sends through nfsmw_android_key. The Android host
// maps them onto the DirectInput keyboard state the game polls (the same
// vocabulary the smoke scripts use: UP, DOWN, LEFT, RIGHT, RETURN, ...).
enum {
    NFSCODE_NONE = 0,
    NFSCODE_UP = 1,
    NFSCODE_DOWN = 2,
    NFSCODE_LEFT = 3,
    NFSCODE_RIGHT = 4,
    NFSCODE_RETURN = 5,
    NFSCODE_ESCAPE = 6,
    NFSCODE_SPACE = 7,
    NFSCODE_KEY_M = 8,
    NFSCODE_KEY_C = 9,
    NFSCODE_KEY_R = 10
};

typedef struct nfsmw_android_env {
    int32_t abi_version;      // = NFSMW_ANDROID_ABI_VERSION
    int32_t screen_width;     // px at start
    int32_t screen_height;    // px at start
    int32_t screen_dpi;
    const char* game_dir;     // absolute path of the staged game directory
    const char* settings_path;// absolute path of the settings JSON, or NULL
} nfsmw_android_env;

// level: 0 verbose, 1 info, 2 warn, 3 error.
typedef void (*nfsmw_android_log_fn)(int32_t level, const char* message);

// REQUIRED. Blocks until the game exits. 0 = clean exit, nonzero = error
// code (reported verbatim in the launcher's error dialog).
int32_t nfsmw_android_init(const nfsmw_android_env* env, nfsmw_android_log_fn log);

// REQUIRED. down: 1 key down, 0 key up. Thread-safe (may come from the UI thread).
void nfsmw_android_key(int32_t code, int32_t down);

// OPTIONAL. Cleanup, called by the launcher once, after init returns.
void nfsmw_android_shutdown(void);

// OPTIONAL. Activity pause / resume (screen off, app switch, ...).
void nfsmw_android_pause(void);
void nfsmw_android_resume(void);

// OPTIONAL. Raw screen touch in px (what the launcher's overlay lets through:
// menu taps and the like). action: 0 down, 1 move, 2 up, 3 cancel.
void nfsmw_android_touch(int32_t pointer_id, int32_t action, float x, float y);

// OPTIONAL. Set a quit flag the game loop checks; the loop exits and init
// returns. The launcher calls this from its "Leave the game" dialog.
void nfsmw_android_request_shutdown(void);

// OPTIONAL. Settings callbacks (the launcher calls them when the user changes
// settings; defaults arrive via settings_path in env).
void nfsmw_android_set_render_scale(float scale);
void nfsmw_android_set_frame_limit(int32_t fps); // 0 = unlimited
void nfsmw_android_resize(int32_t width, int32_t height);

#ifdef __cplusplus
}
#endif
