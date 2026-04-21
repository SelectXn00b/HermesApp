import { SummaryMessageComposable } from './SummaryMessageComposable';
import { AiMessageComposable } from './AiMessageComposable';
import { UserMessageComposable } from './UserMessageComposable';
import type { WebChatMessage, WebThemeSnapshot } from '../../../util/chatTypes';

export function CursorStyleChatMessage({
  message,
  theme
}: {
  message: WebChatMessage;
  theme: WebThemeSnapshot | null;
}) {
  if (message.sender === 'user') {
    return <UserMessageComposable message={message} theme={theme} />;
  }
  if (message.sender === 'summary' || message.sender === 'system') {
    return <SummaryMessageComposable message={message} />;
  }
  return <AiMessageComposable message={message} theme={theme} />;
}
