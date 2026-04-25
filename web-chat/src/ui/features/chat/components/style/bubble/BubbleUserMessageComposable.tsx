import { MessageAttachmentTag } from '../../attachments';
import { BubbleImageBackgroundSurface } from './BubbleImageBackgroundSurface';
import {
  bubbleImageStyle,
  effectiveUserDisplayContent,
  effectiveUserDisplayName
} from '../../../util/chatUtils';
import type {
  WebChatMessage,
  WebMessageAttachment,
  WebMessageImageLink,
  WebThemeSnapshot
} from '../../../util/chatTypes';

function UserBubbleAvatar({
  name,
  url
}: {
  name: string;
  url?: string | null;
}) {
  if (url) {
    return <img alt={name} className="bubble-avatar bubble-avatar-large" src={url} />;
  }

  return (
    <div className="bubble-avatar bubble-avatar-large bubble-avatar-fallback">
      {name.slice(0, 1) || 'U'}
    </div>
  );
}

function imageLinkToAttachment(imageLink: WebMessageImageLink): WebMessageAttachment {
  return {
    id: `image-link:${imageLink.id}`,
    file_name: imageLink.expired ? '图片已过期' : '图片',
    mime_type: 'image/*',
    file_size: 0,
    asset_url: imageLink.asset_url
  };
}

function ReplyPreview({
  message,
  align
}: {
  message: WebChatMessage;
  align: 'start' | 'end';
}) {
  if (!message.reply_preview) {
    return null;
  }

  return (
    <div className={`message-reply-preview align-${align}`}>
      <span className="message-reply-preview-icon">↩</span>
      <span className="message-reply-preview-text">
        {message.reply_preview.sender}: {message.reply_preview.content}
      </span>
    </div>
  );
}

function BubbleUserImageLinks({
  backgroundColor,
  imageLinks,
  textColor
}: {
  backgroundColor?: string | null;
  imageLinks: WebMessageImageLink[];
  textColor?: string | null;
}) {
  if (!imageLinks.length) {
    return null;
  }

  return (
    <div className="bubble-user-image-strip">
      {imageLinks.map((imageLink) =>
        imageLink.asset_url ? (
          <button
            className="bubble-user-image-card"
            key={imageLink.id}
            onClick={() => window.open(imageLink.asset_url ?? '', '_blank', 'noopener,noreferrer')}
            type="button"
          >
            <img alt="User uploaded content" src={imageLink.asset_url} />
          </button>
        ) : (
          <MessageAttachmentTag
            attachment={imageLinkToAttachment(imageLink)}
            backgroundColor={backgroundColor}
            key={imageLink.id}
            textColor={textColor}
          />
        )
      )}
    </div>
  );
}

function BubbleUserAttachmentRow({
  attachments,
  backgroundColor,
  textColor
}: {
  attachments: WebMessageAttachment[];
  backgroundColor?: string | null;
  textColor?: string | null;
}) {
  if (!attachments.length) {
    return null;
  }

  return (
    <div className="chat-message-attachments align-end">
      {attachments.map((attachment) => (
        <MessageAttachmentTag
          attachment={attachment}
          backgroundColor={backgroundColor}
          key={attachment.id}
          textColor={textColor}
        />
      ))}
    </div>
  );
}

export function BubbleUserMessageComposable({
  message,
  theme
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
}) {
  const showAvatar = theme?.bubble.show_avatar ?? true;
  const wideLayout = theme?.bubble.wide_layout ?? false;
  const userName = effectiveUserDisplayName(message, theme);
  const content = effectiveUserDisplayContent(message);
  const imageLinks = message.image_links ?? [];
  const glassClassName = theme?.bubble.user_water_glass
    ? 'is-water-glass'
    : theme?.bubble.user_liquid_glass
      ? 'is-liquid-glass'
      : '';
  const glassVariant = theme?.bubble.user_water_glass
    ? 'water'
    : theme?.bubble.user_liquid_glass
      ? 'liquid'
      : undefined;
  const attachmentBackground = theme?.bubble.user_bubble_color ?? theme?.palette?.surface_variant_color;
  const attachmentText = theme?.bubble.user_text_color ?? theme?.palette?.on_surface_color;
  const backgroundStyle =
    theme?.bubble.user_water_glass || theme?.bubble.user_liquid_glass
      ? undefined
      : bubbleImageStyle(theme, 'user');
  const bubbleClassName = [
    'chat-message-surface',
    'bubble-user-message',
    theme?.bubble.user_rounded ?? true ? 'is-rounded' : 'is-sharp',
    glassClassName
  ]
    .filter(Boolean)
    .join(' ');
  const showWideHeader = wideLayout && (showAvatar || userName);

  if (wideLayout) {
    return (
      <article className="bubble-message-composable user is-wide">
        <ReplyPreview align="end" message={message} />
        <BubbleUserImageLinks
          backgroundColor={attachmentBackground}
          imageLinks={imageLinks}
          textColor={attachmentText}
        />
        <BubbleUserAttachmentRow
          attachments={message.attachments}
          backgroundColor={attachmentBackground}
          textColor={attachmentText}
        />

        {showWideHeader ? (
          <div className="bubble-user-header">
            {userName ? (
              <strong className={message.display_name_is_proxy ? 'is-proxy' : ''}>{userName}</strong>
            ) : null}
            {showAvatar ? (
              <UserBubbleAvatar
                name={userName || 'User'}
                url={message.avatar_url ?? theme?.avatars.user_avatar_url}
              />
            ) : null}
          </div>
        ) : null}

        <BubbleImageBackgroundSurface
          backgroundStyle={backgroundStyle}
          className={bubbleClassName}
          glassBaseColor={theme?.bubble.user_bubble_color ?? theme?.palette?.surface_variant_color}
          glassBorderColor={theme?.palette?.outline_color}
          glassVariant={glassVariant}
          themeMode={theme?.theme_mode}
        >
          <div className="chat-message-content user-message-plain">
            <div className="plain-text-block">{content}</div>
          </div>
        </BubbleImageBackgroundSurface>
      </article>
    );
  }

  return (
    <article className="bubble-message-composable user is-compact">
      <ReplyPreview align="end" message={message} />
      <BubbleUserImageLinks
        backgroundColor={attachmentBackground}
        imageLinks={imageLinks}
        textColor={attachmentText}
      />
      <BubbleUserAttachmentRow
        attachments={message.attachments}
        backgroundColor={attachmentBackground}
        textColor={attachmentText}
      />

      <div className="bubble-inline-row user">
        <div className={`bubble-inline-stack user ${showAvatar ? 'has-avatar' : 'no-avatar'}`}>
          {userName ? (
            <span className={`bubble-user-label ${message.display_name_is_proxy ? 'is-proxy' : ''}`}>
              {userName}
            </span>
          ) : null}
          <BubbleImageBackgroundSurface
            backgroundStyle={backgroundStyle}
            className={bubbleClassName}
            glassBaseColor={theme?.bubble.user_bubble_color ?? theme?.palette?.surface_variant_color}
            glassBorderColor={theme?.palette?.outline_color}
            glassVariant={glassVariant}
            themeMode={theme?.theme_mode}
          >
            <div className="chat-message-content user-message-plain">
              <div className="plain-text-block">{content}</div>
            </div>
          </BubbleImageBackgroundSurface>
        </div>
        {showAvatar ? (
          <UserBubbleAvatar
            name={userName || 'User'}
            url={message.avatar_url ?? theme?.avatars.user_avatar_url}
          />
        ) : null}
      </div>
    </article>
  );
}
