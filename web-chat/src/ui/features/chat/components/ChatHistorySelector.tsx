import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AddCircleIcon,
  BackIcon,
  BranchIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  FolderIcon,
  GroupIcon,
  LockIcon,
  PencilIcon,
  PersonIcon,
  PlusIcon,
  SearchIcon,
  SearchOffIcon,
  SwapIcon,
  TrashIcon,
  TuneIcon
} from '../util/chatIcons';
import type { WebChatSummary } from '../util/chatTypes';

type HistoryDisplayMode = 'BY_CHARACTER_CARD' | 'BY_FOLDER' | 'CURRENT_CHARACTER_ONLY';

const HISTORY_DISPLAY_MODE_KEY = 'web_chat_history_display_mode';
const HISTORY_SHOW_SWIPE_HINT_KEY = 'web_chat_show_swipe_hint';
const LONG_PRESS_DURATION_MS = 420;
const SWIPE_LOCK_DISTANCE_PX = 10;
const SWIPE_ACTION_TRIGGER_PX = 100;
const SWIPE_ACTION_MAX_PX = 116;

function readStoredHistoryDisplayMode(): HistoryDisplayMode {
  if (typeof window === 'undefined') {
    return 'CURRENT_CHARACTER_ONLY';
  }
  const stored = window.localStorage.getItem(HISTORY_DISPLAY_MODE_KEY);
  return stored === 'BY_CHARACTER_CARD' ||
    stored === 'BY_FOLDER' ||
    stored === 'CURRENT_CHARACTER_ONLY'
    ? stored
    : 'CURRENT_CHARACTER_ONLY';
}

function readStoredSwipeHint(): boolean {
  if (typeof window === 'undefined') {
    return true;
  }
  return window.localStorage.getItem(HISTORY_SHOW_SWIPE_HINT_KEY) !== 'false';
}

function writeStoredValue(key: string, value: string) {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(key, value);
  }
}

function NestedHistoryGutter({ kind }: { kind: 'group' | 'chat' }) {
  return (
    <span aria-hidden="true" className={`chat-history-selector-nested-gutter is-${kind}`}>
      <span className="chat-history-selector-nested-line" />
    </span>
  );
}

function buildBindingLabel(chat: WebChatSummary) {
  if (chat.character_group_id) {
    return {
      key: `group:${chat.character_group_id}`,
      label: `群组: ${chat.character_group_name ?? chat.character_group_id}`,
      kind: 'group' as const,
      avatarUrl: chat.binding_avatar_url ?? null
    };
  }
  if (chat.character_card_name) {
    return {
      key: `card:${chat.character_card_name}`,
      label: chat.character_card_name,
      kind: 'card' as const,
      avatarUrl: chat.binding_avatar_url ?? null
    };
  }
  return {
    key: 'unbound',
    label: '未绑定',
    kind: 'unbound' as const,
    avatarUrl: null
  };
}

function HistoryBindingAvatar({
  avatarUrl,
  kind,
  label
}: {
  avatarUrl?: string | null;
  kind: 'group' | 'card' | 'unbound';
  label: string;
}) {
  if (avatarUrl) {
    return (
      <span className="chat-history-selector-character-avatar">
        <img alt={label} src={avatarUrl} />
      </span>
    );
  }

  return (
    <span className="chat-history-selector-character-avatar">
      {kind === 'group' ? <GroupIcon size={14} /> : <PersonIcon size={14} />}
    </span>
  );
}

function matchesCurrentBinding(chat: WebChatSummary, selectedChat: WebChatSummary | null) {
  if (!selectedChat) {
    return true;
  }
  if (selectedChat.character_group_id) {
    return chat.character_group_id === selectedChat.character_group_id;
  }
  if (selectedChat.character_card_name) {
    return (
      !chat.character_group_id &&
      chat.character_card_name === selectedChat.character_card_name
    );
  }
  return !chat.character_group_id && !chat.character_card_name;
}

function buildGroupBuckets(chats: WebChatSummary[]) {
  const buckets = new Map<string, { key: string; label: string; chats: WebChatSummary[] }>();
  chats.forEach((chat) => {
    const key = chat.group ?? '__ungrouped__';
    const existing = buckets.get(key);
    if (existing) {
      existing.chats.push(chat);
      return;
    }
    buckets.set(key, {
      key,
      label: chat.group ?? '未分组',
      chats: [chat]
    });
  });
  return [...buckets.values()];
}

