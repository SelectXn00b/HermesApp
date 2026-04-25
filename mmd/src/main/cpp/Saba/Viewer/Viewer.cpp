#include "Viewer.h"

#include <android/log.h>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <limits>
#include <vector>

#include "Saba/Base/Path.h"
#include "Camera.h"
#include "Light.h"
#include "ModelDrawer.h"
#include "ShadowMap.h"
#include "Saba/GL/Model/MMD/GLMMDModel.h"
#include "Saba/GL/Model/MMD/GLMMDModelDrawContext.h"
#include "Saba/GL/Model/MMD/GLMMDModelDrawer.h"
#include "Saba/Model/MMD/PMDModel.h"
#include "Saba/Model/MMD/PMXModel.h"
#include "Saba/Model/MMD/VMDFile.h"

namespace saba {
namespace {

constexpr const char* kTag = "SabaViewer";

float ComputeRadius(const glm::vec3& bboxMin, const glm::vec3& bboxMax) {
    const glm::vec3 extent = bboxMax - bboxMin;
    const float maxExtent = glm::max(extent.x, glm::max(extent.y, extent.z));
    return glm::max(maxExtent * 0.5f, 0.1f);
}

glm::vec3 ComputeCenter(const glm::vec3& bboxMin, const glm::vec3& bboxMax) {
    return (bboxMin + bboxMax) * 0.5f;
}

bool IsFiniteVec3(const glm::vec3& value) {
    return std::isfinite(value.x) && std::isfinite(value.y) && std::isfinite(value.z);
}

template <typename TIndex>
bool AccumulateReferencedBounds(
    const glm::vec3* positions,
    size_t vertexCount,
    const void* indices,
    size_t indexCount,
    std::vector<uint8_t>* referencedVertices,
    glm::vec3* bboxMin,
    glm::vec3* bboxMax,
    size_t* referencedCount,
    size_t* skippedNonFiniteCount,
    std::string* outError
) {
    const auto* typedIndices = static_cast<const TIndex*>(indices);
    for (size_t index = 0; index < indexCount; ++index) {
        const size_t vertexIndex = static_cast<size_t>(typedIndices[index]);
        if (vertexIndex >= vertexCount) {
            if (outError != nullptr) {
                *outError = "MMD model contains an out-of-range face index.";
            }
            return false;
        }
        if ((*referencedVertices)[vertexIndex] != 0) {
            continue;
        }

        (*referencedVertices)[vertexIndex] = 1;
        const glm::vec3& position = positions[vertexIndex];
        if (!IsFiniteVec3(position)) {
            (*skippedNonFiniteCount)++;
            continue;
        }

        if (*referencedCount == 0) {
            *bboxMin = position;
            *bboxMax = position;
        } else {
            *bboxMin = glm::min(*bboxMin, position);
            *bboxMax = glm::max(*bboxMax, position);
        }
        (*referencedCount)++;
    }
    return true;
}

bool ComputeReferencedModelBounds(
    const MMDModel& model,
    glm::vec3* outBBoxMin,
    glm::vec3* outBBoxMax,
    size_t* outReferencedCount,
    size_t* outSkippedNonFiniteCount,
    std::string* outError
) {
    const glm::vec3* positions = model.GetPositions();
    const void* indices = model.GetIndices();
    const size_t vertexCount = model.GetVertexCount();
    const size_t indexCount = model.GetIndexCount();
    const size_t indexElementSize = model.GetIndexElementSize();

    if (positions == nullptr || vertexCount == 0) {
        if (outError != nullptr) {
            *outError = "MMD model has no vertices.";
        }
        return false;
    }
    if (indices == nullptr || indexCount == 0) {
        if (outError != nullptr) {
            *outError = "MMD model has no faces.";
        }
        return false;
    }

    std::vector<uint8_t> referencedVertices(vertexCount, 0);
    glm::vec3 bboxMin(std::numeric_limits<float>::max());
    glm::vec3 bboxMax(-std::numeric_limits<float>::max());
    size_t referencedCount = 0;
    size_t skippedNonFiniteCount = 0;

    bool success = false;
    switch (indexElementSize) {
        case 1:
            success = AccumulateReferencedBounds<uint8_t>(
                positions,
                vertexCount,
                indices,
                indexCount,
                &referencedVertices,
                &bboxMin,
                &bboxMax,
                &referencedCount,
                &skippedNonFiniteCount,
                outError
            );
            break;
        case 2:
            success = AccumulateReferencedBounds<uint16_t>(
                positions,
                vertexCount,
                indices,
                indexCount,
                &referencedVertices,
                &bboxMin,
                &bboxMax,
                &referencedCount,
                &skippedNonFiniteCount,
                outError
            );
            break;
        case 4:
            success = AccumulateReferencedBounds<uint32_t>(
                positions,
                vertexCount,
                indices,
                indexCount,
                &referencedVertices,
                &bboxMin,
                &bboxMax,
                &referencedCount,
                &skippedNonFiniteCount,
                outError
            );
            break;
        default:
            if (outError != nullptr) {
                *outError = "Unsupported MMD index element size.";
            }
            return false;
    }

    if (!success) {
        return false;
    }
    if (referencedCount == 0) {
        if (outError != nullptr) {
            *outError = "MMD model has no finite referenced vertices.";
        }
        return false;
    }

    if (outBBoxMin != nullptr) {
        *outBBoxMin = bboxMin;
    }
    if (outBBoxMax != nullptr) {
        *outBBoxMax = bboxMax;
    }
    if (outReferencedCount != nullptr) {
        *outReferencedCount = referencedCount;
    }
    if (outSkippedNonFiniteCount != nullptr) {
        *outSkippedNonFiniteCount = skippedNonFiniteCount;
    }
    return true;
}

}  // namespace

Viewer::Viewer() = default;

Viewer::~Viewer() {
    Destroy();
}

bool Viewer::Initialize(std::string* outError) {
    if (m_initialized) {
        return true;
    }

    auto fail = [&](const std::string& message) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        if (outError != nullptr) {
            *outError = message;
        }
        return false;
    };

