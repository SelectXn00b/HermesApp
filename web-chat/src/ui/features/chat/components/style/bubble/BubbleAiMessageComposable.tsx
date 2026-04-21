import { MessageAttachmentTag } from '../../attachments';
import { CustomXmlRenderer } from '../../part/CustomXmlRenderer';
import { BubbleImageBackgroundSurface } from './BubbleImageBackgroundSurface';
import {
  assistantCompactMeta,
  assistantHeaderMeta,
  assistantHeaderName,
  bubbleImageStyle
} from '../../../util/chatUtils';
import type { WebChatMessage, WebThemeSnapshot } from '../../../util/chatTypes';

function BubbleAvatar({
  name,
  url,
  className = 'bubble-avatar'
}: {
  name: string;
  url?: string | null;
  className?: string;
}) {
  if (url) {
    return <img alt={name} className={className} src={url} />;
  }

  return <div className={`${className} bubble-avatar-fallback`}>{name.slice(0, 1) || 'A'}</div>;
}

export function BubbleAiMessageComposable({
  message,
  theme
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
}) {
  const headerName = assistantHeaderName(message, theme);
  const headerMeta = assistantHeaderMeta(message, theme);
  const compactMeta = assistantCompactMeta(message, theme);
  const showAvatar = theme?.bubble.show_avatar ?? true;
  const wideLayout = theme?.bubble.wide_layout ?? false;
  const glassClassName = theme?.bubble.assistant_water_glass
    ? 'is-water-glass'
    : theme?.bubble.assistant_liquid_glass
      ? 'is-liquid-glass'
      : '';
  const glassVariant = theme?.bubble.assistant_water_glass
    ? 'water'
    : theme?.bubble.assistant_liquid_glass
      ? 'liquid'
      : undefined;
  const backgroundStyle =
    theme?.bubble.assistant_water_glass || theme?.bubble.assistant_liquid_glass
      ? undefined
      : bubbleImageStyle(theme, 'assistant');
  const bubbleClassName = [
    'chat-message-surface',
    'bubble-assistant-message',
    theme?.bubble.assistant_rounded ?? true ? 'is-rounded' : 'is-sharp',
    glassClassName
  ]
    .filter(Boolean)
    .join(' ');
  const showWideHeader = wideLayout && (showAvatar || headerName || headerMeta);
  const attachmentBackground = theme?.bubble.assistant_bubble_color ?? theme?.palette?.surface_color;
  const attachmentText = theme?.bubble.assistant_text_color ?? theme?.palette?.on_surface_color;

  if (wideLayout) {
    return (
      <article className="bubble-message-composable ai is-wide">
        {showWideHeader ? (
          <div className="bubble-message-header">
            {showAvatar ? (
              <BubbleAvatar
                name={headerName || 'AI'}
                url={message.avatar_url ?? theme?.avatars.assistant_avatar_url}
              />
            ) : null}
            <div className="bubble-message-header-copy">
              {headerName ? <strong>{headerName}</strong> : null}
              {headerMeta ? <span>{headerMeta}</span> : null}
            </div>
          </div>
        ) : null}

        <BubbleImageBackgroundSurface
          backgroundStyle={backgroundStyle}
          className={bubbleClassName}
          glassBaseColor={theme?.bubble.assistant_bubble_color ?? theme?.palette?.surface_color}
          glassBorderColor={theme?.palette?.outline_color}
          glassVariant={glassVariant}
          themeMode={theme?.theme_mode}
        >
          <div className="chat-message-content">
            <CustomXmlRenderer
              blocks={message.content_blocks}
              content={message.content_raw}
              showStatusTags={theme?.show_status_tags ?? true}
              showThinking={theme?.show_thinking_process ?? true}
              streaming={message.streaming === true}
              toolCollapseMode={theme?.display.tool_collapse_mode ?? 'all'}
            />
          </div>
        </BubbleImageBackgroundSurface>

        {message.attachments.length ? (
          <div className="chat-message-attachments">
            {message.attachments.map((attachment) => (
              <MessageAttachmentTag
                attachment={attachment}
                backgroundColor={attachmentBackground}
                key={attachment.id}
                textColor={attachmentText}
              />
            ))}
          </div>
        ) : null}
      </article>
    );
  }

  return (
    <article className={`bubble-message-composable ai is-compact ${showAvatar ? 'has-avatar' : 'no-avatar'}`}>
      <div className="bubble-inline-row ai">
        {showAvatar ? (
          <BubbleAvatar
            className="bubble-avatar bubble-avatar-large"
            name={headerName || 'AI'}
            url={message.avatar_url ?? theme?.avatars.assistant_avatar_url}
          />
        ) : null}
        <div className={`bubble-inline-stack ai ${showAvatar ? 'has-avatar' : 'no-avatar'}`}>
          {compactMeta ? <span className="bubble-compact-meta">{compactMeta}</span> : null}
          <BubbleImageBackgroundSurface
            backgroundStyle={backgroundStyle}
            className={bubbleClassName}
            glassBaseColor={theme?.bubble.assistant_bubble_color ?? theme?.palette?.surface_color}
            glassBorderColor={theme?.palette?.outline_color}
            glassVariant={glassVariant}
            themeMode={theme?.theme_mode}
          >
            <div className="chat-message-content">
              <CustomXmlRenderer
                blocks={message.content_blocks}
                content={message.content_raw}
                showStatusTags={theme?.show_status_tags ?? true}
                showThinking={theme?.show_thinking_process ?? true}
                streaming={message.streaming === true}
                toolCollapseMode={theme?.display.tool_collapse_mode ?? 'all'}
              />
            </div>
          </BubbleImageBackgroundSurface>
        </div>
      </div>

      {message.attachments.length ? (
        <div className="chat-message-attachments">
          {message.attachments.map((attachment) => (
            <MessageAttachmentTag
              attachment={attachment}
              backgroundColor={attachmentBackground}
              key={attachment.id}
              textColor={attachmentText}
            />
          ))}
        </div>
      ) : null}
    </article>
  );
}
