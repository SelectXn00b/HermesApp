//
//  mnnmodulennative.cpp
//  MNN Module API JNI wrapper
//
//  Created for handling dynamic shape models
//

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <memory>
#include <vector>

#define TAG "MNNModuleNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace MNN;
using namespace MNN::Express;

// 辅助函数：Java String 转 C++ string vector
std::vector<std::string> jstringArrayToVector(JNIEnv* env, jobjectArray jarray) {
    std::vector<std::string> result;
    if (jarray == nullptr) {
        return result;
    }
    
    jsize size = env->GetArrayLength(jarray);
    for (jsize i = 0; i < size; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(jarray, i);
        if (jstr != nullptr) {
            const char* str = env->GetStringUTFChars(jstr, nullptr);
            result.push_back(std::string(str));
            env->ReleaseStringUTFChars(jstr, str);
            env->DeleteLocalRef(jstr);
        }
    }
    return result;
}

// 创建Module从文件
extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeCreateModuleFromFile(
    JNIEnv* env, jclass clazz,
    jstring jfilePath,
    jobjectArray jinputs,
    jobjectArray joutputs,
    jint forwardType,
    jint numThread,
    jint precision,
    jint memoryMode) {
    
    try {
        // 获取文件路径
        const char* filePath = env->GetStringUTFChars(jfilePath, nullptr);
        std::string modelPath(filePath);
        env->ReleaseStringUTFChars(jfilePath, filePath);
        
        // 获取输入输出名称
        std::vector<std::string> inputNames = jstringArrayToVector(env, jinputs);
        std::vector<std::string> outputNames = jstringArrayToVector(env, joutputs);
        
        LOGD("Loading module from: %s", modelPath.c_str());
        LOGD("Input names: %zu, Output names: %zu", inputNames.size(), outputNames.size());
        
        // 配置RuntimeManager
        ScheduleConfig config;
        config.type = (MNNForwardType)forwardType;
        config.numThread = numThread;
        
        BackendConfig backendConfig;
        backendConfig.precision = (BackendConfig::PrecisionMode)precision;
        backendConfig.memory = (BackendConfig::MemoryMode)memoryMode;
        config.backendConfig = &backendConfig;
        
        // 创建RuntimeManager
        std::shared_ptr<Executor::RuntimeManager> rtmgr(
            Executor::RuntimeManager::createRuntimeManager(config)
        );
        
        if (!rtmgr) {
            LOGE("Failed to create RuntimeManager");
            return 0;
        }
        
        // 设置为Module mode
        rtmgr->setMode(Interpreter::Session_Input_Inside);
        
        // 配置Module
        Module::Config moduleConfig;
        moduleConfig.shapeMutable = true;
        moduleConfig.rearrange = true;
        
        // 加载Module
        Module* module = Module::load(
            inputNames,
            outputNames,
            modelPath.c_str(),
            rtmgr,
            &moduleConfig
        );
        
        if (!module) {
            LOGE("Failed to create module from file: %s", modelPath.c_str());
            return 0;
        }
        
        LOGD("Module created successfully");
        return reinterpret_cast<jlong>(module);
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeCreateModuleFromFile: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("Unknown exception in nativeCreateModuleFromFile");
        return 0;
    }
}

// 释放Module
extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeReleaseModule(
    JNIEnv* env, jclass clazz, jlong modulePtr) {
    
    if (modulePtr == 0) {
        return;
    }
    
    try {
        Module* module = reinterpret_cast<Module*>(modulePtr);
        Module::destroy(module);
        LOGD("Module released");
    } catch (const std::exception& e) {
        LOGE("Exception in nativeReleaseModule: %s", e.what());
    }
}

// 前向推理
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeForward(
    JNIEnv* env, jclass clazz,
    jlong modulePtr,
    jlongArray jinputVarPtrs) {
    
    if (modulePtr == 0) {
        LOGE("Invalid module pointer");
        return nullptr;
    }
    
    try {
        Module* module = reinterpret_cast<Module*>(modulePtr);
        
        // 获取输入VARP指针数组
        jsize inputCount = env->GetArrayLength(jinputVarPtrs);
        jlong* inputPtrs = env->GetLongArrayElements(jinputVarPtrs, nullptr);
        
        // 构造输入VARP向量
        std::vector<VARP> inputs;
        for (jsize i = 0; i < inputCount; i++) {
            VARP* varPtr = reinterpret_cast<VARP*>(inputPtrs[i]);
            if (varPtr) {
                inputs.push_back(*varPtr);
            }
        }
        env->ReleaseLongArrayElements(jinputVarPtrs, inputPtrs, JNI_ABORT);
        
        // 执行推理
        std::vector<VARP> outputs = module->onForward(inputs);
        
        // 返回输出VARP指针数组
        jlongArray joutputPtrs = env->NewLongArray(outputs.size());
        if (joutputPtrs) {
            std::vector<jlong> outputPtrs;
            for (auto& var : outputs) {
                VARP* varPtr = new VARP(var);
                outputPtrs.push_back(reinterpret_cast<jlong>(varPtr));
            }
            env->SetLongArrayRegion(joutputPtrs, 0, outputPtrs.size(), outputPtrs.data());
        }
        
        return joutputPtrs;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeForward: %s", e.what());
        return nullptr;
    }
}

