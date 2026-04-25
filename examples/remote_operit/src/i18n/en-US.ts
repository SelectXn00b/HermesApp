import type { RemoteOperitSetupI18n } from "./types";

export const REMOTE_OPERIT_SETUP_EN_US: RemoteOperitSetupI18n = {
  title: "Remote Operit Setup",
  subtitle: "Let the current Operit call another Operit device through the LAN HTTP API.",
  topBanner:
    "Enable External HTTP Chat on the target Operit device first, then paste the LAN URL and Bearer token from that screen here.",
  configCardTitle: "Connection Config",
  configCardSubtitle:
    "After saving, this Operit can forward chat requests to another Operit device. A full http://IP:port URL is recommended.",
  baseUrlLabel: "Remote Operit Base URL",
  baseUrlPlaceholder: "For example http://192.168.1.23:8094",
  tokenLabel: "Bearer Token",
  tokenPlaceholder: "Paste the token shown on the target device",
  timeoutLabel: "Request Timeout (ms)",
  timeoutPlaceholder: "Default 60000",
  applyButton: "Save and Enable",
  applyAndTestButton: "Save and Test",
  applying: "Saving...",
  checking: "Checking...",
  connectionCardTitle: "Connection Status",
  connectionStateIdle: "Idle",
  connectionStateChecking: "Checking",
  connectionStateNotConfigured: "Not configured",
  connectionStateSuccess: "Connected",
  connectionStateFailed: "Failed",
  connectionFieldBaseUrl: "Base URL",
  connectionFieldPackageVersion: "Package Version",
  connectionFieldVersionName: "Remote Version",
  connectionFieldPort: "Port",
  connectionFieldEnabled: "HTTP Enabled",
  connectionFieldServiceRunning: "Service Running",
  connectionFieldError: "Error",
  errorBaseUrlRequired: "Please enter the remote Operit Base URL",
  errorTokenRequired: "Please enter the remote Operit Bearer token",
  importPackageFailed: "Failed to import remote_operit package",
  toolCallFailedPrefix: "Tool call failed: ",
  statusSaved: "Configuration saved and remote_operit is enabled.",
  statusErrorPrefix: "Operation failed: ",
  packageNotEnabled: "Package not imported or config incomplete. Save the connection config first.",
  exampleCardTitle: "How To Use",
  exampleCardSubtitle:
    "Once enabled, the current Operit can forward work to another device. The main entry is remote_operit_chat.",
  examplePromptTitle: "Recommended Use",
  examplePromptBody:
    "Use remote_operit_chat when the other device has files, apps, permissions, or context that this device does not.",
  exampleParamsTitle: "remote_operit_chat Example",
  exampleParamsBody:
    "{\n  \"message\": \"Inspect the latest logs in the download directory and summarize anomalies\",\n  \"group\": \"ops\",\n  \"create_new_chat\": false,\n  \"show_floating\": false,\n  \"stop_after\": true\n}\n\nIt forwards message / group / create_new_chat / chat_id / create_if_none / show_floating / auto_exit_after_ms / stop_after."
};
