"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveWindowsSetupI18n = resolveWindowsSetupI18n;
const en_US_1 = require("./en-US");
const zh_CN_1 = require("./zh-CN");
const DEFAULT_LOCALE = "zh-CN";
const WINDOWS_SETUP_I18N_MAP = {
    "zh-CN": zh_CN_1.WINDOWS_SETUP_ZH_CN,
    "en-US": en_US_1.WINDOWS_SETUP_EN_US
};
function normalizeLocale(locale) {
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
function resolveWindowsSetupI18n(locale) {
    const normalized = normalizeLocale(locale);
    return WINDOWS_SETUP_I18N_MAP[normalized] || WINDOWS_SETUP_I18N_MAP[DEFAULT_LOCALE];
}
