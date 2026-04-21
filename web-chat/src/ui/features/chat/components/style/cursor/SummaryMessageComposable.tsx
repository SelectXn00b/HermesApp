import { useState } from 'react';
import type { WebChatMessage } from '../../../util/chatTypes';

export function SummaryMessageComposable({
  message
}: {
  message: WebChatMessage;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <article className="summary-message-composable">
        <button className="summary-message-toggle" onClick={() => setOpen(true)} type="button">
          <span className="summary-message-line" />
          <span className="summary-message-badge">
            <span className="summary-message-badge-icon">i</span>
            <span>系统摘要</span>
          </span>
          <span className="summary-message-line" />
        </button>
      </article>

      {open ? (
        <div className="structured-modal-backdrop" onClick={() => setOpen(false)} role="presentation">
          <section
            className="summary-message-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="summary-message-dialog-header">
              <strong>系统摘要</strong>
            </header>
            <div className="summary-message-dialog-divider" />
            <div className="summary-message-dialog-body">
              <p>{message.content_raw}</p>
            </div>
            <footer className="summary-message-dialog-footer">
              <button className="summary-message-dialog-button" onClick={() => setOpen(false)} type="button">
                关闭
              </button>
            </footer>
          </section>
        </div>
      ) : null}
    </>
  );
}
