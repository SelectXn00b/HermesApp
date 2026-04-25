export function createCommandsPage({ t, W, on = {} }) {
  return W.Box(
    { className: "route-page", ref: "commandsPage" },
    W.Card(
      {
        title: t("card.commandTitle"),
        subtitle: t("card.commandSubtitle")
      },
      W.Field({ label: t("field.token") }, W.Input({ ref: "commandTokenInput", type: "password", placeholder: t("placeholder.tokenRequiredWhenApiTokenEnabled") })),

      W.Panel(
        {},
        W.PanelTitle(t("panel.presetMode")),
        W.Field({ label: t("field.preset") }, W.Select({ ref: "commandPresetSelect", options: [{ value: "", label: t("placeholder.loadingPresets") }] })),
        W.ButtonGroup({}, W.Button({ text: t("action.runPreset"), ref: "runPresetButton", on: { click: on.runPreset } }))
      ),

      W.Panel(
        {},
        W.PanelTitle(t("panel.rawMode")),
        W.Field(
          { label: t("field.shell") },
          W.Select({
            ref: "rawShellSelect",
            options: [
              { value: "powershell", label: "powershell" },
              { value: "cmd", label: "cmd" },
              { value: "pwsh", label: "pwsh" }
            ]
          })
        ),
        W.Field({ label: t("field.command") }, W.Textarea({ ref: "rawCommandInput", rows: 6, placeholder: t("placeholder.rawCommandExample") })),
        W.ButtonGroup({}, W.Button({ text: t("action.runRaw"), variant: "soft", ref: "runRawButton", on: { click: on.runRaw } }))
      ),

      W.Output({ ref: "commandOutput", minHeight: 150, text: "{}" })
    )
  );
}

export function createCommandsController({ api, refs, t, helpers }) {
  const { setBusy, setNotice, setJsonOutput, asErrorMessage } = helpers;

  function renderPresetSelect(items, allowedPresets) {
    const select = refs.commandPresetSelect;
    if (!select) {
      return;
    }

    const safeItems = Array.isArray(items) ? items : [];
    const safeAllowedPresets = Array.isArray(allowedPresets) ? allowedPresets : [];

    select.replaceChildren();

    for (const item of safeItems) {
      const isAllowed = safeAllowedPresets.includes(item.name);
      const option = document.createElement("option");
      option.value = item.name;
      option.textContent = `${item.name}${isAllowed ? "" : t("preset.disabledSuffix")}`;
      select.appendChild(option);
    }

    if (!safeItems.length) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = t("placeholder.noPresetsAvailable");
      select.appendChild(option);
    }
  }

  async function handleRunPreset() {
    setBusy("runPresetButton", true, t("action.runPreset"), t("action.running"));

    try {
      const result = await api.executeCommand({
        token: refs.commandTokenInput.value,
        preset: refs.commandPresetSelect.value
      });
      setJsonOutput("commandOutput", result);
      setNotice(result.ok ? "ok" : "warn", result.ok ? t("message.presetFinished") : t("message.presetWarn"));
    } catch (error) {
      setJsonOutput("commandOutput", { ok: false, error: asErrorMessage(error) });
      setNotice("error", t("message.presetFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("runPresetButton", false, t("action.runPreset"), t("action.running"));
    }
  }

  async function handleRunRaw() {
    setBusy("runRawButton", true, t("action.runRaw"), t("action.running"));

    try {
      const result = await api.executeCommand({
        token: refs.commandTokenInput.value,
        shell: refs.rawShellSelect.value,
        command: refs.rawCommandInput.value
      });
      setJsonOutput("commandOutput", result);
      setNotice(result.ok ? "ok" : "warn", result.ok ? t("message.rawFinished") : t("message.rawWarn"));
    } catch (error) {
      setJsonOutput("commandOutput", { ok: false, error: asErrorMessage(error) });
      setNotice("error", t("message.rawFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("runRawButton", false, t("action.runRaw"), t("action.running"));
    }
  }

  return {
    renderPresetSelect,
    handleRunPreset,
    handleRunRaw
  };
}
