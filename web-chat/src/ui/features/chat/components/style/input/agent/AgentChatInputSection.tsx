import { useEffect, useRef, useState } from 'react';
import { uploadedAttachmentToMessageAttachment } from '../../../../attachments/AttachmentUtils';
import { AttachmentChip } from '../../../AttachmentChip';
import { AttachmentSelector } from '../../../AttachmentSelector';
import { FullscreenInputDialog } from '../../../FullscreenInputDialog';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  FullscreenIcon,
  HistoryIcon,
  LinkIcon,
  MicIcon,
  PlusIcon,
  SendIcon,
  StopIcon,
  TuneIcon
} from '../../../../util/chatIcons';
import { InputOverlayPopup } from '../common/InputOverlayPopup';
import { ModelSelectorPanel } from '../common/ModelSelectorPanel';
import { PendingMessageQueuePanel } from '../common/PendingMessageQueuePanel';
import type {
  InputProcessingStage,
  PendingQueueMessageItem,
  WebModelSelectorState,
  WebSelectModelResponse,
  WebThemeSnapshot,
  WebUploadedAttachment
} from '../../../../util/chatTypes';

function processingLabel(stage: InputProcessingStage) {
  if (stage === 'connecting') return '正在同步会话与主题';
  if (stage === 'uploading') return '正在上传附件';
  if (stage === 'streaming') return '正在接收回复';
  return '';
}

