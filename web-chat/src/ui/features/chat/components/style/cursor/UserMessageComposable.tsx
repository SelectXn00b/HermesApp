import { MessageAttachmentTag } from '../../attachments';
import { GlassSurface } from '../../part/GlassSurface';
import { effectiveUserDisplayContent, effectiveUserDisplayName } from '../../../util/chatUtils';
import type {
  WebChatMessage,
  WebMessageAttachment,
  WebMessageImageLink,
  WebThemeSnapshot
} from '../../../util/chatTypes';

function imageLinkToAttachment(imageLink: WebMessageImageLink): WebMessageAttachment {
  return {
    id: `image-link:${imageLink.id}`,
    file_name: imageLink.expired ? '图片已过期' : '图片',
    mime_type: 'image/*',
    file_size: 0,
    asset_url: imageLink.asset_url
  };
}

export function UserMessageComposable({
  message,
  theme
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
}) {
  const userName = effectiveUserDisplayName(message, theme);
  const promptTitle = message.display_name_is_proxy && userName ? `Prompt by ${userName}` : 'Prompt';
  const imageLinks = message.image_links ?? [];
  const cursorUserColor =
    theme?.bubble.cursor_user_follow_theme
      ? undefined
      : theme?.bubble.cursor_user_color ?? theme?.bubble.user_bubble_color ?? undefined;
  const cursorUserText = theme?.bubble.user_text_color ?? undefined;
  const hasGlass =
    Boolean(theme?.bubble.cursor_user_water_glass) || Boolean(theme?.bubble.cursor_user_liquid_glass);
  const glassVariant = theme?.bubble.cursor_user_water_glass
    ? 'water'
    : theme?.bubble.cursor_user_liquid_glass
      ? 'liquid'
      : undefined;
  const cardClassName = [
    'cursor-user-card',
    theme?.bubble.cursor_user_water_glass
      ? 'is-water-glass'
      : theme?.bubble.cursor_user_liquid_glass
        ? 'is-liquid-glass'
        : ''
  ]
    .filter(Boolean)
    .join(' ');
  const assetChips = [
    ...imageLinks.map(imageLinkToAttachment),
    ...message.attachments
  ];
  const cursorAttachmentBackground =
    cursorUserColor ?? theme?.bubble.user_bubble_color ?? theme?.palette?.surface_variant_color;
  const cursorAttachmentText = cursorUserText ?? theme?.palette?.on_surface_color;

  return (
    <article className="cursor-user-message">
      {message.reply_preview ? (
        <div className="message-reply-preview align-start">
          <span className="message-reply-preview-icon">↩</span>
          <span className="message-reply-preview-text">
            {message.reply_preview.sender}: {message.reply_preview.content}
          </span>
        </div>
      ) : null}

      {assetChips.length ? (
        <div className="chat-message-attachments cursor-attachment-row">
          {assetChips.map((attachment) => (
            <MessageAttachmentTag
              attachment={attachment}
              backgroundColor={cursorAttachmentBackground}
              key={attachment.id}
              textColor={cursorAttachmentText}
            />
          ))}
        </div>
      ) : null}

      <GlassSurface
        baseColor={cursorUserColor ?? theme?.bubble.user_bubble_color ?? theme?.palette?.surface_variant_color}
        borderColor={theme?.palette?.outline_color}
        className={cardClassName}
        radius={8}
        style={{
          background: hasGlass ? undefined : cursorUserColor,
          color: cursorUserText
        }}
        themeMode={theme?.theme_mode}
        variant={glassVariant}
      >
        <strong className={`cursor-message-label ${message.display_name_is_proxy ? 'is-proxy' : ''}`}>
          {promptTitle}
        </strong>
        <div className="chat-message-content cursor-user-body user-message-plain">
          <div className="plain-text-block">{effectiveUserDisplayContent(message)}</div>
        </div>
      </GlassSurface>
    </article>
  );
}
