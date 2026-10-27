#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NeedleAndroid", __VA_ARGS__)
static std::mutex g_mutex;
static bool g_ready = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_yachiha_needleandroid_NeedleNative_initialize(JNIEnv* env, jclass, jbyteArray model) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ready) return env->NewStringUTF("OK");
    if (model == nullptr) return env->NewStringUTF("ERROR: model asset is missing");
    const jsize length = env->GetArrayLength(model);
    if (length <= 0) return env->NewStringUTF("ERROR: model asset is empty");

    std::vector<unsigned char> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(model, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return env->NewStringUTF("ERROR: could not read model asset");
    }

    const int load_rc = needle_load(bytes.data(), static_cast<unsigned long long>(bytes.size()));
    if (load_rc < 0) {
        LOGE("needle_load failed: %d", load_rc);
        return env->NewStringUTF(("ERROR: Needle model load failed (" + std::to_string(load_rc) + ")").c_str());
    }

    const char* system = "You are Needle, a compact tool-calling model. Return only valid tool calls when a request matches a declared tool. Do not invent values.";
    const char* tools = R"JSON([{"name":"get_current_time","description":"Return the current local time on this device.","parameters":{"type":"object","properties":{},"required":[]}},{"name":"calculate","description":"Evaluate a basic arithmetic expression provided by the user.","parameters":{"type":"object","properties":{"expression":{"type":"string","description":"Arithmetic expression using numbers and + - * / parentheses"}},"required":["expression"]}}])JSON";
    const int init_rc = needle_init(system, tools, nullptr);
    if (init_rc < 0) {
        LOGE("needle_init failed: %d", init_rc);
        return env->NewStringUTF(("ERROR: Needle initialization failed (" + std::to_string(init_rc) + ")").c_str());
    }
    g_ready = true;
    return env->NewStringUTF("OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yachiha_needleandroid_NeedleNative_complete(JNIEnv* env, jclass, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ready) return env->NewStringUTF("ERROR: Needle is not initialized");
    if (prompt == nullptr) return env->NewStringUTF("ERROR: enter a request");
    const char* input = env->GetStringUTFChars(prompt, nullptr);
    if (input == nullptr) return env->NewStringUTF("ERROR: could not read request");

    constexpr int capacity = 65536;
    std::vector<char> output(capacity, '\0');
    const int rc = needle_complete(input, nullptr, 0, 256, output.data(), capacity);
    env->ReleaseStringUTFChars(prompt, input);
    if (rc < 0) return env->NewStringUTF(("ERROR: Needle inference failed (" + std::to_string(rc) + ")").c_str());
    output.back() = '\0';
    const size_t n = strnlen(output.data(), output.size());
    if (n == 0) return env->NewStringUTF("Needle returned no tool call for this request. This is expected for unsupported or off-topic requests.");
    return env->NewStringUTF(output.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yachiha_needleandroid_NeedleNative_reset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ready) needle_reset();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yachiha_needleandroid_NeedleNative_isReady(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_ready ? JNI_TRUE : JNI_FALSE;
}
