#include <jni.h>
#include <lame.h>

#include <algorithm>
#include <cstdint>
#include <memory>

namespace {
void throwIllegalState(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

lame_t asLame(jlong handle) {
    return reinterpret_cast<lame_t>(static_cast<intptr_t>(handle));
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeCreate(
    JNIEnv* env, jobject /* thiz */, jint sampleRate, jint channels, jint bitrateKbps
) {
    if (sampleRate < 8000 || sampleRate > 48000 || (channels != 1 && channels != 2) ||
        bitrateKbps < 32 || bitrateKbps > 320) {
        throwIllegalState(env, "Unsupported MP3 encoder settings");
        return 0;
    }

    lame_t encoder = lame_init();
    if (encoder == nullptr) {
        throwIllegalState(env, "LAME could not allocate an encoder");
        return 0;
    }

    lame_set_num_channels(encoder, channels);
    lame_set_in_samplerate(encoder, sampleRate);
    lame_set_out_samplerate(encoder, sampleRate);
    lame_set_brate(encoder, bitrateKbps);
    lame_set_quality(encoder, 2);

    if (lame_init_params(encoder) < 0) {
        lame_close(encoder);
        throwIllegalState(env, "LAME rejected the PCM audio format");
        return 0;
    }

    return static_cast<jlong>(reinterpret_cast<intptr_t>(encoder));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeEncode(
    JNIEnv* env, jobject /* thiz */, jlong handle, jshortArray pcm
) {
    lame_t encoder = asLame(handle);
    if (encoder == nullptr || pcm == nullptr) {
        throwIllegalState(env, "Invalid MP3 encoder state");
        return nullptr;
    }

    const jsize valueCount = env->GetArrayLength(pcm);
    const int channels = lame_get_num_channels(encoder);
    if (channels < 1 || channels > 2 || valueCount <= 0 || valueCount % channels != 0) {
        throwIllegalState(env, "PCM buffer does not match the encoder channel count");
        return nullptr;
    }

    jshort* samples = env->GetShortArrayElements(pcm, nullptr);
    if (samples == nullptr) return nullptr;

    const int samplesPerChannel = valueCount / channels;
    const int capacity = std::max(8192, static_cast<int>(samplesPerChannel * 1.35) + 7200);
    std::unique_ptr<unsigned char[]> encoded(new unsigned char[capacity]);

    const int written = lame_encode_buffer_interleaved(
        encoder,
        reinterpret_cast<short int*>(samples),
        samplesPerChannel,
        encoded.get(),
        capacity
    );

    env->ReleaseShortArrayElements(pcm, samples, JNI_ABORT);
    if (written < 0) {
        throwIllegalState(env, "LAME failed while encoding an audio frame");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(written);
    if (result != nullptr && written > 0) {
        env->SetByteArrayRegion(result, 0, written, reinterpret_cast<const jbyte*>(encoded.get()));
    }
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeFlush(
    JNIEnv* env, jobject /* thiz */, jlong handle
) {
    lame_t encoder = asLame(handle);
    if (encoder == nullptr) {
        throwIllegalState(env, "Invalid MP3 encoder state");
        return nullptr;
    }

    constexpr int capacity = 16384;
    std::unique_ptr<unsigned char[]> encoded(new unsigned char[capacity]);
    const int written = lame_encode_flush(encoder, encoded.get(), capacity);
    if (written < 0) {
        throwIllegalState(env, "LAME failed to flush the final MP3 frames");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(written);
    if (result != nullptr && written > 0) {
        env->SetByteArrayRegion(result, 0, written, reinterpret_cast<const jbyte*>(encoded.get()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeClose(
    JNIEnv* /* env */, jobject /* thiz */, jlong handle
) {
    lame_t encoder = asLame(handle);
    if (encoder != nullptr) lame_close(encoder);
}
