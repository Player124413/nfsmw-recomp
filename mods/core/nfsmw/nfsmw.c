/* nfsmw.c - Most Wanted's engine rate and resolution.
 *
 * SIMULATION RATE. The engine steps its world and caps its frame rate at one
 * frame time, 1/60 s, which lives in four places (the widescreen fix's
 * SimRate, whose analysis this follows):
 *
 *   0x006660ff  `push 0x3c888889`, the main loop's frame time, passed to the
 *               timer constructor at 0x006484e0. A code immediate: this mod
 *               rewrites the argument on the stack before the call runs.
 *   0x008970f0  a pooled .rdata 1/60 with 17 users. Three of them (the real
 *               timestep and two debug timers) read game.toml's
 *               operand_redirects target instead, 0x00a37800, which this mod
 *               fills; the other fourteen keep 1/60.
 *   0x00903290  World_Service's initial timestep, .data, written here.
 *
 * RESOLUTION. 0x006c27d0 (and its uncalled twin 0x006c28b0) map the video option to a width and
 * height (`void __stdcall(int *w, int *h)`, ret 8). With both settings
 * non-zero they return those instead. The HUD and field of view still assume
 * 4:3; a wider ratio stretches them. */
#include "pop_mod_api.h"
#include <stdio.h>
#include <string.h>

POP_MOD_DECLARE_ABI();

#define TIMER_CTOR 0x006484e0u
#define GET_RESOLUTION_A 0x006c27d0u
#define GET_RESOLUTION_B 0x006c28b0u
#define REDIRECTED_FRAME_TIME 0x00a37800u
#define WORLD_TIMESTEP 0x00903290u
#define ORIGINAL_FRAME_TIME 0x3c888889u /* 1/60 */

static const PopModApi *g_api;
static uint32_t g_hooks[3];
static uint32_t g_frame_time_bits = ORIGINAL_FRAME_TIME;

static uint32_t float_bits(float f) {
    uint32_t b;
    memcpy(&b, &f, 4);
    return b;
}

/* The timer's frame time is its first stack argument; the call has not
 * happened yet, so [esp+4] is the argument the caller pushed. */
static void timer_frame_time(const PopModApi *api, pop_cpu_v1 *cpu, PopHookInvocation *inv, void *user) {
    uint32_t arg;
    (void)inv;
    (void)user;
    if (api->guest_read_u32(api, cpu->esp + 4, &arg) == POP_OK && arg == ORIGINAL_FRAME_TIME)
        api->guest_write_u32(api, cpu->esp + 4, g_frame_time_bits);
}

static void resolution(const PopModApi *api, pop_cpu_v1 *cpu, PopHookInvocation *inv, void *user) {
    int64_t w = 0, h = 0;
    uint32_t pw, ph;
    (void)inv;
    (void)user;
    api->settings_get(api, "width", &w);
    api->settings_get(api, "height", &h);
    if (w <= 0 || h <= 0) {
        api->call_original(api, cpu->target, cpu);
        return;
    }
    if (api->guest_read_u32(api, cpu->esp + 4, &pw) == POP_OK && pw)
        api->guest_write_u32(api, pw, (uint32_t)w);
    if (api->guest_read_u32(api, cpu->esp + 8, &ph) == POP_OK && ph)
        api->guest_write_u32(api, ph, (uint32_t)h);
    api->hook_return(api, cpu, cpu->eax, 8);
}

PopModStatus pop_mod_init(const PopModApi *api) {
    int64_t rate = 60;
    PopModStatus s;
    g_api = api;
    api->settings_get(api, "sim_rate", &rate);
    if (rate < 30)
        rate = 30;
    if (rate > 480)
        rate = 480;
    g_frame_time_bits = float_bits(1.0f / (float)rate);
    {
        char line[96];
        snprintf(line, sizeof line, "core.nfsmw: simulation rate %d Hz", (int)rate);
        api->log(api, line);
    }
    if ((s = api->guest_write_u32(api, REDIRECTED_FRAME_TIME, g_frame_time_bits)) != POP_OK)
        return s;
    if ((s = api->guest_write_u32(api, WORLD_TIMESTEP, g_frame_time_bits)) != POP_OK)
        return s;
    if ((s = api->hook_install(api, TIMER_CTOR, timer_frame_time, POP_HOOK_BEFORE, 0, &g_hooks[0])) != POP_OK)
        return s;
    if ((s = api->hook_install(api, GET_RESOLUTION_A, resolution, POP_HOOK_REPLACE, 0, &g_hooks[1])) != POP_OK)
        return s;
    /* The second getter has no callers in this build and no listing entry. */
    if (api->hook_install(api, GET_RESOLUTION_B, resolution, POP_HOOK_REPLACE, 0, &g_hooks[2]) != POP_OK)
        g_hooks[2] = 0;
    return POP_OK;
}

PopModStatus pop_mod_exit(void) {
    for (int i = 0; i < 3; ++i)
        if (g_hooks[i])
            g_api->hook_remove(g_api, g_hooks[i]);
    return POP_OK;
}
