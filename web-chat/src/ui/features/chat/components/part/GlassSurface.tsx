import type { CSSProperties, ReactNode } from 'react';
import { useMemo } from 'react';
import LiquidGlass from 'liquid-glass-react';

type GlassVariant = 'water' | 'liquid';
type CssWithVars = CSSProperties & Record<`--${string}`, string | number>;

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function clampAlpha(alpha: number) {
  return clamp(alpha, 0, 1);
}

function parseRgb(color: string | null | undefined) {
  if (!color) {
    return null;
  }

  const normalized = color.trim();
  if (normalized.startsWith('#')) {
    const hex = normalized.slice(1);
    if (hex.length !== 3 && hex.length !== 6) {
      return null;
    }

    const size = hex.length === 3 ? 1 : 2;
    const read = (index: number) => {
      const chunk = hex.slice(index * size, index * size + size);
      const expanded = size === 1 ? chunk + chunk : chunk;
      return Number.parseInt(expanded, 16);
    };

    return { r: read(0), g: read(1), b: read(2) };
  }

  const rgbMatch = normalized.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i
  );
  if (!rgbMatch) {
    return null;
  }

  return {
    r: Number.parseFloat(rgbMatch[1]),
    g: Number.parseFloat(rgbMatch[2]),
    b: Number.parseFloat(rgbMatch[3])
  };
}

function toRgba(color: string | null | undefined, alpha: number, fallback: string) {
  const rgb = parseRgb(color);
  if (!rgb) {
    return fallback;
  }
  return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${clampAlpha(alpha).toFixed(3)})`;
}

function resolveVariantConfig(variant: GlassVariant, isDark: boolean) {
  if (variant === 'water') {
    return {
      aberrationIntensity: isDark ? 2.2 : 1.8,
      blurAmount: isDark ? 0.22 : 0.18,
      displacementScale: isDark ? 52 : 42,
      elasticity: 0.1,
      mode: 'standard' as const,
      saturation: isDark ? 170 : 155
    };
  }

  return {
    aberrationIntensity: isDark ? 3.1 : 2.6,
    blurAmount: isDark ? 0.16 : 0.12,
    displacementScale: isDark ? 86 : 72,
    elasticity: 0.14,
    mode: 'prominent' as const,
    saturation: isDark ? 190 : 175
  };
}

export function GlassSurface({
  variant,
  className,
  style,
  children,
  radius,
  baseColor,
  borderColor,
  themeMode,
  widthMode
}: {
  variant?: GlassVariant | null;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
  radius?: number;
  baseColor?: string | null;
  borderColor?: string | null;
  themeMode?: string;
  widthMode?: 'fill' | 'intrinsic';
}) {
  const resolvedRadius = radius ?? 20;
  const isDark = themeMode === 'dark';
  const widthClassName = widthMode === 'intrinsic' ? 'is-intrinsic' : 'is-fill';
  const resolvedConfig = useMemo(() => (variant ? resolveVariantConfig(variant, isDark) : null), [isDark, variant]);
  const resolvedBorderColor = useMemo(
    () =>
      toRgba(
        borderColor ?? baseColor,
        variant === 'water' ? (isDark ? 0.22 : 0.34) : isDark ? 0.28 : 0.42,
        isDark ? 'rgba(255,255,255,0.22)' : 'rgba(255,255,255,0.40)'
      ),
    [baseColor, borderColor, isDark, variant]
  );
  const resolvedToneColor = useMemo(
    () =>
      toRgba(
        baseColor,
        variant === 'water' ? (isDark ? 0.18 : 0.2) : isDark ? 0.22 : 0.24,
        isDark ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.16)'
      ),
    [baseColor, isDark, variant]
  );
  const resolvedShadowColor = useMemo(
    () =>
      toRgba(
        baseColor ?? borderColor,
        variant === 'water' ? (isDark ? 0.26 : 0.2) : isDark ? 0.32 : 0.24,
        isDark ? 'rgba(0,0,0,0.40)' : 'rgba(0,0,0,0.18)'
      ),
    [baseColor, borderColor, isDark, variant]
  );
  const hostStyle = useMemo<CssWithVars>(
    () => ({
      ...style,
      '--glass-surface-border': resolvedBorderColor,
      '--glass-surface-radius': `${resolvedRadius}px`,
      '--glass-surface-shadow': `0 12px 28px ${resolvedShadowColor}`,
      '--glass-surface-tone': resolvedToneColor,
      minWidth: 0,
      width: widthMode === 'intrinsic' ? 'fit-content' : (style?.width ?? '100%')
    }),
    [resolvedBorderColor, resolvedRadius, resolvedShadowColor, resolvedToneColor, style, widthMode]
  );
  const liquidStyle = useMemo<CSSProperties>(
    () => ({
      left: 0,
      minWidth: 0,
      position: 'static',
      top: 0,
      width: widthMode === 'intrinsic' ? 'fit-content' : '100%'
    }),
    [widthMode]
  );

  if (!variant || !resolvedConfig) {
    return (
      <div className={className} style={style}>
        {children}
      </div>
    );
  }

  return (
    <div className={[className, 'glass-surface-host', widthClassName, `is-${variant}-glass`].filter(Boolean).join(' ')} style={hostStyle}>
      <div aria-hidden="true" className="glass-surface-local-backdrop">
        <div className="glass-surface-local-base" />
        <div className="glass-surface-local-image" />
        <div className="glass-surface-local-tint" />
        <div className="glass-surface-local-tone" />
      </div>

      <div className={['glass-surface-liquid-frame', widthClassName].join(' ')}>
        <LiquidGlass
          aberrationIntensity={resolvedConfig.aberrationIntensity}
          blurAmount={resolvedConfig.blurAmount}
          className={['glass-surface-liquid', widthClassName].join(' ')}
          cornerRadius={resolvedRadius}
          displacementScale={resolvedConfig.displacementScale}
          elasticity={resolvedConfig.elasticity}
          mode={resolvedConfig.mode}
          overLight={!isDark}
          padding="0"
          saturation={resolvedConfig.saturation}
          style={liquidStyle}
        >
          <div className="glass-surface-content">{children}</div>
        </LiquidGlass>
      </div>
    </div>
  );
}
