import { LINUX_SSH_SETUP_EN_US } from "./en-US";
import { LINUX_SSH_SETUP_ZH_CN } from "./zh-CN";
import type { LinuxSshSetupI18n } from "./types";

const DEFAULT_LOCALE = "zh-CN";

const LINUX_SSH_SETUP_I18N_MAP: Record<string, LinuxSshSetupI18n> = {
  "zh-CN": LINUX_SSH_SETUP_ZH_CN,
  "en-US": LINUX_SSH_SETUP_EN_US
};

function normalizeLocale(locale?: string): string {
  const value = (locale || "").trim();
  if (!value) {
    return DEFAULT_LOCALE;
  }

  const lower = value.toLowerCase();
  if (lower.startsWith("zh")) {
    return "zh-CN";
  }
  if (lower.startsWith("en")) {
    return "en-US";
  }

  return value;
}

export function resolveLinuxSshSetupI18n(locale?: string): LinuxSshSetupI18n {
  const normalized = normalizeLocale(locale);
  return LINUX_SSH_SETUP_I18N_MAP[normalized] || LINUX_SSH_SETUP_I18N_MAP[DEFAULT_LOCALE];
}

export type { LinuxSshSetupI18n } from "./types";
