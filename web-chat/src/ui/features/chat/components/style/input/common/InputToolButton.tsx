import type { ReactNode } from 'react';

export function InputToolButton({
  active,
  children,
  onClick,
  title
}: {
  active?: boolean;
  children: ReactNode;
  onClick: () => void;
  title: string;
}) {
  return (
    <button
      className={`input-tool-button ${active ? 'is-active' : ''}`}
      onClick={onClick}
      title={title}
      type="button"
    >
      {children}
    </button>
  );
}
