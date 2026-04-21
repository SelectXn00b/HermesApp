import { useCallback, useEffect, useRef } from 'react';
import { BubbleStyleChatMessage } from './style/bubble/BubbleStyleChatMessage';
import { CursorStyleChatMessage } from './style/cursor/CursorStyleChatMessage';
import { ChatScrollNavigator } from './ChatScrollNavigator';
import type { ChatStyle, WebChatMessage, WebThemeSnapshot } from '../util/chatTypes';

function LoadingDots() {
  return (
    <div className="chat-loading-dots">
      <span />
      <span />
      <span />
    </div>
  );
}

export function ChatArea({
  chatHistory,
  currentChatId,
  isLoading,
  isConversationLoading,
  hasMoreHistoryBefore,
  isLoadingHistoryBefore,
  onLoadOlder,
  theme,
  chatStyle,
  autoScrollToBottom,
  onAutoScrollToBottomChange,
  topPadding,
  bottomPadding
}: {
  chatHistory: WebChatMessage[];
  currentChatId: string | null;
  isLoading: boolean;
  isConversationLoading: boolean;
  hasMoreHistoryBefore: boolean;
  isLoadingHistoryBefore: boolean;
  onLoadOlder: () => Promise<void>;
  theme: WebThemeSnapshot | null;
  chatStyle: ChatStyle;
  autoScrollToBottom: boolean;
  onAutoScrollToBottomChange: (value: boolean) => void;
  topPadding: number;
  bottomPadding: number;
}) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const threadRef = useRef<HTMLDivElement | null>(null);

  const lastMessage = chatHistory[chatHistory.length - 1] ?? null;
  const hasLastAssistantStartedStreaming =
    lastMessage?.sender === 'assistant' && lastMessage.content_raw.trim().length > 0;
  const showLoadingIndicator =
    autoScrollToBottom &&
    isLoading &&
    Boolean(
      lastMessage &&
        (
          lastMessage.sender === 'user' ||
          (
            lastMessage.sender === 'assistant' &&
            !hasLastAssistantStartedStreaming &&
            !lastMessage.content_raw.trim()
          )
        )
    );
  const hideLastAssistantBubble =
    chatStyle === 'bubble' &&
    showLoadingIndicator &&
    lastMessage?.sender === 'assistant';

  const renderedMessages = hideLastAssistantBubble
    ? chatHistory.slice(0, chatHistory.length - 1)
    : chatHistory;

  const scrollToBottom = useCallback(() => {
    const node = scrollRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, []);

  useEffect(() => {
    if (!autoScrollToBottom) {
      return;
    }

    let frameId = 0;
    let secondFrameId = 0;
    frameId = window.requestAnimationFrame(() => {
      scrollToBottom();
      secondFrameId = window.requestAnimationFrame(() => {
        scrollToBottom();
      });
    });

    return () => {
      window.cancelAnimationFrame(frameId);
      window.cancelAnimationFrame(secondFrameId);
    };
  }, [
    autoScrollToBottom,
    bottomPadding,
    currentChatId,
    renderedMessages.length,
    scrollToBottom,
    showLoadingIndicator,
    topPadding,
    lastMessage?.id,
    lastMessage?.content_raw
  ]);

  useEffect(() => {
    if (!autoScrollToBottom || !threadRef.current || typeof ResizeObserver === 'undefined') {
      return;
    }

    let frameId = 0;
    const observer = new ResizeObserver(() => {
      window.cancelAnimationFrame(frameId);
      frameId = window.requestAnimationFrame(() => {
        scrollToBottom();
      });
    });

    observer.observe(threadRef.current);
    return () => {
      observer.disconnect();
      window.cancelAnimationFrame(frameId);
    };
  }, [autoScrollToBottom, currentChatId, scrollToBottom]);

  function handleScroll() {
    if (!scrollRef.current) {
      return;
    }
    const distanceToBottom =
      scrollRef.current.scrollHeight - scrollRef.current.scrollTop - scrollRef.current.clientHeight;
    onAutoScrollToBottomChange(distanceToBottom < 32);
  }

  function jumpToLatest() {
    scrollToBottom();
    onAutoScrollToBottomChange(true);
  }

  return (
    <div className="chat-area-shell">
      <div className="chat-area-scroll" onScroll={handleScroll} ref={scrollRef}>
        <div
          className={`chat-area-thread chat-style-${chatStyle} ${theme?.bubble.wide_layout ? 'is-wide' : ''}`}
          ref={threadRef}
          style={{ paddingTop: topPadding, paddingBottom: bottomPadding }}
        >
          {hasMoreHistoryBefore ? (
            <button
              className="chat-pagination-button"
              disabled={isLoadingHistoryBefore}
              onClick={() => {
                void onLoadOlder();
              }}
              type="button"
            >
              {isLoadingHistoryBefore ? '正在加载历史...' : '加载更多历史'}
            </button>
          ) : null}

          {chatHistory.length ? (
            renderedMessages.map((message) =>
              chatStyle === 'bubble' ? (
                <BubbleStyleChatMessage key={message.id} message={message} theme={theme} />
              ) : (
                <CursorStyleChatMessage key={message.id} message={message} theme={theme} />
              )
            )
          ) : (
            <section className="chat-empty-state">
              <strong>{isConversationLoading ? '正在同步会话' : '准备开始聊天'}</strong>
              <p>
                {isConversationLoading
                  ? '正在按需拉取当前会话的主题和最近消息。'
                  : '这里会显示手机当前会话、主题和流式回复。'}
              </p>
            </section>
          )}

          {showLoadingIndicator ? (
            <div className={`chat-loading-indicator ${chatStyle === 'bubble' ? 'is-bubble' : 'is-cursor'}`}>
              <LoadingDots />
            </div>
          ) : null}

          <div aria-hidden="true" className="chat-area-end-spacer" />
        </div>
      </div>

      <ChatScrollNavigator
        hasNewerPages={false}
        hasOlderPages={hasMoreHistoryBefore}
        onJumpToLatest={jumpToLatest}
        onLoadNewer={jumpToLatest}
        onLoadOlder={() => {
          void onLoadOlder();
        }}
      />
    </div>
  );
}
