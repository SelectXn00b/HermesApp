#include <jni.h>

#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <memory>
#include <mutex>
#include <string>

#include "Saba/Viewer/Viewer.h"
#include "android/AndroidAssetSupport.h"

namespace {

constexpr const char* kTag = "MmdRendererBridge";

struct RendererHandle {
    std::mutex mutex;
    std::unique_ptr<saba::Viewer> viewer;
    std::string lastError;
};

RendererHandle* FromHandle(jlong handle) {
    return reinterpret_cast<RendererHandle*>(handle);
}

void SetError(RendererHandle* handle, const std::string& error) {
    if (handle == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->lastError != error && !error.empty()) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kTag,
            "renderer=%p error=%s",
            static_cast<void*>(handle),
            error.c_str()
        );
    }
    handle->lastError = error;
}

void ClearError(RendererHandle* handle) {
    if (handle == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    handle->lastError.clear();
}

std::string GetError(RendererHandle* handle) {
    if (handle == nullptr) {
        return "";
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    return handle->lastError;
}

std::string JStringToString(JNIEnv* env, jstring value) {
    if (env == nullptr || value == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeCreateRenderer(JNIEnv*, jclass) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = new RendererHandle();
    handle->viewer = std::make_unique<saba::Viewer>();
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "create renderer handle=%p",
        static_cast<void*>(handle)
    );
    return reinterpret_cast<jlong>(handle);
#else
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeDestroyRenderer(JNIEnv*, jclass, jlong handleValue) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr) {
        return;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "destroy renderer handle=%p",
        static_cast<void*>(handle)
    );
    delete handle;
#else
    (void) handleValue;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeOnSurfaceCreated(
    JNIEnv* env,
    jclass,
    jlong handleValue,
    jobject assetManager
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr || handle->viewer == nullptr) {
        return;
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "surface created handle=%p",
        static_cast<void*>(handle)
    );
    operit::androidbridge::SetAssetManager(AAssetManager_fromJava(env, assetManager));
    std::string error;
    if (!handle->viewer->OnSurfaceCreated(&error)) {
        SetError(handle, error);
    } else {
        ClearError(handle);
    }
#else
    (void) env;
    (void) handleValue;
    (void) assetManager;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeOnSurfaceChanged(
    JNIEnv*,
    jclass,
    jlong handleValue,
    jint width,
    jint height
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr || handle->viewer == nullptr) {
        return;
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "surface changed handle=%p size=%dx%d",
        static_cast<void*>(handle),
        static_cast<int>(width),
        static_cast<int>(height)
    );
    std::string error;
    if (!handle->viewer->OnSurfaceChanged(width, height, &error)) {
        SetError(handle, error);
    } else {
        ClearError(handle);
    }
#else
    (void) handleValue;
    (void) width;
    (void) height;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeRender(JNIEnv*, jclass, jlong handleValue) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr || handle->viewer == nullptr) {
        return JNI_FALSE;
    }

    std::string error;
    if (!handle->viewer->RenderFrame(&error)) {
        SetError(handle, error);
        return JNI_FALSE;
    }
    ClearError(handle);
    return JNI_TRUE;
#else
    (void) handleValue;
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativePause(JNIEnv*, jclass, jlong handleValue) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle != nullptr && handle->viewer != nullptr) {
        handle->viewer->Pause();
    }
#else
    (void) handleValue;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeResume(JNIEnv*, jclass, jlong handleValue) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle != nullptr && handle->viewer != nullptr) {
        handle->viewer->Resume();
    }
#else
    (void) handleValue;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeSetModelPath(
    JNIEnv* env,
    jclass,
    jlong handleValue,
    jstring pathModel
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr || handle->viewer == nullptr) {
        return;
    }

    const std::string modelPath = JStringToString(env, pathModel);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "set model handle=%p path=%s",
        static_cast<void*>(handle),
        modelPath.empty() ? "<empty>" : modelPath.c_str()
    );
    std::string error;
    if (!handle->viewer->SetModelPath(modelPath, &error)) {
        SetError(handle, error);
    } else {
        ClearError(handle);
    }
#else
    (void) env;
    (void) handleValue;
    (void) pathModel;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeSetAnimationState(
    JNIEnv* env,
    jclass,
    jlong handleValue,
    jstring animationName,
    jboolean isLooping
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle == nullptr || handle->viewer == nullptr) {
        return;
    }

    const std::string normalizedAnimationName = JStringToString(env, animationName);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "set animation handle=%p name=%s looping=%d",
        static_cast<void*>(handle),
        normalizedAnimationName.empty() ? "<none>" : normalizedAnimationName.c_str(),
        isLooping == JNI_TRUE ? 1 : 0
    );
    std::string error;
    if (!handle->viewer->SetAnimationState(
            normalizedAnimationName,
            isLooping == JNI_TRUE,
            &error
        )) {
        SetError(handle, error);
    } else {
        ClearError(handle);
    }
#else
    (void) env;
    (void) handleValue;
    (void) animationName;
    (void) isLooping;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeSetModelRotation(
    JNIEnv*,
    jclass,
    jlong handleValue,
    jfloat rotationX,
    jfloat rotationY,
    jfloat rotationZ
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle != nullptr && handle->viewer != nullptr) {
        handle->viewer->SetModelRotation(rotationX, rotationY, rotationZ);
    }
#else
    (void) handleValue;
    (void) rotationX;
    (void) rotationY;
    (void) rotationZ;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeSetCameraDistanceScale(
    JNIEnv*,
    jclass,
    jlong handleValue,
    jfloat scale
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle != nullptr && handle->viewer != nullptr) {
        handle->viewer->SetCameraDistanceScale(scale);
    }
#else
    (void) handleValue;
    (void) scale;
#endif
}

JNIEXPORT void JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeSetCameraTargetHeight(
    JNIEnv*,
    jclass,
    jlong handleValue,
    jfloat height
) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    auto* handle = FromHandle(handleValue);
    if (handle != nullptr && handle->viewer != nullptr) {
        handle->viewer->SetCameraTargetHeight(height);
    }
#else
    (void) handleValue;
    (void) height;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeGetRendererLastError(
    JNIEnv* env,
    jclass,
    jlong handleValue
) {
    const std::string error = GetError(FromHandle(handleValue));
    return env->NewStringUTF(error.c_str());
}

}  // extern "C"
