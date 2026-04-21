import type { ReactNode } from 'react';

export function InputOverlayPopup({
  children,
  onDismiss,
  panelClassName
}: {
  children: ReactNode;
  onDismiss: () => void;
  panelClassName: string;
}) {
  return (
    <div className="input-overlay-popup" onClick={onDismiss} role="presentation">
      <div
        className={panelClassName}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        {children}
      </div>
    </div>
  );
}
