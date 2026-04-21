# Saba Viewer Android Mapping

## Core Files

| Upstream | Local | Status | Notes |
| --- | --- | --- | --- |
| `viewer/Saba/Viewer/Viewer.h` | `mmd/src/main/cpp/Saba/Viewer/Viewer.h` | Restored | Android-adapted lifecycle API replaces desktop `Run()`. |
| `viewer/Saba/Viewer/Viewer.cpp` | `mmd/src/main/cpp/Saba/Viewer/Viewer.cpp` | Restored | Keeps `saba::Viewer` as native owner, drives render/update passes on Android. |
| `viewer/Saba/Viewer/ViewerContext.h` | `mmd/src/main/cpp/Saba/Viewer/ViewerContext.h` | Restored | Same type boundary, Android assets replace cwd/resource discovery. |
| `viewer/Saba/Viewer/ViewerContext.cpp` | `mmd/src/main/cpp/Saba/Viewer/ViewerContext.cpp` | Restored | Shader/resource root fixed to `Saba/Viewer/resource`. |
| `viewer/Saba/Viewer/Camera.h` | `mmd/src/main/cpp/Saba/Viewer/Camera.h` | Restored | Unchanged type. |
| `viewer/Saba/Viewer/Camera.cpp` | `mmd/src/main/cpp/Saba/Viewer/Camera.cpp` | Restored | Unchanged role. |
| `viewer/Saba/Viewer/Light.h` | `mmd/src/main/cpp/Saba/Viewer/Light.h` | Restored | Unchanged type. |
| `viewer/Saba/Viewer/Light.cpp` | `mmd/src/main/cpp/Saba/Viewer/Light.cpp` | Restored | Unchanged role. |
| `viewer/Saba/Viewer/ShadowMap.h` | `mmd/src/main/cpp/Saba/Viewer/ShadowMap.h` | Restored | Same class boundary, GLES3-safe texture config. |
| `viewer/Saba/Viewer/ShadowMap.cpp` | `mmd/src/main/cpp/Saba/Viewer/ShadowMap.cpp` | Restored | Border color path replaced with `GL_CLAMP_TO_EDGE` for GLES3. |
| `viewer/Saba/Viewer/Grid.h` | `mmd/src/main/cpp/Saba/Viewer/Grid.h` | Restored | Same type retained. |
| `viewer/Saba/Viewer/Grid.cpp` | `mmd/src/main/cpp/Saba/Viewer/Grid.cpp` | Restored | `gl3w` include removed, now GLES3-native. |
| `viewer/Saba/Viewer/ModelDrawer.h` | `mmd/src/main/cpp/Saba/Viewer/ModelDrawer.h` | Restored | Same abstract drawer boundary. |
| `viewer/Saba/Viewer/ModelDrawer.cpp` | `mmd/src/main/cpp/Saba/Viewer/ModelDrawer.cpp` | Restored | Unchanged transform logic. |
| `viewer/Saba/Viewer/CameraOverrider.h` | `mmd/src/main/cpp/Saba/Viewer/CameraOverrider.h` | Restored | Same type retained. |
| `viewer/Saba/Viewer/CameraOverrider.cpp` | `mmd/src/main/cpp/Saba/Viewer/CameraOverrider.cpp` | Restored | Unchanged override hook. |
| `viewer/Saba/Viewer/VMDCameraOverrider.h` | `mmd/src/main/cpp/Saba/Viewer/VMDCameraOverrider.h` | Restored | Same type retained. |
| `viewer/Saba/Viewer/VMDCameraOverrider.cpp` | `mmd/src/main/cpp/Saba/Viewer/VMDCameraOverrider.cpp` | Restored | Uses Saba camera animation runtime. |
| `viewer/Saba/Viewer/ViewerCommand.h` | `mmd/src/main/cpp/Saba/Viewer/ViewerCommand.h` | Restored | Type kept for upstream parity. |
| `viewer/Saba/Viewer/ViewerCommand.cpp` | `mmd/src/main/cpp/Saba/Viewer/ViewerCommand.cpp` | Restored | Parser kept, Android side does not expose command console UI. |
| `viewer/Saba/GL/GLObject.h` | `mmd/src/main/cpp/Saba/GL/GLObject.h` | Restored | Desktop GL loader removed, GLES3 guards added. |
| `viewer/Saba/GL/GLShaderUtil.h` | `mmd/src/main/cpp/Saba/GL/GLShaderUtil.h` | Restored | Same public surface. |
| `viewer/Saba/GL/GLShaderUtil.cpp` | `mmd/src/main/cpp/Saba/GL/GLShaderUtil.cpp` | Restored | GLES3-safe shader type handling. |
| `viewer/Saba/GL/GLSLUtil.h` | `mmd/src/main/cpp/Saba/GL/GLSLUtil.h` | Restored | Same type boundary. |
| `viewer/Saba/GL/GLSLUtil.cpp` | `mmd/src/main/cpp/Saba/GL/GLSLUtil.cpp` | Restored | Android asset-backed shader loader replaces desktop preprocess stack. |
| `viewer/Saba/GL/GLTextureUtil.h` | `mmd/src/main/cpp/Saba/GL/GLTextureUtil.h` | Restored | Same entrypoints. |
| `viewer/Saba/GL/GLTextureUtil.cpp` | `mmd/src/main/cpp/Saba/GL/GLTextureUtil.cpp` | Restored | Android asset/file loading plus DDS CPU decode for GLES3 upload. |
| `viewer/Saba/GL/GLVertexUtil.h` | `mmd/src/main/cpp/Saba/GL/GLVertexUtil.h` | Restored | GLES3 guard added for unavailable `GL_DOUBLE`. |
| `viewer/Saba/GL/Model/MMD/GLMMDModel.h` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModel.h` | Restored | Same GPU model wrapper. |
| `viewer/Saba/GL/Model/MMD/GLMMDModel.cpp` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModel.cpp` | Restored | Uses formal Saba animation/physics update path and texture cache. |
| `viewer/Saba/GL/Model/MMD/GLMMDModelDrawContext.h` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawContext.h` | Restored | Same shader-combination boundary. |
| `viewer/Saba/GL/Model/MMD/GLMMDModelDrawContext.cpp` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawContext.cpp` | Restored | Same shader setup flow. |
| `viewer/Saba/GL/Model/MMD/GLMMDModelDrawer.h` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawer.h` | Restored | Same drawer boundary. |
| `viewer/Saba/GL/Model/MMD/GLMMDModelDrawer.cpp` | `mmd/src/main/cpp/Saba/GL/Model/MMD/GLMMDModelDrawer.cpp` | Restored | Main pass, edge pass, shadow pass, ground shadow pass retained. |

## Android Adapter Layer

| Purpose | Local file | Notes |
| --- | --- | --- |
| Asset-backed resource IO | `mmd/src/main/cpp/android/AndroidAssetSupport.h` | New adapter, keeps viewer core free of Android types. |
| Asset-backed resource IO | `mmd/src/main/cpp/android/AndroidAssetSupport.cpp` | Resolves shaders, builtin toon textures, and binary assets. |
| JNI handle bridge | `mmd/src/main/cpp/android/MmdRendererBridge.cpp` | Thin renderer-instance bridge for `create/surface/render/pause/resume/destroy`. |
| Kotlin thin shell | `mmd/src/main/java/com/ai/assistance/mmd/MmdNative.kt` | High-level renderer JNI only, inspector API kept separate. |
| Kotlin thin shell | `mmd/src/main/java/com/ai/assistance/mmd/MmdGlSurfaceView.kt` | Business-facing entry retained, GLES3 shell only. |

## Resource Mapping

| Upstream | Local | Status | Notes |
| --- | --- | --- | --- |
| `viewer/Saba/Viewer/resource/shader/mmd.vert` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd.vert` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/mmd.frag` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd.frag` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/mmd_edge.vert` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd_edge.vert` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/mmd_edge.frag` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd_edge.frag` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/mmd_ground_shadow.vert` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd_ground_shadow.vert` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/mmd_ground_shadow.frag` | `mmd/src/main/assets/Saba/Viewer/resource/shader/mmd_ground_shadow.frag` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/shadow_shader.vert` | `mmd/src/main/assets/Saba/Viewer/resource/shader/shadow_shader.vert` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/shadow_shader.frag` | `mmd/src/main/assets/Saba/Viewer/resource/shader/shadow_shader.frag` | Restored | Converted to GLSL ES 3.0. |
| `viewer/Saba/Viewer/resource/shader/grid.vert` | `mmd/src/main/assets/Saba/Viewer/resource/shader/grid.vert` | Restored | Added for retained `Grid` type. |
| `viewer/Saba/Viewer/resource/shader/grid.frag` | `mmd/src/main/assets/Saba/Viewer/resource/shader/grid.frag` | Restored | Added for retained `Grid` type. |
| `viewer/Saba/Viewer/resource/mmd/toon01.bmp` - `toon10.bmp` | `mmd/src/main/assets/Saba/Viewer/resource/mmd/toon01.bmp` - `toon10.bmp` | Restored | Mirrors upstream shared toon resource directory. |

## Not Restored

| Upstream | Status | Reason |
| --- | --- | --- |
| `viewer/Saba/Viewer/Viewer` desktop GLFW loop, callbacks, and window ownership details | Replaced by adapter | Android `GLSurfaceView` lifecycle and JNI handle bridge replace desktop window loop. |
| `viewer` ImGui / ImGuizmo UI integration | Android not needed | UI panels are desktop-only and intentionally not exposed on Android. |
| `viewer` Lua / `sol` custom command execution surface | Android not needed | `ViewerCommand` type location is kept, but runtime command system is not a business feature on Android. |
| `viewer/Saba/GL/Model/OBJ/*` | Android not needed | This migration targets the MMD avatar renderer only. |
| `viewer/Saba/GL/Model/XFile/*` | Android not needed | This migration targets the MMD avatar renderer only. |
| `viewer/Saba/Viewer/resource/shader/bg.*`, `copy*.frag`, `quad.vert`, `obj_shader.*`, `xfile_shader.*` | Android not needed | Those shaders belong to the desktop viewer shell, non-MMD paths, or capture/background helpers not used by the Android avatar entry. |
| `viewer/Saba/Viewer/resource/font/*` | Android not needed | The Android shell does not embed ImGui UI. |