function clampSwipeOffset(offset: number) {
  return Math.max(-SWIPE_ACTION_MAX_PX, Math.min(SWIPE_ACTION_MAX_PX, offset));
}

function SwipeableHistoryChatRow({
  chat,
  nested = false,
  active,
  parentChat,
  onDismissSwipeHint,
  onLongPress,
  onSelect,
  onRename,
  onDelete
}: {
  chat: WebChatSummary;
  nested?: boolean;
  active: boolean;
  parentChat: WebChatSummary | null;
  onDismissSwipeHint: () => void;
  onLongPress: (chat: WebChatSummary) => void;
  onSelect: (chatId: string) => void;
  onRename: (chat: WebChatSummary) => void;
  onDelete: (chat: WebChatSummary) => void;
}) {
  const [offsetX, setOffsetX] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const pointerIdRef = useRef<number | null>(null);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const gestureAxisRef = useRef<'horizontal' | 'vertical' | null>(null);
  const ignoreClickRef = useRef(false);
  const longPressTriggeredRef = useRef(false);
  const longPressTimerRef = useRef<number | null>(null);
  const currentOffsetRef = useRef(0);

  useEffect(() => {
    return () => {
      if (longPressTimerRef.current !== null) {
        window.clearTimeout(longPressTimerRef.current);
      }
    };
  }, []);

  function clearLongPress() {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }

  function resetSwipe() {
    currentOffsetRef.current = 0;
    setOffsetX(0);
    setIsDragging(false);
  }

  function finalizeGesture(target?: EventTarget | null) {
    clearLongPress();

    const gestureAxis = gestureAxisRef.current;
    const committedOffset = currentOffsetRef.current;
    const longPressTriggered = longPressTriggeredRef.current;

    if (
      target instanceof Element &&
      pointerIdRef.current !== null &&
      target.hasPointerCapture(pointerIdRef.current)
    ) {
      target.releasePointerCapture(pointerIdRef.current);
    }

    pointerIdRef.current = null;
    gestureAxisRef.current = null;

    if (longPressTriggered) {
      longPressTriggeredRef.current = false;
      resetSwipe();
      return;
    }

    if (gestureAxis === 'horizontal') {
      ignoreClickRef.current = true;
      resetSwipe();
      if (committedOffset >= SWIPE_ACTION_TRIGGER_PX) {
        onRename(chat);
      } else if (committedOffset <= -SWIPE_ACTION_TRIGGER_PX) {
        onDelete(chat);
      }
      return;
    }

    resetSwipe();
  }

  const swipeStartProgress = Math.max(0, offsetX) / SWIPE_ACTION_MAX_PX;
  const swipeEndProgress = Math.max(0, -offsetX) / SWIPE_ACTION_MAX_PX;

  return (
    <div
      className={[
        'chat-history-selector-chat-row',
        active ? 'is-active' : '',
        nested ? 'is-nested' : ''
      ]
        .filter(Boolean)
        .join(' ')}
      key={chat.id}
    >
      {nested ? <NestedHistoryGutter kind="chat" /> : null}
      <div className="chat-history-selector-swipe-shell">
        <div className="chat-history-selector-swipe-track">
          <div aria-hidden="true" className="chat-history-selector-swipe-actions">
            <div
              className="chat-history-selector-swipe-action is-start"
              style={{ opacity: swipeStartProgress, transform: `scale(${0.9 + swipeStartProgress * 0.1})` }}
            >
              <span className="chat-history-selector-swipe-action-icon">
                <PencilIcon size={20} />
              </span>
            </div>
            <div
              className="chat-history-selector-swipe-action is-end"
              style={{ opacity: swipeEndProgress, transform: `scale(${0.9 + swipeEndProgress * 0.1})` }}
            >
              <span className="chat-history-selector-swipe-action-icon">
                <TrashIcon size={20} />
              </span>
            </div>
          </div>

          <button
            className={`chat-history-selector-chat-surface ${isDragging ? 'is-dragging' : ''}`}
            onClick={() => {
              if (ignoreClickRef.current) {
                ignoreClickRef.current = false;
                return;
              }
              onSelect(chat.id);
            }}
            onContextMenu={(event) => {
              event.preventDefault();
            }}
            onPointerCancel={(event) => {
              ignoreClickRef.current = true;
              finalizeGesture(event.currentTarget);
            }}
            onPointerDown={(event) => {
              if (event.button !== 0) {
                return;
              }

              pointerIdRef.current = event.pointerId;
              startXRef.current = event.clientX;
              startYRef.current = event.clientY;
              gestureAxisRef.current = null;
              ignoreClickRef.current = false;
              longPressTriggeredRef.current = false;
              resetSwipe();
              clearLongPress();

              longPressTimerRef.current = window.setTimeout(() => {
                longPressTriggeredRef.current = true;
                ignoreClickRef.current = true;
                resetSwipe();
                onLongPress(chat);
              }, LONG_PRESS_DURATION_MS);
            }}
            onPointerLeave={() => {
              if (gestureAxisRef.current !== 'horizontal') {
                clearLongPress();
              }
            }}
            onPointerMove={(event) => {
              if (pointerIdRef.current !== event.pointerId || longPressTriggeredRef.current) {
                return;
              }

              const deltaX = event.clientX - startXRef.current;
              const deltaY = event.clientY - startYRef.current;

              if (gestureAxisRef.current === null) {
                if (
                  Math.abs(deltaX) < SWIPE_LOCK_DISTANCE_PX &&
                  Math.abs(deltaY) < SWIPE_LOCK_DISTANCE_PX
                ) {
                  return;
                }

                clearLongPress();

                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                  gestureAxisRef.current = 'horizontal';
                  ignoreClickRef.current = true;
                  event.currentTarget.setPointerCapture(event.pointerId);
                } else {
                  gestureAxisRef.current = 'vertical';
                  return;
                }
              }

              if (gestureAxisRef.current !== 'horizontal') {
                return;
              }

              event.preventDefault();
              onDismissSwipeHint();

              const nextOffset = clampSwipeOffset(deltaX);
              currentOffsetRef.current = nextOffset;
              setOffsetX(nextOffset);
              setIsDragging(true);
            }}
            onPointerUp={(event) => finalizeGesture(event.currentTarget)}
            style={{ transform: `translateX(${offsetX}px)` }}
            type="button"
          >
            <div className="chat-history-selector-chat-title-row">
              <div className="chat-history-selector-chat-title-block">
                <strong>{chat.title}</strong>
                {chat.active_streaming ? <span className="chat-history-selector-streaming-dot" /> : null}
              </div>
              {chat.locked ? <LockIcon size={16} /> : null}
            </div>

            {parentChat ? (
              <div className="chat-history-selector-parent-line">
                <BranchIcon size={14} />
                <span>{parentChat.title}</span>
              </div>
            ) : null}
          </button>
        </div>
      </div>
    </div>
  );
}

