import { PencilIcon, SendIcon, TrashIcon } from '../../../../util/chatIcons';
import type { PendingQueueMessageItem } from '../../../../util/chatTypes';

export function PendingMessageQueuePanel({
  queuedMessages,
  expanded,
  onExpandedChange,
  onDeleteMessage,
  onEditMessage,
  onSendMessage
}: {
  queuedMessages: PendingQueueMessageItem[];
  expanded: boolean;
  onExpandedChange: (value: boolean) => void;
  onDeleteMessage: (id: number) => void;
  onEditMessage: (id: number) => void;
  onSendMessage: (id: number) => void;
}) {
  if (!queuedMessages.length) {
    return null;
  }

  return (
    <section className="pending-message-queue-panel">
      <button
        className="pending-message-queue-toggle"
        onClick={() => onExpandedChange(!expanded)}
        type="button"
      >
        <strong>待发送队列</strong>
        <span>{queuedMessages.length}</span>
      </button>

      {expanded ? (
        <div className="pending-message-queue-list">
          {queuedMessages.map((item) => (
            <div className="pending-message-queue-item" key={item.id}>
              <p>{item.text}</p>
              <div className="pending-message-queue-actions">
                <button onClick={() => onEditMessage(item.id)} type="button">
                  <PencilIcon size={14} />
                </button>
                <button onClick={() => onSendMessage(item.id)} type="button">
                  <SendIcon size={14} />
                </button>
                <button onClick={() => onDeleteMessage(item.id)} type="button">
                  <TrashIcon size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}
