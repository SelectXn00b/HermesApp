/* METADATA
{
  name: "workflow"

  display_name: {
    zh: "工作流管理"
    en: "Workflow Management"
  }
  description: {
    zh: '''工作流管理工具：创建/查询/更新/启用/禁用/删除/触发执行；支持 on_success/on_error 分支；支持语音触发（speech）。'''
    en: '''Workflow management tools for creating/querying/updating/enabling/disabling/deleting workflows, triggering execution, and branching via on_success/on_error. Supports speech trigger (speech).'''
  }
  "category": "Workflow",
  enabledByDefault: true

  tools: [
    {
      name: "usage_advice"
      description: {
        zh: '''
工作流工具使用建议（给 AI）：

- 核心概念（请优先对齐这些语义）：
  - 节点类型：trigger/execute/condition/logic/extract
  - 触发节点类型：manual/schedule/tasker/intent/speech
  - 参数引用（ParameterValue）：静态值 vs 引用其他节点输出
  - 分支连线 condition 的语义：on_success/on_error/true/false/regex

- 重要：create_workflow/update_workflow 的 nodes/connections 参数底层类型是 string（JSON 数组字符串）。
  - 本示例封装允许你直接传对象数组，封装层会自动 JSON.stringify。

- 推荐流程：
  1) 先 get_all_workflows 找到候选 workflow_id。
  2) 再 get_workflow 获取 nodes/connections 全量结构。
-  3) 如果你要“整体替换” nodes/connections：构造完整的新数组后用 update_workflow 一次性覆盖。
-  4) 如果你只想“增量修改” nodes/connections：优先使用 patch_workflow（node_patches/connection_patches）。

- 节点与连线的 ID：
  - 节点 id 可省略（服务端会生成），但如果你要创建 connections，强烈建议你在 nodes 里显式写好 id。
  - connections 里 source/target 可以用：
    - sourceNodeId/targetNodeId（推荐）
    - 或 source/target/from/to
    - 或 sourceIndex/targetIndex（按 nodes 数组下标）
    - 或 sourceNodeName/targetNodeName（不推荐：同名会歧义）

- 分支连线 condition（核心）：
  - 通用关键字（适用于任何节点类型）：
    - condition = "on_success" | "success" | "ok"：源节点成功时触发
    - condition = "on_error" | "error" | "failed"：源节点失败时触发（失败分支/补救逻辑）
  - 对 ConditionNode / LogicNode：
    - condition 为空：默认代表 true 分支（相当于 "true"）
    - condition = "false"：false 分支
    - condition = 其它字符串：当作 Regex 匹配源节点输出字符串
  - 对非 Condition/Logic 节点：
    - condition 为空：等价于 on_success（表示“源节点执行成功就走”）
    - condition = on_error：表示“源节点失败就走”（失败分支）

- 参数引用（ParameterValue）：
  - 静态值：直接写字符串/数字/布尔值即可（会被当作 StaticValue）
  - 引用某节点输出：写对象 { nodeId: "<node-id>" }
    - 兼容字段：nodeId / ref / refNodeId

- 触发节点类型（TriggerNode.triggerType）：
  - manual：手动触发（UI 点“触发工作流”）
  - schedule：定时触发（由 WorkManager 调度）
  - tasker：Tasker 事件触发
  - intent：系统广播 Intent 触发
  - speech：语音识别事件触发（当识别文本命中正则时触发；可多工作流同时触发）

  触发配置 TriggerNode.triggerConfig（注意：值全是 string）：
  - schedule：
    - schedule_type: interval | specific_time | cron
    - interval_ms: "900000"  (15分钟)
    - specific_time: "2026-01-04 10:30"  (格式依实现)
    - cron_expression: "15 * * * *"  (简化 cron)
    - repeat: "true"/"false"
    - enabled: "true"/"false"
  - tasker：
    - command: "start_meeting"  (当 Tasker params 中包含该字符串则触发)
  - intent：
    - action: "com.example.MY_ACTION"  (当收到该 action 的 Intent 则触发)
  - speech：
    - pattern: ".*(打开|启动).*(对话|聊天|悬浮窗).*"  (正则；匹配识别文本)
    - ignore_case: "true"/"false"  (可选，默认 true)
    - require_final: "true"/"false"  (可选，默认 true；true 表示仅 final 结果触发)
    - cooldown_ms: "3000"  (可选，默认 3000；每个节点的触发冷却)
'''
        en: '''
Workflow tool usage advice (for the AI):

- Core concepts (align your reasoning with these semantics):
  - Node types: trigger/execute/condition/logic/extract
  - Trigger node types: manual/schedule/tasker/intent/speech
  - Parameter references (ParameterValue): static values vs references to another node output
  - Connection "condition" meaning: on_success/on_error/true/false/regex

- Important: in create_workflow/update_workflow, nodes/connections are strings (JSON array strings) at the API layer.
  - This example wrapper lets you pass object arrays directly; it will JSON.stringify automatically.

- Recommended flow:
  1) Call get_all_workflows to find the candidate workflow_id.
  2) Call get_workflow to retrieve the full nodes/connections structure.
-  3) If you want to replace nodes/connections entirely: build the full new arrays and use update_workflow once to overwrite.
-  4) If you only want incremental changes: prefer patch_workflow (node_patches/connection_patches).

- Node and connection IDs:
  - Node id can be omitted (server will generate it), but if you need to create connections, strongly recommend explicitly setting node ids in nodes.
  - In connections, source/target can be provided as:
    - sourceNodeId/targetNodeId (recommended)
    - or source/target/from/to
    - or sourceIndex/targetIndex (index within nodes array)
    - or sourceNodeName/targetNodeName (not recommended: duplicates are ambiguous)

- Connection condition (core):
  - Global keywords (works for any node type):
    - condition = "on_success" | "success" | "ok": trigger when the source node succeeds
    - condition = "on_error" | "error" | "failed": trigger when the source node fails (error branch / recovery)
  - For ConditionNode / LogicNode:
    - empty condition: defaults to true branch (equivalent to "true")
    - condition = "false": false branch
    - other string: treated as a Regex to match the source node output string
  - For non-Condition/Logic nodes:
    - empty condition: equivalent to on_success (proceed if the source node succeeded)
    - condition = on_error: proceed if the source node failed (error branch)

- Parameter references (ParameterValue):
  - Static value: write a string/number/boolean directly (treated as StaticValue)
  - Reference another node output: write an object { nodeId: "<node-id>" }
    - compatible fields: nodeId / ref / refNodeId

- Trigger node types (TriggerNode.triggerType):
  - manual: manual trigger (tap "trigger workflow" in UI)
  - schedule: scheduled trigger (WorkManager)
  - tasker: triggered by Tasker events
  - intent: triggered by Android broadcast intents
  - speech: triggered by speech recognition events (fires when recognized text matches a regex; multiple workflows can match)

  Trigger configuration TriggerNode.triggerConfig (note: all values are strings):
  - schedule:
    - schedule_type: interval | specific_time | cron
    - interval_ms: "900000" (15 minutes)
    - specific_time: "2026-01-04 10:30" (format depends on implementation)
    - cron_expression: "15 * * * *" (simplified cron)
    - repeat: "true"/"false"
    - enabled: "true"/"false"
  - tasker:
    - command: "start_meeting" (triggered when Tasker params contains this string)
  - intent:
    - action: "com.example.MY_ACTION" (triggered when receiving this action)
  - speech:
    - pattern: ".*(open|start).*(chat|floating).*" (regex; matches recognized text)
    - ignore_case: "true"/"false" (optional, default true)
    - require_final: "true"/"false" (optional, default true; if true, only final results trigger)
    - cooldown_ms: "3000" (optional, default 3000; per-node cooldown)
'''
      }
      parameters: []
    }

    {
      name: "get_all_workflows"
      description: { zh: "获取所有工作流列表（只含概要信息：ID/名称/启用/统计等）。", en: "List all workflows (summary only: id/name/enabled/stats, etc.)." }
      parameters: []
    }

    {
      name: "get_workflow"
      description: { zh: "获取指定工作流完整详情（nodes + connections）。", en: "Get full details of a specific workflow (nodes + connections)." }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }

    {
      name: "create_workflow"
      description: {
        zh: '''
创建工作流。

参数说明：
- nodes: JSON 数组字符串（推荐传对象数组，让封装自动 stringify）
- connections: JSON 数组字符串（同上）

节点类型（node.type）：
- trigger / execute / condition / logic / extract

Execute 节点：
- actionType: 工具名（如 "visit_web" / "list_files" / "get_system_setting" ...）
- actionConfig: 工具参数对象，支持 ParameterValue（静态值/节点引用）

Condition 节点：
- left/right: ParameterValue
- operator: EQ/NE/GT/GTE/LT/LTE/CONTAINS/NOT_CONTAINS/IN/NOT_IN

Logic 节点：
- operator: AND/OR

Extract（运算）节点：
- 说明：node.type 仍然是 "extract"（兼容旧数据），但语义更接近“运算器/计算节点”。
- source: ParameterValue（部分模式不需要，例如 RANDOM_INT）
- mode:
  - REGEX：正则提取
    - expression: 正则表达式
    - group/defaultValue 可选
  - JSON：JSON 路径提取
    - expression: JSON 路径（简化实现）
    - defaultValue 可选
  - SUB：字符串截取
    - startIndex: 起始下标
    - length: 长度（-1 表示到结尾）
    - defaultValue 可选
  - CONCAT：字符串拼接
    - others: ParameterValue[]（被拼接项列表）
  - RANDOM_INT：随机整数
    - randomMin/randomMax
    - useFixed: 是否使用固定值（可选）
    - fixedValue: 固定整数（可选，仅 useFixed=true 时生效；输出仍为数字字符串）
  - RANDOM_STRING：随机字符串
    - randomStringLength: 长度
    - randomStringCharset: 字符集（可选，默认字母数字）
    - useFixed: 是否使用固定值（可选）
    - fixedValue: 固定字符串（可选，仅 useFixed=true 时生效）
'''
        en: '''
Create a workflow.

Parameter notes:
- nodes: JSON array string (recommended: pass object arrays and let the wrapper stringify)
- connections: JSON array string (same as above)

Node types (node.type):
- trigger / execute / condition / logic / extract

Execute node:
- actionType: tool name (e.g. "visit_web" / "list_files" / "get_system_setting" ...)
- actionConfig: tool parameter object, supports ParameterValue (static value / node reference)

Condition node:
- left/right: ParameterValue
- operator: EQ/NE/GT/GTE/LT/LTE/CONTAINS/NOT_CONTAINS/IN/NOT_IN

Logic node:
- operator: AND/OR

Extract (operator) node:
- Note: node.type is still "extract" for backward compatibility, but it behaves like an "operator" node.
- source: ParameterValue (not required for some modes like RANDOM_INT)
- mode:
  - REGEX: regex extraction
    - expression: regex pattern
    - group/defaultValue are optional
  - JSON: JSON path extraction
    - expression: JSON path (simplified)
    - defaultValue optional
  - SUB: substring
    - startIndex
    - length (-1 means to end)
    - defaultValue optional
  - CONCAT: string concatenation
    - others: ParameterValue[]
  - RANDOM_INT: random integer
    - randomMin/randomMax
    - useFixed: whether to use a fixed value (optional)
    - fixedValue: fixed integer (optional, only effective when useFixed=true; output is still a numeric string)
  - RANDOM_STRING: random string
    - randomStringLength
    - randomStringCharset (optional, default: alphanumeric)
    - useFixed: whether to use a fixed value (optional)
    - fixedValue: fixed string (optional, only effective when useFixed=true)
'''
      }
      parameters: [
        { name: "name", description: { zh: "工作流名称", en: "Workflow name" }, type: "string", required: true }
        { name: "description", description: { zh: "工作流描述（可选）", en: "Workflow description (optional)" }, type: "string", required: false }
        { name: "nodes", description: { zh: "可选，节点 JSON 数组字符串（或直接传节点数组，由封装 stringify）", en: "Optional. Nodes JSON array string (or pass an array and the wrapper will stringify)." }, type: "string", required: false }
        { name: "connections", description: { zh: "可选，连线 JSON 数组字符串（或直接传连线数组，由封装 stringify）", en: "Optional. Connections JSON array string (or pass an array and the wrapper will stringify)." }, type: "string", required: false }
        { name: "enabled", description: { zh: "可选，是否启用（默认 true）", en: "Optional. Whether to enable (default: true)." }, type: "boolean", required: false }
      ]
    }

    {
      name: "update_workflow"
      description: {
        zh: '''
更新工作流。

注意：update_workflow 的 nodes / connections 是“整体覆盖”。
- 若你只改其中一部分，推荐使用 patch_workflow。
- 或者：get_workflow 取回旧结构 -> 本地构造新数组（保留未改部分）-> update_workflow 一次性传回。
'''
        en: '''
Update a workflow.

Note: nodes/connections in update_workflow are full overwrites.
- If you only change part of them, prefer patch_workflow.
- Or: call get_workflow to fetch the old structure -> build new arrays locally (keeping unchanged parts) -> call update_workflow once.
'''
      }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
        { name: "name", description: { zh: "可选，新名称", en: "Optional. New name." }, type: "string", required: false }
        { name: "description", description: { zh: "可选，新描述", en: "Optional. New description." }, type: "string", required: false }
        { name: "nodes", description: { zh: "可选，节点 JSON 数组字符串（整体覆盖）", en: "Optional. Nodes JSON array string (full overwrite)." }, type: "string", required: false }
        { name: "connections", description: { zh: "可选，连线 JSON 数组字符串（整体覆盖）", en: "Optional. Connections JSON array string (full overwrite)." }, type: "string", required: false }
        { name: "enabled", description: { zh: "可选，是否启用", en: "Optional. Whether to enable." }, type: "boolean", required: false }
      ]
    }

    {
      name: "patch_workflow"
      description: {
        zh: '''
差异更新工作流（增量 patch）。

使用 node_patches / connection_patches 传入 JSON 数组字符串：
- op: add | update | remove
- id: 可选
- node / connection: 对象

说明：
- add：必须提供 node/connection
- update：必须提供 id 或 node.id/connection.id
- remove：必须提供 id
'''
        en: '''
Patch a workflow (incremental update).

Use node_patches / connection_patches as JSON array strings:
- op: add | update | remove
- id: optional
- node / connection: object

Notes:
- add: must provide node/connection
- update: must provide id OR node.id/connection.id
- remove: must provide id
'''
      }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
        { name: "name", description: { zh: "可选，新名称", en: "Optional. New name." }, type: "string", required: false }
        { name: "description", description: { zh: "可选，新描述", en: "Optional. New description." }, type: "string", required: false }
        { name: "enabled", description: { zh: "可选，是否启用", en: "Optional. Whether to enable." }, type: "boolean", required: false }
        { name: "node_patches", description: { zh: "可选，节点 patch JSON 数组字符串", en: "Optional. Node patch JSON array string." }, type: "string", required: false }
        { name: "connection_patches", description: { zh: "可选，连线 patch JSON 数组字符串", en: "Optional. Connection patch JSON array string." }, type: "string", required: false }
      ]
    }

    {
      name: "enable_workflow"
      description: { zh: "启用指定工作流。", en: "Enable a specific workflow." }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }

    {
      name: "disable_workflow"
      description: { zh: "禁用指定工作流。", en: "Disable a specific workflow." }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }

    {
      name: "delete_workflow"
      description: { zh: "删除指定工作流。", en: "Delete a specific workflow." }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }

    {
      name: "trigger_workflow"
      description: { zh: "触发指定工作流执行（相当于 UI 手动触发）。", en: "Trigger execution of a workflow (equivalent to manual trigger in UI)." }
      parameters: [
        { name: "workflow_id", description: { zh: "工作流 ID", en: "Workflow ID" }, type: "string", required: true }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const WorkflowIntegration = (function () {
    async function usage_advice(_params) {
        return {
            success: true,
            message: "请阅读 workflow 工具 METADATA 中的 usage_advice 说明：包含节点/连线 schema、分支 condition 规则、触发类型与配置示例。建议在脚本里直接使用 Tools.Workflow.getAll/get/create/update/patch/setEnabled/enable/disable/delete/trigger 等封装方法。"
        };
    }
    /**
     * 获取所有工作流
     * @returns 工作流列表
     */
    async function get_all_workflows() {
        const data = await Tools.Workflow.getAll();
        return {
            success: true,
            message: "成功获取工作流列表",
            data
        };
    }
    /**
     * 创建新工作流
     * @param params 工作流参数
     * @returns 创建结果
     */
    async function create_workflow(params) {
        const data = await Tools.Workflow.create(params.name, params.description || "", params.nodes || null, params.connections || null, params.enabled);
        return {
            success: true,
            message: "成功创建工作流",
            data
        };
    }
    /**
     * 获取工作流详情
     * @param params 获取参数
     * @returns 工作流详情
     */
    async function get_workflow(params) {
        const data = await Tools.Workflow.get(params.workflow_id);
        return {
            success: true,
            message: "成功获取工作流详情",
            data
        };
    }
    /**
     * 更新工作流
     * @param params 更新参数
     * @returns 更新结果
     */
    async function update_workflow(params) {
        const { workflow_id, ...updates } = params;
        const data = await Tools.Workflow.update(workflow_id, updates);
        return {
            success: true,
            message: "成功更新工作流",
            data
        };
    }
    /**
     * 差异更新工作流（增量 patch）
     * @param params patch 参数
     * @returns 更新结果
     */
    async function patch_workflow(params) {
        const { workflow_id, ...patch } = params;
        const data = await Tools.Workflow.patch(workflow_id, patch);
        return {
            success: true,
            message: "成功差异更新工作流",
            data
        };
    }
    /**
     * 启用工作流
     * @param params 启用参数
     * @returns 更新结果
     */
    async function enable_workflow(params) {
        const data = await Tools.Workflow.enable(params.workflow_id);
        return {
            success: true,
            message: "成功启用工作流",
            data
        };
    }
    /**
     * 禁用工作流
     * @param params 禁用参数
     * @returns 更新结果
     */
    async function disable_workflow(params) {
        const data = await Tools.Workflow.disable(params.workflow_id);
        return {
            success: true,
            message: "成功禁用工作流",
            data
        };
    }
    /**
     * 删除工作流
     * @param params 删除参数
     * @returns 删除结果
     */
    async function delete_workflow(params) {
        const data = await Tools.Workflow['delete'](params.workflow_id);
        return {
            success: true,
            message: "成功删除工作流",
            data
        };
    }
    /**
     * 触发工作流执行
     * @param params 触发参数
     * @returns 执行结果
     */
    async function trigger_workflow(params) {
        const data = await Tools.Workflow.trigger(params.workflow_id);
        return {
            success: true,
            message: "成功触发工作流",
            data
        };
    }
    /**
     * 包装工具执行，处理错误和结果
     * @param func 要执行的函数
     * @param params 函数参数
     */
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
        usage_advice: (params) => wrapToolExecution(usage_advice, params),
        get_all_workflows: () => wrapToolExecution(get_all_workflows, {}),
        create_workflow: (params) => wrapToolExecution(create_workflow, params),
        get_workflow: (params) => wrapToolExecution(get_workflow, params),
        update_workflow: (params) => wrapToolExecution(update_workflow, params),
        patch_workflow: (params) => wrapToolExecution(patch_workflow, params),
        enable_workflow: (params) => wrapToolExecution(enable_workflow, params),
        disable_workflow: (params) => wrapToolExecution(disable_workflow, params),
        delete_workflow: (params) => wrapToolExecution(delete_workflow, params),
        trigger_workflow: (params) => wrapToolExecution(trigger_workflow, params)
    };
})();
exports.usage_advice = WorkflowIntegration.usage_advice;
exports.get_all_workflows = WorkflowIntegration.get_all_workflows;
exports.create_workflow = WorkflowIntegration.create_workflow;
exports.get_workflow = WorkflowIntegration.get_workflow;
exports.update_workflow = WorkflowIntegration.update_workflow;
exports.patch_workflow = WorkflowIntegration.patch_workflow;
exports.enable_workflow = WorkflowIntegration.enable_workflow;
exports.disable_workflow = WorkflowIntegration.disable_workflow;
exports.delete_workflow = WorkflowIntegration.delete_workflow;
exports.trigger_workflow = WorkflowIntegration.trigger_workflow;
