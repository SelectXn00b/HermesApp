import { WINDOWS_SETUP_EN_US } from "./en-US";
import { WINDOWS_SETUP_ZH_CN } from "./zh-CN";
import type { WindowsSetupI18n } from "./types";

const DEFAULT_LOCALE = "zh-CN";

const WINDOWS_SETUP_I18N_MAP: Record<string, WindowsSetupI18n> = {
  "zh-CN": WINDOWS_SETUP_ZH_CN,
  "en-US": WINDOWS_SETUP_EN_US
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

export function resolveWindowsSetupI18n(locale?: string): WindowsSetupI18n {
  const normalized = normalizeLocale(locale);
  return WINDOWS_SETUP_I18N_MAP[normalized] || WINDOWS_SETUP_I18N_MAP[DEFAULT_LOCALE];
}

export type { WindowsSetupI18n } from "./types";
