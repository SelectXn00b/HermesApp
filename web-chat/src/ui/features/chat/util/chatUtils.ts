import type { CSSProperties } from 'react';
import type { WebChatMessage, WebMessageAttachment, WebThemeSnapshot } from './chatTypes';

export function formatTime(timestamp: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(timestamp);
}

export function estimateTokenCount(text: string) {
  return Math.max(0, Math.round(text.trim().length / 4));
}

function normalizeText(value: string | null | undefined) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function assistantHeaderName(message: WebChatMessage, theme: WebThemeSnapshot | null) {
  if (!theme?.display.show_role_name) {
    return '';
  }
  return normalizeText(message.role_name) ?? '';
}

export function assistantHeaderMeta(message: WebChatMessage, theme: WebThemeSnapshot | null) {
  const showModelName = theme?.display.show_model_name ?? false;
  const showModelProvider = theme?.display.show_model_provider ?? false;
  const modelName = showModelName ? normalizeText(message.model_name) : null;
  const provider = showModelProvider ? normalizeText(message.provider) : null;

  if (modelName && provider) {
    return `${modelName} by ${provider}`;
  }
  return modelName ?? provider ?? '';
}

export function assistantCompactMeta(message: WebChatMessage, theme: WebThemeSnapshot | null) {
  const parts = [
    theme?.display.show_role_name ? normalizeText(message.role_name) : null,
    theme?.display.show_model_name ? normalizeText(message.model_name) : null
  ].filter((value): value is string => Boolean(value));
  const provider =
    theme?.display.show_model_provider ? normalizeText(message.provider) : null;
  if (provider) {
    if (parts.length && theme?.display.show_model_name && normalizeText(message.model_name)) {
      parts[parts.length - 1] = `${parts[parts.length - 1]} by ${provider}`;
    } else {
      parts.push(provider);
    }
  }
  return parts.join(' | ');
}

export function effectiveUserDisplayName(
  message: WebChatMessage,
  theme: WebThemeSnapshot | null
) {
  const proxyName = normalizeText(message.display_name);
  if (proxyName) {
    return proxyName;
  }
  if (theme?.display.show_user_name) {
    return normalizeText(theme.display.global_user_name) ?? '';
  }
  return '';
}

export function effectiveUserDisplayContent(message: WebChatMessage) {
  return message.display_content ?? message.content_raw;
}

export function isAssistantMessage(message: WebChatMessage) {
  return message.sender === 'assistant';
}

export function isUserMessage(message: WebChatMessage) {
  return message.sender === 'user';
}

export function isSummaryMessage(message: WebChatMessage) {
  return message.sender === 'summary' || message.sender === 'system';
}

export function isImageAttachment(attachment: WebMessageAttachment) {
  return attachment.mime_type.startsWith('image/');
}

export function formatFileSize(size?: number | null) {
  if (typeof size !== 'number' || Number.isNaN(size) || size <= 0) {
    return '';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export function bubbleImageStyle(
  theme: WebThemeSnapshot | null,
  sender: 'assistant' | 'user'
): CSSProperties | undefined {
  if (!theme) {
    return undefined;
  }

  const imageTheme = sender === 'user' ? theme.bubble.user_image : theme.bubble.assistant_image;
  if (!imageTheme.enabled || !imageTheme.asset_url) {
    return undefined;
  }

  return {
    backgroundImage: `linear-gradient(180deg, rgba(10, 12, 20, 0.06), rgba(10, 12, 20, 0.12)), url(${imageTheme.asset_url})`,
    backgroundSize: imageTheme.render_mode === 'repeat' ? 'auto' : 'cover',
    backgroundRepeat: imageTheme.render_mode === 'repeat' ? 'repeat' : 'no-repeat',
    backgroundPosition: 'center'
  };
}