export function ChatHistorySelector({
  open,
  chats,
  search,
  selectedChatId,
  busy,
  streaming,
  onClose,
  onCreateChat,
  onRenameChat,
  onDeleteChat,
  onSearchChange,
  onSelectChat
}: {
  open: boolean;
  chats: WebChatSummary[];
  search: string;
  selectedChatId: string | null;
  busy: boolean;
  streaming: boolean;
  onClose: () => void;
  onCreateChat: (options?: { group?: string | null }) => Promise<void>;
  onRenameChat: (chat: WebChatSummary, title: string) => Promise<void>;
  onDeleteChat: (chat: WebChatSummary) => Promise<void>;
  onSearchChange: (value: string) => void;
  onSelectChat: (chatId: string) => void;
}) {
  const [editingChat, setEditingChat] = useState<WebChatSummary | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<WebChatSummary | null>(null);
  const [actionTarget, setActionTarget] = useState<WebChatSummary | null>(null);
  const [draftTitle, setDraftTitle] = useState('');
  const [newGroupName, setNewGroupName] = useState('');
  const [showSearchBox, setShowSearchBox] = useState(Boolean(search));
  const [showSettingsDialog, setShowSettingsDialog] = useState(false);
  const [showNewGroupDialog, setShowNewGroupDialog] = useState(false);
  const [showSwipeHint, setShowSwipeHint] = useState(readStoredSwipeHint);
  const [historyDisplayMode, setHistoryDisplayMode] = useState<HistoryDisplayMode>(
    readStoredHistoryDisplayMode
  );
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const [collapsedCharacters, setCollapsedCharacters] = useState<Set<string>>(new Set());
  useEffect(() => {
    if (!open) {
      setEditingChat(null);
      setDeleteTarget(null);
      setActionTarget(null);
      setShowSettingsDialog(false);
      setShowNewGroupDialog(false);
    }
  }, [open]);

  useEffect(() => {
    if (search) {
      setShowSearchBox(true);
    }
  }, [search]);

  const selectedChat = useMemo(
    () => chats.find((chat) => chat.id === selectedChatId) ?? null,
    [chats, selectedChatId]
  );

  const filteredChats = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return chats;
    }
    return chats.filter((chat) => {
      return [
        chat.title,
        chat.group ?? '',
        chat.character_card_name ?? '',
        chat.character_group_name ?? '',
        chat.character_group_id ?? ''
      ].some((value) => value.toLowerCase().includes(keyword));
    });
  }, [chats, search]);

  const visibleChats = useMemo(() => {
    switch (historyDisplayMode) {
      case 'BY_CHARACTER_CARD':
      case 'BY_FOLDER':
        return filteredChats;
      case 'CURRENT_CHARACTER_ONLY':
      default:
        return filteredChats.filter((chat) => matchesCurrentBinding(chat, selectedChat));
    }
  }, [filteredChats, historyDisplayMode, selectedChat]);

  const chatsById = useMemo(() => {
    return new Map(chats.map((chat) => [chat.id, chat] as const));
  }, [chats]);

  const characterBuckets = useMemo(() => {
    if (historyDisplayMode !== 'BY_CHARACTER_CARD') {
      return [];
    }

    const buckets = new Map<
      string,
      {
        key: string;
        label: string;
        kind: 'group' | 'card' | 'unbound';
        avatarUrl: string | null;
        groups: ReturnType<typeof buildGroupBuckets>;
      }
    >();

    visibleChats.forEach((chat) => {
      const binding = buildBindingLabel(chat);
      const bucket = buckets.get(binding.key);
      if (bucket) {
        const groupBucket = bucket.groups.find((item) => item.key === (chat.group ?? '__ungrouped__'));
        if (groupBucket) {
          groupBucket.chats.push(chat);
        } else {
          bucket.groups.push({
            key: chat.group ?? '__ungrouped__',
            label: chat.group ?? '未分组',
            chats: [chat]
          });
        }
        return;
      }

      buckets.set(binding.key, {
        key: binding.key,
        label: binding.label,
        kind: binding.kind,
        avatarUrl: binding.avatarUrl,
        groups: buildGroupBuckets([chat])
      });
    });

    return [...buckets.values()];
  }, [historyDisplayMode, visibleChats]);

  const groupBuckets = useMemo(() => {
    if (historyDisplayMode === 'BY_CHARACTER_CARD') {
      return [];
    }
    return buildGroupBuckets(visibleChats);
  }, [historyDisplayMode, visibleChats]);

  function toggleCollapsedGroup(key: string) {
    setCollapsedGroups((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function toggleCollapsedCharacter(key: string) {
    setCollapsedCharacters((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function updateHistoryDisplayMode(mode: HistoryDisplayMode) {
    setHistoryDisplayMode(mode);
    writeStoredValue(HISTORY_DISPLAY_MODE_KEY, mode);
  }

  function dismissSwipeHint() {
    if (!showSwipeHint) {
      return;
    }
    setShowSwipeHint(false);
    writeStoredValue(HISTORY_SHOW_SWIPE_HINT_KEY, 'false');
  }

  function renderChatItem(chat: WebChatSummary, nested = false) {
    const parentChat = chat.parent_chat_id ? chatsById.get(chat.parent_chat_id) : null;
    return (
      <SwipeableHistoryChatRow
        active={selectedChatId === chat.id}
        key={chat.id}
        chat={chat}
        nested={nested}
        onDelete={(target) => setDeleteTarget(target)}
        onDismissSwipeHint={dismissSwipeHint}
        onLongPress={(target) => setActionTarget(target)}
        onRename={(target) => {
          setEditingChat(target);
          setDraftTitle(target.title);
        }}
        onSelect={onSelectChat}
        parentChat={parentChat}
      />
    );
  }

  function renderGroupBucket(
    bucket: { key: string; label: string; chats: WebChatSummary[] },
    nested = false
  ) {
    const collapsed = collapsedGroups.has(bucket.key);
    return (
      <section
        className={['chat-history-selector-group-block', nested ? 'is-nested' : '']
          .filter(Boolean)
          .join(' ')}
        key={bucket.key}
      >
        {nested ? <NestedHistoryGutter kind="group" /> : null}
        <button
          className="chat-history-selector-group-header"
          onClick={() => toggleCollapsedGroup(bucket.key)}
          type="button"
        >
          <span className="chat-history-selector-group-header-main">
            <FolderIcon size={16} />
            <strong>{bucket.label}</strong>
            {bucket.label !== '未分组' ? (
              <em>(长按管理)</em>
            ) : null}
          </span>
          {collapsed ? <ChevronDownIcon size={16} /> : <ChevronUpIcon size={16} />}
        </button>

        {collapsed ? null : (
          <div className="chat-history-selector-group-list">
            {bucket.chats.map((chat) => renderChatItem(chat, nested))}
          </div>
        )}
      </section>
    );
  }

  return (
    <>
      <div
        aria-hidden={!open}
        className={`chat-history-selector-scrim ${open ? 'is-visible' : ''}`}
        onClick={onClose}
      />
      <aside className={`chat-history-selector ${open ? 'is-open' : ''}`}>
        <div className="chat-history-selector-top">
          <header className="chat-history-selector-title-row">
            <strong>对话历史</strong>
            <div className="chat-history-selector-title-actions">
              <button
                onClick={() => {
                  setShowSearchBox((value) => !value);
                  if (showSearchBox) {
                    onSearchChange('');
                  }
                }}
                type="button"
              >
                {showSearchBox ? <SearchOffIcon size={18} /> : <SearchIcon size={18} />}
              </button>
              <button onClick={() => setShowSettingsDialog(true)} type="button">
                <TuneIcon size={18} />
              </button>
              <button onClick={onClose} type="button">
                <BackIcon size={18} />
              </button>
            </div>
          </header>

          <div className="chat-history-selector-create-row">
            <button
              className="chat-history-selector-create-button"
              disabled={busy || streaming}
              onClick={() => onCreateChat()}
              type="button"
            >
              <PlusIcon size={16} />
              <span>新建对话</span>
            </button>
            <button
              className="chat-history-selector-create-icon"
              disabled={busy || streaming}
              onClick={() => setShowNewGroupDialog(true)}
              type="button"
            >
              <AddCircleIcon size={20} />
            </button>
          </div>

          {showSearchBox ? (
            <label className="chat-history-selector-search-field">
              <SearchIcon size={16} />
              <input
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="搜索"
                value={search}
              />
            </label>
          ) : null}

          {showSwipeHint ? (
            <button
              className="chat-history-selector-swipe-hint"
              onClick={dismissSwipeHint}
              type="button"
            >
              <SwapIcon size={16} />
              <span>左右滑动可编辑或删除(点击不再显示)</span>
            </button>
          ) : null}
        </div>

        <div className="chat-history-selector-divider" />

        <div className="chat-history-selector-list">
          {historyDisplayMode === 'BY_CHARACTER_CARD' ? (
            characterBuckets.length ? (
              characterBuckets.map((bucket) => {
                const collapsed = collapsedCharacters.has(bucket.key);
                return (
                  <section className="chat-history-selector-character-block" key={bucket.key}>
                    <button
                      className="chat-history-selector-character-header"
                      onClick={() => toggleCollapsedCharacter(bucket.key)}
                      type="button"
                    >
                      <span className="chat-history-selector-character-chip">
                        <HistoryBindingAvatar
                          avatarUrl={bucket.avatarUrl}
                          kind={bucket.kind}
                          label={bucket.label}
                        />
                        <strong>{bucket.label}</strong>
                      </span>
                      <span className="chat-history-selector-character-line" />
                      {collapsed ? <ChevronDownIcon size={18} /> : <ChevronUpIcon size={18} />}
                    </button>

                    {collapsed ? null : (
                      <div className="chat-history-selector-character-content">
                        {bucket.groups.map((group) => renderGroupBucket(group, true))}
                      </div>
                    )}
                  </section>
                );
              })
            ) : (
              <div className="chat-history-selector-empty">
                <strong>{busy ? '正在同步会话' : '没有匹配的会话'}</strong>
                <span>{busy ? '历史列表会在打开侧栏时按需加载。' : '换个关键词，或者直接新建对话。'}</span>
              </div>
            )
          ) : groupBuckets.length ? (
            groupBuckets.map((group) => renderGroupBucket(group))
          ) : (
            <div className="chat-history-selector-empty">
              <strong>{busy ? '正在同步会话' : '没有匹配的会话'}</strong>
              <span>{busy ? '历史列表会在打开侧栏时按需加载。' : '换个关键词，或者直接新建对话。'}</span>
            </div>
          )}
        </div>
      </aside>

      {actionTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog history-action-dialog" role="dialog">
            <header>
              <h3>对话历史</h3>
              <p>{actionTarget.title}</p>
            </header>
            <div className="history-action-list">
              <button
                className="history-action-item"
                onClick={() => {
                  setEditingChat(actionTarget);
                  setDraftTitle(actionTarget.title);
                  setActionTarget(null);
                }}
                type="button"
              >
                <PencilIcon size={18} />
                <span>编辑标题</span>
              </button>
              <button
                className="history-action-item is-danger"
                onClick={() => {
                  setDeleteTarget(actionTarget);
                  setActionTarget(null);
                }}
                type="button"
              >
                <TrashIcon size={18} />
                <span>删除</span>
              </button>
            </div>
            <footer>
              <button onClick={() => setActionTarget(null)} type="button">
                取消
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {showSettingsDialog ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog history-settings-dialog" role="dialog">
            <header>
              <h3>聊天记录设置</h3>
            </header>
            <div className="history-settings-section">
              <span>显示模式</span>
              {[
                {
                  mode: 'BY_CHARACTER_CARD' as const,
                  title: '按角色卡分类',
                  description: '角色卡-文件夹-对话三级分类'
                },
                {
                  mode: 'BY_FOLDER' as const,
                  title: '按文件夹分类',
                  description: '显示为文件夹+对话（全部角色卡）'
                },
                {
                  mode: 'CURRENT_CHARACTER_ONLY' as const,
                  title: '仅显示当前角色卡',
                  description: '仅显示当前角色卡下的文件夹和对话'
                }
              ].map((item) => {
                const selected = historyDisplayMode === item.mode;
                return (
                  <button
                    className={`history-settings-option ${selected ? 'is-selected' : ''}`}
                    key={item.mode}
                    onClick={() => updateHistoryDisplayMode(item.mode)}
                    type="button"
                  >
                    <span className="history-settings-option-copy">
                      <strong>{item.title}</strong>
                      <small>{item.description}</small>
                    </span>
                    {selected ? <CheckIcon size={18} /> : null}
                  </button>
                );
              })}
            </div>
            <footer>
              <button onClick={() => setShowSettingsDialog(false)} type="button">
                关闭
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {showNewGroupDialog ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>新建分组</h3>
            </header>
            <input
              autoFocus
              onChange={(event) => setNewGroupName(event.target.value)}
              placeholder="新分组名称"
              value={newGroupName}
            />
            <footer>
              <button
                onClick={() => {
                  setShowNewGroupDialog(false);
                  setNewGroupName('');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  const trimmed = newGroupName.trim();
                  if (!trimmed) {
                    return;
                  }
                  void (async () => {
                    await onCreateChat({ group: trimmed });
                    setShowNewGroupDialog(false);
                    setNewGroupName('');
                  })();
                }}
                type="button"
              >
                创建
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {editingChat ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>重命名会话</h3>
              <p>会同步修改手机当前历史列表中的标题。</p>
            </header>
            <input
              autoFocus
              onChange={(event) => setDraftTitle(event.target.value)}
              value={draftTitle}
            />
            <footer>
              <button
                onClick={() => {
                  setEditingChat(null);
                  setDraftTitle('');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    await onRenameChat(editingChat, draftTitle);
                    setEditingChat(null);
                    setDraftTitle('');
                  })();
                }}
                type="button"
              >
                保存
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {deleteTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>确认删除聊天</h3>
              <p>确定要删除聊天“{deleteTarget.title}”吗？此操作不可撤销。</p>
            </header>
            <div className="history-dialog-delete-target">{deleteTarget.title}</div>
            <footer>
              <button onClick={() => setDeleteTarget(null)} type="button">
                取消
              </button>
              <button
                className="is-danger"
                onClick={() => {
                  void (async () => {
                    await onDeleteChat(deleteTarget);
                    setDeleteTarget(null);
                  })();
                }}
                type="button"
              >
                确定删除
              </button>
            </footer>
          </div>
        </div>
      ) : null}
    </>
  );
}
