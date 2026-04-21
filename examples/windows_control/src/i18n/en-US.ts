import type { WindowsSetupI18n } from "./types";

export const WINDOWS_SETUP_EN_US: WindowsSetupI18n = {
  title: "Windows Quick Setup",
  subtitle:
    "Use script-driven UI to verify agent connection, share installer package, and apply config.",
  step1Title: "Step 1: Share Installer",
  step1Desc: "Export and share Operit PC Agent ZIP to the target Windows device.",
  step1Button: "Export and Share ZIP",
  exporting: "Exporting...",
  shareTitle: "Operit PC Agent",
  step1SuccessPrefix: "Exported and opened share sheet: ",
  step1ShareFailed: "Share failed",
  step1MissingResource: "Installer resource not found",
  step2Title: "Step 2: Unzip and Start Agent on PC",
  step2Desc:
    "On Windows, unzip the exported ZIP. In the extracted file list, find operit_pc_agent.bat and double-click it.",
  step3Title: "Step 3: Finish Web Wizard and Paste Config Below",
  step3Desc:
    "Follow the auto-opened web setup wizard. On the final step, click Copy, then paste the copied config into the field below.",
  configLabel: "Paste Copied Config JSON",
  configPlaceholder:
    "{\"WINDOWS_AGENT_BASE_URL\":\"http://...\",\"WINDOWS_AGENT_TOKEN\":\"...\"}",
  envTip:
    "Paste the full config copied in the final web wizard step. Must include WINDOWS_AGENT_BASE_URL and WINDOWS_AGENT_TOKEN.",
  applyButton: "Paste and Apply",
  applying: "Applying...",
  recheckButton: "Check Connection Again",
  checking: "Checking...",
  successApply:
    "Config env saved, windows_control import/use attempted, now re-checking connection.",
  errorHostRequired: "Please input WINDOWS_AGENT_BASE_URL",
  errorTokenRequired: "Please input WINDOWS_AGENT_TOKEN",
  errorPasteEmpty: "Please paste config JSON first",
  errorPasteInvalidPrefix: "Config JSON parse failed: ",
  statusErrorPrefix: "Operation failed: ",
  connectionCardTitle: "Connection Overview",
  connectionStateIdle: "Idle",
  connectionStateChecking: "Checking",
  connectionStateNotConfigured: "Not configured",
  connectionStateSuccess: "Connected",
  connectionStateFailed: "Failed",
  connectionFieldBaseUrl: "Base URL",
  connectionFieldPackageVersion: "Package Version",
  connectionFieldAgentVersion: "Agent Version",
  connectionFieldDuration: "Duration",
  connectionFieldCommand: "Command",
  connectionFieldError: "Error",
  connectionFixBaseUrlLabel: "Fix Base URL",
  connectionFixBaseUrlPlaceholder: "e.g. 192.168.1.10 or http://192.168.1.10:58321",
  connectionFixApplyButton: "Apply and Retry",
  packageNotEnabled: "Package not imported or env incomplete. Complete step 3 first."
};
