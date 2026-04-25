import { useEffect, useState } from 'react';
import { ChevronDownIcon, LinkIcon, TuneIcon } from '../../../../util/chatIcons';
import type {
  WebModelSelectorState,
  WebSelectModelResponse
} from '../../../../util/chatTypes';
import { ModelSelectorPanel } from '../common/ModelSelectorPanel';

export function ClassicChatSettingsBar({
  contextPercent,
  modelSelector,
  modelSelectorLoading,
  onSelectModelConfig,
  onToggleSettings,
  settingsOpen
}: {
  contextPercent: number;
  modelSelector: WebModelSelectorState | null;
  modelSelectorLoading: boolean;
  onSelectModelConfig: (
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch?: boolean
  ) => Promise<WebSelectModelResponse | null>;
  onToggleSettings: () => void;
  settingsOpen: boolean;
}) {
  const [showModelDropdown, setShowModelDropdown] = useState(false);

  useEffect(() => {
    if (!settingsOpen) {
      setShowModelDropdown(false);
    }
  }, [settingsOpen]);

  return (
    <div className="classic-chat-settings-bar">
      <button
        aria-expanded={settingsOpen}
        className={`classic-settings-anchor ${settingsOpen ? 'is-active' : ''}`}
        onClick={onToggleSettings}
        type="button"
      >
        <TuneIcon size={22} />
      </button>

      {settingsOpen ? (
        <div className="classic-settings-popup" role="dialog">
          <ModelSelectorPanel
            expanded={showModelDropdown}
            loading={modelSelectorLoading}
            onExpandedChange={setShowModelDropdown}
            onSelectModel={onSelectModelConfig}
            selector={modelSelector}
          />

          <button className="classic-settings-popup-row" type="button">
            <span className="classic-settings-popup-icon">
              <LinkIcon size={16} />
            </span>
            <span className="classic-settings-popup-copy">
              <strong>记忆</strong>
              <em>{contextPercent}%</em>
            </span>
            <span className="classic-settings-popup-chevron">
              <ChevronDownIcon size={14} />
            </span>
          </button>

          <button className="classic-settings-popup-row" type="button">
            <span className="classic-settings-popup-icon">
              <TuneIcon size={16} />
            </span>
            <span className="classic-settings-popup-copy">
              <strong>设置选项</strong>
              <em>{contextPercent}%</em>
            </span>
            <span className="classic-settings-popup-chevron">
              <ChevronDownIcon size={14} />
            </span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
