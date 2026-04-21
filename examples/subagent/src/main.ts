import {
  onToolResultXmlRender,
  onToolXmlRender,
} from "./plugin/subagent-xml-render-plugin";

export { onToolResultXmlRender, onToolXmlRender };

export function registerToolPkg(): boolean {
  ToolPkg.registerXmlRenderPlugin({
    id: "subagent_tool_render",
    tag: "tool",
    function: onToolXmlRender,
  });

  ToolPkg.registerXmlRenderPlugin({
    id: "subagent_tool_result_render",
    tag: "tool_result",
    function: onToolResultXmlRender,
  });

  return true;
}
