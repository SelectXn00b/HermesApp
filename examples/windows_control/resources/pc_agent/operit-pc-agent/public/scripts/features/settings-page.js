const PRESET_CHECKBOX_NAME = "settingsAllowedPreset";

export function createSettingsPage({ t, W, on = {} }) {
  return W.Box(
    { className: "route-page", ref: "settingsPage" },
    W.Card(
      {
        title: t("card.configTitle"),
        subtitle: t("card.configSubtitle")
      },
      W.Form(
        { ref: "configForm", on: { submit: on.saveConfig } },
        W.Grid2(
          {},
          W.Field({ label: t("field.bindAddress") }, W.Input({ ref: "settingsBindAddressInput", placeholder: "127.0.0.1" })),
          W.Field({ label: t("field.port") }, W.Input({ ref: "settingsPortInput", type: "number", min: 1, max: 65535 })),
          W.Field({ label: t("field.maxCommandTimeoutMs") }, W.Input({ ref: "settingsMaxCommandInput", type: "number", min: 1000, max: 600000 })),
          W.Field({ label: t("field.apiToken") }, W.Input({ ref: "settingsApiTokenInput", type: "password", placeholder: t("placeholder.apiTokenEmptyDisable") }))
        ),
        W.Panel({}, W.PanelTitle(t("panel.allowedPresets")), W.CheckList({ ref: "settingsPresetChecksWrap" })),
        W.ButtonGroup({}, W.Button({ text: t("action.saveConfig"), type: "submit", ref: "saveConfigButton" }))
      ),
      W.Output({ ref: "configOutput", minHeight: 92, text: JSON.stringify({ loaded: false }, null, 2) })
    )
  );
}

export function createSettingsController({ api, refs, t, W, render, helpers, callbacks = {} }) {
  const { setBusy, setNotice, setJsonOutput, asErrorMessage } = helpers;
  const { onConfigSaved } = callbacks;

  function fillSettingsForm(config) {
    refs.settingsBindAddressInput.value = config.bindAddress || "";
    refs.settingsPortInput.value = config.port || 58321;
    refs.settingsMaxCommandInput.value = config.maxCommandMs || 30000;
    refs.settingsApiTokenInput.value = config.apiToken || "";
  }

  function collectCheckedPresets() {
    const container = refs.settingsPresetChecksWrap;
    if (!container) {
      return [];
    }

    const checks = container.querySelectorAll(`input[name='${PRESET_CHECKBOX_NAME}']:checked`);
    return Array.from(checks).map((node) => node.value);
  }

  function renderPresetChecklist(items, allowedPresets) {
    const wrap = refs.settingsPresetChecksWrap;
    if (!wrap) {
      return;
    }

    const safeItems = Array.isArray(items) ? items : [];
    const safeAllowedPresets = Array.isArray(allowedPresets) ? allowedPresets : [];

    wrap.replaceChildren();

    for (const item of safeItems) {
      const isAllowed = safeAllowedPresets.includes(item.name);
      const node = render(
        W.CheckItem({
          name: PRESET_CHECKBOX_NAME,
          value: item.name,
          title: item.name,
          hint: item.description,
          checked: isAllowed
        }),
        { refs: {} }
      );
      wrap.appendChild(node);
    }
  }

  async function saveConfigFromSettings(event) {
    event.preventDefault();
    setBusy("saveConfigButton", true, t("action.saveConfig"), t("action.working"));

    try {
      const payload = {
        bindAddress: refs.settingsBindAddressInput.value.trim(),
        port: Number(refs.settingsPortInput.value),
        maxCommandMs: Number(refs.settingsMaxCommandInput.value),
        apiToken: refs.settingsApiTokenInput.value,
        allowedPresets: collectCheckedPresets()
      };

      const result = await api.updateConfig(payload);
      setJsonOutput("configOutput", result);

      if (result.restartRequired) {
        setNotice("warn", t("message.configSavedRestartRequired"));
      } else {
        setNotice("ok", t("message.configSaved"));
      }

      if (typeof onConfigSaved === "function") {
        await onConfigSaved();
      }
    } catch (error) {
      setJsonOutput("configOutput", { ok: false, error: asErrorMessage(error) });
      setNotice("error", t("message.configSaveFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("saveConfigButton", false, t("action.saveConfig"), t("action.working"));
    }
  }

  return {
    fillSettingsForm,
    renderPresetChecklist,
    saveConfigFromSettings
  };
}
