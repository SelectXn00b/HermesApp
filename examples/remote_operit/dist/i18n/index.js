"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveRemoteOperitSetupI18n = resolveRemoteOperitSetupI18n;
const en_US_1 = require("./en-US");
const zh_CN_1 = require("./zh-CN");
const DEFAULT_LOCALE = "zh-CN";
const REMOTE_OPERIT_SETUP_I18N_MAP = {
    "zh-CN": zh_CN_1.REMOTE_OPERIT_SETUP_ZH_CN,
    "en-US": en_US_1.REMOTE_OPERIT_SETUP_EN_US
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
function resolveRemoteOperitSetupI18n(locale) {
    const normalized = normalizeLocale(locale);
    return REMOTE_OPERIT_SETUP_I18N_MAP[normalized] || REMOTE_OPERIT_SETUP_I18N_MAP[DEFAULT_LOCALE];
}
