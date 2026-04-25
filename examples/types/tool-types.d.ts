/**
 * Tool name type definitions for Assistance Package Tools
 * 
 * This file defines the available tool names and maps them to their result types.
 */

import {
    DirectoryListingData, FileContentData, BinaryFileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData, FileConversionResultData, FileFormatConversionsResultData,
    HttpResponseData, VisitWebResultData,
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    DeviceInfoResultData, NotificationData, LocationData,
    UIPageResultData, UIActionResultData, CombinedOperationResultData, AutomationExecutionResultData,
    CalculationResultData, FFmpegResultData, ADBResultData, IntentResultData, TerminalCommandResultData, HiddenTerminalCommandResultData, TerminalSessionScreenResultData,
    FilePartContentData, FileApplyResultData, WorkflowListResultData, WorkflowResultData, WorkflowDetailResultData,
    StringResultData, ChatServiceStartResultData, ChatCreationResultData, ChatListResultData, ChatFindResultData, AgentStatusResultData,
    ChatSwitchResultData, ChatTitleUpdateResultData, ChatDeleteResultData, MessageSendResultData, MemoryQueryResultData, MemoryLinkResultData, MemoryLinkQueryResultData, GrepResultData,
    ChatMessagesResultData, CharacterCardListResultData,
    EnvironmentVariableReadResultData, EnvironmentVariableWriteResultData,
    SandboxPackageResultItem, SandboxPackagesResultData, SandboxPackageUpdateResultData,
    McpRestartLogPluginResultItem, McpRestartWithLogsResultData,
    SpeechServicesConfigResultData, SpeechServicesUpdateResultData,
    SandboxScriptExecutionResultData,
    ModelConfigsResultData, ModelConfigCreateResultData, ModelConfigUpdateResultData, ModelConfigDeleteResultData,
    FunctionModelConfigsResultData, FunctionModelConfigResultData, FunctionModelBindingResultData, ModelConfigConnectionTestResultData
} from './results';

/**
 * Maps tool names to their result data types
 */
export interface ToolResultMap {
    // File operations
    'list_files': DirectoryListingData;
    'read_file': FileContentData;
    'read_file_part': FilePartContentData;
    'read_file_full': FileContentData;
    'read_file_binary': BinaryFileContentData;

    'write_file': FileOperationData;
    'delete_file': FileOperationData;
    'file_exists': FileExistsData;
    'move_file': FileOperationData;
    'copy_file': FileOperationData;
    'make_directory': FileOperationData;
    'find_files': FindFilesResultData;
    'grep_code': GrepResultData;
    'grep_context': GrepResultData;
    'file_info': FileInfoData;
    'zip_files': FileOperationData;
    'unzip_files': FileOperationData;
    'open_file': FileOperationData;
    'share_file': FileOperationData;
    'download_file': FileOperationData;
    'apply_file': FileApplyResultData;

    // Network operations
    'http_request': HttpResponseData;
    'visit_web': VisitWebResultData;
    'browser_click': StringResultData;
    'browser_close': StringResultData;
    'browser_console_messages': StringResultData;
    'browser_drag': StringResultData;
    'browser_evaluate': StringResultData;
    'browser_file_upload': StringResultData;
    'browser_fill_form': StringResultData;
    'browser_handle_dialog': StringResultData;
    'browser_hover': StringResultData;
    'browser_navigate': StringResultData;
    'browser_navigate_back': StringResultData;
    'browser_network_requests': StringResultData;
    'browser_press_key': StringResultData;
    'browser_resize': StringResultData;
    'browser_run_code': StringResultData;
    'browser_select_option': StringResultData;
    'browser_wait_for': StringResultData;
    'browser_snapshot': StringResultData;
    'browser_take_screenshot': StringResultData;
    'browser_type': StringResultData;
    'browser_tabs': StringResultData;
    'multipart_request': HttpResponseData;
    'manage_cookies': HttpResponseData;

    // System operations
    'sleep': SleepResultData;
    'get_system_setting': SystemSettingData;
    'modify_system_setting': SystemSettingData;
    'toast': StringResultData;
    'send_notification': StringResultData;
    'install_app': AppOperationData;
    'uninstall_app': AppOperationData;
    'list_installed_apps': AppListData;
    'start_app': AppOperationData;
    'stop_app': AppOperationData;
    'device_info': DeviceInfoResultData;
    'get_notifications': NotificationData;
    'get_device_location': LocationData;
    'read_environment_variable': EnvironmentVariableReadResultData;
    'write_environment_variable': EnvironmentVariableWriteResultData;
    'list_sandbox_packages': SandboxPackagesResultData;
    'set_sandbox_package_enabled': SandboxPackageUpdateResultData;
    'execute_sandbox_script_direct': SandboxScriptExecutionResultData;
    'restart_mcp_with_logs': McpRestartWithLogsResultData;
    'get_speech_services_config': SpeechServicesConfigResultData;
    'set_speech_services_config': SpeechServicesUpdateResultData;
    'list_model_configs': ModelConfigsResultData;
    'create_model_config': ModelConfigCreateResultData;
    'update_model_config': ModelConfigUpdateResultData;
    'delete_model_config': ModelConfigDeleteResultData;
    'list_function_model_configs': FunctionModelConfigsResultData;
    'get_function_model_config': FunctionModelConfigResultData;
    'set_function_model_config': FunctionModelBindingResultData;
    'test_model_config_connection': ModelConfigConnectionTestResultData;
    'trigger_tasker_event': string;

    // UI operations
    'get_page_info': UIPageResultData;
    'click_element': UIActionResultData;
    'tap': UIActionResultData;
    'set_input_text': UIActionResultData;
    'press_key': UIActionResultData;
    'swipe': UIActionResultData;
    'combined_operation': CombinedOperationResultData;
    'run_ui_subagent': AutomationExecutionResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_memory': MemoryQueryResultData;

    // FFmpeg operations
    'ffmpeg_execute': FFmpegResultData;
    'ffmpeg_info': FFmpegResultData;
    'ffmpeg_convert': FFmpegResultData;

    // ADB operations
    'execute_shell': ADBResultData;

    // Intent operations
    'execute_intent': IntentResultData;
    'send_broadcast': IntentResultData;

    // Terminal operations
    'execute_terminal': TerminalCommandResultData;
    'execute_hidden_terminal_command': HiddenTerminalCommandResultData;
    'get_terminal_session_screen': TerminalSessionScreenResultData;

    // Workflow operations
    'get_all_workflows': WorkflowListResultData;
    'create_workflow': WorkflowDetailResultData;
    'get_workflow': WorkflowDetailResultData;
    'update_workflow': WorkflowDetailResultData;
    'patch_workflow': WorkflowDetailResultData;
    'delete_workflow': StringResultData;
    'trigger_workflow': StringResultData;

    // Chat Manager operations
    'start_chat_service': ChatServiceStartResultData;
    'create_new_chat': ChatCreationResultData;
    'list_chats': ChatListResultData;
    'find_chat': ChatFindResultData;
    'agent_status': AgentStatusResultData;
    'switch_chat': ChatSwitchResultData;
    'update_chat_title': ChatTitleUpdateResultData;
    'delete_chat': ChatDeleteResultData;

    'send_message_to_ai': MessageSendResultData;
    'send_message_to_ai_advanced': MessageSendResultData;
    'list_character_cards': CharacterCardListResultData;
    'get_chat_messages': ChatMessagesResultData;

    // Memory operations
    'link_memories': MemoryLinkResultData;
    'query_memory_links': MemoryLinkQueryResultData;
} 
