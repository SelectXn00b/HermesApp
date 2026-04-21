"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onToolXmlRender = exports.onToolResultXmlRender = void 0;
exports.registerToolPkg = registerToolPkg;
const subagent_xml_render_plugin_1 = require("./plugin/subagent-xml-render-plugin");
Object.defineProperty(exports, "onToolResultXmlRender", { enumerable: true, get: function () { return subagent_xml_render_plugin_1.onToolResultXmlRender; } });
Object.defineProperty(exports, "onToolXmlRender", { enumerable: true, get: function () { return subagent_xml_render_plugin_1.onToolXmlRender; } });
function registerToolPkg() {
    ToolPkg.registerXmlRenderPlugin({
        id: "subagent_tool_render",
        tag: "tool",
        function: subagent_xml_render_plugin_1.onToolXmlRender,
    });
    ToolPkg.registerXmlRenderPlugin({
        id: "subagent_tool_result_render",
        tag: "tool_result",
        function: subagent_xml_render_plugin_1.onToolResultXmlRender,
    });
    return true;
}
