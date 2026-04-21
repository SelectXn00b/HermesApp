import { useEffect, useMemo, useState } from 'react';
import {
  CheckIcon,
  GroupIcon,
  PersonIcon,
  SortIcon
} from '../util/chatIcons';
import type {
  WebActivePromptSnapshot,
  WebActivePromptTarget,
  WebCharacterCardSelectorItem,
  WebCharacterGroupSelectorItem,
  WebCharacterSelectorResponse
} from '../util/chatTypes';

type CharacterSelectorSortOption = 'DEFAULT' | 'NAME_ASC' | 'UPDATED_DESC';

const SORT_OPTION_STORAGE_KEY = 'web_chat_character_selector_sort_option';

function readStoredSortOption(): CharacterSelectorSortOption {
  if (typeof window === 'undefined') {
    return 'DEFAULT';
  }
  const stored = window.localStorage.getItem(SORT_OPTION_STORAGE_KEY);
  return stored === 'NAME_ASC' || stored === 'UPDATED_DESC' || stored === 'DEFAULT'
    ? stored
    : 'DEFAULT';
}

function writeStoredSortOption(value: CharacterSelectorSortOption) {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SORT_OPTION_STORAGE_KEY, value);
  }
}

function sortCards(
  cards: WebCharacterCardSelectorItem[],
  sortOption: CharacterSelectorSortOption
) {
  switch (sortOption) {
    case 'NAME_ASC':
      return [...cards].sort((left, right) => left.name.localeCompare(right.name, 'zh-Hans-CN'));
    case 'UPDATED_DESC':
      return [...cards].sort((left, right) => right.updated_at - left.updated_at);
    case 'DEFAULT':
    default:
      return cards;
  }
}

function SelectorAvatar({
  avatarUrl,
  fallback,
  label
}: {
  avatarUrl?: string | null;
  fallback: 'group' | 'card';
  label: string;
}) {
  if (avatarUrl) {
    return (
      <span className="character-selector-avatar">
        <img alt={label} src={avatarUrl} />
      </span>
    );
  }

  return (
    <span className="character-selector-avatar is-fallback">
      {fallback === 'group' ? <GroupIcon size={18} /> : <PersonIcon size={18} />}
    </span>
  );
}

function CharacterCardRow({
  card,
  selected,
  onClick
}: {
  card: WebCharacterCardSelectorItem;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`character-selector-item ${selected ? 'is-selected' : ''}`}
      onClick={onClick}
      type="button"
    >
      <SelectorAvatar avatarUrl={card.avatar_url} fallback="card" label={card.name} />
      <span className="character-selector-item-copy">
        <strong>{card.name}</strong>
        {card.description ? <small>{card.description}</small> : null}
      </span>
      {selected ? <CheckIcon size={16} /> : null}
    </button>
  );
}

function CharacterGroupRow({
  group,
  selected,
  onClick
}: {
  group: WebCharacterGroupSelectorItem;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`character-selector-item ${selected ? 'is-selected' : ''}`}
      onClick={onClick}
      type="button"
    >
      <SelectorAvatar avatarUrl={group.avatar_url} fallback="group" label={group.name} />
      <span className="character-selector-item-copy">
        <strong>{group.name}</strong>
        <small>{`成员：${group.member_count}`}</small>
      </span>
      {selected ? <CheckIcon size={16} /> : null}
    </button>
  );
}

