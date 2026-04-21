import PlanExecutionRenderer from "../ui/plan-execution.ui.js";

export function supportsPlanTag(tagName: string): boolean {
  return String(tagName || "").toLowerCase() === "plan";
}

export function renderPlanXml(
  xmlContent: string,
  tagName?: string
): ToolPkg.XmlRenderHookObjectResult {
  const normalizedTagName = String(tagName || "plan").toLowerCase();
  const normalizedXmlContent = String(xmlContent || "");
  const matched = supportsPlanTag(normalizedTagName);
  console.log(
    "deepsearching renderPlanXml enter",
    JSON.stringify({
      tagName: normalizedTagName,
      matched,
      xmlLength: normalizedXmlContent.length,
      preview: normalizedXmlContent.slice(0, 120)
    })
  );
  if (!matched) {
    console.log(
      "deepsearching renderPlanXml skip",
      JSON.stringify({ tagName: normalizedTagName, reason: "unsupported_tag" })
    );
    return { handled: false };
  }
  const emptyState: ToolPkg.JsonObject = {};
  const result: ToolPkg.XmlRenderHookObjectResult = {
    handled: true,
    composeDsl: {
      screen: PlanExecutionRenderer,
      state: { ...emptyState, xmlContent: normalizedXmlContent },
      memo: emptyState
    }
  };
  console.log(
    "deepsearching renderPlanXml result",
    JSON.stringify({
      tagName: normalizedTagName,
      handled: true,
      stateKeys: Object.keys(result.composeDsl?.state || {})
    })
  );
  return result;
}
