import { ChevronDownIcon, ChevronUpIcon } from '../util/chatIcons';

export function ChatScrollNavigator({
  hasOlderPages,
  hasNewerPages,
  onLoadOlder,
  onLoadNewer,
  onJumpToLatest
}: {
  hasOlderPages: boolean;
  hasNewerPages: boolean;
  onLoadOlder: () => void;
  onLoadNewer: () => void;
  onJumpToLatest: () => void;
}) {
  if (!hasOlderPages && !hasNewerPages) {
    return null;
  }

  return (
    <div className="chat-scroll-navigator">
      {hasOlderPages ? (
        <button onClick={onLoadOlder} type="button">
          <ChevronUpIcon size={16} />
        </button>
      ) : null}
      <button onClick={onJumpToLatest} type="button">
        最新
      </button>
      {hasNewerPages ? (
        <button onClick={onLoadNewer} type="button">
          <ChevronDownIcon size={16} />
        </button>
      ) : null}
    </div>
  );
}
