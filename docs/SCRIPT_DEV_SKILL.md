---
name: SandboxPackage_DEV
description: 用于 墨思 Sandbox Package 开发。
---

# SandboxPackage_DEV

## 第一部分：安装与更新

这个 skill 不再要求 AI 一次性手动下载 `SKILL.md`、两份 guide 文档和整套 `types`。

安装与更新都走同一个脚本：

- 先把安装脚本下载到本地
- 再通过 `operit_editor` 这个 package 里的 `debug_run_sandbox_script` 工具运行它
- 脚本会自动创建目录，并更新 `SKILL.md`、`references/SCRIPT_DEV_GUIDE.md`、`references/TOOLPKG_FORMAT_GUIDE.md`、`types/*.d.ts`

这里说的“执行”，指的是：

- 不是用 shell 执行
- 不是直接打开这个 `.js` 文件
- 而是调用 `operit_editor:debug_run_sandbox_script`

也就是先确保 `operit_editor` 这个 package 可用，再让它执行 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`

最终目录应当长这样：

```text
/sdcard/Download/Operit/skills/SandboxPackage_DEV/
  SKILL.md
  references/
    SCRIPT_DEV_GUIDE.md
    TOOLPKG_FORMAT_GUIDE.md
  types/
    index.d.ts
    core.d.ts
    ...
  scripts/
    install_or_update.js
```

首次安装时，按下面顺序做：

1. 先创建 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/`
2. 用 `download_file` 下载 `https://cdn.jsdelivr.net/gh/AAswordman/Operit@main/tools/sandboxpackage_dev_install_or_update.js`
3. 保存为 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`
4. 调用 `operit_editor` 的 `debug_run_sandbox_script`
5. 把 `source_path` 设为 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`
6. 等脚本执行完成

如果当前环境里没有直接暴露这个工具名，就先使用 `use_package` 调用 `operit_editor`，再执行 `debug_run_sandbox_script`。

这个安装脚本会自动处理下面这些内容：

- 创建 `SandboxPackage_DEV` 目录
- 下载并更新 `SKILL.md`
- 下载并更新 `references/SCRIPT_DEV_GUIDE.md`
- 下载并更新 `references/TOOLPKG_FORMAT_GUIDE.md`
- 下载并更新 `types/` 下全部类型文件

更新时按下面规则处理：

1. 每次正式开始新的 Sandbox Package 开发任务前，优先重新下载一次安装脚本并重新运行
2. 如果怀疑两份 guide 文档、types 或 `SKILL.md` 已经过旧，也重新运行这个脚本
3. 如果本地 skill 目录缺文件、文件名不对、或者内容明显陈旧，不要手动零散修补，直接重跑安装脚本

下载完以后，查资料时默认这样做：

1. 先用 `grep_code` 在 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/` 里搜关键字
2. 再用 `read_file_part` 读取命中的具体片段
3. 只有片段不够时才扩大范围

不要默认直接读取整个 `SCRIPT_DEV_GUIDE.md`、整个 `TOOLPKG_FORMAT_GUIDE.md` 或整个 `types` 文件，原因是：

- 它们内容比较大，容易把上下文撑爆
- 先更新本地 skill，再检索的方式更稳
- 本地 skill 可以长期复用，但 `types` 最容易过时，所以需要高频重跑安装脚本

## 第二部分：Sandbox Package 撰写

撰写 Sandbox Package 时，不要凭记忆硬写，先查本地 skill 资料。

### 方案选择优先级

默认优先考虑撰写普通 JS 包脚本，不要一上来就选择 `ToolPkg`。

按下面规则判断：

1. 如果目标只是新增或修改工具函数、参数、返回结构、环境变量声明、普通资源访问，优先使用普通 JS 包脚本
2. 如果只是为了“以后可能扩展”或者“看起来更完整”，不要提前升级成 `ToolPkg`
3. 只有当需求明确涉及配置界面、工具箱页面、宿主级注册能力，或者各种软件 hook / plugin / lifecycle / prompt hook / message processing 时，才考虑 `ToolPkg`
4. 如果只是少量配置项，优先考虑参数或 `env`，不要默认为了配置而增加 UI
5. 如果需求边界还不清晰，先按普通 JS 包脚本设计；只有确认普通脚本承载不了时，再升级到 `ToolPkg`

可以把 `ToolPkg` 理解成一种更重的插件格式。它适合下面这些场景：

- 需要新增配置界面或工具箱 UI
- 需要注册 `registerToolPkg` 相关入口
- 需要 app lifecycle hook、message processing plugin、xml render plugin、input menu toggle、prompt hook 等宿主级扩展
- 需要围绕 `manifest`、多模块目录、资源打包、安装刷新流程来组织完整插件

如果不满足这些条件，就优先写普通 JS 包脚本。

推荐的查阅顺序：

1. 先查 `types/index.d.ts`，确认全局入口和主要能力
2. 再查 `types/core.d.ts`、`types/java-bridge.d.ts`，确认运行时与桥接接口
3. 查 `types/results.d.ts`，确认常见返回结构
4. 如果涉及设置类能力，再查 `types/software_settings.d.ts`
5. 只有方案已经确定为 `ToolPkg` 时，再查 `types/toolpkg.d.ts`
6. 需要普通脚本格式、元数据、示例写法时，再查 `references/SCRIPT_DEV_GUIDE.md`
7. 需要 `ToolPkg` 的 `manifest`、目录结构、资源、UI 模块、注册函数与调试安装流程时，再查 `references/TOOLPKG_FORMAT_GUIDE.md`

推荐的撰写流程：

1. 先默认按普通 `.js` Sandbox Package 思路设计，确认是不是仅靠普通包脚本就能完成
2. 如果需求明确涉及配置界面或软件 hook，再切换到 `ToolPkg` 方案
3. 如果是普通 JS Sandbox Package，先用 `grep_code` 在 `SCRIPT_DEV_GUIDE.md` 里搜索 `METADATA`、`tool`、`execute`、`package` 等关键字
4. 如果是 `ToolPkg`，用 `grep_code` 在 `TOOLPKG_FORMAT_GUIDE.md` 里搜索 `manifest`、`subpackage`、`registerToolPkg`、`resource`、`ui`、`debug_toolpkg`、`hook` 等关键字
5. 用 `read_file_part` 读取相关段落，确认脚本结构、元数据、manifest 和注册写法
6. 用 `types/` 里的定义约束参数、返回值、可调用能力和结果结构
7. 开始写包时，优先遵循最新本地 types 和本地 guide，不要依赖旧记忆

如果写到一半发现本地类型和实际需求对不上，先不要硬猜，先重新运行安装脚本，再继续写。
