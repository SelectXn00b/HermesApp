import type { CSSProperties, ReactNode } from 'react';
import { GlassSurface } from '../../part/GlassSurface';

export function BubbleImageBackgroundSurface({
  backgroundStyle,
  children,
  className,
  glassVariant,
  glassBaseColor,
  glassBorderColor,
  themeMode
}: {
  backgroundStyle?: CSSProperties;
  children: ReactNode;
  className?: string;
  glassVariant?: 'water' | 'liquid';
  glassBaseColor?: string | null;
  glassBorderColor?: string | null;
  themeMode?: string;
}) {
  return (
    <GlassSurface
      baseColor={glassBaseColor}
      borderColor={glassBorderColor}
      className={className}
      radius={20}
      style={backgroundStyle}
      themeMode={themeMode}
      variant={glassVariant}
      widthMode="intrinsic"
    >
      {children}
    </GlassSurface>
  );
}
