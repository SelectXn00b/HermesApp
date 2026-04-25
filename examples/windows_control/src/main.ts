import toolboxUI from "./ui/windows_setup/index.ui.js";

export function registerToolPkg() {
  ToolPkg.registerToolboxUiModule({
    id: "windows_setup",
    runtime: "compose_dsl",
    screen: toolboxUI,
    params: {},
    title: {
      zh: "Windows 一键配置",
      en: "Windows Quick Setup",
    },
  });

  ToolPkg.registerAppLifecycleHook({
    id: "windows_app_create",
    event: "application_on_create",
    function: onApplicationCreate,
  });

  return true;
}

export function onApplicationCreate() {
  return { ok: true };
}
