/*
 * Benflix game player: a small libretro "frontend".
 *
 * An emulator ("core", e.g. mGBA for Game Boy Advance) is a separate .so file that Benflix downloads.
 * This file opens it, gives it the game, runs it one frame at a time, draws each frame with OpenGL ES 2
 * (sharp pixels, kept to the game's shape), collects its sound for Kotlin to play, and tells it which
 * buttons are held. Everything here runs on the game's GL thread, except setting buttons.
 */
#include <jni.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include "libretro.h"

#define TAG "BenflixGames"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

/* ---------- The core's functions ---------- */
static void *core_handle;
static void (*c_init)(void);
static void (*c_deinit)(void);
static void (*c_get_system_info)(struct retro_system_info *);
static void (*c_get_system_av_info)(struct retro_system_av_info *);
static void (*c_set_environment)(retro_environment_t);
static void (*c_set_video_refresh)(retro_video_refresh_t);
static void (*c_set_audio_sample)(retro_audio_sample_t);
static void (*c_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*c_set_input_poll)(retro_input_poll_t);
static void (*c_set_input_state)(retro_input_state_t);
static void (*c_set_controller_port_device)(unsigned, unsigned);
static void (*c_reset)(void);
static void (*c_run)(void);
static size_t (*c_serialize_size)(void);
static bool (*c_serialize)(void *, size_t);
static bool (*c_unserialize)(const void *, size_t);
static bool (*c_load_game)(const struct retro_game_info *);
static void (*c_unload_game)(void);
static void *(*c_get_memory_data)(unsigned);
static size_t (*c_get_memory_size)(unsigned);

static int core_ready;
static int game_loaded;
static char system_dir[1024];
static char save_dir[1024];
static void *game_data;

/* Core settings ("key=value"), set from Kotlin before the game starts */
#define MAX_OPTIONS 64
static char option_keys[MAX_OPTIONS][128];
static char option_values[MAX_OPTIONS][128];
static int option_count;

static enum retro_pixel_format pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
static struct retro_system_av_info av_info;
static unsigned frame_w, frame_h;
/* Turned picture (arcade games made for an upright screen, like Pac-Man): 0-3 quarter turns anticlockwise */
static unsigned rotation;

/* The game's shape (width / height) as shown, after any turn */
static double shown_aspect(void) {
    double a = av_info.geometry.aspect_ratio > 0 ? av_info.geometry.aspect_ratio
               : (av_info.geometry.base_height > 0 ? (double)av_info.geometry.base_width / av_info.geometry.base_height
                  : (frame_h > 0 ? (double)frame_w / frame_h : 4.0 / 3.0));
    return (rotation & 1) ? 1.0 / a : a;
}

/* ---------- Picture ---------- */
static uint16_t *frame565;  /* For 0RGB1555 and RGB565 cores */
static uint32_t *frame8888; /* For XRGB8888 cores, stored as RGBA bytes */
static size_t frame_cap;
static int frame_new;
static unsigned tex_w, tex_h;
static int tex_is_8888 = -1;

static GLuint program, texture, vbo;
static GLint attr_pos, attr_uv, uni_tex;
static int view_w = 1, view_h = 1;

/* ---------- Sound (interleaved stereo 16-bit) ---------- */
#define AUDIO_CAP (48000 * 2)
static int16_t audio_buf[AUDIO_CAP];
static size_t audio_len;

/* ---------- Buttons: 4 players, a libretro joypad bitmask each, plus sticks ---------- */
static volatile uint16_t pad_buttons[4];
static volatile int16_t pad_analog[4][2][2];

static void core_log(enum retro_log_level level, const char *fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    int prio = level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR : level == RETRO_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    if (level == RETRO_LOG_DEBUG) return;
    __android_log_print(prio, "BenflixCore", "%s", buf);
}

static bool environment(unsigned cmd, void *data) {
    switch (cmd & ~RETRO_ENVIRONMENT_EXPERIMENTAL) {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            ((struct retro_log_callback *)data)->log = core_log;
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *)data = true;
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char **)data = system_dir;
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = save_dir;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            enum retro_pixel_format f = *(enum retro_pixel_format *)data;
            if (f == RETRO_PIXEL_FORMAT_0RGB1555 || f == RETRO_PIXEL_FORMAT_RGB565 || f == RETRO_PIXEL_FORMAT_XRGB8888) {
                pixel_format = f;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *v = (struct retro_variable *)data;
            for (int i = 0; i < option_count; i++) {
                if (strcmp(option_keys[i], v->key) == 0) {
                    v->value = option_values[i];
                    return true;
                }
            }
            v->value = NULL;
            return false;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool *)data = false;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_MESSAGE:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
            return true;
        case RETRO_ENVIRONMENT_SET_ROTATION:
            if (data) rotation = *(const unsigned *)data & 3;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *)data = RETRO_LANGUAGE_ENGLISH;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            if (data) *(int *)data = 3;
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            av_info.geometry = *(const struct retro_game_geometry *)data;
            return true;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            av_info = *(const struct retro_system_av_info *)data;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            if (data) *(unsigned *)data = 0;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_MAX_USERS:
            if (data) *(unsigned *)data = 4;
            return true;
        default:
            return false;
    }
}

