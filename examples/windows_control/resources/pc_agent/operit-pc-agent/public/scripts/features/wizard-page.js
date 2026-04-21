export function createWizardPage({ t, W, on = {} }) {
  return W.Box(
    { className: "route-page", ref: "wizardPage" },
    W.Card(
      {
        title: t("wizard.title"),
        subtitle: t("wizard.subtitle")
      },
      W.Box(
        { className: "wizard-steps" },
        W.Button({ text: t("wizard.stepNav1"), variant: "soft", className: "wizard-step-btn", ref: "wizardStep1Button", on: { click: on.wizardStep1 } }),
        W.Button({ text: t("wizard.stepNav2"), variant: "soft", className: "wizard-step-btn", ref: "wizardStep2Button", on: { click: on.wizardStep2 } })
      ),

      W.Panel(
        { className: "wizard-step-panel", ref: "wizardStep1Panel" },
        W.PanelTitle(t("wizard.step1Title")),
        W.Text({ as: "p", className: "wizard-step-desc", text: t("wizard.step1Desc") }),
        W.Grid2(
          {},
          W.Field({ label: t("field.bindAddress") }, W.Input({ ref: "wizardBindAddressInput", placeholder: "127.0.0.1" })),
          W.Field({ label: t("field.port") }, W.Input({ ref: "wizardPortInput", type: "number", min: 1, max: 65535 })),
          W.Field({ label: t("field.maxCommandTimeoutMs") }, W.Input({ ref: "wizardMaxCommandInput", type: "number", min: 1000, max: 600000 })),
          W.Field({ label: t("field.apiToken") }, W.Input({ ref: "wizardApiTokenInput", type: "password", placeholder: t("placeholder.apiTokenEmptyDisable") }))
        ),
        W.Text({ as: "p", className: "wizard-hint", text: t("wizard.step1Hint") }),
        W.ButtonGroup(
          {},
          W.Button({ text: t("action.saveAndNext"), ref: "wizardSaveNextButton", on: { click: on.wizardSaveNext } }),
          W.Button({ text: t("action.toSettings"), variant: "soft", ref: "wizardToSettingsButton", on: { click: on.wizardToSettings } })
        ),
        W.Output({ ref: "wizardStep1Output", minHeight: 88, text: "{}" })
      ),

      W.Panel(
        { className: "wizard-step-panel", ref: "wizardStep2Panel" },
        W.PanelTitle(t("wizard.step2Title")),
        W.Text({ as: "p", className: "wizard-step-desc", text: t("wizard.step2Desc") }),
        W.Panel(
          { className: "wizard-one-click" },
          W.PanelTitle(t("wizard.oneClickTitle")),
          W.Text({ as: "p", className: "wizard-step-desc", text: t("wizard.oneClickDesc") }),
          W.ButtonGroup(
            {},
            W.Button({ text: t("action.oneClickFill"), ref: "wizardOneClickFillButton", on: { click: on.wizardOneClickFill } }),
            W.Button({ text: t("action.copyPayload"), variant: "soft", ref: "wizardCopyPayloadButton", on: { click: on.wizardCopyPayload } }),
            W.Button({ text: t("action.toggleAdvancedShow"), variant: "soft", ref: "wizardToggleAdvancedButton", on: { click: on.wizardToggleAdvanced } })
          )
        ),
        W.Panel(
          { className: "wizard-advanced-panel", ref: "wizardAdvancedPanel" },
          W.Grid2(
            {},
            W.Field({ label: t("field.windowsAgentBaseUrl") }, W.Input({ ref: "mobileBaseUrlInput", placeholder: t("placeholder.mobileBaseUrl"), on: { input: on.mobileInput, change: on.mobileInput } })),
            W.Field({ label: t("field.windowsAgentToken") }, W.Input({ ref: "mobileTokenInput", type: "password", placeholder: t("placeholder.tokenRequiredWhenApiTokenEnabled"), on: { input: on.mobileInput, change: on.mobileInput } })),
            W.Field({
              label: t("field.windowsAgentDefaultShell")
            }, W.Select({
              ref: "mobileDefaultShellInput",
              options: [
                { value: "powershell", label: "powershell" },
                { value: "pwsh", label: "pwsh" },
                { value: "cmd", label: "cmd" }
              ],
              on: { change: on.mobileInput, input: on.mobileInput }
            })),
            W.Field({ label: t("field.windowsAgentTimeoutMs") }, W.Input({ ref: "mobileTimeoutMsInput", type: "number", min: 1000, max: 600000, placeholder: "30000", on: { input: on.mobileInput, change: on.mobileInput } }))
          )
        ),
        W.Text({ as: "p", className: "wizard-snippet-title", text: t("wizard.mobileJsonTitle") }),
        W.Output({ ref: "wizardMobileJsonOutput", minHeight: 120, text: "{}" })
      )
    )
  );
}