    m_viewerContext.SetFrameBufferSize(1, 1);
    m_viewerContext.SetWindowSize(1, 1);
    m_viewerContext.EnableShadow(true);
    m_viewerContext.EnableCameraOverride(false);
    m_viewerContext.SetClipElapsed(true);
    m_viewerContext.SetPlayMode(ViewerContext::PlayMode::Stop);
    m_viewerContext.SetMMDGroundShadowColor(glm::vec4(0.0f, 0.0f, 0.0f, 0.35f));

    if (!m_viewerContext.Initialize()) {
        return fail("failed to initialize viewer context.");
    }

    if (!m_viewerContext.m_shadowmap.InitializeShader(&m_viewerContext)) {
        return fail("failed to initialize viewer shadow map shader.");
    }

    if (!m_viewerContext.m_shadowmap.Setup(1024, 1024, 4)) {
        return fail("failed to initialize viewer shadow map framebuffer.");
    }

    m_viewerContext.m_light.SetLightColor(glm::vec3(1.0f));
    m_viewerContext.m_light.SetLightDirection(glm::normalize(glm::vec3(-0.35f, -1.0f, -0.45f)));
    m_viewerContext.m_camera.SetSize(1.0f, 1.0f);
    m_viewerContext.m_camera.SetClip(m_nearClip, m_farClip);
    m_viewerContext.m_camera.UpdateMatrix();

    m_mmdDrawContext = std::make_unique<GLMMDModelDrawContext>(&m_viewerContext);
    m_initialized = true;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "initialize success resourceDir=%s",
        m_viewerContext.GetResourceDir().c_str()
    );
    return true;
}

void Viewer::Destroy() {
    ClearModel();
    m_mmdDrawContext.reset();
    if (m_initialized) {
        m_viewerContext.Uninitialize();
    }
    m_initialized = false;
    m_surfaceCreated = false;
}

bool Viewer::OnSurfaceCreated(std::string* outError) {
    if (!EnsureInitialized(outError)) {
        return false;
    }
    m_surfaceCreated = true;
    m_hasLastFrameAt = false;
    m_modelStateApplied = false;
    m_animationStateApplied = m_animationName.empty();
    __android_log_print(ANDROID_LOG_INFO, kTag, "surface created");
    return true;
}

