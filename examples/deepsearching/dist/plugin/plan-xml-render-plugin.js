"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.supportsPlanTag = supportsPlanTag;
exports.renderPlanXml = renderPlanXml;
const plan_execution_ui_js_1 = __importDefault(require("../ui/plan-execution.ui.js"));
function supportsPlanTag(tagName) {
    return String(tagName || "").toLowerCase() === "plan";
}
function renderPlanXml(xmlContent, tagName) {
    const normalizedTagName = String(tagName || "plan").toLowerCase();
    const normalizedXmlContent = String(xmlContent || "");
    const matched = supportsPlanTag(normalizedTagName);
    console.log("deepsearching renderPlanXml enter", JSON.stringify({
        tagName: normalizedTagName,
        matched,
        xmlLength: normalizedXmlContent.length,
        preview: normalizedXmlContent.slice(0, 120)
    }));
    if (!matched) {
        console.log("deepsearching renderPlanXml skip", JSON.stringify({ tagName: normalizedTagName, reason: "unsupported_tag" }));
        return { handled: false };
    }
    const emptyState = {};
    const result = {
        handled: true,
        composeDsl: {
            screen: plan_execution_ui_js_1.default,
            state: { ...emptyState, xmlContent: normalizedXmlContent },
            memo: emptyState
        }
    };
    console.log("deepsearching renderPlanXml result", JSON.stringify({
        tagName: normalizedTagName,
        handled: true,
        stateKeys: Object.keys(result.composeDsl?.state || {})
    }));
    return result;
}
