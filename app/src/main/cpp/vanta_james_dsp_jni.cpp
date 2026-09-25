#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cstdio>
#include <atomic>

extern "C" {
#include "jdsp_header.h"
}

#include "EELStdOutExtension.h"

#define LOG_TAG "VantaImmersiveDSP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Calls are serialized by VantaEqualizerProcessor.nativeLock. JamesDSP takes
// its own non-recursive mutex inside processing/configuration; taking that same
// mutex in this wrapper deadlocks the audio thread.
static JamesDSPLib *as_dsp(jlong handle) {
    return reinterpret_cast<JamesDSPLib *>(handle);
}

// Lock-free double-buffered spectrum for UI thread.
// Audio thread writes to bufA while UI reads bufB, then atomically swaps.
#define SPECTRUM_SIZE 128
static float spectrumBufA[SPECTRUM_SIZE];
static float spectrumBufB[SPECTRUM_SIZE];
static std::atomic<int> spectrumReadIndex{0}; // 0 = bufA readable, 1 = bufB readable

static void updateSpectrumCopy(const float *src, int count) {
    int writeIdx = spectrumReadIndex.load(std::memory_order_acquire);
    float *writeBuf = (writeIdx == 0) ? spectrumBufB : spectrumBufA;
    int copyCount = (count > SPECTRUM_SIZE) ? SPECTRUM_SIZE : count;
    memcpy(writeBuf, src, copyCount * sizeof(float));
    spectrumReadIndex.store(1 - writeIdx, std::memory_order_release);
}

