/* METADATA
{
  name: "vflow_trigger"
  display_name: {
    zh: "VFlow 触发器"
    en: "VFlow Trigger"
  }
  description: {
    zh: "触发vflow app的工作流。"
    en: "Trigger VFlow app workflows."
  }
  enabledByDefault: false
  category: "Workflow"
  tools: [
    {
      name: "trigger_vflow_workflow"
      description: {
        zh: "根据 workflow_id 触发 VFlow 工作流（需安装 com.chaomixian.vflow）。"
        en: "Trigger a VFlow workflow by workflow_id (requires com.chaomixian.vflow installed)."
      }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const VFlowTrigger = (function () {
    const DEFAULT_ACTION = "com.chaomixian.vflow.EXECUTE_WORKFLOW_SHORTCUT";
    const DEFAULT_COMPONENT = "com.chaomixian.vflow/.ui.common.ShortcutExecutorActivity";
    async function trigger_vflow_workflow(params) {
        if (!params || !params.workflow_id) {
            return { success: false, message: "workflow_id 不能为空" };
        }
        const result = await Tools.System.intent({
            type: "activity",
            action: DEFAULT_ACTION,
            component: DEFAULT_COMPONENT,
            extras: {
                workflow_id: params.workflow_id
            }
        });
        return {
            success: true,
            message: "已触发 VFlow 工作流",
            data: result
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({ success: false, message: String(error && error.message ? error.message : error) });
        }
    }
    return {
        trigger_vflow_workflow: (params) => wrapToolExecution(trigger_vflow_workflow, params)
    };
})();
exports.trigger_vflow_workflow = VFlowTrigger.trigger_vflow_workflow;
