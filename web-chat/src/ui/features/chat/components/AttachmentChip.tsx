import { formatFileSize, isImageAttachment } from '../util/chatUtils';
import type { WebMessageAttachment } from '../util/chatTypes';

export function AttachmentChip({
  attachment,
  removable,
  onRemove
}: {
  attachment: WebMessageAttachment;
  removable?: boolean;
  onRemove?: (id: string) => void;
}) {
  const sizeLabel = formatFileSize(attachment.file_size);
  const imagePreview = attachment.asset_url && isImageAttachment(attachment);

  return (
    <div className={`attachment-chip ${removable ? 'is-removable' : ''}`}>
      {imagePreview ? (
        <div className="attachment-chip-preview">
          <img alt={attachment.file_name} src={attachment.asset_url ?? ''} />
        </div>
      ) : null}
      <div className="attachment-chip-copy">
        <strong>{attachment.file_name}</strong>
        <span>{sizeLabel || attachment.mime_type}</span>
      </div>
      {removable && onRemove ? (
        <button onClick={() => onRemove(attachment.id)} type="button">
          移除
        </button>
      ) : null}
    </div>
  );
}