static void readSpectrumCopy(float *dst, int count) {
    int readIdx = spectrumReadIndex.load(std::memory_order_acquire);
    float *readBuf = (readIdx == 0) ? spectrumBufA : spectrumBufB;
    int copyCount = (count > SPECTRUM_SIZE) ? SPECTRUM_SIZE : count;
    memcpy(dst, readBuf, copyCount * sizeof(float));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeCreate(
    JNIEnv *env, jobject thiz, jint blockSize, jfloat sampleRate) {
    JamesDSPGlobalMemoryAllocation();
    auto *dsp = static_cast<JamesDSPLib *>(malloc(sizeof(JamesDSPLib)));
    if (!dsp) {
        JamesDSPGlobalMemoryDeallocation();
        return 0;
    }
    memset(dsp, 0, sizeof(JamesDSPLib));
    JamesDSPInit(dsp, blockSize, sampleRate);
    if (!JamesDSPGetMutexStatus(dsp)) {
        LOGE("Immersive DSP mutex init failed");
        JamesDSPFree(dsp);
        free(dsp);
        JamesDSPGlobalMemoryDeallocation();
        return 0;
    }
    if (blockSize > static_cast<jint>(dsp->blockSizeMax)) {
        JamesDSPReallocateBlock(dsp, static_cast<size_t>(blockSize));
    }
    JLimiterInit(dsp);
    JLimiterSetCoefficients(dsp, -0.1, 60.0);
    JamesDSPSetPostGain(dsp, 0.0);
    // JamesDSPInit owns effect construction. Do not initialize those allocations twice.
    return reinterpret_cast<jlong>(dsp);
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeDestroy(
    JNIEnv *env, jobject thiz, jlong handle) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    // JamesDSPFree owns effect destruction, including all filter allocations.
    JamesDSPFree(dsp);
    free(dsp);
    JamesDSPGlobalMemoryDeallocation();
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeSetSampleRate(
    JNIEnv *env, jobject thiz, jlong handle, jfloat sampleRate, jboolean forceRefresh) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    JamesDSPSetSampleRate(dsp, sampleRate, forceRefresh ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeEnsureBlockSize(
    JNIEnv *env, jobject thiz, jlong handle, jint blockSize) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (static_cast<size_t>(blockSize) > dsp->blockSizeMax) {
        JamesDSPReallocateBlock(dsp, static_cast<size_t>(blockSize));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureSpatial(
    JNIEnv *env, jobject thiz, jlong handle,
    jboolean spatialEnabled,
    jfloat stereoWidenLevel,
    jboolean crossfeedEnabled, jint crossfeedMode,
    jboolean reverbEnabled, jint reverbPreset) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;


    if (spatialEnabled) {
        if (stereoWidenLevel > 0.01f) {
            StereoEnhancementSetParam(dsp, stereoWidenLevel);
            StereoEnhancementEnable(dsp);
        } else {
            StereoEnhancementDisable(dsp);
        }

        if (crossfeedEnabled) {
            CrossfeedChangeMode(dsp, crossfeedMode);
            if (!dsp->crossfeedEnabled) CrossfeedEnable(dsp, 1);
        } else if (dsp->crossfeedEnabled) {
            CrossfeedDisable(dsp);
        }

        if (reverbEnabled) {
            Reverb_SetParam(dsp, reverbPreset);
            if (!dsp->reverbEnabled) ReverbEnable(dsp);
        } else if (dsp->reverbEnabled) {
            ReverbDisable(dsp);
        }
    } else {
        StereoEnhancementDisable(dsp);
        if (dsp->crossfeedEnabled) CrossfeedDisable(dsp);
        if (dsp->reverbEnabled) ReverbDisable(dsp);
    }

}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureEQ(
    JNIEnv *env, jobject thiz, jlong handle,
    jdoubleArray freqAxis, jdoubleArray gainDb, jboolean enable) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;


    if (enable) {
        jsize len = env->GetArrayLength(freqAxis);
        if (len == NUMPTS && env->GetArrayLength(gainDb) == NUMPTS) {
            jdouble *freqs = env->GetDoubleArrayElements(freqAxis, nullptr);
            jdouble *gains = env->GetDoubleArrayElements(gainDb, nullptr);
            MultimodalEqualizerAxisInterpolation(dsp, 1, 1, freqs, gains);
            env->ReleaseDoubleArrayElements(freqAxis, freqs, JNI_ABORT);
            env->ReleaseDoubleArrayElements(gainDb, gains, JNI_ABORT);
        }
        MultimodalEqualizerEnable(dsp, 1);
    } else {
        MultimodalEqualizerDisable(dsp);
    }

}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureBassBoost(
    JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jdouble amount) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (enabled) {
        BassBoostSetParam(dsp, static_cast<float>(amount * 10.0));
        BassBoostEnable(dsp);
    } else {
        BassBoostDisable(dsp);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureTube(
    JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jdouble drive) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (enabled) {
        double gainDb = drive * 18.0 - 6.0;
        VacuumTubeSetGain(dsp, gainDb);
        VacuumTubeEnable(dsp);
    } else {
        VacuumTubeDisable(dsp);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureAutoEq(
    JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jstring profileData) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (enabled && profileData != nullptr) {
        const char *utfStr = env->GetStringUTFChars(profileData, nullptr);
        if (utfStr != nullptr && strlen(utfStr) > 0) {
            char *mutableStr = strdup(utfStr);
            if (mutableStr) {
                int result = DDCStringParser(dsp, mutableStr);
                free(mutableStr);
                if (result == 0) {
                    DDCEnable(dsp, 1);
                } else {
                    LOGE("AutoEQ DDCStringParser failed: %d", result);
                }
            }
            env->ReleaseStringUTFChars(profileData, utfStr);
        }
    } else {
        DDCDisable(dsp);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureLimiter(
    JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (enabled) {
        JLimiterSetCoefficients(dsp, -0.1, 60.0);
    } else {
        JLimiterSetCoefficients(dsp, 20.0, 200.0);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeConfigureConvolver(
    JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jstring irAssetPath) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return;
    if (enabled) {
        Convolver1DEnable(dsp);
    } else {
        Convolver1DDisable(dsp);
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeGetSpectrum(
    JNIEnv *env, jobject thiz, jlong handle) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp) return nullptr;

    // Lock-free read from double-buffered copy. Audio thread writes to
    // the inactive buffer after each processFloatDeinterleaved call.
    float temp[SPECTRUM_SIZE];
    readSpectrumCopy(temp, SPECTRUM_SIZE);
    jfloatArray result = env->NewFloatArray(SPECTRUM_SIZE);
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, SPECTRUM_SIZE, temp);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_audiophile_musicplayer_playback_dsp_VantaEqualizerNative_nativeProcessDeinterleaved(
    JNIEnv *env, jobject thiz, jlong handle,
    jfloatArray leftArray, jfloatArray rightArray, jint offset, jint frameCount) {
    JamesDSPLib *dsp = as_dsp(handle);
    if (!dsp || frameCount <= 0) return;

    jsize lenL = env->GetArrayLength(leftArray);
    jsize lenR = env->GetArrayLength(rightArray);
    if (offset + frameCount > lenL || offset + frameCount > lenR) return;

    jfloat *left = env->GetFloatArrayElements(leftArray, nullptr);
    jfloat *right = env->GetFloatArrayElements(rightArray, nullptr);
    if (!left || !right) {
        if (left) env->ReleaseFloatArrayElements(leftArray, left, JNI_ABORT);
        if (right) env->ReleaseFloatArrayElements(rightArray, right, JNI_ABORT);
        return;
    }

    float *inL = left + offset;
    float *inR = right + offset;
    dsp->processFloatDeinterleaved(dsp, inL, inR, inL, inR, static_cast<size_t>(frameCount));
    updateSpectrumCopy(dsp->comp.mag, SPECTRUM_SIZE);

    env->ReleaseFloatArrayElements(leftArray, left, 0);
    env->ReleaseFloatArrayElements(rightArray, right, 0);
}
