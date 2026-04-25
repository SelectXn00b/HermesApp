import { useMemo, useState } from 'react';
import { ChatHeader } from './ChatHeader';

function UsageRing({
  percent,
  tone
}: {
  percent: number;
  tone: 'normal' | 'warn' | 'danger';
}) {
  const radius = 15;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference - (percent / 100) * circumference;

  return (
    <div className="chat-usage-ring">
      <svg viewBox="0 0 38 38">
        <circle className="chat-usage-ring-track" cx="19" cy="19" r={radius} />
        <circle
          className={`chat-usage-ring-value tone-${tone}`}
          cx="19"
          cy="19"
          r={radius}
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
        />
      </svg>
      <span>{percent}</span>
    </div>
  );
}

export function ChatScreenHeader({
  activeCharacterName,
  activeCharacterAvatarUrl,
  showChatHistorySelector,
  onToggleChatHistorySelector,
  onCharacterSwitcherClick,
  onLaunchFloatingWindow,
  isFloatingMode,
  runningTaskCount,
  contextPercent,
  contextLabel,
  contextCurrentValue,
  contextMaxValue,
  isStreaming,
  isConnecting
}: {
  activeCharacterName: string;
  activeCharacterAvatarUrl?: string | null;
  showChatHistorySelector: boolean;
  onToggleChatHistorySelector: () => void;
  onCharacterSwitcherClick: () => void;
  onLaunchFloatingWindow: () => void;
  isFloatingMode: boolean;
  runningTaskCount: number;
  contextPercent: number;
  contextLabel: string;
  contextCurrentValue: number;
  contextMaxValue: number;
  isStreaming: boolean;
  isConnecting: boolean;
}) {
  const [showStats, setShowStats] = useState(false);
  const statusLabel = useMemo(() => {
    if (isConnecting) {
      return '同步中';
    }
    if (isStreaming) {
      return '回复中';
    }
    return '已连接';
  }, [isConnecting, isStreaming]);
  const totalTokens = contextCurrentValue;
  const ringTone =
    contextPercent > 90 ? 'danger' : contextPercent > 75 ? 'warn' : 'normal';

  return (
    <header className="chat-screen-header">
      <ChatHeader
        activeCharacterAvatarUrl={activeCharacterAvatarUrl}
        activeCharacterName={activeCharacterName}
        isFloatingMode={isFloatingMode}
        onCharacterClick={onCharacterSwitcherClick}
        onLaunchFloatingWindow={onLaunchFloatingWindow}
        onToggleChatHistorySelector={onToggleChatHistorySelector}
        runningTaskCount={runningTaskCount}
        showChatHistorySelector={showChatHistorySelector}
      />

      <div className="chat-screen-header-side">
        <button
          className="chat-screen-header-usage-button"
          onClick={() => setShowStats(!showStats)}
          type="button"
        >
          <UsageRing percent={contextPercent} tone={ringTone} />
        </button>

        {showStats ? (
          <div className="chat-screen-header-stats-sheet">
            <span>{statusLabel}</span>
            <strong>{contextLabel}</strong>
            <div className="chat-screen-header-stat-row">
              <span>当前窗口</span>
              <strong>{contextCurrentValue}</strong>
            </div>
            <div className="chat-screen-header-stat-row">
              <span>窗口上限</span>
              <strong>{contextMaxValue}</strong>
            </div>
            <div className="chat-screen-header-stat-row is-highlight">
              <span>总计估算</span>
              <strong>{totalTokens}</strong>
            </div>
          </div>
        ) : null}
      </div>
    </header>
  );
}
