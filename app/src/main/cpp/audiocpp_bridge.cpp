#include <jni.h>

#include <algorithm>
#include <cmath>
#include <filesystem>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "audiocpp.h"
#include "engine/framework/audio/wav_reader.h"
#include "engine/framework/audio/wav_writer.h"

namespace {
std::string read_string(JNIEnv *env, jstring value) {
    const char *characters = env->GetStringUTFChars(value, nullptr);
    if (!characters) throw std::runtime_error("Could not read input");
    try {
        const std::string result(characters);
        env->ReleaseStringUTFChars(value, characters);
        return result;
    } catch (...) {
        env->ReleaseStringUTFChars(value, characters);
        throw;
    }
}

void check(audiocpp_status status) {
    if (status == AUDIOCPP_OK) return;
    const char *error = audiocpp_last_error();
    throw std::runtime_error(error && *error ? error : "Local speech inference failed");
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_service_AudioCppNative_synthesize(
    JNIEnv *env, jobject, jstring family, jstring runtime_spec, jstring model_path, jstring reference,
    jbyteArray text, jstring language, jstring output, jfloat rate, jfloat volume, jint threads) {
    try {
        const auto family_name = read_string(env, family);
        const auto spec = read_string(env, runtime_spec);
        const auto model_name = read_string(env, model_path);
        const auto reference_path = read_string(env, reference);
        const auto target_language = read_string(env, language);
        const auto output_path = read_string(env, output);
        const jsize byte_count = env->GetArrayLength(text);
        std::string target(static_cast<size_t>(byte_count), '\0');
        env->GetByteArrayRegion(text, 0, byte_count, reinterpret_cast<jbyte *>(target.data()));
        if (env->ExceptionCheck()) return nullptr;

        const bool cosy = family_name == "cosyvoice3";
        if (!cosy && family_name != "index_tts2") throw std::runtime_error("Unknown local voice model");
        audiocpp_registry *registry_ptr = nullptr;
        check(audiocpp_registry_create(nullptr, &registry_ptr));
        const std::unique_ptr<audiocpp_registry, decltype(&audiocpp_registry_free)> registry(registry_ptr, audiocpp_registry_free);
        audiocpp_model_config model_config{family_name.c_str(), nullptr, nullptr, spec.c_str()};
        audiocpp_model *model_ptr = nullptr;
        check(audiocpp_model_load(registry.get(), model_name.c_str(), &model_config, nullptr, &model_ptr));
        const std::unique_ptr<audiocpp_model, decltype(&audiocpp_model_free)> model(model_ptr, audiocpp_model_free);

        const std::unique_ptr<audiocpp_options, decltype(&audiocpp_options_free)> options(audiocpp_options_create(), audiocpp_options_free);
        if (!options) throw std::runtime_error("Could not allocate speech options");
        check(audiocpp_options_set(options.get(), cosy ? "cosyvoice3.mem_saver" : "index_tts2.mem_saver", "true"));
        const audiocpp_backend_config backend{"cpu", 0, threads};
        audiocpp_session *session_ptr = nullptr;
        check(audiocpp_session_create(model.get(), "clon", "offline", &backend, options.get(), &session_ptr));
        const std::unique_ptr<audiocpp_session, decltype(&audiocpp_session_free)> session(session_ptr, audiocpp_session_free);

        const std::unique_ptr<audiocpp_request, decltype(&audiocpp_request_free)> request(audiocpp_request_create(), audiocpp_request_free);
        if (!request) throw std::runtime_error("Could not allocate speech request");
        const auto sample = engine::audio::read_wav_f32(std::filesystem::path(reference_path));
        if (sample.channels != 1 || sample.sample_rate <= 0 || sample.samples.empty()) {
            throw std::runtime_error("Could not read the reference recording");
        }
        check(audiocpp_request_set_voice_audio(request.get(), sample.samples.data(), sample.samples.size(), sample.sample_rate, 1));
        check(audiocpp_request_set_text(request.get(), target.c_str(), cosy ? nullptr : target_language.c_str()));
        if (cosy) {
            check(audiocpp_request_set_text_language(request.get(), target_language.c_str()));
            check(audiocpp_request_set_option(request.get(), "template_name", "cross_lingual"));
        }
        if (target_language == "ja") check(audiocpp_request_set_option(request.get(), "text_chunk_mode", "japanese"));
        check(audiocpp_request_set_option(request.get(), "text_chunk_size", "160"));
        const auto speed = std::to_string(cosy ? rate : 1.0f / rate);
        check(audiocpp_request_set_option(request.get(), cosy ? "speed" : "duration_scale", speed.c_str()));

        audiocpp_result *result_ptr = nullptr;
        check(audiocpp_session_run(session.get(), request.get(), &result_ptr));
        const std::unique_ptr<audiocpp_result, decltype(&audiocpp_result_free)> result(result_ptr, audiocpp_result_free);
        const float *samples = nullptr;
        size_t frames = 0;
        int sample_rate = 0, channels = 0;
        check(audiocpp_result_audio(result.get(), &samples, &frames, &sample_rate, &channels));
        if (!samples || frames == 0 || channels != 1 || sample_rate <= 0) {
            throw std::runtime_error("The model returned no playable mono audio");
        }
        std::vector<float> output_audio(frames);
        std::transform(samples, samples + frames, output_audio.begin(), [volume](float sample_value) {
            return std::isfinite(sample_value) ? sample_value * volume : 0.0f;
        });
        engine::audio::write_pcm16_wav(output_path, sample_rate, channels, output_audio);
        return nullptr;
    } catch (const std::exception &error) {
        return env->ExceptionCheck() ? nullptr : env->NewStringUTF(error.what());
    } catch (...) {
        return env->NewStringUTF("Local speech inference failed");
    }
}
