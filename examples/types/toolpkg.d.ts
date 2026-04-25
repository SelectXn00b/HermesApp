import type { ToolParams } from './core';
import type { ComposeDslScreen } from './compose-dsl';

export namespace ToolPkg {
    export type LocalizedText = string | { [lang: string]: string };

    export type JsonPrimitive = string | number | boolean | null;

    export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

    export interface JsonObject {
        [key: string]: JsonValue;
    }

    export type AppLifecycleEvent =
        | "application_on_create"
        | "application_on_foreground"
        | "application_on_background"
        | "application_on_low_memory"
        | "application_on_trim_memory"
        | "application_on_terminate"
        | "activity_on_create"
        | "activity_on_start"
        | "activity_on_resume"
        | "activity_on_pause"
        | "activity_on_stop"
        | "activity_on_destroy";

    export type HookEventName =
        | AppLifecycleEvent
        | "message_processing"
        | "xml_render"
        | "input_menu_toggle"
        | ToolLifecycleEventName
        | PromptInputEventName
        | PromptHistoryEventName
        | SystemPromptComposeEventName
        | ToolPromptComposeEventName
        | PromptFinalizeEventName;

    export type HookReturn = JsonValue | void | Promise<JsonValue | void>;

    export type HookHandler<TEvent> = (event: TEvent) => HookReturn;

    export type AppLifecycleHookReturn =
        | JsonValue
        | void
        | Promise<JsonValue | void>;

    export interface MessageProcessingHookObjectResult extends JsonObject {
        matched?: boolean;
        text?: string;
        content?: string;
        chunks?: string[];
    }

    export type MessageProcessingHookReturnValue =
        | boolean
        | string
        | MessageProcessingHookObjectResult
        | null
        | void;

    export type MessageProcessingHookReturn =
        | MessageProcessingHookReturnValue
        | Promise<MessageProcessingHookReturnValue>;

    export interface XmlRenderHookObjectResult {
        handled?: boolean;
        text?: string;
        content?: string;
        composeDsl?: {
            screen: ComposeDslScreen;
            state?: JsonObject;
            memo?: JsonObject;
            moduleSpec?: JsonObject;
        };
    }

    export type XmlRenderHookReturn =
        | string
        | XmlRenderHookObjectResult
        | null
        | void
        | Promise<string | XmlRenderHookObjectResult | null | void>;

    export interface InputMenuToggleDefinitionResult extends JsonObject {
        id: string;
        title: string;
        description?: string;
        isChecked?: boolean;
    }

    export interface InputMenuToggleObjectResult extends JsonObject {
        toggles?: InputMenuToggleDefinitionResult[];
    }

    export type InputMenuToggleHookReturn =
        | InputMenuToggleDefinitionResult[]
        | InputMenuToggleObjectResult
        | null
        | void
        | Promise<InputMenuToggleDefinitionResult[] | InputMenuToggleObjectResult | null | void>;

    export type ToolLifecycleEventName =
        | "tool_call_requested"
        | "tool_permission_checked"
        | "tool_execution_started"
        | "tool_execution_result"
        | "tool_execution_error"
        | "tool_execution_finished";

    export type PromptInputEventName =
        | "before_process"
        | "after_process";

    export type PromptHistoryEventName =
        | "before_prepare_history"
        | "after_prepare_history";

    export type SystemPromptComposeEventName =
        | "before_compose_system_prompt"
        | "compose_system_prompt_sections"
        | "after_compose_system_prompt";

    export type ToolPromptComposeEventName =
        | "before_compose_tool_prompt"
        | "filter_tool_prompt_items"
        | "after_compose_tool_prompt";

    export type PromptFinalizeEventName =
        | "before_finalize_prompt"
        | "before_send_to_model";

    export type PromptTurnKind =
        | "SYSTEM"
        | "USER"
        | "ASSISTANT"
        | "TOOL_CALL"
        | "TOOL_RESULT"
        | "SUMMARY";

    export interface PromptTurn extends JsonObject {
        kind: PromptTurnKind;
        content: string;
        toolName?: string;
        metadata?: JsonObject;
    }

    export interface ToolLifecycleEventPayload extends JsonObject {
        toolName: string;
        parameters?: { [key: string]: string };
        description?: string;
        granted?: boolean;
        reason?: string;
        success?: boolean;
        errorMessage?: string;
        resultText?: string;
        resultJson?: JsonValue;
    }

    export interface PromptHookObjectResult extends JsonObject {
        rawInput?: string;
        processedInput?: string;
        chatHistory?: PromptTurn[];
        preparedHistory?: PromptTurn[];
        systemPrompt?: string;
        toolPrompt?: string;
        metadata?: JsonObject;
    }

    export interface PromptHookEventPayload extends JsonObject {
        stage?: string;
        chatId?: string;
        functionType?: string;
        promptFunctionType?: string;
        useEnglish?: boolean;
        rawInput?: string;
        processedInput?: string;
        chatHistory?: PromptTurn[];
        preparedHistory?: PromptTurn[];
        systemPrompt?: string;
        toolPrompt?: string;
        modelParameters?: JsonObject[];
        availableTools?: JsonObject[];
        metadata?: JsonObject;
    }

