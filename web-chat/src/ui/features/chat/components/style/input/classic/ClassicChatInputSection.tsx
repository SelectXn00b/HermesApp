import { useEffect, useRef, useState } from 'react';
import { uploadedAttachmentToMessageAttachment } from '../../../../attachments/AttachmentUtils';
import { AttachmentChip } from '../../../AttachmentChip';
import { AttachmentSelector } from '../../../AttachmentSelector';
import { FullscreenInputDialog } from '../../../FullscreenInputDialog';
import { SimpleLinearProgressIndicator } from '../../../SimpleLinearProgressIndicator';
import {
  FullscreenIcon,
  MicIcon,
  PlusIcon,
  SendIcon,
  StopIcon
} from '../../../../util/chatIcons';
import { ClassicChatSettingsBar } from './ClassicChatSettingsBar';
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

export function ClassicChatInputSection({
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
  const [settingsOpen, setSettingsOpen] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const canSendMessage = messageInput.trim().length > 0 || pendingUploads.length > 0;
  const showQueueAction = isLoading && messageInput.trim().length > 0;
  const showCancelAction = isLoading && !showQueueAction;
  const showProcessingStatus = showInputProcessingStatus && inputProcessingStage !== 'idle';
  const processingProgress =
    inputProcessingStage === 'streaming' ? 0.76 : inputProcessingStage === 'uploading' ? 0.52 : 0.38;

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }
    textarea.style.height = '0px';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 120)}px`;
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
    <div className="classic-chat-input-section">
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
        <div className="input-processing-status is-classic">
          <SimpleLinearProgressIndicator progress={processingProgress} />
          <div className="input-processing-status-message">{processingLabel(inputProcessingStage)}</div>
        </div>
      ) : null}

      {pendingUploads.length ? (
        <div className="composer-attachment-strip is-classic">
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

      <div className="classic-input-row">
        <label className="classic-input-field">
          <textarea
            onChange={(event) => onMessageInputChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                submitCurrentAction();
              }
            }}
            placeholder="发消息给当前会话..."
            ref={textareaRef}
            rows={1}
            value={messageInput}
          />
          <button className="classic-input-fullscreen" onClick={() => setFullscreenOpen(true)} type="button">
            <FullscreenIcon size={16} />
          </button>
        </label>

        <button
          className={`classic-input-circle ${attachmentPanelOpen ? 'is-active' : ''}`}
          onClick={() => {
            setSettingsOpen(false);
            onAttachmentPanelChange(!attachmentPanelOpen);
          }}
          title="附件"
          type="button"
        >
          <PlusIcon size={18} />
        </button>

        <button
          className={[
            'classic-input-circle',
            'primary',
            showQueueAction ? 'is-queue' : '',
            showCancelAction ? 'is-danger' : ''
          ]
            .filter(Boolean)
            .join(' ')}
          onClick={submitCurrentAction}
          type="button"
        >
          {showCancelAction ? <StopIcon size={18} /> : canSendMessage ? <SendIcon size={18} /> : <MicIcon size={18} />}
        </button>
      </div>

      <ClassicChatSettingsBar
        contextPercent={contextPercent}
        modelSelector={modelSelector}
        modelSelectorLoading={modelSelectorLoading}
        onSelectModelConfig={onSelectModelConfig}
        onToggleSettings={() => {
          onAttachmentPanelChange(false);
          setSettingsOpen(!settingsOpen);
        }}
        settingsOpen={settingsOpen}
      />

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