static void ensure_frame(size_t pixels) {
    if (pixels <= frame_cap) return;
    free(frame565);
    free(frame8888);
    frame565 = (uint16_t *)malloc(pixels * sizeof(uint16_t));
    frame8888 = (uint32_t *)malloc(pixels * sizeof(uint32_t));
    frame_cap = pixels;
}

static void video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return; /* "Same picture as last time" */
    ensure_frame((size_t)width * height);
    frame_w = width;
    frame_h = height;
    const uint8_t *src = (const uint8_t *)data;
    if (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        for (unsigned y = 0; y < height; y++) {
            const uint32_t *row = (const uint32_t *)(src + y * pitch);
            uint32_t *dst = frame8888 + (size_t)y * width;
            for (unsigned x = 0; x < width; x++) {
                uint32_t p = row[x];
                /* 0x00RRGGBB -> bytes R,G,B,A in memory (little endian 0xAABBGGRR) */
                dst[x] = 0xFF000000u | ((p & 0xFFu) << 16) | (p & 0xFF00u) | ((p >> 16) & 0xFFu);
            }
        }
    } else if (pixel_format == RETRO_PIXEL_FORMAT_RGB565) {
        for (unsigned y = 0; y < height; y++) memcpy(frame565 + (size_t)y * width, src + y * pitch, width * 2);
    } else {
        for (unsigned y = 0; y < height; y++) {
            const uint16_t *row = (const uint16_t *)(src + y * pitch);
            uint16_t *dst = frame565 + (size_t)y * width;
            for (unsigned x = 0; x < width; x++) {
                uint16_t p = row[x];
                dst[x] = (uint16_t)(((p & 0x7C00u) << 1) | ((p & 0x03E0u) << 1) | ((p & 0x0200u) >> 4) | (p & 0x001Fu));
            }
        }
    }
    frame_new = 1;
}

static void audio_sample(int16_t left, int16_t right) {
    if (audio_len + 2 > AUDIO_CAP) return;
    audio_buf[audio_len++] = left;
    audio_buf[audio_len++] = right;
}

static size_t audio_batch(const int16_t *data, size_t frames) {
    size_t n = frames * 2;
    if (audio_len + n > AUDIO_CAP) n = AUDIO_CAP - audio_len;
    memcpy(audio_buf + audio_len, data, n * sizeof(int16_t));
    audio_len += n;
    return frames;
}

static void input_poll(void) {}

static int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    switch (device & RETRO_DEVICE_MASK) {
        case RETRO_DEVICE_JOYPAD:
            if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)pad_buttons[port];
            if (id >= 16) return 0;
            return (pad_buttons[port] >> id) & 1;
        case RETRO_DEVICE_ANALOG:
            if (index < 2 && id < 2) return pad_analog[port][index][id];
            return 0;
        default:
            return 0;
    }
}

#define LOAD_SYM(var, name)                              \
    do {                                                 \
        *(void **)(&var) = dlsym(core_handle, name);     \
        if (!var) {                                      \
            LOGW("The emulator is missing %s", name);    \
            return JNI_FALSE;                            \
        }                                                \
    } while (0)

static void copy_jstring(JNIEnv *env, jstring s, char *out, size_t cap) {
    out[0] = 0;
    if (!s) return;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    snprintf(out, cap, "%s", c);
    (*env)->ReleaseStringUTFChars(env, s, c);
}

#define JNI_FN(name) Java_com_github_damontecres_wholphin_games_RetroNative_##name

JNIEXPORT void JNICALL JNI_FN(setOption)(JNIEnv *env, jobject thiz, jstring key, jstring value) {
    char k[128], v[128];
    copy_jstring(env, key, k, sizeof(k));
    copy_jstring(env, value, v, sizeof(v));
    for (int i = 0; i < option_count; i++) {
        if (strcmp(option_keys[i], k) == 0) {
            snprintf(option_values[i], sizeof(option_values[i]), "%s", v);
            return;
        }
    }
    if (option_count >= MAX_OPTIONS) return;
    snprintf(option_keys[option_count], sizeof(option_keys[0]), "%s", k);
    snprintf(option_values[option_count], sizeof(option_values[0]), "%s", v);
    option_count++;
}