    export type ToolLifecycleHookReturn = void | Promise<void>;

    export type PromptInputHookReturn =
        | string
        | PromptHookObjectResult
        | null
        | void
        | Promise<string | PromptHookObjectResult | null | void>;

    export type PromptHistoryHookReturn =
        | PromptTurn[]
        | PromptHookObjectResult
        | null
        | void
        | Promise<PromptTurn[] | PromptHookObjectResult | null | void>;

    export type SystemPromptComposeHookReturn =
        | string
        | PromptHookObjectResult
        | null
        | void
        | Promise<string | PromptHookObjectResult | null | void>;

    export type ToolPromptComposeHookReturn =
        | string
        | PromptHookObjectResult
        | null
        | void
        | Promise<string | PromptHookObjectResult | null | void>;

    export type PromptFinalizeHookReturn =
        | string
        | PromptTurn[]
        | PromptHookObjectResult
        | null
        | void
        | Promise<string | PromptTurn[] | PromptHookObjectResult | null | void>;

    export type AppLifecycleHookHandler =
        (event: AppLifecycleHookEvent) => AppLifecycleHookReturn;

    export type MessageProcessingHookHandler =
        (event: MessageProcessingHookEvent) =>
            MessageProcessingHookReturnValue
            | Promise<MessageProcessingHookReturnValue>;

    export type XmlRenderHookHandler =
        (event: XmlRenderHookEvent) => XmlRenderHookReturn;

    export type InputMenuToggleHookHandler =
        (event: InputMenuToggleHookEvent) => InputMenuToggleHookReturn;

    export type ToolLifecycleHookHandler =
        (event: ToolLifecycleHookEvent) => ToolLifecycleHookReturn;

    export type PromptInputHookHandler =
        (event: PromptInputHookEvent) => PromptInputHookReturn;

    export type PromptHistoryHookHandler =
        (event: PromptHistoryHookEvent) => PromptHistoryHookReturn;

    export type PromptEstimateHistoryHookHandler =
        (event: PromptEstimateHistoryHookEvent) => PromptHistoryHookReturn;

    export type SystemPromptComposeHookHandler =
        (event: SystemPromptComposeHookEvent) => SystemPromptComposeHookReturn;

    export type ToolPromptComposeHookHandler =
        (event: ToolPromptComposeHookEvent) => ToolPromptComposeHookReturn;

    export type PromptFinalizeHookHandler =
        (event: PromptFinalizeHookEvent) => PromptFinalizeHookReturn;

    export type PromptEstimateFinalizeHookHandler =
        (event: PromptEstimateFinalizeHookEvent) => PromptFinalizeHookReturn;

    export interface HookEventBase<
        TEventName extends string,
        TPayload extends JsonObject = JsonObject
    > {
        event: TEventName;
        eventName: TEventName;
        eventPayload: TPayload;
        toolPkgId?: string;
        containerPackageName?: string;
        functionName?: string;
        pluginId?: string;
        hookId?: string;
        timestampMs?: number;
    }

    export interface AppLifecycleEventPayload extends JsonObject {
        extras?: JsonObject;
    }

    export interface MessageProcessingEventPayload extends JsonObject {
        messageContent?: string;
        chatHistory?: PromptTurn[];
        workspacePath?: string;
        maxTokens?: number;
        tokenUsageThreshold?: number;
        probeOnly?: boolean;
        executionId?: string;
    }

    export interface XmlRenderEventPayload extends JsonObject {
        xmlContent?: string;
        tagName?: string;
    }

    export interface InputMenuToggleEventPayload extends JsonObject {
        action?: "create" | "toggle" | string;
        toggleId?: string;
    }

    export interface AppLifecycleHookEvent
        extends HookEventBase<AppLifecycleEvent, AppLifecycleEventPayload> {}

    export interface MessageProcessingHookEvent
        extends HookEventBase<"message_processing", MessageProcessingEventPayload> {}

    export interface XmlRenderHookEvent
        extends HookEventBase<"xml_render", XmlRenderEventPayload> {}

    export interface InputMenuToggleHookEvent
        extends HookEventBase<"input_menu_toggle", InputMenuToggleEventPayload> {}

    export interface ToolLifecycleHookEvent
        extends HookEventBase<ToolLifecycleEventName, ToolLifecycleEventPayload> {}

    export interface PromptInputHookEvent
        extends HookEventBase<PromptInputEventName, PromptHookEventPayload> {}

    export interface PromptHistoryHookEvent
        extends HookEventBase<PromptHistoryEventName, PromptHookEventPayload> {}

    export interface PromptEstimateHistoryHookEvent
        extends HookEventBase<PromptHistoryEventName, PromptHookEventPayload> {}

    export interface SystemPromptComposeHookEvent
        extends HookEventBase<SystemPromptComposeEventName, PromptHookEventPayload> {}

