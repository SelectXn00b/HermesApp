package com.ai.assistance.operit.data.mcp.plugins

import org.json.JSONObject

/** 项目类型枚举 */
enum class ProjectType {
    PYTHON,
    NODEJS,
    TYPESCRIPT,
    UNKNOWN
}

/** 项目结构信息数据类 */
data class ProjectStructure(
        val type: ProjectType,
        val hasRequirementsTxt: Boolean = false,
        val hasPyprojectToml: Boolean = false,
        val hasSetupPy: Boolean = false,
        val hasPackageJson: Boolean = false,
        val hasTsConfig: Boolean = false,
        val mainPythonModule: String? = null,
        val mainJsFile: String? = null,
        val mainTsFile: String? = null,
        val hasTsFiles: Boolean = false,
        val configExample: String? = null,
        val moduleNameFromConfig: String? = null,
        val hasTypeScriptDependency: Boolean = false,
        val packageJsonScripts: JSONObject? = null,
        val packageJsonContent: String? = null,
        val tsConfigOutDir: String? = null,
        val tsConfigRootDir: String? = null,
        val tsConfigContent: String? = null,
        val pythonPackageName: String? = null  // 从 pyproject.toml 解析的包名
)