/* Opens the emulator and starts the game. Returns false (and logs why) if it can't. */
JNIEXPORT jboolean JNICALL JNI_FN(load)(JNIEnv *env, jobject thiz, jstring jcore, jstring jgame, jstring jsystem, jstring jsave) {
    rotation = 0;
    char core_path[1024], game_path[1024];
    copy_jstring(env, jcore, core_path, sizeof(core_path));
    copy_jstring(env, jgame, game_path, sizeof(game_path));
    copy_jstring(env, jsystem, system_dir, sizeof(system_dir));
    copy_jstring(env, jsave, save_dir, sizeof(save_dir));

    core_handle = dlopen(core_path, RTLD_NOW | RTLD_LOCAL);
    if (!core_handle) {
        LOGW("Couldn't open the emulator: %s", dlerror());
        return JNI_FALSE;
    }
    LOAD_SYM(c_init, "retro_init");
    LOAD_SYM(c_deinit, "retro_deinit");
    LOAD_SYM(c_get_system_info, "retro_get_system_info");
    LOAD_SYM(c_get_system_av_info, "retro_get_system_av_info");
    LOAD_SYM(c_set_environment, "retro_set_environment");
    LOAD_SYM(c_set_video_refresh, "retro_set_video_refresh");
    LOAD_SYM(c_set_audio_sample, "retro_set_audio_sample");
    LOAD_SYM(c_set_audio_sample_batch, "retro_set_audio_sample_batch");
    LOAD_SYM(c_set_input_poll, "retro_set_input_poll");
    LOAD_SYM(c_set_input_state, "retro_set_input_state");
    LOAD_SYM(c_set_controller_port_device, "retro_set_controller_port_device");
    LOAD_SYM(c_reset, "retro_reset");
    LOAD_SYM(c_run, "retro_run");
    LOAD_SYM(c_serialize_size, "retro_serialize_size");
    LOAD_SYM(c_serialize, "retro_serialize");
    LOAD_SYM(c_unserialize, "retro_unserialize");
    LOAD_SYM(c_load_game, "retro_load_game");
    LOAD_SYM(c_unload_game, "retro_unload_game");
    LOAD_SYM(c_get_memory_data, "retro_get_memory_data");
    LOAD_SYM(c_get_memory_size, "retro_get_memory_size");

    c_set_environment(environment);
    c_init();
    core_ready = 1;
    c_set_video_refresh(video_refresh);
    c_set_audio_sample(audio_sample);
    c_set_audio_sample_batch(audio_batch);
    c_set_input_poll(input_poll);
    c_set_input_state(input_state);

    struct retro_system_info info;
    memset(&info, 0, sizeof(info));
    c_get_system_info(&info);
    LOGI("Emulator %s %s, need_fullpath=%d", info.library_name ? info.library_name : "?",
         info.library_version ? info.library_version : "", info.need_fullpath);

    struct retro_game_info game;
    memset(&game, 0, sizeof(game));
    game.path = game_path;
    if (!info.need_fullpath) {
        FILE *f = fopen(game_path, "rb");
        if (!f) {
            LOGW("Couldn't open the game file %s", game_path);
            return JNI_FALSE;
        }
        fseek(f, 0, SEEK_END);
        long size = ftell(f);
        fseek(f, 0, SEEK_SET);
        game_data = malloc(size > 0 ? (size_t)size : 1);
        if (!game_data || fread(game_data, 1, (size_t)size, f) != (size_t)size) {
            fclose(f);
            LOGW("Couldn't read the game file");
            return JNI_FALSE;
        }
        fclose(f);
        game.data = game_data;
        game.size = (size_t)size;
    }
    if (!c_load_game(&game)) {
        LOGW("The emulator couldn't start this game");
        return JNI_FALSE;
    }
    game_loaded = 1;
    c_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    c_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);
    memset(&av_info, 0, sizeof(av_info));
    c_get_system_av_info(&av_info);
    LOGI("Game started: %ux%u at %.3f fps, sound %.0f Hz", av_info.geometry.base_width, av_info.geometry.base_height,
         av_info.timing.fps, av_info.timing.sample_rate);
    return JNI_TRUE;
}

