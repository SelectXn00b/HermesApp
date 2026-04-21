import { HistoryIcon, PersonIcon, PictureInPictureIcon } from '../util/chatIcons';

const CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH = 12;

function toChatHeaderName(name: string) {
  return name.length <= CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH
    ? name
    : `${name.slice(0, CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH)}…`;
}

function HeaderAvatar({
  activeCharacterAvatarUrl,
  activeCharacterName
}: {
  activeCharacterAvatarUrl?: string | null;
  activeCharacterName: string;
}) {
  if (activeCharacterAvatarUrl) {
    return <img alt={activeCharacterName} className="chat-header-avatar" src={activeCharacterAvatarUrl} />;
  }

  return (
    <div className="chat-header-avatar chat-header-avatar-fallback">
      <PersonIcon size={14} />
    </div>
  );
}

export function ChatHeader({
  showChatHistorySelector,
  onToggleChatHistorySelector,
  onLaunchFloatingWindow,
  isFloatingMode,
  runningTaskCount,
  activeCharacterName,
  activeCharacterAvatarUrl,
  onCharacterClick
}: {
  showChatHistorySelector: boolean;
  onToggleChatHistorySelector: () => void;
  onLaunchFloatingWindow: () => void;
  isFloatingMode: boolean;
  runningTaskCount: number;
  activeCharacterName: string;
  activeCharacterAvatarUrl?: string | null;
  onCharacterClick: () => void;
}) {
  const displayCharacterName = toChatHeaderName(activeCharacterName || '当前角色');

  return (
    <div className="chat-header-row">
      {runningTaskCount >= 2 ? (
        <button
          className="chat-header-count-button"
          onClick={onToggleChatHistorySelector}
          type="button"
        >
          <HistoryIcon size={18} />
          <span>{runningTaskCount}</span>
        </button>
      ) : (
        <button
          className={`chat-header-icon-button ${showChatHistorySelector ? 'is-active' : ''}`}
          onClick={onToggleChatHistorySelector}
          type="button"
        >
          <HistoryIcon size={18} />
        </button>
      )}

      <button
        className={`chat-header-icon-button ${isFloatingMode ? 'is-active' : ''}`}
        onClick={onLaunchFloatingWindow}
        type="button"
      >
        <PictureInPictureIcon size={18} />
      </button>

      <button className="chat-header-character-switcher" onClick={onCharacterClick} type="button">
        <HeaderAvatar
          activeCharacterAvatarUrl={activeCharacterAvatarUrl}
          activeCharacterName={activeCharacterName}
        />
        <span>{displayCharacterName}</span>
      </button>
    </div>
  );
}
