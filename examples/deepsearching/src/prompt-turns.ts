import type { JavaBridgeValue } from "../../types/java-bridge";

const PromptTurnKindClass = Java.type("com.ai.assistance.operit.core.chat.hooks.PromptTurnKind");

export type PromptTurn = ToolPkg.PromptTurn;
export type PromptTurnKind = ToolPkg.PromptTurnKind;

export function createPromptTurn(
  kind: PromptTurnKind,
  content: string,
  toolName?: string | null,
  metadata?: ToolPkg.JsonObject
): PromptTurn {
  const turn: PromptTurn = {
    kind,
    content: String(content ?? ""),
  };
  if (typeof toolName === "string" && toolName.length > 0) {
    turn.toolName = toolName;
  }
  if (metadata && typeof metadata === "object" && !Array.isArray(metadata)) {
    turn.metadata = metadata;
  }
  return turn;
}

export function normalizePromptTurnList(value: unknown): PromptTurn[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const turns: PromptTurn[] = [];
  for (const item of value) {
    if (!item || typeof item !== "object") {
      continue;
    }

    const record = item as Partial<PromptTurn>;
    const kind = normalizePromptTurnKind(record.kind);
    if (!kind) {
      continue;
    }

    turns.push(
      createPromptTurn(
        kind,
        String(record.content ?? ""),
        typeof record.toolName === "string" ? record.toolName : undefined,
        isJsonObject(record.metadata) ? record.metadata : undefined
      )
    );
  }
  return turns;
}

export function toKotlinPromptTurnList(history: PromptTurn[]): JavaBridgeValue {
  return (history || []).map((turn) =>
    Java.newInstance(
      "com.ai.assistance.operit.core.chat.hooks.PromptTurn",
      resolvePromptTurnKind(turn.kind),
      String(turn.content ?? ""),
      typeof turn.toolName === "string" ? turn.toolName : null,
      toJavaJsonObject(turn.metadata)
    )
  );
}

function normalizePromptTurnKind(kind: unknown): PromptTurnKind | null {
  const normalized = String(kind ?? "").trim().toUpperCase();
  switch (normalized) {
    case "SYSTEM":
    case "USER":
    case "ASSISTANT":
    case "TOOL_CALL":
    case "TOOL_RESULT":
    case "SUMMARY":
      return normalized;
    default:
      return null;
  }
}

function resolvePromptTurnKind(kind: PromptTurnKind): JavaBridgeValue {
  switch (kind) {
    case "SYSTEM":
      return PromptTurnKindClass.SYSTEM;
    case "ASSISTANT":
      return PromptTurnKindClass.ASSISTANT;
    case "TOOL_CALL":
      return PromptTurnKindClass.TOOL_CALL;
    case "TOOL_RESULT":
      return PromptTurnKindClass.TOOL_RESULT;
    case "SUMMARY":
      return PromptTurnKindClass.SUMMARY;
    case "USER":
    default:
      return PromptTurnKindClass.USER;
  }
}

function isJsonObject(value: unknown): value is ToolPkg.JsonObject {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function toJavaJsonObject(value: ToolPkg.JsonObject | undefined): JavaBridgeValue {
  if (!value) {
    return {};
  }
  const map: Record<string, ToolPkg.JsonValue | null> = {};
  for (const [key, item] of Object.entries(value)) {
    map[String(key)] = toJavaValue(item) as ToolPkg.JsonValue | null;
  }
  return map;
}

function toJavaValue(value: ToolPkg.JsonValue | undefined): JavaBridgeValue {
  if (value === undefined || value === null) {
    return null;
  }
  if (Array.isArray(value)) {
    return value.map((item) => toJavaValue(item));
  }
  if (typeof value === "object") {
    return toJavaJsonObject(value as ToolPkg.JsonObject);
  }
  return value;
}