/* [frames per second, sound sample rate, picture shape (width / height)] */
JNIEXPORT jdoubleArray JNICALL JNI_FN(timing)(JNIEnv *env, jobject thiz) {
    double shape = shown_aspect();
    jdouble values[3] = {av_info.timing.fps > 0 ? av_info.timing.fps : 60.0,
                         av_info.timing.sample_rate > 0 ? av_info.timing.sample_rate : 44100.0,
                         shape};
    jdoubleArray out = (*env)->NewDoubleArray(env, 3);
    (*env)->SetDoubleArrayRegion(env, out, 0, 3, values);
    return out;
}

JNIEXPORT void JNICALL JNI_FN(run)(JNIEnv *env, jobject thiz, jint frames) {
    if (!game_loaded) return;
    for (int i = 0; i < frames; i++) c_run();
}

/* Copies out the sound made since last time. Returns how many shorts were copied. */
JNIEXPORT jint JNICALL JNI_FN(drainAudio)(JNIEnv *env, jobject thiz, jshortArray out) {
    jsize cap = (*env)->GetArrayLength(env, out);
    jsize n = (jsize)audio_len < cap ? (jsize)audio_len : cap;
    if (n > 0) (*env)->SetShortArrayRegion(env, out, 0, n, audio_buf);
    audio_len = 0;
    return n;
}

JNIEXPORT void JNICALL JNI_FN(setButtons)(JNIEnv *env, jobject thiz, jint port, jint mask) {
    if (port >= 0 && port < 4) pad_buttons[port] = (uint16_t)mask;
}

JNIEXPORT void JNICALL JNI_FN(setStick)(JNIEnv *env, jobject thiz, jint port, jint stick, jint x, jint y) {
    if (port < 0 || port >= 4 || stick < 0 || stick > 1) return;
    pad_analog[port][stick][0] = (int16_t)x;
    pad_analog[port][stick][1] = (int16_t)y;
}

JNIEXPORT void JNICALL JNI_FN(reset)(JNIEnv *env, jobject thiz) {
    if (game_loaded) c_reset();
}

JNIEXPORT jbyteArray JNICALL JNI_FN(saveState)(JNIEnv *env, jobject thiz) {
    if (!game_loaded) return NULL;
    size_t size = c_serialize_size();
    if (size == 0) return NULL;
    void *buf = malloc(size);
    if (!buf) return NULL;
    jbyteArray out = NULL;
    if (c_serialize(buf, size)) {
        out = (*env)->NewByteArray(env, (jsize)size);
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)size, (const jbyte *)buf);
    }
    free(buf);
    return out;
}

JNIEXPORT jboolean JNICALL JNI_FN(loadState)(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!game_loaded || !data) return JNI_FALSE;
    jsize n = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    bool ok = c_unserialize(bytes, (size_t)n);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* The game's own save (battery save in the cartridge), or null if it has none */
JNIEXPORT jbyteArray JNICALL JNI_FN(getSaveRam)(JNIEnv *env, jobject thiz) {
    if (!game_loaded) return NULL;
    size_t size = c_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = c_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return NULL;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)size);
    (*env)->SetByteArrayRegion(env, out, 0, (jsize)size, (const jbyte *)data);
    return out;
}

JNIEXPORT jboolean JNICALL JNI_FN(setSaveRam)(JNIEnv *env, jobject thiz, jbyteArray bytes) {
    if (!game_loaded || !bytes) return JNI_FALSE;
    size_t size = c_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = c_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    jsize n = (*env)->GetArrayLength(env, bytes);
    if (!data || size == 0) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, bytes, 0, (jsize)((size_t)n < size ? (size_t)n : size), (jbyte *)data);
    return JNI_TRUE;
}

/* The last picture as ARGB pixels, with width and height first: [w, h, pixels...] */
JNIEXPORT jintArray JNICALL JNI_FN(snapshot)(JNIEnv *env, jobject thiz) {
    if (frame_w == 0 || frame_h == 0) return NULL;
    size_t count = (size_t)frame_w * frame_h;
    jintArray out = (*env)->NewIntArray(env, (jsize)(count + 2));
    jint *px = (*env)->GetIntArrayElements(env, out, NULL);
    px[0] = (jint)frame_w;
    px[1] = (jint)frame_h;
    for (size_t i = 0; i < count; i++) {
        uint32_t argb;
        if (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
            uint32_t p = frame8888[i]; /* 0xAABBGGRR */
            argb = 0xFF000000u | ((p & 0xFFu) << 16) | (p & 0xFF00u) | ((p >> 16) & 0xFFu);
        } else {
            uint16_t p = frame565[i];
            uint32_t r = (p >> 11) & 0x1F, g = (p >> 5) & 0x3F, b = p & 0x1F;
            argb = 0xFF000000u | ((r << 3 | r >> 2) << 16) | ((g << 2 | g >> 4) << 8) | (b << 3 | b >> 2);
        }
        px[i + 2] = (jint)argb;
    }
    (*env)->ReleaseIntArrayElements(env, out, px, 0);
    return out;
}