export function AgentChatInputSection({
  messageInput,
  onMessageInputChange,
  onSendMessage,
  onQueueMessage,
  onCancelMessage,
  onUploadFiles,
  pendingUploads,
  onRemovePendingUpload,
  isLoading,
  inputProcessingStage,
  showInputProcessingStatus,
  attachmentPanelOpen,
  onAttachmentPanelChange,
  pendingQueueMessages,
  isPendingQueueExpanded,
  onPendingQueueExpandedChange,
  onDeletePendingQueueMessage,
  onEditPendingQueueMessage,
  onSendPendingQueueMessage,
  modelSelector,
  modelSelectorLoading,
  onSelectModelConfig,
  contextPercent
}: {
  messageInput: string;
  onMessageInputChange: (value: string) => void;
  onSendMessage: () => Promise<void>;
  onQueueMessage: () => void;
  onCancelMessage: () => void;
  onUploadFiles: (files: FileList | File[]) => Promise<void>;
  pendingUploads: WebUploadedAttachment[];
  onRemovePendingUpload: (attachmentId: string) => void;
  isLoading: boolean;
  inputProcessingStage: InputProcessingStage;
  showInputProcessingStatus: boolean;
  attachmentPanelOpen: boolean;
  onAttachmentPanelChange: (value: boolean) => void;
  pendingQueueMessages: PendingQueueMessageItem[];
  isPendingQueueExpanded: boolean;
  onPendingQueueExpandedChange: (value: boolean) => void;
  onDeletePendingQueueMessage: (id: number) => void;
  onEditPendingQueueMessage: (id: number) => void;
  onSendPendingQueueMessage: (id: number) => Promise<void>;
  modelSelector: WebModelSelectorState | null;
  modelSelectorLoading: boolean;
  onSelectModelConfig: (
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch?: boolean
  ) => Promise<WebSelectModelResponse | null>;
  contextPercent: number;
  theme: WebThemeSnapshot | null;
}) {
  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const [showExtraSettings, setShowExtraSettings] = useState(false);
  const [showModelSelector, setShowModelSelector] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const canSendMessage = messageInput.trim().length > 0 || pendingUploads.length > 0;
  const showQueueAction = isLoading && messageInput.trim().length > 0;
  const showCancelAction = isLoading && !showQueueAction;
  const showProcessingStatus = showInputProcessingStatus && inputProcessingStage !== 'idle';
  const processingProgress =
    inputProcessingStage === 'streaming' ? 0.82 : inputProcessingStage === 'uploading' ? 0.56 : 0.4;
  const modelLabel = (() => {
    const currentModelName = modelSelector?.current_model_name?.trim();
    if (!currentModelName) {
      return '模型配置';
    }
    return currentModelName.length > 26 ? `${currentModelName.slice(0, 26)}...` : currentModelName;
  })();
  const progressRadius = 18;
  const circumference = 2 * Math.PI * progressRadius;
  const dashOffset = circumference - processingProgress * circumference;

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }
    textarea.style.height = '0px';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 144)}px`;
  }, [messageInput]);

  function submitCurrentAction() {
    if (showCancelAction) {
      onCancelMessage();
      return;
    }
    if (showQueueAction) {
      onQueueMessage();
      return;
    }
    if (canSendMessage) {
      void onSendMessage();
    }
  }

  return (
    <div className="agent-chat-input-section">
      <PendingMessageQueuePanel
        expanded={isPendingQueueExpanded}
        onDeleteMessage={onDeletePendingQueueMessage}
        onEditMessage={onEditPendingQueueMessage}
        onExpandedChange={onPendingQueueExpandedChange}
        onSendMessage={(id) => {
          void onSendPendingQueueMessage(id);
        }}
        queuedMessages={pendingQueueMessages}
      />

      {showProcessingStatus ? (
        <div className="input-processing-status is-agent">
          <div className="input-processing-status-message">{processingLabel(inputProcessingStage)}</div>
        </div>
      ) : null}

      {pendingUploads.length ? (
        <div className="composer-attachment-strip is-agent">
          {pendingUploads.map((upload) => (
            <AttachmentChip
              attachment={uploadedAttachmentToMessageAttachment(upload)}
              key={upload.attachment_id}
              onRemove={onRemovePendingUpload}
              removable
            />
          ))}
        </div>
      ) : null}

      <div className="agent-input-card">
        <label className="agent-input-field">
          <textarea
            onChange={(event) => onMessageInputChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                submitCurrentAction();
              }
            }}
            placeholder="请输入您的问题..."
            ref={textareaRef}
            rows={1}
            value={messageInput}
          />
          <button className="agent-input-fullscreen" onClick={() => setFullscreenOpen(true)} type="button">
            <FullscreenIcon size={16} />
          </button>
        </label>

        <div className="agent-input-bottom">
          <div className="agent-model-slot">
            <button
              aria-expanded={showModelSelector}
              className={`agent-model-pill ${showModelSelector ? 'is-active' : ''}`}
              onClick={() => {
                onAttachmentPanelChange(false);
                setShowExtraSettings(false);
                setShowModelSelector(!showModelSelector);
              }}
              type="button"
            >
              <strong>{modelLabel || '未选择'}</strong>
              {showModelSelector ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
            </button>
          </div>

          <button
            className={`agent-icon-button ${showExtraSettings ? 'is-active' : ''}`}
            onClick={() => {
              onAttachmentPanelChange(false);
              setShowModelSelector(false);
              setShowExtraSettings(!showExtraSettings);
            }}
            title="设置选项"
            type="button"
          >
            <TuneIcon size={20} />
          </button>

          <button
            className={`agent-icon-button ${attachmentPanelOpen ? 'is-active' : ''}`}
            onClick={() => {
              setShowModelSelector(false);
              setShowExtraSettings(false);
              onAttachmentPanelChange(!attachmentPanelOpen);
            }}
            title="附件"
            type="button"
          >
            <PlusIcon size={24} />
          </button>

          <div className="agent-action-orb-shell">
            {showProcessingStatus ? (
              <svg className="agent-action-progress" viewBox="0 0 44 44">
                <circle className="agent-action-progress-track" cx="22" cy="22" r={progressRadius} />
                <circle
                  className="agent-action-progress-value"
                  cx="22"
                  cy="22"
                  r={progressRadius}
                  strokeDasharray={circumference}
                  strokeDashoffset={dashOffset}
                />
              </svg>
            ) : null}
            <button
              className={[
                'agent-action-orb',
                showQueueAction ? 'is-queue' : '',
                showCancelAction ? 'is-danger' : ''
              ]
                .filter(Boolean)
                .join(' ')}
              onClick={submitCurrentAction}
              type="button"
            >
              {showCancelAction ? (
                <StopIcon size={18} />
              ) : canSendMessage ? (
                <SendIcon size={18} />
              ) : (
                <MicIcon size={18} />
              )}
            </button>
          </div>
        </div>

      </div>

      {showModelSelector ? (
        <InputOverlayPopup onDismiss={() => setShowModelSelector(false)} panelClassName="agent-popup-card">
          <div className="agent-popup-scroll">
            <button className="agent-popup-row is-expandable" type="button">
              <span className="agent-popup-row-icon">
                <TuneIcon size={16} />
              </span>
              <span className="agent-popup-row-copy is-inline">
                <strong>思考:</strong>
                <em>关闭</em>
              </span>
              <span className="agent-popup-row-tail">
                <ChevronDownIcon size={18} />
              </span>
            </button>

            <button className="agent-popup-row" type="button">
              <span className="agent-popup-row-icon">
                <LinkIcon size={16} />
              </span>
              <span className="agent-popup-row-copy is-inline">
                <strong>Max模式</strong>
                <em>关闭</em>
              </span>
            </button>

            <div className="agent-popup-section">
              <ModelSelectorPanel
                allowCollapse={false}
                expanded
                loading={modelSelectorLoading}
                onExpandedChange={() => {}}
                onSelectModel={onSelectModelConfig}
                onSelectionCommitted={() => setShowModelSelector(false)}
                selector={modelSelector}
              />
            </div>
          </div>
        </InputOverlayPopup>
      ) : null}

      {showExtraSettings ? (
        <InputOverlayPopup onDismiss={() => setShowExtraSettings(false)} panelClassName="agent-popup-card">
          <div className="agent-popup-scroll">
            <button className="agent-popup-row is-expandable" type="button">
              <span className="agent-popup-row-icon">
                <HistoryIcon size={16} />
              </span>
              <span className="agent-popup-row-copy is-inline">
                <strong>记忆</strong>
                <em>默认</em>
              </span>
              <span className="agent-popup-row-tail">
                <ChevronDownIcon size={18} />
              </span>
            </button>

            <button className="agent-popup-row" type="button">
              <span className="agent-popup-row-icon">
                <LinkIcon size={16} />
              </span>
              <span className="agent-popup-row-copy is-inline">
                <strong>记忆附着</strong>
                <em>关闭</em>
              </span>
            </button>

            <button className="agent-popup-row" type="button">
              <span className="agent-popup-row-icon">
                <TuneIcon size={16} />
              </span>
              <span className="agent-popup-row-copy is-inline">
                <strong>设置选项</strong>
                <em>{contextPercent}%</em>
              </span>
            </button>
          </div>
        </InputOverlayPopup>
      ) : null}

      <AttachmentSelector
        onDismiss={() => onAttachmentPanelChange(false)}
        onUploadFiles={(files) => {
          void onUploadFiles(files);
        }}
        visible={attachmentPanelOpen}
      />

      {fullscreenOpen ? (
        <FullscreenInputDialog
          onConfirm={() => setFullscreenOpen(false)}
          onDismiss={() => setFullscreenOpen(false)}
          onValueChange={onMessageInputChange}
          value={messageInput}
        />
      ) : null}
    </div>
  );
}
