import { useEffect, useState, type ReactNode } from 'react';

function joinClassNames(parts: Array<string | false | null | undefined>) {
  return parts.filter(Boolean).join(' ');
}

function ExpandChevronIcon() {
  return (
    <svg
      aria-hidden="true"
      className="structured-expand-icon"
      fill="currentColor"
      height="20"
      viewBox="0 0 24 24"
      width="20"
    >
      <path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z" />
    </svg>
  );
}

export function StructuredExpandRow({
  title,
  expanded,
  onClick,
  live = false,
  className
}: {
  title: string;
  expanded: boolean;
  onClick: () => void;
  live?: boolean;
  className?: string;
}) {
  return (
    <button
      aria-expanded={expanded}
      className={joinClassNames([
        'structured-expand-row',
        expanded && 'is-expanded',
        live && 'is-live',
        className
      ])}
      onClick={onClick}
      type="button"
    >
      <span className="structured-expand-leading" aria-hidden="true">
        <ExpandChevronIcon />
      </span>
      <span className="structured-expand-title">{title}</span>
    </button>
  );
}

export function AnimatedExpandBody({
  visible,
  durationMs,
  className,
  variant = 'fade',
  skipExitAnimation = false,
  children
}: {
  visible: boolean;
  durationMs: number;
  className?: string;
  variant?: 'fade' | 'think';
  skipExitAnimation?: boolean;
  children: ReactNode;
}) {
  const [mounted, setMounted] = useState(visible);
  const [active, setActive] = useState(visible);

  useEffect(() => {
    let frameId = 0;
    let timeoutId = 0;

    if (visible) {
      setMounted(true);
      frameId = window.requestAnimationFrame(() => {
        setActive(true);
      });
      return () => {
        window.cancelAnimationFrame(frameId);
      };
    }

    if (skipExitAnimation) {
      setActive(false);
      setMounted(false);
      return;
    }

    setActive(false);
    timeoutId = window.setTimeout(() => {
      setMounted(false);
    }, durationMs);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [durationMs, skipExitAnimation, visible]);

  if (!mounted) {
    return null;
  }

  return (
    <div
      className={joinClassNames([
        'structured-expand-body-shell',
        `is-${variant}`,
        active && 'is-visible',
        className
      ])}
      style={{ ['--structured-expand-duration' as string]: `${durationMs}ms` }}
    >
      <div className="structured-expand-body-inner">{children}</div>
    </div>
  );
}