    export interface ToolPromptComposeHookEvent
        extends HookEventBase<ToolPromptComposeEventName, PromptHookEventPayload> {}

    export interface PromptFinalizeHookEvent
        extends HookEventBase<PromptFinalizeEventName, PromptHookEventPayload> {}

    export interface PromptEstimateFinalizeHookEvent
        extends HookEventBase<PromptFinalizeEventName, PromptHookEventPayload> {}

    export interface ToolboxUiModuleRegistration {
        id: string;
        runtime?: string;
        screen: ComposeDslScreen;
        params?: ToolParams;
        title?: LocalizedText;
    }

    export interface AppLifecycleHookRegistration {
        id: string;
        event: AppLifecycleEvent;
        function: AppLifecycleHookHandler;
    }

    export interface MessageProcessingPluginRegistration {
        id: string;
        function: MessageProcessingHookHandler;
    }

    export interface XmlRenderPluginRegistration {
        id: string;
        tag: string;
        function: XmlRenderHookHandler;
    }

    export interface InputMenuTogglePluginRegistration {
        id: string;
        function: InputMenuToggleHookHandler;
    }

    export interface ToolLifecycleHookRegistration {
        id: string;
        function: ToolLifecycleHookHandler;
    }

    export interface PromptInputHookRegistration {
        id: string;
        function: PromptInputHookHandler;
    }

    export interface PromptHistoryHookRegistration {
        id: string;
        function: PromptHistoryHookHandler;
    }

    export interface PromptEstimateHistoryHookRegistration {
        id: string;
        function: PromptEstimateHistoryHookHandler;
    }

    export interface SystemPromptComposeHookRegistration {
        id: string;
        function: SystemPromptComposeHookHandler;
    }

    export interface ToolPromptComposeHookRegistration {
        id: string;
        function: ToolPromptComposeHookHandler;
    }

    export interface PromptFinalizeHookRegistration {
        id: string;
        function: PromptFinalizeHookHandler;
    }

    export interface PromptEstimateFinalizeHookRegistration {
        id: string;
        function: PromptEstimateFinalizeHookHandler;
    }

    export interface Registry {
        registerToolboxUiModule(definition: ToolboxUiModuleRegistration): void;
        registerAppLifecycleHook(definition: AppLifecycleHookRegistration): void;
        registerMessageProcessingPlugin(definition: MessageProcessingPluginRegistration): void;
        registerXmlRenderPlugin(definition: XmlRenderPluginRegistration): void;
        registerInputMenuTogglePlugin(definition: InputMenuTogglePluginRegistration): void;
        registerToolLifecycleHook(definition: ToolLifecycleHookRegistration): void;
        registerPromptInputHook(definition: PromptInputHookRegistration): void;
        registerPromptHistoryHook(definition: PromptHistoryHookRegistration): void;
        registerPromptEstimateHistoryHook(definition: PromptEstimateHistoryHookRegistration): void;
        registerSystemPromptComposeHook(definition: SystemPromptComposeHookRegistration): void;
        registerToolPromptComposeHook(definition: ToolPromptComposeHookRegistration): void;
        registerPromptFinalizeHook(definition: PromptFinalizeHookRegistration): void;
        registerPromptEstimateFinalizeHook(definition: PromptEstimateFinalizeHookRegistration): void;
        readResource(key: string, outputFileName?: string, internal?: boolean): Promise<string>;
    }
}

declare global {
    function registerToolPkgToolboxUiModule(definition: ToolPkg.ToolboxUiModuleRegistration): void;

    function registerToolPkgAppLifecycleHook(definition: ToolPkg.AppLifecycleHookRegistration): void;

    function registerToolPkgMessageProcessingPlugin(definition: ToolPkg.MessageProcessingPluginRegistration): void;

    function registerToolPkgXmlRenderPlugin(definition: ToolPkg.XmlRenderPluginRegistration): void;

    function registerToolPkgInputMenuTogglePlugin(definition: ToolPkg.InputMenuTogglePluginRegistration): void;

    function registerToolPkgToolLifecycleHook(definition: ToolPkg.ToolLifecycleHookRegistration): void;

    function registerToolPkgPromptInputHook(definition: ToolPkg.PromptInputHookRegistration): void;

    function registerToolPkgPromptHistoryHook(definition: ToolPkg.PromptHistoryHookRegistration): void;

    function registerToolPkgPromptEstimateHistoryHook(definition: ToolPkg.PromptEstimateHistoryHookRegistration): void;

    function registerToolPkgSystemPromptComposeHook(definition: ToolPkg.SystemPromptComposeHookRegistration): void;

    function registerToolPkgToolPromptComposeHook(definition: ToolPkg.ToolPromptComposeHookRegistration): void;

    function registerToolPkgPromptFinalizeHook(definition: ToolPkg.PromptFinalizeHookRegistration): void;

    function registerToolPkgPromptEstimateFinalizeHook(definition: ToolPkg.PromptEstimateFinalizeHookRegistration): void;

    const ToolPkg: ToolPkg.Registry;
}
