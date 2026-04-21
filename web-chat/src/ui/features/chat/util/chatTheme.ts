import type { CSSProperties } from 'react';
import type { WebThemeSnapshot } from './chatTypes';

type ThemeStyle = CSSProperties & Record<string, string | number>;

function fallbackColor(value: string | null | undefined, fallback: string) {
  return value ?? fallback;
}

function resolveFontFamily(theme: WebThemeSnapshot) {
  if (theme.font.custom_font_asset_url) {
    return '"OperitThemeFont", "PingFang SC", "Microsoft YaHei", sans-serif';
  }
  if (theme.font.system_font_name?.trim()) {
    return `"${theme.font.system_font_name}", "PingFang SC", "Microsoft YaHei", sans-serif`;
  }
  return '"PingFang SC", "Microsoft YaHei", sans-serif';
}

function clampOpacity(value: number | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0.2;
  }
  return Math.max(0, Math.min(1, value));
}

function toRgba(color: string | null | undefined, alpha?: number) {
  if (!color) {
    return null;
  }

  const normalized = color.trim();
  if (normalized.startsWith('#')) {
    const hex = normalized.slice(1);
    const size = hex.length === 3 ? 1 : 2;
    if (hex.length !== 3 && hex.length !== 6) {
      return normalized;
    }

    const read = (index: number) => {
      const chunk = hex.slice(index * size, index * size + size);
      const expanded = size === 1 ? chunk + chunk : chunk;
      return Number.parseInt(expanded, 16);
    };

    const red = read(0);
    const green = read(1);
    const blue = read(2);
    const resolvedAlpha = alpha ?? 1;
    return `rgba(${red}, ${green}, ${blue}, ${resolvedAlpha.toFixed(3)})`;
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  );
  if (!rgbaMatch) {
    return normalized;
  }

  const red = Number.parseFloat(rgbaMatch[1]);
  const green = Number.parseFloat(rgbaMatch[2]);
  const blue = Number.parseFloat(rgbaMatch[3]);
  const baseAlpha = rgbaMatch[4] ? Number.parseFloat(rgbaMatch[4]) : 1;
  const resolvedAlpha = alpha === undefined ? baseAlpha : alpha;
  return `rgba(${red}, ${green}, ${blue}, ${resolvedAlpha.toFixed(3)})`;
}

function parseRgb(color: string | null | undefined) {
  if (!color) {
    return null;
  }

  const normalized = color.trim();
  if (normalized.startsWith('#')) {
    const hex = normalized.slice(1);
    const size = hex.length === 3 ? 1 : 2;
    if (hex.length !== 3 && hex.length !== 6) {
      return null;
    }

    const read = (index: number) => {
      const chunk = hex.slice(index * size, index * size + size);
      const expanded = size === 1 ? chunk + chunk : chunk;
      return Number.parseInt(expanded, 16);
    };

    return {
      red: read(0),
      green: read(1),
      blue: read(2)
    };
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  );
  if (!rgbaMatch) {
    return null;
  }

  return {
    red: Number.parseFloat(rgbaMatch[1]),
    green: Number.parseFloat(rgbaMatch[2]),
    blue: Number.parseFloat(rgbaMatch[3])
  };
}

function resolveReadableTextColor(
  color: string | null | undefined,
  dark = '#08111d',
  light = '#f7fbff'
) {
  const rgb = parseRgb(color);
  if (!rgb) {
    return light;
  }

  const luminance = (0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue) / 255;
  return luminance > 0.58 ? dark : light;
}

