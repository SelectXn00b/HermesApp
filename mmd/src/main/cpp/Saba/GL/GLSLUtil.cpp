#include "GLSLUtil.h"

#include "GLShaderUtil.h"
#include "android/AndroidAssetSupport.h"

#include <fstream>
#include <sstream>

namespace saba {
namespace {

bool ReadTextFile(const std::string& path, std::string* outText) {
    if (outText == nullptr) {
        return false;
    }

    std::ifstream stream(path, std::ios::binary);
    if (!stream.is_open()) {
        return false;
    }

    std::ostringstream buffer;
    buffer << stream.rdbuf();
    *outText = buffer.str();
    return true;
}

bool ReadShaderText(const std::string& path, std::string* outText) {
    if (operit::androidbridge::ReadTextAsset(path, outText)) {
        return true;
    }
    return ReadTextFile(path, outText);
}

std::string ApplyDefines(const std::string& source, const GLSLDefine& define) {
    if (define.GetMap().empty()) {
        return source;
    }

    std::ostringstream prefix;
    for (const auto& [key, value] : define.GetMap()) {
        prefix << "#define " << key;
        if (!value.empty()) {
            prefix << ' ' << value;
        }
        prefix << '\n';
    }

    const auto firstLineEnd = source.find('\n');
    if (firstLineEnd == std::string::npos) {
        return prefix.str() + source;
    }

    if (source.rfind("#version", 0) == 0) {
        return source.substr(0, firstLineEnd + 1) + prefix.str() + source.substr(firstLineEnd + 1);
    }

    return prefix.str() + source;
}

}  // namespace

void GLSLDefine::Define(const std::string& def, const std::string& defValue) {
    m_defines[def] = defValue;
}

void GLSLDefine::Undefine(const std::string& def) {
    m_defines.erase(def);
}

void GLSLDefine::Clear() {
    m_defines.clear();
}

GLSLInclude::GLSLInclude(const std::string& workDir)
    : m_workDir(workDir) {}

void GLSLInclude::AddInclude(const std::string& include) {
    m_pathList.push_back(include);
}

void GLSLInclude::Clear() {
    m_pathList.clear();
}

bool InitializeGLSLUtil() {
    return true;
}

void UninitializeGLSLUtil() {
}

bool PreprocessGLSL(
    std::string* outCode,
    GLSLShaderLang,
    const std::string& inCode,
    const GLSLDefine& define,
    const GLSLInclude&,
    std::string* outMessage
) {
    if (outCode == nullptr) {
        return false;
    }

    *outCode = ApplyDefines(inCode, define);
    if (outMessage != nullptr) {
        outMessage->clear();
    }
    return true;
}

void GLSLShaderUtil::SetShaderDir(const std::string& shaderDir) {
    m_shaderDir = shaderDir;
}

void GLSLShaderUtil::SetGLSLDefine(const GLSLDefine& define) {
    m_define = define;
}

void GLSLShaderUtil::SetGLSLInclude(const GLSLInclude& include) {
    m_include = include;
}

GLProgramObject GLSLShaderUtil::CreateProgram(const char* shaderName) {
    if (shaderName == nullptr) {
        return GLProgramObject();
    }

    std::string vsSource;
    std::string fsSource;
    const std::string basePath = m_shaderDir.empty() ? "" : (m_shaderDir + "/");
    if (!ReadShaderText(basePath + shaderName + std::string(".vert"), &vsSource) ||
        !ReadShaderText(basePath + shaderName + std::string(".frag"), &fsSource)) {
        return GLProgramObject();
    }

    std::string processedVs;
    std::string processedFs;
    if (!PreprocessGLSL(&processedVs, GLSLShaderLang::Vertex, vsSource, m_define, m_include, nullptr) ||
        !PreprocessGLSL(&processedFs, GLSLShaderLang::Fragment, fsSource, m_define, m_include, nullptr)) {
        return GLProgramObject();
    }

    return CreateShaderProgram(processedVs.c_str(), processedFs.c_str());
}

GLProgramObject GLSLShaderUtil::CreateProgram(const char* vsCode, const char* fsCode) {
    if (vsCode == nullptr || fsCode == nullptr) {
        return GLProgramObject();
    }

    std::string processedVs;
    std::string processedFs;
    if (!PreprocessGLSL(&processedVs, GLSLShaderLang::Vertex, vsCode, m_define, m_include, nullptr) ||
        !PreprocessGLSL(&processedFs, GLSLShaderLang::Fragment, fsCode, m_define, m_include, nullptr)) {
        return GLProgramObject();
    }

    return CreateShaderProgram(processedVs.c_str(), processedFs.c_str());
}

}  // namespace saba
