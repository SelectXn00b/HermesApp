import { REMOTE_OPERIT_SETUP_EN_US } from "./en-US";
import { REMOTE_OPERIT_SETUP_ZH_CN } from "./zh-CN";
import type { RemoteOperitSetupI18n } from "./types";

const DEFAULT_LOCALE = "zh-CN";

const REMOTE_OPERIT_SETUP_I18N_MAP: Record<string, RemoteOperitSetupI18n> = {
  "zh-CN": REMOTE_OPERIT_SETUP_ZH_CN,
  "en-US": REMOTE_OPERIT_SETUP_EN_US
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

export function resolveRemoteOperitSetupI18n(locale?: string): RemoteOperitSetupI18n {
  const normalized = normalizeLocale(locale);
  return REMOTE_OPERIT_SETUP_I18N_MAP[normalized] || REMOTE_OPERIT_SETUP_I18N_MAP[DEFAULT_LOCALE];
}

export type { RemoteOperitSetupI18n } from "./types";