export function createWizardController({ api, refs, state, t, helpers, callbacks = {} }) {
  const { setBusy, setNotice, setJsonOutput, asErrorMessage } = helpers;
  const { reloadConfigAndHealth, onConfigUpdated } = callbacks;

  function setWizardStep(stepIndex) {
    const safeStep = Math.max(0, Math.min(1, Number(stepIndex) || 0));
    state.wizardStep = safeStep;
    applyWizardStepUi();
  }

  function fillWizardStep1Form(config) {
    refs.wizardBindAddressInput.value = config.bindAddress || "";
    refs.wizardPortInput.value = config.port || 58321;
    refs.wizardMaxCommandInput.value = config.maxCommandMs || 30000;
    refs.wizardApiTokenInput.value = config.apiToken || "";
  }

  function chooseRecommendedHost() {
    const preferredLan =
      state.health &&
      state.health.network &&
      state.health.network.preferredLan &&
      String(state.health.network.preferredLan).trim();

    if (preferredLan) {
      return preferredLan;
    }

    const networkHost =
      state.health &&
      state.health.network &&
      state.health.network.recommendedHost &&
      String(state.health.network.recommendedHost).trim();

    if (networkHost && networkHost !== "127.0.0.1" && networkHost !== "0.0.0.0" && networkHost !== "localhost") {
      return networkHost;
    }

    const bindAddress = state.config && state.config.bindAddress ? String(state.config.bindAddress).trim() : "";
    if (bindAddress && bindAddress !== "127.0.0.1" && bindAddress !== "0.0.0.0" && bindAddress !== "localhost") {
      return bindAddress;
    }

    return "";
  }

  function applyWizardBindAddressAutoDefault() {
    if (state.wizardBindAutoApplied || !refs.wizardBindAddressInput) {
      return;
    }

    const savedBind = state.config && state.config.bindAddress ? String(state.config.bindAddress).trim() : "";
    const currentBind = String(refs.wizardBindAddressInput.value || "").trim();

    if (savedBind && savedBind !== "127.0.0.1") {
      state.wizardBindAutoApplied = true;
      return;
    }

    if (currentBind && currentBind !== "127.0.0.1") {
      state.wizardBindAutoApplied = true;
      return;
    }

    const recommendedHost = chooseRecommendedHost();
    if (recommendedHost) {
      refs.wizardBindAddressInput.value = recommendedHost;
      state.wizardBindAutoApplied = true;
    }
  }

  function buildRecommendedAgentBaseUrl() {
    const host = chooseRecommendedHost();
    if (!host) {
      return "";
    }

    const port = state.config && state.config.port ? Number(state.config.port) : 58321;
    const safePort = Number.isFinite(port) && port > 0 ? Math.floor(port) : 58321;
    return `http://${host}:${safePort}`;
  }

  function fillWizardHostHint() {
    if (!refs.mobileBaseUrlInput) {
      return;
    }

    const current = String(refs.mobileBaseUrlInput.value || "").trim();
    if (current) {
      return;
    }

    const suggestedBaseUrl = buildRecommendedAgentBaseUrl();
    if (suggestedBaseUrl) {
      refs.mobileBaseUrlInput.value = suggestedBaseUrl;
    }
  }

  function applyOneClickMobileDefaults(options = {}) {
    const force = !!options.force;

    const defaultValues = {
      baseUrl: buildRecommendedAgentBaseUrl(),
      token: String((state.config && state.config.apiToken) || "").trim(),
      defaultShell: "powershell",
      timeoutMs: String((state.config && state.config.maxCommandMs) || 30000)
    };

    const assignText = (refName, value) => {
      const node = refs[refName];
      if (!node || value === undefined || value === null || value === "") {
        return;
      }

      const current = String(node.value || "").trim();
      if (force || !current) {
        node.value = value;
      }
    };

    assignText("mobileBaseUrlInput", defaultValues.baseUrl);
    assignText("mobileTokenInput", defaultValues.token);
    assignText("mobileDefaultShellInput", defaultValues.defaultShell);
    assignText("mobileTimeoutMsInput", defaultValues.timeoutMs);
  }

  function buildMobileEnvObject(options = {}) {
    const includeRequired = !!options.includeRequired;

    const data = {
      WINDOWS_AGENT_BASE_URL: String(refs.mobileBaseUrlInput.value || "").trim(),
      WINDOWS_AGENT_TOKEN: String(refs.mobileTokenInput.value || "").trim(),
      WINDOWS_AGENT_DEFAULT_SHELL: String(refs.mobileDefaultShellInput.value || "").trim(),
      WINDOWS_AGENT_TIMEOUT_MS: String(refs.mobileTimeoutMsInput.value || "").trim()
    };

    const requiredKeys = ["WINDOWS_AGENT_BASE_URL", "WINDOWS_AGENT_TOKEN"];

    const compact = {};
    for (const [key, value] of Object.entries(data)) {
      if (value) {
        compact[key] = value;
        continue;
      }

      if (includeRequired && requiredKeys.includes(key)) {
        compact[key] = "";
      }
    }

    return compact;
  }

  function toEnvText(envObject) {
    return Object.entries(envObject)
      .map(([key, value]) => `${key}=${value}`)
      .join("\n");
  }

  function renderMobileSnippets() {
    const envObject = buildMobileEnvObject({ includeRequired: true });
    const jsonText = JSON.stringify(envObject, null, 2);
    const envText = toEnvText(envObject);

    if (refs.wizardMobileJsonOutput) {
      refs.wizardMobileJsonOutput.textContent = jsonText;
    }

    if (refs.wizardMobileEnvOutput) {
      refs.wizardMobileEnvOutput.textContent = envText;
    }

    return { jsonText, envText };
  }

  function applyWizardAdvancedUi() {
    if (!refs.wizardAdvancedPanel || !refs.wizardToggleAdvancedButton) {
      return;
    }

    const visible = !!state.wizardAdvancedVisible;
    refs.wizardAdvancedPanel.hidden = !visible;
    refs.wizardToggleAdvancedButton.textContent = visible
      ? t("action.toggleAdvancedHide")
      : t("action.toggleAdvancedShow");
  }

  function applyWizardStepUi() {
    const panels = [refs.wizardStep1Panel, refs.wizardStep2Panel];
    const buttons = [refs.wizardStep1Button, refs.wizardStep2Button];

    panels.forEach((panel, index) => {
      panel.hidden = index !== state.wizardStep;
    });

    buttons.forEach((button, index) => {
      button.classList.toggle("is-active", index === state.wizardStep);
      button.classList.toggle("is-done", index < state.wizardStep);
    });

    applyWizardAdvancedUi();
  }

  function syncFromState(options = {}) {
    fillWizardHostHint();
    applyWizardBindAddressAutoDefault();
    applyOneClickMobileDefaults({ force: !!options.forceMobileDefaults });
    renderMobileSnippets();
  }

  async function handleWizardStep1SaveNext() {
    setBusy("wizardSaveNextButton", true, t("action.saveAndNext"), t("action.working"));

    try {
      const payload = {
        bindAddress: refs.wizardBindAddressInput.value.trim(),
        port: Number(refs.wizardPortInput.value),
        maxCommandMs: Number(refs.wizardMaxCommandInput.value),
        apiToken: refs.wizardApiTokenInput.value
      };

      const result = await api.updateConfig(payload);
      setJsonOutput("wizardStep1Output", result);

      if (result.restartRequired) {
        setNotice("warn", t("message.configSavedRestartRequired"));
      } else {
        setNotice("ok", t("message.wizardStep1Done"));
      }

      if (typeof reloadConfigAndHealth === "function") {
        await reloadConfigAndHealth();
      }

      setWizardStep(1);
    } catch (error) {
      setJsonOutput("wizardStep1Output", { ok: false, error: asErrorMessage(error) });
      setNotice("error", t("message.configSaveFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("wizardSaveNextButton", false, t("action.saveAndNext"), t("action.working"));
    }
  }


  async function ensureAgentTokenForOneClick() {
    const currentToken = String((state.config && state.config.apiToken) || "").trim();
    if (currentToken) {
      return currentToken;
    }

    const updated = await api.updateConfig({ apiToken: "" });
    if (!updated || !updated.config) {
      throw new Error("Failed to generate API token");
    }

    state.config = updated.config;

    if (typeof onConfigUpdated === "function") {
      onConfigUpdated(updated.config);
    }

    return String(updated.config.apiToken || "").trim();
  }

  async function handleWizardOneClickFill() {
    setBusy("wizardOneClickFillButton", true, t("action.oneClickFill"), t("action.working"));

    try {
      await ensureAgentTokenForOneClick();
      syncFromState({ forceMobileDefaults: true });
      setNotice("ok", t("message.oneClickFilled"));
    } catch (error) {
      setNotice("error", t("message.copyFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("wizardOneClickFillButton", false, t("action.oneClickFill"), t("action.working"));
    }
  }

  async function copyText(text) {
    if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
      await navigator.clipboard.writeText(text);
      return;
    }

    const temp = document.createElement("textarea");
    temp.value = text;
    temp.setAttribute("readonly", "readonly");
    temp.style.position = "fixed";
    temp.style.opacity = "0";
    document.body.appendChild(temp);
    temp.select();
    document.execCommand("copy");
    document.body.removeChild(temp);
  }

  async function handleWizardCopyPayload() {
    setBusy("wizardCopyPayloadButton", true, t("action.copyPayload"), t("action.working"));

    try {
      const { jsonText } = renderMobileSnippets();
      await copyText(jsonText);
      setNotice("ok", t("message.copyPayloadSuccess"));
    } catch (error) {
      setNotice("error", t("message.copyFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("wizardCopyPayloadButton", false, t("action.copyPayload"), t("action.working"));
    }
  }

  function handleWizardToggleAdvanced() {
    state.wizardAdvancedVisible = !state.wizardAdvancedVisible;
    applyWizardAdvancedUi();
  }

  function handleMobileSnippetInput() {
    renderMobileSnippets();
  }

  return {
    fillWizardStep1Form,
    setWizardStep,
    applyWizardStepUi,
    syncFromState,
    handleWizardStep1SaveNext,
    handleWizardOneClickFill,
    handleWizardCopyPayload,
    handleWizardToggleAdvanced,
    handleMobileSnippetInput
  };
}
