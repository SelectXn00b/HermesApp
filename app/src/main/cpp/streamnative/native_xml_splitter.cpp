#include <jni.h>
#include <vector>

#include "streamnative/StreamOperators.h"

namespace {

inline jintArray segmentsToJIntArray(JNIEnv* env, const std::vector<streamnative::Segment>& segments) {
    jintArray out = env->NewIntArray(static_cast<jsize>(segments.size() * 3));
    if (out == nullptr) {
        return nullptr;
    }

    std::vector<jint> flat;
    flat.reserve(segments.size() * 3);
    for (const auto& s : segments) {
        flat.push_back(static_cast<jint>(s.type));
        flat.push_back(static_cast<jint>(s.start));
        flat.push_back(static_cast<jint>(s.end));
    }

    env->SetIntArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    return out;
}

} // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_ai_assistance_operit_util_streamnative_NativeXmlSplitter_nativeSplitXmlSegments(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring content
) {
    if (content == nullptr) {
        return env->NewIntArray(0);
    }

    const jsize len = env->GetStringLength(content);
    const jchar* chars = env->GetStringChars(content, nullptr);

    std::vector<streamnative::Segment> segments = streamnative::splitByXml(chars, static_cast<int>(len));

    env->ReleaseStringChars(content, chars);

    return segmentsToJIntArray(env, segments);
}
