import type { ComposeDslContext, ComposeNode } from "../../../../types/compose-dsl";
import { resolveRemoteOperitSetupI18n, type RemoteOperitSetupI18n } from "../../i18n";

const REMOTE_OPERIT_PACKAGE_NAME = "remote_operit";
const REMOTE_OPERIT_TOOL_CONFIGURE = "remote_operit_configure";
const REMOTE_OPERIT_TOOL_TEST_CONNECTION = "remote_operit_test_connection";

const KEY_BASE_URL = "REMOTE_OPERIT_BASE_URL";
const KEY_TOKEN = "REMOTE_OPERIT_TOKEN";
const KEY_TIMEOUT_MS = "REMOTE_OPERIT_TIMEOUT_MS";

type ConnectionCardStatus = "idle" | "checking" | "notConfigured" | "success" | "failed";

interface ConnectionCardModel {
  status: ConnectionCardStatus;
  baseUrl: string;
  packageVersion: string;
  versionName: string;
  port: string;
  enabled: string;
  serviceRunning: string;
  error: string;
}

function resolveText(): RemoteOperitSetupI18n {
  const rawLocale = getLang();
  const locale = String(rawLocale || "").trim().toLowerCase();
  const preferredLocale = locale.startsWith("en") ? "en-US" : "zh-CN";
  return resolveRemoteOperitSetupI18n(preferredLocale);
}

function useStateValue<T>(ctx: ComposeDslContext, key: string, initialValue: T): {
  value: T;
  set: (value: T) => void;
} {
  const pair = ctx.useState<T>(key, initialValue);
  return { value: pair[0], set: pair[1] };
}

function asText(value: unknown): string {
  return String(value == null ? "" : value);
}

function firstNonBlank(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

function toErrorText(error: unknown): string {
  if (error instanceof Error) {
    return error.message || "unknown";
  }
  return String(error || "unknown");
}

function parseToolRecord(result: unknown): Record<string, unknown> {
  if (result && typeof result === "object" && !Array.isArray(result)) {
    return result as Record<string, unknown>;
  }
  if (typeof result === "string") {
    const raw = result.trim();
    if (!raw) {
      return {};
    }
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      return {};
    }
  }
  return {};
}

function formatBoolean(value: unknown): string {
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  const raw = asText(value).trim().toLowerCase();
  if (!raw) {
    return "";
  }
  if (raw === "true" || raw === "1" || raw === "yes" || raw === "on") {
    return "true";
  }
  if (raw === "false" || raw === "0" || raw === "no" || raw === "off") {
    return "false";
  }
  return raw;
}

async function resolveToolName(
  ctx: ComposeDslContext,
  packageName: string,
  toolName: string
): Promise<string> {
  if (ctx.resolveToolName) {
    const resolved = await ctx.resolveToolName({ packageName, toolName, preferImported: true });
    const value = asText(resolved).trim();
    if (value) {
      return value;
    }
  }
  return `${packageName}:${toolName}`;
}

function resolveRuntimePackageName(ctx: ComposeDslContext, fallback: string): string {
  const currentPackageName = asText(ctx.getCurrentPackageName?.() || "").trim();
  const currentToolPkgId = asText(ctx.getCurrentToolPkgId?.() || "").trim();
  if (!currentPackageName) {
    return fallback;
  }
  if (currentToolPkgId && currentPackageName === currentToolPkgId) {
    return fallback;
  }
  return currentPackageName;
}

