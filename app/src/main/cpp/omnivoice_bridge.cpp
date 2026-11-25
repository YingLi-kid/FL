#include <jni.h>

#include <cstdlib>
#include <memory>
#include <string>

#include "audio-io.h"
#include "omnivoice.h"

namespace {
std::string read_utf8(JNIEnv *env, jbyteArray value) {
    const jsize size = env->GetArrayLength(value);
    std::string result(static_cast<size_t>(size), '\0');
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

struct CancelState {
    JNIEnv *env;
    jobject callback;
    jmethodID method;
};

bool check_cancel(void *data) {
    auto &cancel = *static_cast<CancelState *>(data);
    const jboolean cancelled = cancel.env->CallBooleanMethod(cancel.callback, cancel.method);
    return cancelled == JNI_TRUE || cancel.env->ExceptionCheck();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_service_OmniVoiceNative_synthesize(
    JNIEnv *env, jobject, jbyteArray model, jbyteArray codec, jbyteArray reference,
    jbyteArray transcript, jbyteArray text, jbyteArray language, jbyteArray destination,
    jfloat volume, jobject cancel_callback) {
    try {
        const auto model_path = read_utf8(env, model);
        const auto codec_path = read_utf8(env, codec);
        const auto reference_path = read_utf8(env, reference);
        const auto reference_text = read_utf8(env, transcript);
        const auto target_text = read_utf8(env, text);
        const auto language_id = read_utf8(env, language);
        const auto output_path = read_utf8(env, destination);
        jclass callback_class = env->FindClass("java/util/function/BooleanSupplier");
        if (!callback_class || env->ExceptionCheck()) return nullptr;
        jmethodID method = env->GetMethodID(callback_class, "getAsBoolean", "()Z");
        if (!method) return nullptr;
        CancelState cancellation{env, cancel_callback, method};

        int sample_count = 0;
        std::unique_ptr<float, decltype(&free)> samples(
            audio_read_mono(reference_path.c_str(), 24000, &sample_count), free);
        if (!samples || sample_count <= 0) return env->NewStringUTF("OmniVoice could not decode the reference.");

        ov_init_params init;
        ov_init_default_params(&init);
        init.model_path = model_path.c_str();
        init.codec_path = codec_path.c_str();
        std::unique_ptr<ov_context, decltype(&ov_free)> voice(ov_init(&init), ov_free);
        if (!voice) return env->NewStringUTF("OmniVoice could not load the model and codec.");

        ov_tts_params params;
        ov_tts_default_params(&params);
        params.text = target_text.c_str();
        params.lang = language_id.c_str();
        params.ref_audio_24k = samples.get();
        params.ref_n_samples = sample_count;
        params.ref_text = reference_text.c_str();
        params.cancel = check_cancel;
        params.cancel_user_data = &cancellation;
        ov_audio audio{};
        const auto status = ov_synthesize(voice.get(), &params, &audio);
        const std::unique_ptr<ov_audio, decltype(&ov_audio_free)> cleanup(&audio, ov_audio_free);
        if (env->ExceptionCheck()) return nullptr;
        if (status != OV_STATUS_OK || !audio.samples || audio.n_samples <= 0) {
            return env->NewStringUTF("OmniVoice synthesis failed.");
        }
        for (int index = 0; index < audio.n_samples; ++index) audio.samples[index] *= volume;
        if (!audio_write_wav(output_path.c_str(), audio.samples, audio.n_samples, audio.sample_rate)) {
            return env->NewStringUTF("OmniVoice could not write the WAV file.");
        }
        return nullptr;
    } catch (...) {
        return env->NewStringUTF("OmniVoice native processing failed.");
    }
}
