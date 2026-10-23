#include <jni.h>
#include <cmath>
#include <memory>
#include "vendor/pocket_tts.cpp"

namespace {
struct Engine {
    std::unique_ptr<pocket_tts::PocketTTS> tts;
    std::string voice;
};

// Never expose native failure text, model paths, or article content in Android logs.
void fail(JNIEnv* env) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/io/IOException");
    if (type) env->ThrowNew(type, "Pocket speech synthesis failed");
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_sk2andy_materialbrowser_pocket_NativePocketSpeech_create(
        JNIEnv* env, jclass, jstring directory, jstring voice) {
    const char* path = env->GetStringUTFChars(directory, nullptr);
    if (!path) return 0;
    std::string root(path);
    env->ReleaseStringUTFChars(directory, path);
    const char* voice_chars = env->GetStringUTFChars(voice, nullptr);
    if (!voice_chars) return 0;
    std::string voice_name(voice_chars);
    env->ReleaseStringUTFChars(voice, voice_chars);
    try {
        pocket_tts::Config config;
        config.models_dir = root + "/models";
        config.tokenizer_path = config.models_dir + "/tokenizer.model";
        config.voices_dir = root + "/voices";
        config.precision = "fp32";
        config.temperature = 0.5f;
        config.lsd_steps = 1;
        config.num_threads = 4;
        config.sentence_pause_ms = 250;
        config.max_text_tokens = 50;
        config.voice_cache = false;
        config.verbose = false;
        auto engine = std::make_unique<Engine>();
        // Catalog exposes stable voice IDs; every verified pack stores ID.wav.
        engine->voice = voice_name + ".wav";
        engine->tts = std::make_unique<pocket_tts::PocketTTS>(config);
        return reinterpret_cast<jlong>(engine.release());
    } catch (...) {
        fail(env);
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_sk2andy_materialbrowser_pocket_NativePocketSpeech_synthesize(
        JNIEnv* env, jclass, jlong handle, jbyteArray utf8_text, jobject sink) {
    auto* engine = reinterpret_cast<Engine*>(handle);
    if (!engine || !sink || !utf8_text) return JNI_FALSE;
    const jsize length = env->GetArrayLength(utf8_text);
    if (length <= 0 || length > 4096) return JNI_FALSE;
    std::string text(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(utf8_text, 0, length, reinterpret_cast<jbyte*>(text.data()));
    if (env->ExceptionCheck()) return JNI_FALSE;
    jclass type = env->GetObjectClass(sink);
    if (!type) return JNI_FALSE;
    const jmethodID on_audio = env->GetMethodID(type, "onAudio", "([F)Z");
    const jmethodID cancelled = env->GetMethodID(type, "isCancelled", "()Z");
    env->DeleteLocalRef(type);
    if (!on_audio || !cancelled) return JNI_FALSE;
    bool stopped = false;
    size_t total = 0;
    engine->tts->is_cancelled = [&] {
        if (stopped || env->ExceptionCheck()) return true;
        stopped = env->CallBooleanMethod(sink, cancelled) || env->ExceptionCheck();
        return stopped;
    };
    try {
        engine->tts->stream(text, engine->voice, [&](const float* samples, size_t count) {
            if (engine->tts->is_cancelled()) return false;
            constexpr size_t max_samples = 24000 * 120;
            if (count > max_samples || total > max_samples - count) {
                throw std::runtime_error("Audio limit");
            }
            for (size_t i = 0; i < count; ++i) {
                if (!std::isfinite(samples[i])) throw std::runtime_error("Invalid audio");
            }
            total += count;
            jfloatArray chunk = env->NewFloatArray(static_cast<jsize>(count));
            if (!chunk) { stopped = true; return false; }
            env->SetFloatArrayRegion(chunk, 0, static_cast<jsize>(count), samples);
            if (!env->ExceptionCheck()) stopped = !env->CallBooleanMethod(sink, on_audio, chunk);
            else stopped = true;
            env->DeleteLocalRef(chunk);
            return !stopped && !env->ExceptionCheck();
        }, 1500);
    } catch (...) {
        engine->tts->is_cancelled = [] { return false; };
        fail(env);
        return JNI_FALSE;
    }
    engine->tts->is_cancelled = [] { return false; };
    return !stopped && !env->ExceptionCheck() && total > 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_sk2andy_materialbrowser_pocket_NativePocketSpeech_destroy(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Engine*>(handle);
}