// 创建输入VARP
extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeCreateInputVar(
    JNIEnv* env, jclass clazz,
    jintArray jshape,
    jint dataFormat,
    jint dataType) {
    
    try {
        // 获取shape
        jsize shapeSize = env->GetArrayLength(jshape);
        jint* shapeData = env->GetIntArrayElements(jshape, nullptr);
        
        std::vector<int> shape(shapeData, shapeData + shapeSize);
        env->ReleaseIntArrayElements(jshape, shapeData, JNI_ABORT);
        
        // 创建halide_type_t
        halide_type_t dtype;
        dtype.code = (halide_type_code_t)(dataType >> 8);
        dtype.bits = dataType & 0xFF;
        dtype.lanes = 1;
        
        // 创建VARP
        VARP var = _Input(shape, (Dimensionformat)dataFormat, dtype);
        VARP* varPtr = new VARP(var);
        
        return reinterpret_cast<jlong>(varPtr);
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeCreateInputVar: %s", e.what());
        return 0;
    }
}

// 设置VARP数据（float）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeSetVarFloatData(
    JNIEnv* env, jclass clazz,
    jlong varPtr,
    jfloatArray jdata) {
    
    if (varPtr == 0) {
        return JNI_FALSE;
    }
    
    try {
        VARP* var = reinterpret_cast<VARP*>(varPtr);
        
        jsize dataSize = env->GetArrayLength(jdata);
        jfloat* data = env->GetFloatArrayElements(jdata, nullptr);
        
        // 写入数据
        auto ptr = (*var)->writeMap<float>();
        if (ptr) {
            memcpy(ptr, data, dataSize * sizeof(float));
        }
        
        env->ReleaseFloatArrayElements(jdata, data, JNI_ABORT);
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeSetVarFloatData: %s", e.what());
        return JNI_FALSE;
    }
}

// 设置VARP数据（int）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeSetVarIntData(
    JNIEnv* env, jclass clazz,
    jlong varPtr,
    jintArray jdata) {
    
    if (varPtr == 0) {
        return JNI_FALSE;
    }
    
    try {
        VARP* var = reinterpret_cast<VARP*>(varPtr);
        
        jsize dataSize = env->GetArrayLength(jdata);
        jint* data = env->GetIntArrayElements(jdata, nullptr);
        
        // 写入数据
        auto ptr = (*var)->writeMap<int>();
        if (ptr) {
            memcpy(ptr, data, dataSize * sizeof(int));
        }
        
        env->ReleaseIntArrayElements(jdata, data, JNI_ABORT);
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeSetVarIntData: %s", e.what());
        return JNI_FALSE;
    }
}

// 获取VARP数据（float）
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeGetVarFloatData(
    JNIEnv* env, jclass clazz, jlong varPtr) {
    
    if (varPtr == 0) {
        return nullptr;
    }
    
    try {
        VARP* var = reinterpret_cast<VARP*>(varPtr);
        auto info = (*var)->getInfo();
        
        if (!info) {
            LOGE("Failed to get var info");
            return nullptr;
        }
        
        int size = info->size;
        auto ptr = (*var)->readMap<float>();
        
        if (!ptr) {
            LOGE("Failed to read var data");
            return nullptr;
        }
        
        jfloatArray jdata = env->NewFloatArray(size);
        if (jdata) {
            env->SetFloatArrayRegion(jdata, 0, size, ptr);
        }
        
        return jdata;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeGetVarFloatData: %s", e.what());
        return nullptr;
    }
}

// 获取VARP形状
extern "C" JNIEXPORT jintArray JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeGetVarShape(
    JNIEnv* env, jclass clazz, jlong varPtr) {
    
    if (varPtr == 0) {
        return nullptr;
    }
    
    try {
        VARP* var = reinterpret_cast<VARP*>(varPtr);
        auto info = (*var)->getInfo();
        
        if (!info) {
            return nullptr;
        }
        
        jintArray jshape = env->NewIntArray(info->dim.size());
        if (jshape) {
            env->SetIntArrayRegion(jshape, 0, info->dim.size(), info->dim.data());
        }
        
        return jshape;
        
    } catch (const std::exception& e) {
        LOGE("Exception in nativeGetVarShape: %s", e.what());
        return nullptr;
    }
}

// 释放VARP
extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_mnn_MNNModuleNative_nativeReleaseVar(
    JNIEnv* env, jclass clazz, jlong varPtr) {
    
    if (varPtr == 0) {
        return;
    }
    
    try {
        VARP* var = reinterpret_cast<VARP*>(varPtr);
        delete var;
    } catch (const std::exception& e) {
        LOGE("Exception in nativeReleaseVar: %s", e.what());
    }
}