bool Viewer::OnSurfaceChanged(int width, int height, std::string* outError) {
    if (!EnsureInitialized(outError)) {
        return false;
    }

    const int safeWidth = width > 0 ? width : 1;
    const int safeHeight = height > 0 ? height : 1;
    m_viewerContext.SetFrameBufferSize(safeWidth, safeHeight);
    m_viewerContext.SetWindowSize(safeWidth, safeHeight);
    m_viewerContext.ResizeCaptureTexture();
    UpdateCamera();
    glViewport(0, 0, safeWidth, safeHeight);
    __android_log_print(ANDROID_LOG_INFO, kTag, "surface changed size=%dx%d", safeWidth, safeHeight);
    return true;
}

bool Viewer::RenderFrame(std::string* outError) {
    if (!EnsureInitialized(outError)) {
        return false;
    }

    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    UpdateAnimationClock();
    UpdateCamera();

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, m_viewerContext.GetFrameBufferWidth(), m_viewerContext.GetFrameBufferHeight());
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    if (m_mmdDrawer == nullptr) {
        return true;
    }

    m_viewerContext.m_shadowmap.SetClip(m_nearClip, m_farClip);
    m_viewerContext.m_shadowmap.CalcShadowMap(&m_viewerContext.m_camera, &m_viewerContext.m_light);

    for (size_t shadowIndex = 0; shadowIndex < m_viewerContext.m_shadowmap.GetShadowMapCount(); ++shadowIndex) {
        const auto& clipSpace = m_viewerContext.m_shadowmap.GetClipSpace(shadowIndex);
        glBindFramebuffer(GL_FRAMEBUFFER, clipSpace.m_shadowmapFBO);
        glViewport(0, 0, m_viewerContext.m_shadowmap.GetWidth(), m_viewerContext.m_shadowmap.GetHeight());
        glClear(GL_DEPTH_BUFFER_BIT);
        m_mmdDrawer->DrawShadowMap(&m_viewerContext, shadowIndex);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, m_viewerContext.GetFrameBufferWidth(), m_viewerContext.GetFrameBufferHeight());
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    m_mmdDrawer->Update(&m_viewerContext);
    m_mmdDrawer->Draw(&m_viewerContext);
    return true;
}

bool Viewer::SetModelPath(const std::string& modelPath, std::string* outError) {
    if (modelPath.empty()) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "clear model");
        ClearModel();
        m_modelPath.clear();
        m_modelStateApplied = true;
        return true;
    }
    if (modelPath == m_modelPath && m_modelStateApplied) {
        return true;
    }
    return LoadModel(modelPath, outError);
}

bool Viewer::SetAnimationState(const std::string& animationName, bool isLooping, std::string* outError) {
    if (animationName == m_animationName && isLooping == m_animationLooping && m_animationStateApplied) {
        return true;
    }
    return LoadMotion(animationName, isLooping, outError);
}

void Viewer::SetModelRotation(float rotationX, float rotationY, float rotationZ) {
    m_rotationX = rotationX;
    m_rotationY = rotationY;
    m_rotationZ = rotationZ;
    UpdateModelTransform();
}

void Viewer::SetCameraDistanceScale(float scale) {
    m_cameraDistanceScale = glm::clamp(scale, 0.02f, 12.0f);
    UpdateCamera();
}

void Viewer::SetCameraTargetHeight(float height) {
    m_cameraTargetHeight = glm::clamp(height, -2.0f, 2.0f);
    UpdateCamera();
}

void Viewer::Pause() {
    m_paused = true;
}

void Viewer::Resume() {
    m_paused = false;
    m_hasLastFrameAt = false;
}

bool Viewer::EnsureInitialized(std::string* outError) {
    if (m_initialized) {
        return true;
    }
    return Initialize(outError);
}

void Viewer::ClearModel() {
    m_mmdDrawer.reset();
    m_glMmdModel.reset();
    m_mmdModel.reset();
    m_motionPath.clear();
    m_maxMotionFrame = 0;
    m_modelStateApplied = false;
    m_animationStateApplied = m_animationName.empty();
    m_viewerContext.SetAnimationTime(0.0);
    m_viewerContext.SetPlayMode(ViewerContext::PlayMode::Stop);
}

