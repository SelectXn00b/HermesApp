import { DEEPSEARCH_EN_US } from "./en-US";
import { DEEPSEARCH_ZH_CN } from "./zh-CN";
import type { DeepSearchI18n } from "./types";

const DEFAULT_LOCALE = "zh-CN";

const DEEPSEARCH_I18N_MAP: Record<string, DeepSearchI18n> = {
  "zh-CN": DEEPSEARCH_ZH_CN,
  "en-US": DEEPSEARCH_EN_US
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

export function resolveDeepSearchI18n(locale?: string): DeepSearchI18n {
  const normalized = normalizeLocale(locale);
  return DEEPSEARCH_I18N_MAP[normalized] || DEEPSEARCH_I18N_MAP[DEFAULT_LOCALE];
}

export type { DeepSearchI18n } from "./types";
