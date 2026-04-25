import { SummaryMessageComposable } from '../cursor/SummaryMessageComposable';
import { BubbleAiMessageComposable } from './BubbleAiMessageComposable';
import { BubbleUserMessageComposable } from './BubbleUserMessageComposable';
import type { WebChatMessage, WebThemeSnapshot } from '../../../util/chatTypes';

export function BubbleStyleChatMessage({
  message,
  theme
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
}) {
  if (message.sender === 'user') {
    return <BubbleUserMessageComposable message={message} theme={theme} />;
  }
  if (message.sender === 'summary' || message.sender === 'system') {
    return <SummaryMessageComposable message={message} />;
  }
  return <BubbleAiMessageComposable message={message} theme={theme} />;
}