bool Viewer::LoadModel(const std::string& modelPath, std::string* outError) {
    if (!EnsureInitialized(outError)) {
        return false;
    }

    auto fail = [&](const std::string& message) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        if (outError != nullptr) {
            *outError = message;
        }
        return false;
    };

    std::shared_ptr<MMDModel> model;
    glm::vec3 bboxMin(0.0f);
    glm::vec3 bboxMax(0.0f);
    size_t referencedVertexCount = 0;
    size_t skippedNonFiniteVertexCount = 0;

    const std::filesystem::path path(modelPath);
    const std::string extension = path.extension().string();
    const std::string sharedMmdResourceDir = PathUtil::Combine(m_viewerContext.GetResourceDir(), "mmd");
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load model start path=%s ext=%s sharedMmdResourceDir=%s",
        modelPath.c_str(),
        extension.c_str(),
        sharedMmdResourceDir.c_str()
    );

    if (extension == ".pmd" || extension == ".PMD") {
        auto pmdModel = std::make_shared<PMDModel>();
        if (!pmdModel->Load(modelPath, sharedMmdResourceDir)) {
            return fail("failed to load PMD model: " + modelPath);
        }
        model = pmdModel;
    } else if (extension == ".pmx" || extension == ".PMX") {
        auto pmxModel = std::make_shared<PMXModel>();
        if (!pmxModel->Load(modelPath, sharedMmdResourceDir)) {
            return fail("failed to load PMX model: " + modelPath);
        }
        model = pmxModel;
    } else {
        return fail("unsupported MMD model extension: " + modelPath);
    }

    if (!ComputeReferencedModelBounds(
            *model,
            &bboxMin,
            &bboxMax,
            &referencedVertexCount,
            &skippedNonFiniteVertexCount,
            outError
        )) {
        return false;
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "computed referenced bbox path=%s referencedVertices=%zu skippedNonFinite=%zu bboxMin=(%.6f, %.6f, %.6f) bboxMax=(%.6f, %.6f, %.6f)",
        modelPath.c_str(),
        referencedVertexCount,
        skippedNonFiniteVertexCount,
        bboxMin.x,
        bboxMin.y,
        bboxMin.z,
        bboxMax.x,
        bboxMax.y,
        bboxMax.z
    );

    auto glModel = std::make_shared<GLMMDModel>();
    if (!glModel->Create(model)) {
        return fail("failed to create GL MMD model resources.");
    }
    // This avatar preview uses a transparent background and centers the model on screen.
    // Keeping Saba's ground shadow in that setup projects a dark silhouette into empty space.
    glModel->EnableGroundShadow(false);

    auto drawer = std::make_shared<GLMMDModelDrawer>(m_mmdDrawContext.get(), glModel);
    if (!drawer->Create()) {
        return fail("failed to create GL MMD model drawer.");
    }

    drawer->SetBBox(bboxMin, bboxMax);

    m_modelCenter = ComputeCenter(bboxMin, bboxMax);
    const float radius = ComputeRadius(bboxMin, bboxMax);
    m_baseCameraDistance = glm::max(1.8f, radius * 3.0f + 0.7f);
    m_nearClip = glm::max(0.01f, radius / 100.0f);
    m_farClip = glm::max(120.0f, m_baseCameraDistance + radius * 40.0f);

    m_modelPath = modelPath;
    m_mmdModel = std::move(model);
    m_glMmdModel = std::move(glModel);
    m_mmdDrawer = std::move(drawer);
    m_hasLastFrameAt = false;
    m_modelStateApplied = true;

    UpdateModelTransform();
    UpdateCamera();

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load model success path=%s vertices=%zu materials=%zu subMeshes=%zu bboxMin=(%.6f, %.6f, %.6f) bboxMax=(%.6f, %.6f, %.6f) radius=%.3f cameraDistance=%.3f clip=%.4f..%.3f",
        modelPath.c_str(),
        m_mmdModel->GetVertexCount(),
        m_mmdModel->GetMaterialCount(),
        m_mmdModel->GetSubMeshCount(),
        bboxMin.x,
        bboxMin.y,
        bboxMin.z,
        bboxMax.x,
        bboxMax.y,
        bboxMax.z,
        radius,
        m_baseCameraDistance,
        m_nearClip,
        m_farClip
    );

    if (!m_animationName.empty()) {
        return LoadMotion(m_animationName, m_animationLooping, outError);
    }
    m_animationStateApplied = true;
    return true;
}