async function ensureImportedAndUsed(ctx: ComposeDslContext, packageName: string, text: RemoteOperitSetupI18n): Promise<void> {
  const imported = ctx.isPackageImported ? !!(await ctx.isPackageImported(packageName)) : false;
  if (!imported && ctx.importPackage) {
    const result = await ctx.importPackage(packageName);
    const message = asText(result).toLowerCase();
    if (message.includes("error") || message.includes("failed") || message.includes("not found")) {
      throw new Error(asText(result) || text.importPackageFailed);
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

function buildConnectionMetaRows(
  ctx: ComposeDslContext,
  text: RemoteOperitSetupI18n,
  model: ConnectionCardModel,
  contentColor: string
): ComposeNode[] {
  const rows: ComposeNode[] = [];
  const addRow = (label: string, value: string): void => {
    const content = value.trim();
    if (!content) {
      return;
    }
    rows.push(
      ctx.UI.Row({ fillMaxWidth: true, verticalAlignment: "start" }, [
        ctx.UI.Text({
          text: `${label}:`,
          style: "bodySmall",
          color: contentColor,
          fontWeight: "semiBold",
          width: 120
        }),
        ctx.UI.Text({
          text: content,
          style: "bodySmall",
          color: contentColor,
          weight: 1
        })
      ])
    );
  };

  addRow(text.connectionFieldBaseUrl, model.baseUrl);
  addRow(text.connectionFieldPackageVersion, model.packageVersion);
  addRow(text.connectionFieldVersionName, model.versionName);
  addRow(text.connectionFieldPort, model.port);
  addRow(text.connectionFieldEnabled, model.enabled);
  addRow(text.connectionFieldServiceRunning, model.serviceRunning);
  addRow(text.connectionFieldError, model.error);
  return rows;
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const text = resolveText();
  const baseUrlState = useStateValue(ctx, "baseUrl", ctx.getEnv(KEY_BASE_URL) || "");
  const tokenState = useStateValue(ctx, "token", ctx.getEnv(KEY_TOKEN) || "");
  const timeoutMsState = useStateValue(ctx, "timeoutMs", ctx.getEnv(KEY_TIMEOUT_MS) || "60000");

  const busyState = useStateValue(ctx, "busy", false);
  const busyLabelState = useStateValue(ctx, "busyLabel", "");
  const successMessageState = useStateValue(ctx, "successMessage", "");
  const errorMessageState = useStateValue(ctx, "errorMessage", "");
  const hasInitializedState = useStateValue(ctx, "hasInitialized", false);

  const connectionCardState = useStateValue<ConnectionCardModel>(ctx, "connectionCard", {
    status: "idle",
    baseUrl: "",
    packageVersion: "",
    versionName: "",
    port: "",
    enabled: "",
    serviceRunning: "",
    error: ""
  });

  const setConnectionCard = (next: ConnectionCardModel): void => {
    connectionCardState.set(next);
  };

  const callRemoteTool = async (toolName: string, params?: Record<string, unknown>): Promise<Record<string, unknown>> => {
    const packageName = resolveRuntimePackageName(ctx, REMOTE_OPERIT_PACKAGE_NAME);
    await ensureImportedAndUsed(ctx, packageName, text);

    const resolved = await resolveToolName(ctx, packageName, toolName);
    const candidates = [
      resolved,
      `${packageName}:${toolName}`,
      `${REMOTE_OPERIT_PACKAGE_NAME}:${toolName}`
    ].filter((item, index, arr) => !!item && arr.indexOf(item) === index);

    let lastError = "";
    for (let i = 0; i < candidates.length; i += 1) {
      try {
        const result = await ctx.callTool(candidates[i], params);
        return parseToolRecord(result);
      } catch (error) {
        lastError = toErrorText(error);
      }
    }

    throw new Error(lastError || `${text.toolCallFailedPrefix}${toolName}`);
  };

  const checkConnectionByTool = async (): Promise<void> => {
    const currentBaseUrl = baseUrlState.value.trim();
    const currentToken = tokenState.value.trim();
    if (!currentBaseUrl || !currentToken) {
      setConnectionCard({
        status: "notConfigured",
        baseUrl: currentBaseUrl,
        packageVersion: "",
        versionName: "",
        port: "",
        enabled: "",
        serviceRunning: "",
        error: text.packageNotEnabled
      });
      return;
    }

    setConnectionCard({
      ...connectionCardState.value,
      status: "checking",
      baseUrl: currentBaseUrl,
      error: ""
    });

    const result = await callRemoteTool(REMOTE_OPERIT_TOOL_TEST_CONNECTION, {
      timeout_ms: timeoutMsState.value.trim() || "60000"
    });

    const success = result.success !== false;
    setConnectionCard({
      status: success ? "success" : "failed",
      baseUrl: firstNonBlank(asText(result.remoteBaseUrl), currentBaseUrl),
      packageVersion: asText(result.packageVersion).trim(),
      versionName: asText(result.versionName).trim(),
      port: asText(result.port).trim(),
      enabled: formatBoolean(result.enabled),
      serviceRunning: formatBoolean(result.serviceRunning),
      error: success ? "" : firstNonBlank(asText(result.error), `HTTP ${asText(result.httpStatus).trim()}`)
    });
  };

  const saveConfig = async (shouldTest: boolean): Promise<void> => {
    const baseUrl = baseUrlState.value.trim();
    const token = tokenState.value.trim();
    const timeoutMs = timeoutMsState.value.trim();

    if (!baseUrl) {
      errorMessageState.set(text.errorBaseUrlRequired);
      return;
    }
    if (!token) {
      errorMessageState.set(text.errorTokenRequired);
      return;
    }

    busyState.set(true);
    busyLabelState.set(shouldTest ? text.checking : text.applying);
    successMessageState.set("");
    errorMessageState.set("");

    try {
      const result = await callRemoteTool(REMOTE_OPERIT_TOOL_CONFIGURE, {
        base_url: baseUrl,
        token,
        timeout_ms: timeoutMs || "60000"
      });
      if (result.success === false) {
        throw new Error(firstNonBlank(asText(result.error), text.statusErrorPrefix));
      }

      const normalizedBaseUrl = firstNonBlank(asText(result.remoteBaseUrl), baseUrl);
      const resolvedTimeout = firstNonBlank(asText(result.timeoutMs), timeoutMs || "60000");
      baseUrlState.set(normalizedBaseUrl);
      timeoutMsState.set(resolvedTimeout);
      successMessageState.set(text.statusSaved);

      if (shouldTest) {
        await checkConnectionByTool();
      } else {
        setConnectionCard({
          ...connectionCardState.value,
          status: "idle",
          baseUrl: normalizedBaseUrl,
          error: ""
        });
      }
    } catch (error) {
      errorMessageState.set(`${text.statusErrorPrefix}${toErrorText(error)}`);
    } finally {
      busyState.set(false);
      busyLabelState.set("");
    }
  };

  const statusConfigByStatus: Record<
    ConnectionCardStatus,
    { text: string; containerColor: string; contentColor: string; icon: string }
  > = {
    idle: {
      text: text.connectionStateIdle,
      containerColor: "surfaceVariant",
      contentColor: "onSurfaceVariant",
      icon: "devices"
    },
    checking: {
      text: text.connectionStateChecking,
      containerColor: "tertiaryContainer",
      contentColor: "onTertiaryContainer",
      icon: "sync"
    },
    notConfigured: {
      text: text.connectionStateNotConfigured,
      containerColor: "secondaryContainer",
      contentColor: "onSecondaryContainer",
      icon: "settings"
    },
    success: {
      text: text.connectionStateSuccess,
      containerColor: "primaryContainer",
      contentColor: "onPrimaryContainer",
      icon: "checkCircle"
    },
    failed: {
      text: text.connectionStateFailed,
      containerColor: "errorContainer",
      contentColor: "onErrorContainer",
      icon: "error"
    }
  };

  const currentStatusUi = statusConfigByStatus[connectionCardState.value.status];

  const connectionCardChildren: ComposeNode[] = [
    ctx.UI.Row({ verticalAlignment: "center" }, [
      ctx.UI.Icon({
        name: currentStatusUi.icon,
        tint: currentStatusUi.contentColor
      }),
      ctx.UI.Spacer({ width: 8 }),
      ctx.UI.Column({ spacing: 2 }, [
        ctx.UI.Text({
          text: text.connectionCardTitle,
          style: "titleMedium",
          color: currentStatusUi.contentColor,
          fontWeight: "semiBold"
        }),
        ctx.UI.Text({
          text: currentStatusUi.text,
          style: "bodySmall",
          color: currentStatusUi.contentColor
        })
      ])
    ])
  ];

  if (connectionCardState.value.status === "checking") {
    connectionCardChildren.push(
      ctx.UI.Row({ verticalAlignment: "center" }, [
        ctx.UI.CircularProgressIndicator({ width: 16, height: 16, strokeWidth: 2, color: currentStatusUi.contentColor }),
        ctx.UI.Spacer({ width: 8 }),
        ctx.UI.Text({
          text: text.checking,
          style: "bodyMedium",
          color: currentStatusUi.contentColor
        })
      ])
    );
  } else {
    connectionCardChildren.push(
      ...buildConnectionMetaRows(ctx, text, connectionCardState.value, currentStatusUi.contentColor)
    );
  }

  const rootChildren: ComposeNode[] = [
    ctx.UI.Row({ verticalAlignment: "center" }, [
      ctx.UI.Icon({
        name: "devices",
        tint: "primary"
      }),
      ctx.UI.Spacer({ width: 8 }),
      ctx.UI.Text({
        text: text.title,
        style: "headlineSmall",
        fontWeight: "bold"
      })
    ]),
    ctx.UI.Text({
      text: text.subtitle,
      style: "bodyMedium",
      color: "onSurfaceVariant"
    }),
    ctx.UI.Surface({
      fillMaxWidth: true,
      shape: { cornerRadius: 12 },
      containerColor: "secondaryContainer"
    }, [
      ctx.UI.Row({ padding: 14, verticalAlignment: "center" }, [
        ctx.UI.Icon({ name: "info", tint: "onSecondaryContainer" }),
        ctx.UI.Spacer({ width: 8 }),
        ctx.UI.Text({
          text: text.topBanner,
          style: "bodySmall",
          color: "onSecondaryContainer"
        })
      ])
    ]),
    ctx.UI.Card({
      fillMaxWidth: true,
      containerColor: currentStatusUi.containerColor,
      contentColor: currentStatusUi.contentColor
    }, [
      ctx.UI.Column({ padding: 16, spacing: 10 }, connectionCardChildren)
    ]),
    ctx.UI.Card({ fillMaxWidth: true }, [
      ctx.UI.Column({ padding: 16, spacing: 10 }, [
        ctx.UI.Row({ verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "settings", tint: "primary" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: text.configCardTitle,
            style: "titleMedium",
            fontWeight: "semiBold"
          })
        ]),
        ctx.UI.Text({
          text: text.configCardSubtitle,
          style: "bodyMedium",
          color: "onSurfaceVariant"
        }),
        ctx.UI.TextField({
          label: text.baseUrlLabel,
          placeholder: text.baseUrlPlaceholder,
          value: baseUrlState.value,
          onValueChange: baseUrlState.set,
          singleLine: true
        }),
        ctx.UI.TextField({
          label: text.tokenLabel,
          placeholder: text.tokenPlaceholder,
          value: tokenState.value,
          onValueChange: tokenState.set,
          singleLine: true,
          isPassword: true
        }),
        ctx.UI.TextField({
          label: text.timeoutLabel,
          placeholder: text.timeoutPlaceholder,
          value: timeoutMsState.value,
          onValueChange: timeoutMsState.set,
          singleLine: true
        }),
        busyState.value
          ? ctx.UI.Button({
            enabled: false,
            fillMaxWidth: true,
            onClick: () => saveConfig(false)
          }, [
            ctx.UI.Row({ verticalAlignment: "center", horizontalArrangement: "center" }, [
              ctx.UI.CircularProgressIndicator({ width: 16, height: 16, strokeWidth: 2, color: "onPrimary" }),
              ctx.UI.Spacer({ width: 8 }),
              ctx.UI.Text({ text: busyLabelState.value || text.applying })
            ])
          ])
          : ctx.UI.Button({
            text: text.applyButton,
            enabled: true,
            fillMaxWidth: true,
            onClick: () => saveConfig(false)
          }),
        busyState.value
          ? ctx.UI.Spacer({ height: 0 })
          : ctx.UI.Button({
            text: text.applyAndTestButton,
            enabled: true,
            fillMaxWidth: true,
            onClick: () => saveConfig(true)
          })
      ])
    ]),
    ctx.UI.Card({
      fillMaxWidth: true,
      containerColor: "surfaceVariant"
    }, [
      ctx.UI.Column({ padding: 16, spacing: 12 }, [
        ctx.UI.Row({ verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "bolt", tint: "primary" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: text.exampleCardTitle,
            style: "titleMedium",
            fontWeight: "semiBold"
          })
        ]),
        ctx.UI.Text({
          text: text.exampleCardSubtitle,
          style: "bodyMedium",
          color: "onSurfaceVariant"
        }),
        ctx.UI.Text({
          text: text.examplePromptTitle,
          style: "bodyMedium",
          fontWeight: "semiBold"
        }),
        ctx.UI.Text({
          text: text.examplePromptBody,
          style: "bodySmall",
          color: "onSurfaceVariant"
        }),
        ctx.UI.Surface({
          fillMaxWidth: true,
          shape: { cornerRadius: 10 },
          containerColor: "primaryContainer"
        }, [
          ctx.UI.Column({ padding: 12, spacing: 8 }, [
            ctx.UI.Text({
              text: text.exampleParamsTitle,
              style: "bodyMedium",
              fontWeight: "semiBold",
              color: "onPrimaryContainer"
            }),
            ctx.UI.Text({
              text: text.exampleParamsBody,
              style: "bodySmall",
              color: "onPrimaryContainer"
            })
          ])
        ])
      ])
    ])
  ];

  if (successMessageState.value.trim()) {
    rootChildren.push(
      ctx.UI.Card({ containerColor: "primaryContainer", fillMaxWidth: true }, [
        ctx.UI.Row({ padding: 14, verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "checkCircle", tint: "onPrimaryContainer" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: successMessageState.value,
            style: "bodyMedium",
            color: "onPrimaryContainer"
          })
        ])
      ])
    );
  }

  if (errorMessageState.value.trim()) {
    rootChildren.push(
      ctx.UI.Card({ containerColor: "errorContainer", fillMaxWidth: true }, [
        ctx.UI.Row({ padding: 14, verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "error", tint: "onErrorContainer" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: errorMessageState.value,
            style: "bodyMedium",
            color: "onErrorContainer"
          })
        ])
      ])
    );
  }

  return ctx.UI.Column(
    {
      onLoad: async () => {
        if (!hasInitializedState.value) {
          hasInitializedState.set(true);
          try {
            await checkConnectionByTool();
          } catch (error) {
            errorMessageState.set(`${text.statusErrorPrefix}${toErrorText(error)}`);
          }
        }
      },
      fillMaxSize: true,
      padding: 16,
      spacing: 16
    },
    rootChildren
  );
}
