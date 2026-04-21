"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const i18n_1 = require("../i18n");
const LINUX_SSH_PACKAGE_NAME = "linux_ssh";
const DEFAULT_TMUX_SESSION_NAME = "operit_ai";
const ENV_KEYS = {
    host: "LINUX_SSH_HOST",
    port: "LINUX_SSH_PORT",
    username: "LINUX_SSH_USERNAME",
    password: "LINUX_SSH_PASSWORD",
    privateKeyPath: "LINUX_SSH_PRIVATE_KEY_PATH",
    timeoutMs: "LINUX_SSH_TIMEOUT_MS"
};
function resolveText() {
    const rawLocale = getLang();
    const locale = String(rawLocale || "").trim().toLowerCase();
    const preferredLocale = locale.startsWith("en") ? "en-US" : "zh-CN";
    return (0, i18n_1.resolveLinuxSshSetupI18n)(preferredLocale);
}
function asText(value) {
    return String(value == null ? "" : value);
}
function useStateValue(ctx, key, initialValue) {
    const pair = ctx.useState(key, initialValue);
    return { value: pair[0], set: pair[1] };
}
function parseOptionalPositiveInt(raw) {
    const value = Number(raw.trim());
    if (!Number.isFinite(value) || value <= 0) {
        return undefined;
    }
    return Math.floor(value);
}
function formatResult(result) {
    if (typeof result === "string") {
        return result;
    }
    try {
        return JSON.stringify(result, null, 2);
    }
    catch {
        return asText(result);
    }
}
function toErrorText(error) {
    if (error instanceof Error) {
        return error.message || resolveText().unknown;
    }
    return asText(error || resolveText().unknown);
}
function extractMarkedBlock(output, beginToken, endToken) {
    const begin = output.indexOf(beginToken);
    if (begin < 0) {
        return "";
    }
    const from = begin + beginToken.length;
    const end = output.indexOf(endToken, from);
    if (end < 0) {
        return output.slice(from).trim();
    }
    return output.slice(from, end).trim();
}
function getToolOutputText(result) {
    const record = parseToolRecord(result);
    const output = asText(record.output || "");
    const rawOutput = asText(record.rawOutput || "");
    const error = asText(record.error || "");
    const captureFromOutput = extractMarkedBlock(output, "__OPERIT_TMUX_CAPTURE_BEGIN__", "__OPERIT_TMUX_CAPTURE_END__");
    if (captureFromOutput) {
        return captureFromOutput;
    }
    const captureFromRaw = extractMarkedBlock(rawOutput, "__OPERIT_TMUX_CAPTURE_BEGIN__", "__OPERIT_TMUX_CAPTURE_END__");
    if (captureFromRaw) {
        return captureFromRaw;
    }
    if (output) {
        return output;
    }
    if (rawOutput) {
        return rawOutput;
    }
    if (error) {
        return error;
    }
    if (typeof result === "string") {
        return result;
    }
    return formatResult(result);
}
function parseToolRecord(result) {
    if (result && typeof result === "object" && !Array.isArray(result)) {
        return result;
    }
    if (typeof result === "string") {
        const raw = result.trim();
        if (!raw) {
            return {};
        }
        try {
            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
                return parsed;
            }
        }
        catch {
            return {};
        }
    }
    return {};
}
function parseTmuxTabsFromListResult(result) {
    const record = parseToolRecord(result);
    const windows = record.windows;
    if (!Array.isArray(windows)) {
        return [];
    }
    return windows
        .map((item) => item)
        .map((item) => {
        const name = asText(item.name || "").trim();
        if (!name) {
            return null;
        }
        const index = asText(item.index || "").trim();
        const label = asText(item.label || "").trim() || (index ? `#${index} ${name}` : name);
        const key = asText(item.key || "").trim() || (index ? `${index}:${name}` : name);
        return {
            key,
            label,
            windowName: name
        };
    })
        .filter((item) => item !== null);
}
function getNextTaskWindowName(tabs) {
    const existingNames = new Set(tabs.map((tab) => tab.windowName));
    let seq = 1;
    while (existingNames.has(`task-${seq}`)) {
        seq += 1;
    }
    return `task-${seq}`;
}
async function resolveToolName(ctx, packageName, toolName) {
    if (ctx.resolveToolName) {
        const resolved = await ctx.resolveToolName({ packageName, toolName, preferImported: true });
        const value = asText(resolved).trim();
        if (value) {
            return value;
        }
    }
    return `${packageName}:${toolName}`;
}
function resolveRuntimePackageName(ctx, fallback) {
    const currentPackageName = asText(ctx.getCurrentPackageName ? ctx.getCurrentPackageName() : "").trim();
    const currentToolPkgId = asText(ctx.getCurrentToolPkgId ? ctx.getCurrentToolPkgId() : "").trim();
    if (!currentPackageName) {
        return fallback;
    }
    if (currentToolPkgId && currentPackageName === currentToolPkgId) {
        return fallback;
    }
    return currentPackageName;
}
async function ensureImportedAndUsed(ctx, packageName) {
    const imported = ctx.isPackageImported ? !!(await ctx.isPackageImported(packageName)) : false;
    if (!imported && ctx.importPackage) {
        const result = await ctx.importPackage(packageName);
        const message = asText(result).toLowerCase();
        if (message.includes("error") || message.includes("failed") || message.includes("not found")) {
            throw new Error(asText(result) || resolveText().importPackageFailed);
        }
    }
    if (ctx.usePackage) {
        const useResult = await ctx.usePackage(packageName);
        const useText = asText(useResult);
        if (useText && useText.toLowerCase().includes("error")) {
            throw new Error(useText);
        }
    }
}
async function saveEnvValues(ctx, values) {
    if (ctx.setEnvs) {
        await ctx.setEnvs(values);
        return;
    }
    const keys = Object.keys(values);
    for (let i = 0; i < keys.length; i += 1) {
        const key = keys[i];
        await ctx.setEnv(key, values[key]);
    }
}
function Screen(ctx) {
    const text = resolveText();
    const hostState = useStateValue(ctx, "host", ctx.getEnv(ENV_KEYS.host) || "");
    const portState = useStateValue(ctx, "port", ctx.getEnv(ENV_KEYS.port) || "22");
    const usernameState = useStateValue(ctx, "username", ctx.getEnv(ENV_KEYS.username) || "");
    const passwordState = useStateValue(ctx, "password", ctx.getEnv(ENV_KEYS.password) || "");
    const privateKeyPathState = useStateValue(ctx, "privateKeyPath", ctx.getEnv(ENV_KEYS.privateKeyPath) || "");
    const timeoutMsState = useStateValue(ctx, "timeoutMs", ctx.getEnv(ENV_KEYS.timeoutMs) || "20000");
    const tmuxCommandState = useStateValue(ctx, "tmuxCommand", "");
    const tmuxTabsState = useStateValue(ctx, "tmuxTabs", []);
    const selectedTmuxTabState = useStateValue(ctx, "selectedTmuxTab", "");
    const tmuxPreviewState = useStateValue(ctx, "tmuxPreview", text.tmuxPreviewTapHint);
    const tmuxLinesState = useStateValue(ctx, "tmuxLines", "300");
    const busyState = useStateValue(ctx, "busy", false);
    const statusState = useStateValue(ctx, "status", text.statusIdle);
    const outputState = useStateValue(ctx, "output", "");
    const hasInitializedState = useStateValue(ctx, "hasInitialized", false);
    const getConnectionParams = () => {
        const params = {
            host: hostState.value.trim(),
            username: usernameState.value.trim(),
            password: passwordState.value,
            private_key_path: privateKeyPathState.value.trim()
        };
        const parsedPort = parseOptionalPositiveInt(portState.value);
        const parsedTimeout = parseOptionalPositiveInt(timeoutMsState.value);
        if (parsedPort !== undefined) {
            params.port = parsedPort;
        }
        if (parsedTimeout !== undefined) {
            params.timeout_ms = parsedTimeout;
        }
        return params;
    };
    const saveCurrentConfigToEnv = async () => {
        await saveEnvValues(ctx, {
            [ENV_KEYS.host]: hostState.value.trim(),
            [ENV_KEYS.port]: portState.value.trim(),
            [ENV_KEYS.username]: usernameState.value.trim(),
            [ENV_KEYS.password]: passwordState.value,
            [ENV_KEYS.privateKeyPath]: privateKeyPathState.value.trim(),
            [ENV_KEYS.timeoutMs]: timeoutMsState.value.trim()
        });
    };
    const callLinuxTool = async (toolName, params) => {
        const packageName = resolveRuntimePackageName(ctx, LINUX_SSH_PACKAGE_NAME);
        await ensureImportedAndUsed(ctx, packageName);
        const resolved = await resolveToolName(ctx, packageName, toolName);
        const candidates = [
            resolved,
            `${packageName}:${toolName}`,
            `${LINUX_SSH_PACKAGE_NAME}:${toolName}`
        ].filter((item, index, arr) => !!item && arr.indexOf(item) === index);
        let lastError = "";
        for (let i = 0; i < candidates.length; i += 1) {
            try {
                return await ctx.callTool(candidates[i], params);
            }
            catch (error) {
                lastError = toErrorText(error);
            }
        }
        throw new Error(lastError || `${text.toolCallFailedPrefix}${toolName}`);
    };
    const runAction = async (title, action) => {
        if (busyState.value) {
            return;
        }
        busyState.set(true);
        statusState.set(`${title}...`);
        try {
            await action();
            outputState.set("");
            statusState.set(`${title}${text.actionCompletedSuffix}`);
        }
        catch (error) {
            statusState.set(`${title}${text.actionFailedSuffix}`);
            outputState.set(toErrorText(error));
        }
        finally {
            busyState.set(false);
        }
    };
    const saveConfigAction = async () => {
        await runAction(text.actionSaveConfig, async () => {
            const params = getConnectionParams();
            if (!params.host || !params.username) {
                throw new Error(text.errorHostUsernameRequired);
            }
            await saveCurrentConfigToEnv();
            return await callLinuxTool("linux_ssh_configure", params);
        });
    };
    const captureTmuxWindow = async (windowName) => {
        return await callLinuxTool("linux_ssh_tmux_capture", {
            ...getConnectionParams(),
            window_name: windowName,
            max_lines: parseOptionalPositiveInt(tmuxLinesState.value) || 300
        });
    };
    const refreshTmuxTabsAction = async () => {
        await runAction(text.actionSyncTmuxSession, async () => {
            await saveCurrentConfigToEnv();
            const listResult = await callLinuxTool("linux_ssh_tmux_list_windows", getConnectionParams());
            const tabs = parseTmuxTabsFromListResult(listResult);
            tmuxTabsState.set(tabs);
            const listRecord = parseToolRecord(listResult);
            const sessionExists = listRecord.sessionExists !== false;
            const current = selectedTmuxTabState.value;
            const nextSelected = tabs.some((tab) => tab.windowName === current)
                ? current
                : (tabs[0]?.windowName || "");
            selectedTmuxTabState.set(nextSelected);
            if (!nextSelected) {
                tmuxPreviewState.set(sessionExists
                    ? text.tmuxSessionNoWindowsAfterCommand
                    : text.tmuxSessionNotFoundAutoCreate);
                return { listResult, tabs };
            }
            const captureResult = await captureTmuxWindow(nextSelected);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            return { listResult, captureResult, tabs };
        });
    };
    const createTmuxTabAction = async () => {
        await runAction(text.actionCreateTmuxTab, async () => {
            await saveCurrentConfigToEnv();
            const listResult = await callLinuxTool("linux_ssh_tmux_list_windows", getConnectionParams());
            const tabs = parseTmuxTabsFromListResult(listResult);
            const windowName = getNextTaskWindowName(tabs);
            const createResult = await callLinuxTool("linux_ssh_tmux_input", {
                ...getConnectionParams(),
                window_name: windowName,
                control: "enter"
            });
            selectedTmuxTabState.set(windowName);
            const listAfterCreateResult = await callLinuxTool("linux_ssh_tmux_list_windows", getConnectionParams());
            tmuxTabsState.set(parseTmuxTabsFromListResult(listAfterCreateResult));
            const captureResult = await captureTmuxWindow(windowName);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            return { listResult, createResult, listAfterCreateResult, captureResult };
        });
    };
    const deleteTmuxTabAction = async () => {
        await runAction(text.actionDeleteCurrentTab, async () => {
            const windowName = selectedTmuxTabState.value.trim();
            if (!windowName) {
                throw new Error(text.errorDeleteTabRequired);
            }
            await saveCurrentConfigToEnv();
            const removeResult = await callLinuxTool("linux_ssh_tmux_close", {
                ...getConnectionParams(),
                window_name: windowName
            });
            const listAfterDeleteResult = await callLinuxTool("linux_ssh_tmux_list_windows", getConnectionParams());
            const tabs = parseTmuxTabsFromListResult(listAfterDeleteResult);
            tmuxTabsState.set(tabs);
            const nextSelected = tabs[0]?.windowName || "";
            selectedTmuxTabState.set(nextSelected);
            if (!nextSelected) {
                const listRecord = parseToolRecord(listAfterDeleteResult);
                const sessionExists = listRecord.sessionExists !== false;
                tmuxPreviewState.set(sessionExists
                    ? text.tmuxSessionNoWindowsCreateBelow
                    : text.tmuxSessionNotFoundAutoCreate);
                tmuxCommandState.set("");
                return { removeResult, listAfterDeleteResult, tabs };
            }
            const captureResult = await captureTmuxWindow(nextSelected);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            tmuxCommandState.set("");
            return { removeResult, listAfterDeleteResult, captureResult, tabs };
        });
    };
    const selectTmuxTabAction = async (windowName) => {
        if (!windowName) {
            return;
        }
        selectedTmuxTabState.set(windowName);
        await runAction(text.actionLoadTmuxWindow, async () => {
            await saveCurrentConfigToEnv();
            const captureResult = await captureTmuxWindow(windowName);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            return captureResult;
        });
    };
    const refreshSelectedTmuxWindowAction = async () => {
        const windowName = selectedTmuxTabState.value.trim();
        if (!windowName) {
            await runAction(text.actionRefreshTmuxPreview, async () => {
                throw new Error(text.errorSelectTmuxTabFirst);
            });
            return;
        }
        await runAction(text.actionRefreshTmuxPreview, async () => {
            await saveCurrentConfigToEnv();
            const captureResult = await captureTmuxWindow(windowName);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            return captureResult;
        });
    };
    const runTmuxCommandAction = async () => {
        await runAction(text.actionRunTmuxCommand, async () => {
            const selectedWindow = selectedTmuxTabState.value.trim();
            const windowName = selectedWindow || tmuxTabsState.value[0]?.windowName || getNextTaskWindowName(tmuxTabsState.value);
            const command = tmuxCommandState.value.trim();
            if (!command) {
                throw new Error(text.errorEnterCommandFirst);
            }
            await saveCurrentConfigToEnv();
            if (!selectedWindow) {
                selectedTmuxTabState.set(windowName);
            }
            const runResult = await callLinuxTool("linux_ssh_tmux_input", {
                ...getConnectionParams(),
                window_name: windowName,
                input: command,
                control: "enter"
            });
            const captureResult = await captureTmuxWindow(windowName);
            const previewText = getToolOutputText(captureResult).trim();
            tmuxPreviewState.set(previewText || text.tmuxWindowNoOutput);
            tmuxCommandState.set("");
            return { runResult, captureResult };
        });
    };
    const configExpandedState = useStateValue(ctx, "configExpanded", false);
    const tmuxPanelExpandedState = useStateValue(ctx, "tmuxPanelExpanded", true);
    return ctx.UI.LazyColumn({
        onLoad: async () => {
            if (!hasInitializedState.value) {
                hasInitializedState.set(true);
                statusState.set(text.statusLoadedCurrentConfig);
                await refreshTmuxTabsAction();
            }
        },
        fillMaxSize: true,
        padding: { horizontal: 16, vertical: 8 },
        spacing: 4
    }, [
        ctx.UI.Surface({
            fillMaxWidth: true,
            shape: { cornerRadius: 10 },
            containerColor: "secondaryContainer"
        }, [
            ctx.UI.Row({ padding: { horizontal: 12, vertical: 10 }, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "info", tint: "onSecondaryContainer", size: 16 }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: text.topBannerTip,
                    style: "bodySmall",
                    color: "onSecondaryContainer"
                })
            ])
        ]),
        ...(() => {
            // 配置卡片
            const configCard = ctx.UI.Card({
                fillMaxWidth: true,
                shape: { cornerRadius: 12 },
                containerColor: "surface",
                border: { width: 0.7, color: "outlineVariant", alpha: 0.5 },
                elevation: 1
            }, [
                ctx.UI.Column({ padding: { horizontal: 16, vertical: 12 }, spacing: configExpandedState.value ? 12 : 0 }, [
                    // 标题行
                    ctx.UI.Row({
                        fillMaxWidth: true,
                        horizontalArrangement: "spaceBetween",
                        verticalAlignment: "center",
                        onClick: () => configExpandedState.set(!configExpandedState.value)
                    }, [
                        ctx.UI.Row({ verticalAlignment: "center", weight: 1 }, [
                            ctx.UI.Icon({ name: "settings", tint: "primary", size: 24 }),
                            ctx.UI.Spacer({ width: 8 }),
                            ctx.UI.Text({
                                text: text.connectionConfigTitle,
                                style: "titleMedium",
                                fontWeight: "bold",
                                color: "onSurface"
                            })
                        ]),
                        ctx.UI.Icon({
                            name: configExpandedState.value ? "expandLess" : "expandMore",
                            tint: "onSurfaceVariant"
                        })
                    ]),
                    // 可折叠内容
                    configExpandedState.value ? ctx.UI.Column({ spacing: 12 }, [
                        // 信息横幅
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 8 },
                            containerColor: "tertiaryContainer"
                        }, [
                            ctx.UI.Row({ padding: 12, verticalAlignment: "center" }, [
                                ctx.UI.Icon({ name: "info", tint: "tertiary", size: 18 }),
                                ctx.UI.Spacer({ width: 8 }),
                                ctx.UI.Text({
                                    text: text.connectionConfigTip,
                                    style: "bodySmall",
                                    color: "onTertiaryContainer"
                                })
                            ])
                        ]),
                        // 输入字段
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 10 },
                            containerColor: "surfaceVariant",
                            alpha: 0.35
                        }, [
                            ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                ctx.UI.Text({
                                    text: text.fieldHostLabel,
                                    style: "bodyMedium",
                                    fontWeight: "medium"
                                }),
                                ctx.UI.Text({
                                    text: text.fieldHostDesc,
                                    style: "bodySmall",
                                    color: "onSurfaceVariant"
                                }),
                                ctx.UI.Spacer({ height: 4 }),
                                ctx.UI.Surface({
                                    fillMaxWidth: true,
                                    shape: { cornerRadius: 6 },
                                    containerColor: "surfaceVariant",
                                    alpha: 0.65
                                }, [
                                    ctx.UI.TextField({
                                        value: hostState.value,
                                        onValueChange: hostState.set,
                                        singleLine: true,
                                        placeholder: text.fieldHostPlaceholder,
                                        style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                    })
                                ])
                            ])
                        ]),
                        ctx.UI.Row({ spacing: 8 }, [
                            ctx.UI.Surface({
                                weight: 1,
                                shape: { cornerRadius: 10 },
                                containerColor: "surfaceVariant",
                                alpha: 0.35
                            }, [
                                ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                    ctx.UI.Text({ text: text.fieldPortLabel, style: "bodyMedium", fontWeight: "medium" }),
                                    ctx.UI.Text({ text: text.fieldPortDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                    ctx.UI.Spacer({ height: 4 }),
                                    ctx.UI.Surface({
                                        fillMaxWidth: true,
                                        shape: { cornerRadius: 6 },
                                        containerColor: "surfaceVariant",
                                        alpha: 0.65
                                    }, [
                                        ctx.UI.TextField({
                                            value: portState.value,
                                            onValueChange: portState.set,
                                            singleLine: true,
                                            placeholder: "22",
                                            style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                        })
                                    ])
                                ])
                            ]),
                            ctx.UI.Surface({
                                weight: 2,
                                shape: { cornerRadius: 10 },
                                containerColor: "surfaceVariant",
                                alpha: 0.35
                            }, [
                                ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                    ctx.UI.Text({ text: text.fieldUsernameLabel, style: "bodyMedium", fontWeight: "medium" }),
                                    ctx.UI.Text({ text: text.fieldUsernameDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                    ctx.UI.Spacer({ height: 4 }),
                                    ctx.UI.Surface({
                                        fillMaxWidth: true,
                                        shape: { cornerRadius: 6 },
                                        containerColor: "surfaceVariant",
                                        alpha: 0.65
                                    }, [
                                        ctx.UI.TextField({
                                            value: usernameState.value,
                                            onValueChange: usernameState.set,
                                            singleLine: true,
                                            placeholder: "root",
                                            style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                        })
                                    ])
                                ])
                            ])
                        ]),
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 10 },
                            containerColor: "surfaceVariant",
                            alpha: 0.35
                        }, [
                            ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                ctx.UI.Text({ text: text.fieldPasswordLabel, style: "bodyMedium", fontWeight: "medium" }),
                                ctx.UI.Text({ text: text.fieldPasswordDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                ctx.UI.Spacer({ height: 4 }),
                                ctx.UI.Surface({
                                    fillMaxWidth: true,
                                    shape: { cornerRadius: 6 },
                                    containerColor: "surfaceVariant",
                                    alpha: 0.65
                                }, [
                                    ctx.UI.TextField({
                                        value: passwordState.value,
                                        onValueChange: passwordState.set,
                                        singleLine: true,
                                        isPassword: true,
                                        style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                    })
                                ])
                            ])
                        ]),
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 10 },
                            containerColor: "surfaceVariant",
                            alpha: 0.35
                        }, [
                            ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                ctx.UI.Text({ text: text.fieldPrivateKeyLabel, style: "bodyMedium", fontWeight: "medium" }),
                                ctx.UI.Text({ text: text.fieldPrivateKeyDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                ctx.UI.Spacer({ height: 4 }),
                                ctx.UI.Surface({
                                    fillMaxWidth: true,
                                    shape: { cornerRadius: 6 },
                                    containerColor: "surfaceVariant",
                                    alpha: 0.65
                                }, [
                                    ctx.UI.TextField({
                                        value: privateKeyPathState.value,
                                        onValueChange: privateKeyPathState.set,
                                        singleLine: true,
                                        placeholder: "/path/to/id_rsa",
                                        style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                    })
                                ])
                            ])
                        ]),
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 10 },
                            containerColor: "surfaceVariant",
                            alpha: 0.35
                        }, [
                            ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                ctx.UI.Text({ text: text.fieldTimeoutLabel, style: "bodyMedium", fontWeight: "medium" }),
                                ctx.UI.Text({ text: text.fieldTimeoutDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                ctx.UI.Spacer({ height: 4 }),
                                ctx.UI.Surface({
                                    fillMaxWidth: true,
                                    shape: { cornerRadius: 6 },
                                    containerColor: "surfaceVariant",
                                    alpha: 0.65
                                }, [
                                    ctx.UI.TextField({
                                        value: timeoutMsState.value,
                                        onValueChange: timeoutMsState.set,
                                        singleLine: true,
                                        style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                    })
                                ])
                            ])
                        ]),
                        ctx.UI.Button({
                            text: text.saveConfigButton,
                            enabled: !busyState.value,
                            onClick: saveConfigAction,
                            fillMaxWidth: true,
                            shape: { cornerRadius: 8 }
                        })
                    ]) : ctx.UI.Spacer({ height: 0 })
                ])
            ]);
            const tmuxPreviewLinesRaw = (tmuxPreviewState.value || text.tmuxPreviewTapHint).replace(/\r/g, "").split("\n");
            const tmuxPreviewLines = tmuxPreviewLinesRaw.length > 0 ? tmuxPreviewLinesRaw : [text.tmuxPreviewTapHint];
            const tmuxPreviewHeight = Math.min(280, Math.max(120, tmuxPreviewLines.length * 18 + 16));
            // tmux 配置卡片
            const tmuxCard = ctx.UI.Card({
                fillMaxWidth: true,
                shape: { cornerRadius: 12 },
                containerColor: "surface",
                elevation: 1
            }, [
                ctx.UI.Column({ padding: 16, spacing: tmuxPanelExpandedState.value ? 12 : 0 }, [
                    ctx.UI.Row({
                        fillMaxWidth: true,
                        horizontalArrangement: "spaceBetween",
                        verticalAlignment: "center",
                        onClick: () => tmuxPanelExpandedState.set(!tmuxPanelExpandedState.value)
                    }, [
                        ctx.UI.Row({ verticalAlignment: "center", weight: 1 }, [
                            ctx.UI.Icon({ name: "terminal", tint: "primary", size: 24 }),
                            ctx.UI.Spacer({ width: 8 }),
                            ctx.UI.Text({
                                text: text.tmuxConfigTitle,
                                style: "titleMedium",
                                fontWeight: "bold"
                            })
                        ]),
                        ctx.UI.Icon({
                            name: tmuxPanelExpandedState.value ? "expandLess" : "expandMore",
                            tint: "onSurfaceVariant"
                        })
                    ]),
                    tmuxPanelExpandedState.value ? ctx.UI.Column({ spacing: 12 }, [
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 8 },
                            containerColor: "surfaceVariant",
                            alpha: 0.35
                        }, [
                            ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                ctx.UI.Row({
                                    fillMaxWidth: true,
                                    horizontalArrangement: "spaceBetween",
                                    verticalAlignment: "center"
                                }, [
                                    ctx.UI.Text({
                                        text: text.tmuxSessionTabsTitle,
                                        style: "bodyLarge",
                                        fontWeight: "semiBold",
                                        paddingVertical: 2
                                    }),
                                    ctx.UI.Row({ spacing: 1, verticalAlignment: "center" }, [
                                        ctx.UI.IconButton({
                                            icon: "delete",
                                            enabled: !busyState.value,
                                            width: 26,
                                            height: 26,
                                            padding: 0,
                                            onClick: deleteTmuxTabAction
                                        }),
                                        ctx.UI.IconButton({
                                            icon: "sync",
                                            enabled: !busyState.value,
                                            width: 26,
                                            height: 26,
                                            padding: 0,
                                            onClick: refreshTmuxTabsAction
                                        }),
                                        ctx.UI.IconButton({
                                            icon: "add",
                                            enabled: !busyState.value,
                                            width: 26,
                                            height: 26,
                                            padding: 0,
                                            onClick: createTmuxTabAction
                                        })
                                    ])
                                ]),
                                tmuxTabsState.value.length > 0
                                    ? ctx.UI.LazyRow({ spacing: 8, fillMaxWidth: true }, tmuxTabsState.value.map((tab) => ctx.UI.Surface({
                                        shape: { cornerRadius: 999 },
                                        containerColor: tab.windowName === selectedTmuxTabState.value ? "primary" : "surfaceVariant",
                                        alpha: tab.windowName === selectedTmuxTabState.value ? 1 : 0.6
                                    }, [
                                        ctx.UI.Row({
                                            onClick: () => selectTmuxTabAction(tab.windowName),
                                            padding: { horizontal: 12, vertical: 8 },
                                            verticalAlignment: "center"
                                        }, [
                                            ctx.UI.Text({
                                                text: tab.label,
                                                style: "labelLarge",
                                                color: tab.windowName === selectedTmuxTabState.value ? "onPrimary" : "onSurface"
                                            })
                                        ])
                                    ])))
                                    : ctx.UI.Text({
                                        text: text.tmuxNoWindowsCreateCommand,
                                        style: "bodySmall",
                                        color: "onSurfaceVariant"
                                    })
                            ])
                        ]),
                        ctx.UI.Surface({
                            fillMaxWidth: true,
                            shape: { cornerRadius: 10 },
                            containerColor: "#0B0F14"
                        }, [
                            ctx.UI.Column({ spacing: 0 }, [
                                ctx.UI.Row({
                                    fillMaxWidth: true,
                                    horizontalArrangement: "spaceBetween",
                                    verticalAlignment: "center",
                                    padding: { horizontal: 10, vertical: 6 }
                                }, [
                                    ctx.UI.Row({ verticalAlignment: "center", spacing: 6, weight: 1 }, [
                                        ctx.UI.Text({
                                            text: "● ● ●",
                                            style: "labelSmall",
                                            color: "#6E7B8A"
                                        }),
                                        ctx.UI.Text({
                                            text: selectedTmuxTabState.value
                                                ? `tmux://${DEFAULT_TMUX_SESSION_NAME}/${selectedTmuxTabState.value}`
                                                : `tmux://${DEFAULT_TMUX_SESSION_NAME}/no-window`,
                                            style: "labelSmall",
                                            color: "#9EE6FF",
                                            maxLines: 1
                                        })
                                    ]),
                                    ctx.UI.Row({
                                        width: 16,
                                        height: 16,
                                        verticalAlignment: "center",
                                        onClick: busyState.value ? undefined : refreshSelectedTmuxWindowAction
                                    }, [
                                        ctx.UI.Icon({
                                            name: "refresh",
                                            tint: "#FFFFFF",
                                            size: 16
                                        })
                                    ]),
                                ]),
                                ctx.UI.Surface({
                                    fillMaxWidth: true,
                                    containerColor: "#05070A"
                                }, [
                                    ctx.UI.LazyColumn({
                                        fillMaxWidth: true,
                                        height: tmuxPreviewHeight,
                                        autoScrollToEnd: true,
                                        spacing: 2,
                                        padding: { horizontal: 10, vertical: 8 }
                                    }, tmuxPreviewLines.map((line, index) => ctx.UI.Text({
                                        key: `tmux-preview-line-${index}`,
                                        text: line || " ",
                                        style: "bodySmall",
                                        color: "#C6F6D5"
                                    })))
                                ]),
                                ctx.UI.Text({
                                    text: text.tmuxRefreshHint,
                                    style: "labelSmall",
                                    color: "#6E7B8A",
                                    padding: { horizontal: 10, vertical: 4 }
                                })
                            ])
                        ]),
                        ctx.UI.Row({ spacing: 8 }, [
                            ctx.UI.Surface({
                                weight: 1,
                                shape: { cornerRadius: 10 },
                                containerColor: "surfaceVariant",
                                alpha: 0.35
                            }, [
                                ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                    ctx.UI.Text({ text: text.tmuxCommandInputLabel, style: "bodyMedium", fontWeight: "medium" }),
                                    ctx.UI.Text({ text: text.tmuxCommandInputDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                    ctx.UI.Spacer({ height: 4 }),
                                    ctx.UI.Surface({
                                        fillMaxWidth: true,
                                        shape: { cornerRadius: 6 },
                                        containerColor: "surfaceVariant",
                                        alpha: 0.65
                                    }, [
                                        ctx.UI.TextField({
                                            value: tmuxCommandState.value,
                                            onValueChange: tmuxCommandState.set,
                                            placeholder: text.tmuxCommandPlaceholder,
                                            minLines: 2,
                                            style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                        })
                                    ])
                                ])
                            ]),
                            ctx.UI.Surface({
                                width: 120,
                                shape: { cornerRadius: 10 },
                                containerColor: "surfaceVariant",
                                alpha: 0.35
                            }, [
                                ctx.UI.Column({ padding: 12, spacing: 8 }, [
                                    ctx.UI.Text({ text: text.tmuxPreviewLinesLabel, style: "bodyMedium", fontWeight: "medium" }),
                                    ctx.UI.Text({ text: text.tmuxPreviewLinesDesc, style: "bodySmall", color: "onSurfaceVariant" }),
                                    ctx.UI.Spacer({ height: 4 }),
                                    ctx.UI.Surface({
                                        fillMaxWidth: true,
                                        shape: { cornerRadius: 6 },
                                        containerColor: "surfaceVariant",
                                        alpha: 0.65
                                    }, [
                                        ctx.UI.TextField({
                                            value: tmuxLinesState.value,
                                            onValueChange: tmuxLinesState.set,
                                            singleLine: true,
                                            style: { fontSize: 14, fontWeight: "semiBold", color: "primary" }
                                        })
                                    ])
                                ])
                            ])
                        ]),
                        ctx.UI.Button({
                            text: text.tmuxSendCurrentTabButton,
                            enabled: !busyState.value,
                            onClick: runTmuxCommandAction,
                            fillMaxWidth: true,
                            shape: { cornerRadius: 8 }
                        })
                    ]) : ctx.UI.Spacer({ height: 0 })
                ])
            ]);
            return [tmuxCard, configCard];
        })(),
        // 状态卡片
        ctx.UI.Card({
            fillMaxWidth: true,
            shape: { cornerRadius: 12 },
            containerColor: "primaryContainer",
            elevation: 1
        }, [
            ctx.UI.Column({
                padding: 16,
                spacing: busyState.value || !!outputState.value ? 8 : 0
            }, (() => {
                const children = [
                    ctx.UI.Row({ verticalAlignment: "center" }, [
                        ctx.UI.Icon({
                            name: busyState.value ? "sync" : "checkCircle",
                            tint: "onPrimaryContainer"
                        }),
                        ctx.UI.Spacer({ width: 8 }),
                        ctx.UI.Text({
                            text: statusState.value,
                            style: "titleMedium",
                            fontWeight: "semiBold",
                            color: "onPrimaryContainer"
                        })
                    ])
                ];
                if (busyState.value) {
                    children.push(ctx.UI.LinearProgressIndicator({ fillMaxWidth: true }));
                }
                if (outputState.value) {
                    children.push(ctx.UI.Surface({
                        fillMaxWidth: true,
                        shape: { cornerRadius: 8 },
                        containerColor: "surface",
                        alpha: 0.6
                    }, [
                        ctx.UI.Column({ padding: 12 }, [
                            ctx.UI.Text({
                                text: outputState.value,
                                style: "bodySmall",
                                color: "onPrimaryContainer"
                            })
                        ])
                    ]));
                }
                return children;
            })())
        ])
    ]);
}