bool Viewer::LoadMotion(const std::string& animationName, bool isLooping, std::string* outError) {
    auto fail = [&](const std::string& message) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        if (outError != nullptr) {
            *outError = message;
        }
        return false;
    };

    m_animationName = animationName;
    m_animationLooping = isLooping;
    m_motionPath.clear();
    m_maxMotionFrame = 0;
    m_animationStateApplied = false;
    m_viewerContext.SetAnimationTime(0.0);
    m_hasLastFrameAt = false;

    if (m_glMmdModel == nullptr || m_modelPath.empty()) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "skip motion apply name=%s because model is not ready",
            animationName.empty() ? "<none>" : animationName.c_str()
        );
        return true;
    }

    m_glMmdModel->ClearAnimation();
    if (animationName.empty()) {
        m_animationStateApplied = true;
        __android_log_print(ANDROID_LOG_INFO, kTag, "clear motion for model=%s", m_modelPath.c_str());
        return true;
    }

    const std::filesystem::path motionPath = std::filesystem::path(m_modelPath).parent_path() / animationName;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load motion start model=%s motion=%s looping=%d",
        m_modelPath.c_str(),
        motionPath.string().c_str(),
        isLooping ? 1 : 0
    );
    VMDFile vmdFile;
    if (!ReadVMDFile(&vmdFile, motionPath.string().c_str())) {
        return fail("failed to load VMD motion: " + motionPath.string());
    }

    if (!m_glMmdModel->LoadAnimation(vmdFile)) {
        return fail("failed to bind VMD motion to loaded MMD model.");
    }

    m_glMmdModel->ResetAnimation();
    m_motionPath = motionPath.string();
    if (m_glMmdModel->GetVMDAnimation() != nullptr) {
        m_maxMotionFrame = m_glMmdModel->GetVMDAnimation()->GetMaxKeyTime();
    }
    m_animationStateApplied = true;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load motion success motion=%s maxFrame=%d looping=%d",
        m_motionPath.c_str(),
        m_maxMotionFrame,
        m_animationLooping ? 1 : 0
    );
    return true;
}

void Viewer::UpdateModelTransform() {
    if (m_mmdDrawer == nullptr) {
        return;
    }

    m_mmdDrawer->SetTranslate(-m_modelCenter);
    m_mmdDrawer->SetRotate(glm::radians(glm::vec3(m_rotationX, m_rotationY, m_rotationZ)));
}

void Viewer::UpdateCamera() {
    const float effectiveCameraDistance = m_baseCameraDistance * m_cameraDistanceScale;
    const float effectiveNearClip = glm::clamp(m_nearClip * m_cameraDistanceScale, 0.005f, 0.1f);

    m_viewerContext.m_camera.LookAt(
        glm::vec3(0.0f, m_cameraTargetHeight, 0.0f),
        glm::vec3(0.0f, 0.18f + m_cameraTargetHeight, effectiveCameraDistance),
        glm::vec3(0.0f, 1.0f, 0.0f)
    );
    m_viewerContext.m_camera.SetSize(
        static_cast<float>(glm::max(1, m_viewerContext.GetFrameBufferWidth())),
        static_cast<float>(glm::max(1, m_viewerContext.GetFrameBufferHeight()))
    );
    m_viewerContext.m_camera.SetClip(effectiveNearClip, m_farClip);
    m_viewerContext.m_camera.UpdateMatrix();
}

void Viewer::UpdateAnimationClock() {
    const auto now = std::chrono::steady_clock::now();
    double elapsedSeconds = 0.0;
    if (m_hasLastFrameAt) {
        elapsedSeconds = std::chrono::duration<double>(now - m_lastFrameAt).count();
    }
    m_lastFrameAt = now;
    m_hasLastFrameAt = true;

    if (m_paused) {
        elapsedSeconds = 0.0;
    }

    m_viewerContext.SetElapsedTime(elapsedSeconds);
    if (m_motionPath.empty()) {
        m_viewerContext.SetPlayMode(ViewerContext::PlayMode::Stop);
        return;
    }

    double animationTime = m_viewerContext.GetAnimationTime();
    if (!m_paused) {
        animationTime += elapsedSeconds;
    }

    const double maxAnimationTime = static_cast<double>(m_maxMotionFrame) / 30.0;
    if (maxAnimationTime > 0.0) {
        if (m_animationLooping) {
            while (animationTime > maxAnimationTime) {
                animationTime -= maxAnimationTime;
            }
        } else if (animationTime > maxAnimationTime) {
            animationTime = maxAnimationTime;
        }
    }

    m_viewerContext.SetAnimationTime(animationTime);
    m_viewerContext.SetPlayMode(ViewerContext::PlayMode::Play);
}

}  // namespace saba
