#pragma once

#include "ViewerContext.h"

#include <memory>
#include <string>
#include <chrono>

namespace saba {

class GLMMDModel;
class GLMMDModelDrawContext;
class GLMMDModelDrawer;
class MMDModel;

class Viewer {
public:
    Viewer();
    ~Viewer();

    bool Initialize(std::string* outError);
    void Destroy();

    bool OnSurfaceCreated(std::string* outError);
    bool OnSurfaceChanged(int width, int height, std::string* outError);
    bool RenderFrame(std::string* outError);

    bool SetModelPath(const std::string& modelPath, std::string* outError);
    bool SetAnimationState(const std::string& animationName, bool isLooping, std::string* outError);
    void SetModelRotation(float rotationX, float rotationY, float rotationZ);
    void SetCameraDistanceScale(float scale);
    void SetCameraTargetHeight(float height);
    void Pause();
    void Resume();

private:
    bool EnsureInitialized(std::string* outError);
    void ClearModel();
    bool LoadModel(const std::string& modelPath, std::string* outError);
    bool LoadMotion(const std::string& animationName, bool isLooping, std::string* outError);
    void UpdateModelTransform();
    void UpdateCamera();
    void UpdateAnimationClock();

private:
    ViewerContext m_viewerContext;
    std::unique_ptr<GLMMDModelDrawContext> m_mmdDrawContext;
    std::shared_ptr<GLMMDModel> m_glMmdModel;
    std::shared_ptr<GLMMDModelDrawer> m_mmdDrawer;
    std::shared_ptr<MMDModel> m_mmdModel;

    std::string m_modelPath;
    std::string m_animationName;
    std::string m_motionPath;

    bool m_initialized = false;
    bool m_surfaceCreated = false;
    bool m_paused = false;
    bool m_animationLooping = false;
    bool m_modelStateApplied = false;
    bool m_animationStateApplied = true;

    float m_rotationX = 0.0f;
    float m_rotationY = 0.0f;
    float m_rotationZ = 0.0f;
    float m_cameraDistanceScale = 1.0f;
    float m_cameraTargetHeight = 0.0f;

    float m_baseCameraDistance = 3.0f;
    float m_nearClip = 0.01f;
    float m_farClip = 100.0f;
    glm::vec3 m_modelCenter = glm::vec3(0.0f);

    int32_t m_maxMotionFrame = 0;
    std::chrono::steady_clock::time_point m_lastFrameAt;
    bool m_hasLastFrameAt = false;
};

}  // namespace saba