export function buildChatThemeStyle(theme: WebThemeSnapshot | null): ThemeStyle {
  if (!theme) {
    return {};
  }

  const backgroundOpacity = clampOpacity(theme.background.opacity);
  const isLight = theme.theme_mode === 'light';
  const palette = theme.palette;
  const hasBackgroundAsset = Boolean(theme.background.asset_url);
  const primary = theme.primary_color || palette.primary_color || '#8ca9ff';
  const secondary = theme.secondary_color || palette.secondary_color || '#67d4c8';
  const backgroundColor = palette.background_color || (isLight ? '#faf8ff' : '#101520');
  const surfaceColor = palette.surface_color || (isLight ? '#ffffff' : '#1b202b');
  const surfaceVariantColor =
    palette.surface_variant_color || (isLight ? '#ece8f1' : '#45464f');
  const surfaceContainerColor =
    palette.surface_container_color || surfaceColor;
  const surfaceContainerHighColor =
    palette.surface_container_high_color || surfaceContainerColor;
  const outlineColor = palette.outline_color || (isLight ? '#7a7a85' : '#8f909a');
  const outlineVariantColor =
    palette.outline_variant_color || (isLight ? '#c8c4cf' : '#45464f');
  const primaryContainerColor =
    palette.primary_container_color || (toRgba(primary, isLight ? 0.24 : 0.28) ?? primary);
  const onPrimaryContainerColor =
    palette.on_primary_container_color || resolveReadableTextColor(primaryContainerColor);
  const onPrimaryColor = resolveReadableTextColor(primary);
  const onSecondaryColor = resolveReadableTextColor(secondary);
  const cardSoft = toRgba(surfaceVariantColor, 0.3) ?? surfaceVariantColor;
  const cardStrong = toRgba(surfaceVariantColor, 0.5) ?? surfaceVariantColor;
  const cardSubtle = toRgba(surfaceVariantColor, 0.2) ?? surfaceVariantColor;
  const historyPanelBackground =
    (hasBackgroundAsset ? toRgba(surfaceContainerColor, 0.96) : null) ?? surfaceContainerColor;
  const panelSurface =
    (hasBackgroundAsset ? toRgba(surfaceColor, 0.85) : null) ?? surfaceColor;
  const panelSurfaceStrong =
    (hasBackgroundAsset ? toRgba(surfaceColor, 0.92) : null) ?? surfaceColor;
  const transparentPanelSurface = toRgba(surfaceColor, 0.72) ?? surfaceColor;
  const transparentPanelSurfaceStrong = toRgba(surfaceColor, 0.9) ?? surfaceColor;
  const shadowColor = isLight ? 'rgba(44, 58, 90, 0.14)' : 'rgba(0, 0, 0, 0.24)';
  const assistantBubble = fallbackColor(
    theme.bubble.assistant_bubble_color,
    surfaceColor
  );
  const userBubble = fallbackColor(
    theme.chat_style === 'cursor' && !theme.bubble.cursor_user_follow_theme
      ? theme.bubble.cursor_user_color
      : theme.bubble.user_bubble_color,
    primaryContainerColor
  );
  const backgroundTint =
    hasBackgroundAsset && !theme.header.transparent
      ? toRgba(backgroundColor, isLight ? 0.04 : 0.08) ?? 'transparent'
      : 'transparent';

  return {
    '--chat-primary': primary,
    '--chat-secondary': secondary,
    '--chat-primary-container': primaryContainerColor,
    '--chat-on-primary-container': onPrimaryContainerColor,
    '--chat-on-primary': onPrimaryColor,
    '--chat-on-secondary': onSecondaryColor,
    '--chat-root-background': backgroundColor,
    '--chat-background-image': theme.background.asset_url ? `url(${theme.background.asset_url})` : 'none',
    '--chat-background-opacity': String(theme.background.asset_url ? backgroundOpacity : 0),
    '--chat-background-tint': backgroundTint,
    '--chat-surface-color': surfaceColor,
    '--chat-surface-variant-color': surfaceVariantColor,
    '--chat-surface-container-color': surfaceContainerColor,
    '--chat-surface-container-high-color': surfaceContainerHighColor,
    '--chat-surface-strong': surfaceColor,
    '--chat-surface-soft': toRgba(surfaceContainerHighColor, 0.94) ?? surfaceContainerHighColor,
    '--chat-surface-faint': cardSubtle,
    '--chat-card-soft': cardSoft,
    '--chat-card-strong': cardStrong,
    '--chat-history-panel-bg': historyPanelBackground,
    '--chat-history-item-bg': cardSoft,
    '--chat-history-item-active-bg': toRgba(primaryContainerColor, 0.3) ?? primaryContainerColor,
    '--chat-border': toRgba(outlineVariantColor, 0.68) ?? outlineVariantColor,
    '--chat-field-border': toRgba(outlineColor, 0.72) ?? outlineColor,
    '--chat-field-border-strong': outlineColor,
    '--chat-shadow': shadowColor,
    '--chat-text-main': palette.on_surface_color,
    '--chat-text-soft': palette.on_surface_variant_color,
    '--chat-text-muted': toRgba(palette.on_surface_color, 0.7) ?? palette.on_surface_color,
    '--chat-user-bubble': userBubble,
    '--chat-assistant-bubble': assistantBubble,
    '--chat-user-text': fallbackColor(
      theme.bubble.user_text_color,
      onPrimaryContainerColor
    ),
    '--chat-assistant-text': fallbackColor(
      theme.bubble.assistant_text_color,
      palette.on_surface_color
    ),
    '--chat-font-scale': String(theme.font.scale || 1),
    '--chat-font-family': resolveFontFamily(theme),
    '--chat-user-radius': theme.bubble.user_rounded ? '20px' : '8px',
    '--chat-assistant-radius': theme.bubble.assistant_rounded ? '20px' : '8px',
    '--chat-avatar-radius':
      theme.avatars.shape === 'square'
        ? `${theme.avatars.corner_radius ?? 10}px`
        : '999px',
    '--chat-user-padding-left': `${theme.bubble.user_padding_left || 12}px`,
    '--chat-user-padding-right': `${theme.bubble.user_padding_right || 12}px`,
    '--chat-assistant-padding-left': `${theme.bubble.assistant_padding_left || 12}px`,
    '--chat-assistant-padding-right': `${theme.bubble.assistant_padding_right || 12}px`,
    '--chat-header-bg': theme.header.transparent
      ? 'transparent'
      : toRgba(surfaceVariantColor, 0.2) ?? surfaceVariantColor,
    '--chat-header-icon-active-bg': toRgba(primary, 0.15) ?? primary,
    '--chat-header-icon-muted': toRgba(palette.on_surface_color, 0.7) ?? palette.on_surface_color,
    '--chat-header-track': toRgba(surfaceVariantColor, 0.3) ?? surfaceVariantColor,
    '--chat-button-filled-bg': primaryContainerColor,
    '--chat-button-filled-fg': onPrimaryContainerColor,
    '--chat-composer-bg': theme.input.transparent ? 'transparent' : panelSurface,
    '--chat-composer-bg-transparent': transparentPanelSurface,
    '--chat-composer-bg-strong': transparentPanelSurfaceStrong,
    '--chat-queue-bg': theme.input.transparent ? transparentPanelSurface : panelSurface,
    '--chat-queue-item-bg':
      theme.input.transparent ? transparentPanelSurfaceStrong : panelSurfaceStrong,
    '--chat-field-bg': surfaceColor,
    '--chat-scroll-button-bg': toRgba(surfaceContainerHighColor, 0.62) ?? surfaceContainerHighColor,
    '--chat-panel-bg': panelSurfaceStrong,
    '--chat-code-block-bg': toRgba(surfaceVariantColor, isLight ? 0.54 : 0.42) ?? surfaceVariantColor,
    '--chat-scrim': toRgba(palette.on_surface_color, isLight ? 0.14 : 0.38) ?? palette.on_surface_color,
    '--chat-dialog-scrim':
      toRgba(palette.on_surface_color, isLight ? 0.22 : 0.5) ?? palette.on_surface_color,
    '--chat-action-primary-bg': primary,
    '--chat-action-primary-fg': onPrimaryColor,
    '--chat-action-queued-bg': secondary,
    '--chat-action-queued-fg': onSecondaryColor,
    '--chat-action-danger-bg': isLight ? '#d35d6d' : '#e06e7f',
    '--chat-action-danger-fg': '#fef8f9'
  };
}

export function buildChatFontFaceCss(theme: WebThemeSnapshot | null) {
  if (!theme?.font.custom_font_asset_url) {
    return '';
  }

  return `@font-face {
    font-family: "OperitThemeFont";
    src: url("${theme.font.custom_font_asset_url}");
  }`;
}
