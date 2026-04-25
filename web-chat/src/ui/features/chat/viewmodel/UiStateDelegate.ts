import { ApiError } from '../util/chatApi';
import type { ContextStatsSnapshot, WebChatMessage, WebChatSummary } from '../util/chatTypes';

const CONTEXT_WINDOW_MAX = 16_000;

export function normalizeError(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }
  return '发生未知错误';
}

export function buildVisibleChats(chats: WebChatSummary[], search: string) {
  const query = search.trim().toLowerCase();
  if (!query) {
    return chats;
  }

  return chats.filter((chat) => {
    const title = chat.title.toLowerCase();
    const group = (chat.group ?? '').toLowerCase();
    return title.includes(query) || group.includes(query);
  });
}

export function buildContextStats(
  messages: WebChatMessage[],
  messageInput: string
): ContextStatsSnapshot {
  const currentValue =
    messages.reduce((sum, message) => sum + message.content_raw.length, 0) + messageInput.length;
  const maxValue = CONTEXT_WINDOW_MAX;
  const percent = Math.max(0, Math.min(99, Math.round((currentValue / maxValue) * 100)));

  return {
    currentValue,
    maxValue,
    percent
  };
}

export function handleUnauthorizedError(error: unknown, clearToken: () => void) {
  if (error instanceof ApiError && error.status === 401) {
    clearToken();
    return true;
  }
  return false;
}