export function CharacterSelectorPanel({
  open,
  loading,
  selector,
  activePrompt,
  onClose,
  onSelectTarget
}: {
  open: boolean;
  loading: boolean;
  selector: WebCharacterSelectorResponse | null;
  activePrompt: WebActivePromptSnapshot | null;
  onClose: () => void;
  onSelectTarget: (target: WebActivePromptTarget) => void;
}) {
  const [sortOption, setSortOption] = useState<CharacterSelectorSortOption>(readStoredSortOption);
  const [sortMenuOpen, setSortMenuOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      setSortMenuOpen(false);
      return;
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [onClose, open]);

  const sortedCards = useMemo(() => {
    return sortCards(selector?.cards ?? [], sortOption);
  }, [selector?.cards, sortOption]);

  const totalCount = (selector?.cards.length ?? 0) + (selector?.groups.length ?? 0);

  function updateSortOption(nextValue: CharacterSelectorSortOption) {
    setSortOption(nextValue);
    setSortMenuOpen(false);
    writeStoredSortOption(nextValue);
  }

  return (
    <>
      <div
        aria-hidden={!open}
        className={`character-selector-scrim ${open ? 'is-visible' : ''}`}
        onClick={onClose}
      />

      <section
        aria-hidden={!open}
        aria-modal="true"
        className={`character-selector-panel ${open ? 'is-open' : ''}`}
        role="dialog"
      >
        <div className="character-selector-panel-shell" onClick={(event) => event.stopPropagation()}>
          <header className="character-selector-panel-header">
            <span className="character-selector-panel-title">
              <strong>选择角色</strong>
              <small>{`${totalCount} 个角色`}</small>
            </span>

            <div className="character-selector-panel-actions">
              <div className="character-selector-sort-anchor">
                <button
                  className={`character-selector-action ${sortMenuOpen ? 'is-active' : ''}`}
                  onClick={() => setSortMenuOpen((current) => !current)}
                  type="button"
                >
                  <SortIcon size={18} />
                </button>

                {sortMenuOpen ? (
                  <div className="character-selector-sort-menu" role="menu">
                    {[
                      { key: 'DEFAULT' as const, label: '默认顺序' },
                      { key: 'NAME_ASC' as const, label: '按名称' },
                      { key: 'UPDATED_DESC' as const, label: '按最近更新时间' }
                    ].map((option) => {
                      const selected = sortOption === option.key;
                      return (
                        <button
                          className={`character-selector-sort-option ${selected ? 'is-selected' : ''}`}
                          key={option.key}
                          onClick={() => updateSortOption(option.key)}
                          role="menuitem"
                          type="button"
                        >
                          <span>{option.label}</span>
                          {selected ? <CheckIcon size={16} /> : null}
                        </button>
                      );
                    })}
                  </div>
                ) : null}
              </div>
            </div>
          </header>

          <div className="character-selector-panel-list">
            {loading && !selector ? (
              <div className="character-selector-empty">
                <strong>正在加载角色</strong>
                <span>稍等一下，角色卡和群组马上就绪。</span>
              </div>
            ) : selector ? (
              <>
                {selector.groups.length ? (
                  <section className="character-selector-section">
                    <span className="character-selector-section-title">群组</span>
                    <div className="character-selector-section-list">
                      {selector.groups.map((group) => (
                        <CharacterGroupRow
                          group={group}
                          key={group.id}
                          onClick={() => {
                            onSelectTarget({ type: 'character_group', id: group.id });
                          }}
                          selected={
                            activePrompt?.type === 'character_group' && activePrompt.id === group.id
                          }
                        />
                      ))}
                    </div>
                  </section>
                ) : null}

                <section className="character-selector-section">
                  <span className="character-selector-section-title">角色卡</span>
                  <div className="character-selector-section-list">
                    {sortedCards.map((card) => (
                      <CharacterCardRow
                        card={card}
                        key={card.id}
                        onClick={() => {
                          onSelectTarget({ type: 'character_card', id: card.id });
                        }}
                        selected={
                          activePrompt?.type === 'character_card' && activePrompt.id === card.id
                        }
                      />
                    ))}
                  </div>
                </section>
              </>
            ) : (
              <div className="character-selector-empty">
                <strong>暂时没有可用角色</strong>
                <span>当前没有拿到角色卡或群组数据。</span>
              </div>
            )}
          </div>
        </div>
      </section>
    </>
  );
}
