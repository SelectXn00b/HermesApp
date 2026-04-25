import type { RemoteOperitSetupI18n } from "./types";

export const REMOTE_OPERIT_SETUP_ZH_CN: RemoteOperitSetupI18n = {
  title: "远程 Operit 配置",
  subtitle: "让当前 Operit 通过局域网 HTTP API 调用另一台设备上的 Operit。",
  topBanner:
    "先在目标设备的 Operit 设置里启用“外部 HTTP 调用”，再把该设备页面里显示的局域网地址和 Bearer Token 填到这里。",
  configCardTitle: "连接配置",
  configCardSubtitle:
    "保存后，当前 Operit 就能把聊天请求转发到另一台设备上的 Operit。推荐填写完整的 http://IP:端口。",
  baseUrlLabel: "远程 Operit Base URL",
  baseUrlPlaceholder: "例如 http://192.168.1.23:8094",
  tokenLabel: "Bearer Token",
  tokenPlaceholder: "填写目标设备页面显示的 Token",
  timeoutLabel: "请求超时（毫秒）",
  timeoutPlaceholder: "默认 60000",
  applyButton: "保存并启用",
  applyAndTestButton: "保存并检测连接",
  applying: "正在保存...",
  checking: "检测中...",
  connectionCardTitle: "连接状态",
  connectionStateIdle: "等待检测",
  connectionStateChecking: "正在检测",
  connectionStateNotConfigured: "尚未配置",
  connectionStateSuccess: "连接成功",
  connectionStateFailed: "连接失败",
  connectionFieldBaseUrl: "Base URL",
  connectionFieldPackageVersion: "包版本",
  connectionFieldVersionName: "远程版本",
  connectionFieldPort: "监听端口",
  connectionFieldEnabled: "HTTP 开关",
  connectionFieldServiceRunning: "服务状态",
  connectionFieldError: "错误",
  errorBaseUrlRequired: "请填写远程 Operit 的 Base URL",
  errorTokenRequired: "请填写远程 Operit 的 Bearer Token",
  importPackageFailed: "导入 remote_operit 包失败",
  toolCallFailedPrefix: "调用工具失败：",
  statusSaved: "配置已保存并启用 remote_operit。",
  statusErrorPrefix: "操作失败：",
  packageNotEnabled: "包未导入或配置不完整，请先保存连接配置。",
  exampleCardTitle: "使用方式",
  exampleCardSubtitle:
    "导入并启用后，当前 Operit 可以直接把任务转发到另一台设备。常用入口是 remote_operit_chat。",
  examplePromptTitle: "推荐场景",
  examplePromptBody:
    "当另一台设备拥有本机没有的文件、应用、权限或上下文时，让当前 Operit 调 remote_operit_chat 去协作。",
  exampleParamsTitle: "remote_operit_chat 参数示例",
  exampleParamsBody:
    "{\n  \"message\": \"请检查下载目录里最新的日志并总结异常\",\n  \"group\": \"ops\",\n  \"create_new_chat\": false,\n  \"show_floating\": false,\n  \"stop_after\": true\n}\n\n支持透传 message / group / create_new_chat / chat_id / create_if_none / show_floating / auto_exit_after_ms / stop_after。"
};
