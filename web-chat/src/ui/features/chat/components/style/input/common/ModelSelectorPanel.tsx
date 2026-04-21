import { useMemo, useState } from 'react';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  DataObjectIcon,
  InfoIcon
} from '../../../../util/chatIcons';
import type {
  WebModelSelectorConfig,
  WebModelSelectorState,
  WebSelectModelResponse
} from '../../../../util/chatTypes';
import { CharacterCardModelBindingSwitchConfirmDialog } from './CharacterCardModelBindingSwitchConfirmDialog';

const AUTO_GLM_WARNING =
  '禁止使用autoglm作为对话主模型。对话模型和ui控制模型是分离的，请选择任意一个别的聪明的大模型。如有疑问，请仔细阅读文档学习软件的模型配置机制。';
const MODEL_SELECTOR_INFO =
  '在这里选择一个已经配置好的模型，或者点击下方的管理配置去新建或修改模型';

type PendingSelection = {
  configId: string;
  modelIndex: number;
};

function currentModelName(selector: WebModelSelectorState | null) {
  const value = selector?.current_model_name?.trim();
  return value ? value : '未选择';
}

function configModelSummary(config: WebModelSelectorConfig) {
  if (config.models.length > 1) {
    return `${config.models.length}个模型`;
  }
  return config.model_name || '未选择';
}

export function ModelSelectorPanel({
  selector,
  loading,
  expanded,
  onExpandedChange,
  onSelectModel,
  allowCollapse = true,
  onManageModels,
  onSelectionCommitted
}: {
  selector: WebModelSelectorState | null;
  loading?: boolean;
  expanded: boolean;
  onExpandedChange: (value: boolean) => void;
  onSelectModel: (
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch?: boolean
  ) => Promise<WebSelectModelResponse | null>;
  allowCollapse?: boolean;
  onManageModels?: (() => void) | null;
  onSelectionCommitted?: (() => void) | null;
}) {
  const [expandedConfigId, setExpandedConfigId] = useState<string | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [pendingSelection, setPendingSelection] = useState<PendingSelection | null>(null);
  const [localMessage, setLocalMessage] = useState<string | null>(null);
  const modelName = useMemo(() => currentModelName(selector), [selector]);

  async function runSelection(configId: string, modelIndex: number, confirmCharacterCardSwitch = false) {
    const response = await onSelectModel(configId, modelIndex, confirmCharacterCardSwitch);
    if (!response) {
      return;
    }

    if (response.requires_character_card_switch_confirmation) {
      setPendingSelection({ configId, modelIndex });
      return;
    }

    if (response.success) {
      setPendingSelection(null);
      setLocalMessage(null);
      setExpandedConfigId(null);
      if (allowCollapse) {
        onExpandedChange(false);
      }
      onSelectionCommitted?.();
    }
  }

  async function handleSelect(config: WebModelSelectorConfig, modelIndex: number) {
    const modelNameValue = config.models[modelIndex] ?? '';
    if (modelNameValue.toLowerCase().includes('autoglm')) {
      setLocalMessage(AUTO_GLM_WARNING);
      return;
    }

    await runSelection(config.id, modelIndex, false);
  }

  return (
    <>
      <div className="model-selector-panel">
        <div className="model-selector-header">
          <span className="model-selector-header-icon">
            <DataObjectIcon size={16} />
          </span>
          <button
            className="model-selector-info-button"
            onClick={() => setInfoOpen(true)}
            type="button"
          >
            <InfoIcon size={16} />
          </button>
          <button
            className={`model-selector-summary ${allowCollapse ? 'is-clickable' : ''}`}
            onClick={() => {
              if (allowCollapse) {
                onExpandedChange(!expanded);
              }
            }}
            type="button"
          >
            <span className="model-selector-summary-label">模型:</span>
            <span className="model-selector-summary-value">{modelName}</span>
            {allowCollapse ? (
              expanded ? <ChevronUpIcon size={20} /> : <ChevronDownIcon size={20} />
            ) : null}
          </button>
        </div>

        {expanded ? (
          <div className="model-selector-body">
            {loading ? <div className="model-selector-empty">正在加载模型配置...</div> : null}

            {!loading && !selector?.configs.length ? (
              <div className="model-selector-empty">没有可用的模型</div>
            ) : null}

            {!loading
              ? selector?.configs.map((config) => {
                  const isSelected = config.selected;
                  const hasMultipleModels = config.models.length > 1;
                  const isExpanded = expandedConfigId === config.id;

                  return (
                    <div className="model-selector-config-block" key={config.id}>
                      <button
                        className={`model-selector-config-row ${isSelected ? 'is-selected' : ''}`}
                        onClick={() => {
                          if (hasMultipleModels) {
                            setExpandedConfigId(isExpanded ? null : config.id);
                            return;
                          }
                          void handleSelect(config, 0);
                        }}
                        type="button"
                      >
                        <span className="model-selector-config-name">{config.name}</span>
                        {hasMultipleModels ? (
                          <span className="model-selector-config-tail">
                            <span className="model-selector-config-count">
                              {config.models.length}个模型
                            </span>
                            {isExpanded ? (
                              <ChevronUpIcon size={16} />
                            ) : (
                              <ChevronDownIcon size={16} />
                            )}
                          </span>
                        ) : (
                          <span className="model-selector-config-model">
                            {configModelSummary(config)}
                          </span>
                        )}
                      </button>

                      {hasMultipleModels && isExpanded ? (
                        <div className="model-selector-model-list">
                          {config.models.map((item, index) => {
                            const isModelSelected =
                              config.selected && config.selected_model_index === index;
                            return (
                              <button
                                className={`model-selector-model-row ${isModelSelected ? 'is-selected' : ''}`}
                                key={`${config.id}-${item}-${index}`}
                                onClick={() => {
                                  void handleSelect(config, index);
                                }}
                                type="button"
                              >
                                {item}
                              </button>
                            );
                          })}
                        </div>
                      ) : null}
                    </div>
                  );
                })
              : null}

            {onManageModels ? (
              <button className="model-selector-manage-button" onClick={onManageModels} type="button">
                管理配置
              </button>
            ) : null}

            {selector?.locked_by_character_card ? (
              <div className="model-selector-lock-hint">
                当前角色卡已固定模型: {selector.locked_character_card_name || '当前角色'}
              </div>
            ) : null}

            {localMessage ? <div className="model-selector-local-message">{localMessage}</div> : null}
          </div>
        ) : null}
      </div>

      {infoOpen ? (
        <div className="dialog-scrim" onClick={() => setInfoOpen(false)} role="presentation">
          <div
            className="history-dialog model-selector-info-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header>
              <h3>模型配置</h3>
              <p>{MODEL_SELECTOR_INFO}</p>
            </header>
            <footer>
              <button onClick={() => setInfoOpen(false)} type="button">
                我知道了
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      <CharacterCardModelBindingSwitchConfirmDialog
        onConfirm={() => {
          if (!pendingSelection) {
            return;
          }
          void runSelection(
            pendingSelection.configId,
            pendingSelection.modelIndex,
            true
          );
        }}
        onDismiss={() => setPendingSelection(null)}
        open={pendingSelection !== null}
      />
    </>
  );
}