JNIEXPORT void JNICALL JNI_FN(unload)(JNIEnv *env, jobject thiz) {
    if (game_loaded) c_unload_game();
    game_loaded = 0;
    rotation = 0;
    if (core_ready) c_deinit();
    core_ready = 0;
    /* The emulator stays open: some can't be opened twice. The game runs in its own process anyway. */
    free(game_data);
    game_data = NULL;
}

/* ---------- Drawing ---------- */
static const char *VS =
    "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv;"
    "void main(){ vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }";
static const char *FS =
    "precision mediump float; varying vec2 vUv; uniform sampler2D uTex;"
    "void main(){ gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }";

static GLuint compile(GLenum type, const char *src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    return s;
}

/* Called whenever the GL surface is (re)made */
JNIEXPORT void JNICALL JNI_FN(glInit)(JNIEnv *env, jobject thiz) {
    program = glCreateProgram();
    glAttachShader(program, compile(GL_VERTEX_SHADER, VS));
    glAttachShader(program, compile(GL_FRAGMENT_SHADER, FS));
    glLinkProgram(program);
    attr_pos = glGetAttribLocation(program, "aPos");
    attr_uv = glGetAttribLocation(program, "aUv");
    uni_tex = glGetUniformLocation(program, "uTex");
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    /* Sharp pixels */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenBuffers(1, &vbo);
    tex_w = tex_h = 0;
    tex_is_8888 = -1;
    frame_new = frame_w > 0;
}

JNIEXPORT void JNICALL JNI_FN(glResize)(JNIEnv *env, jobject thiz, jint w, jint h) {
    view_w = w > 0 ? w : 1;
    view_h = h > 0 ? h : 1;
}

JNIEXPORT void JNICALL JNI_FN(glDraw)(JNIEnv *env, jobject thiz) {
    glViewport(0, 0, view_w, view_h);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    if (frame_w == 0 || frame_h == 0) return;

    glBindTexture(GL_TEXTURE_2D, texture);
    int is8888 = pixel_format == RETRO_PIXEL_FORMAT_XRGB8888;
    if (frame_new) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, is8888 ? 4 : 2);
        if (frame_w != tex_w || frame_h != tex_h || is8888 != tex_is_8888) {
            if (is8888)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, frame_w, frame_h, 0, GL_RGBA, GL_UNSIGNED_BYTE, frame8888);
            else
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, frame_w, frame_h, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, frame565);
            tex_w = frame_w;
            tex_h = frame_h;
            tex_is_8888 = is8888;
        } else if (is8888) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame_w, frame_h, GL_RGBA, GL_UNSIGNED_BYTE, frame8888);
        } else {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame_w, frame_h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, frame565);
        }
        frame_new = 0;
    }

    /* Keep the game's shape, as big as fits, centred */
    float aspect = (float)shown_aspect();
    float view_aspect = (float)view_w / (float)view_h;
    float sx = 1.0f, sy = 1.0f;
    if (view_aspect > aspect) sx = aspect / view_aspect; else sy = view_aspect / aspect;
    /* Corners anticlockwise from bottom left; a turned game takes its picture corners from further round */
    static const float uv[4][2] = {{0.0f, 1.0f}, {1.0f, 1.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}};
    const float *bl = uv[(0 + 4 - rotation) % 4], *br = uv[(1 + 4 - rotation) % 4];
    const float *tr = uv[(2 + 4 - rotation) % 4], *tl = uv[(3 + 4 - rotation) % 4];
    float quad[] = {
        -sx, -sy, bl[0], bl[1],
        sx, -sy, br[0], br[1],
        -sx, sy, tl[0], tl[1],
        sx, sy, tr[0], tr[1],
    };
    glUseProgram(program);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STREAM_DRAW);
    glEnableVertexAttribArray((GLuint)attr_pos);
    glVertexAttribPointer((GLuint)attr_pos, 2, GL_FLOAT, GL_FALSE, 16, (const void *)0);
    glEnableVertexAttribArray((GLuint)attr_uv);
    glVertexAttribPointer((GLuint)attr_uv, 2, GL_FLOAT, GL_FALSE, 16, (const void *)8);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(uni_tex, 0);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}
