import type { CSSProperties } from 'react';
import {
  AudioAttachmentIcon,
  CodeAttachmentIcon,
  DescriptionAttachmentIcon,
  ImageAttachmentIcon,
  ScreenshotMonitorIcon,
  VideoAttachmentIcon
} from '../../util/chatIcons';
import type { WebMessageAttachment } from '../../util/chatTypes';

type MessageAttachmentTagProps = {
  attachment: WebMessageAttachment;
  backgroundColor?: string | null;
  className?: string;
  textColor?: string | null;
};

function toRgba(color: string | null | undefined, alpha: number) {
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

    return `rgba(${read(0)}, ${read(1)}, ${read(2)}, ${alpha.toFixed(3)})`;
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
  return `rgba(${red}, ${green}, ${blue}, ${alpha.toFixed(3)})`;
}

function attachmentDisplayLabel(attachment: WebMessageAttachment) {
  if (attachment.mime_type === 'text/json' && attachment.file_name === 'screen_content.json') {
    return '屏幕内容';
  }
  if (attachment.mime_type === 'application/vnd.workspace-context+xml') {
    return '工作区';
  }
  return attachment.file_name;
}

function AttachmentIcon({ attachment }: { attachment: WebMessageAttachment }) {
  if (attachment.mime_type === 'text/json' && attachment.file_name === 'screen_content.json') {
    return <ScreenshotMonitorIcon size={12} />;
  }
  if (attachment.mime_type === 'application/vnd.workspace-context+xml') {
    return <CodeAttachmentIcon size={12} />;
  }
  if (attachment.mime_type.startsWith('image/')) {
    return <ImageAttachmentIcon size={12} />;
  }
  if (attachment.mime_type.startsWith('audio/')) {
    return <AudioAttachmentIcon size={12} />;
  }
  if (attachment.mime_type.startsWith('video/')) {
    return <VideoAttachmentIcon size={12} />;
  }
  return <DescriptionAttachmentIcon size={12} />;
}

export function MessageAttachmentTag({
  attachment,
  backgroundColor,
  className,
  textColor
}: MessageAttachmentTagProps) {
  const iconColor = textColor ? (toRgba(textColor, 0.8) ?? textColor) : 'var(--chat-text-soft)';
  const style = {
    '--message-attachment-bg': toRgba(backgroundColor, 0.5) ?? 'var(--chat-card-strong)',
    '--message-attachment-text': textColor ?? 'var(--chat-text-main)',
    '--message-attachment-icon': iconColor
  } as CSSProperties;

  return (
    <div className={['message-attachment-tag', className].filter(Boolean).join(' ')} style={style}>
      <span aria-hidden="true" className="message-attachment-tag-icon">
        <AttachmentIcon attachment={attachment} />
      </span>
      <span className="message-attachment-tag-label">{attachmentDisplayLabel(attachment)}</span>
    </div>
  );
}
