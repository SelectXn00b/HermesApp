import { useEffect, useRef, useState } from 'react';
import { ChatArea } from './ChatArea';
import { CharacterSelectorPanel } from './CharacterSelectorPanel';
import { ChatHistorySelector } from './ChatHistorySelector';
import { ChatScreenHeader } from './ChatScreenHeader';
import { AgentChatInputSection } from './style/input/agent/AgentChatInputSection';
import { ClassicChatInputSection } from './style/input/classic/ClassicChatInputSection';
import type { ChatViewModel } from '../viewmodel/ChatViewModel';
import {
  getIsFloatingMode,
  toggleFloatingWindow
} from '../viewmodel/FloatingWindowDelegate';

export function ChatScreenContent({
  viewModel
}: {
  viewModel: ChatViewModel;
}) {
  const headerRef = useRef<HTMLDivElement | null>(null);
  const composerHostRef = useRef<HTMLDivElement | null>(null);
  const [headerHeight, setHeaderHeight] = useState(0);
  const [composerHeight, setComposerHeight] = useState(0);
  const [isFloatingMode, setIsFloatingMode] = useState(getIsFloatingMode());
  const overlayMode = Boolean(viewModel.theme?.header.overlay);
  const showInputProcessingStatus =
    viewModel.theme?.show_input_processing_status ?? viewModel.boot?.show_input_processing_status ?? true;

  useEffect(() => {
    const element = headerRef.current;
    if (!element || typeof ResizeObserver === 'undefined') {
      setHeaderHeight(element?.getBoundingClientRect().height ?? 0);
      return;
    }

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      setHeaderHeight(entry?.contentRect.height ?? 0);
    });
    observer.observe(element);
    setHeaderHeight(element.getBoundingClientRect().height);
    return () => observer.disconnect();
  }, [overlayMode, viewModel.activeCharacterName, viewModel.historyOpen, viewModel.selectedChatId]);

  useEffect(() => {
    const element = composerHostRef.current;
    if (!element || typeof ResizeObserver === 'undefined') {
      setComposerHeight(element?.getBoundingClientRect().height ?? 0);
      return;
    }

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      setComposerHeight(entry?.contentRect.height ?? 0);
    });

    observer.observe(element);
    setComposerHeight(element.getBoundingClientRect().height);
    return () => observer.disconnect();
  }, [
    viewModel.activeInputStyle,
    viewModel.attachmentPanelOpen,
    viewModel.error,
    viewModel.inputProcessingStage,
    viewModel.isPendingQueueExpanded,
    viewModel.pendingQueueMessages.length,
    viewModel.pendingUploads.length
  ]);

  useEffect(() => {
    function handleFullscreenChange() {
      setIsFloatingMode(Boolean(document.fullscreenElement));
    }

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  const composer = viewModel.activeInputStyle === 'agent' ? (
    <AgentChatInputSection
      attachmentPanelOpen={viewModel.attachmentPanelOpen}
      contextPercent={viewModel.contextStats.percent}
      inputProcessingStage={viewModel.inputProcessingStage}
      isLoading={viewModel.isStreaming || viewModel.isConnecting}
      isPendingQueueExpanded={viewModel.isPendingQueueExpanded}
      messageInput={viewModel.messageInput}
      modelSelector={viewModel.modelSelector}
      modelSelectorLoading={viewModel.modelSelectorLoading}
      onAttachmentPanelChange={viewModel.setAttachmentPanelOpen}
      onCancelMessage={viewModel.cancelCurrentMessage}
      onDeletePendingQueueMessage={viewModel.deletePendingQueueMessage}
      onEditPendingQueueMessage={viewModel.editPendingQueueMessage}
      onMessageInputChange={viewModel.setMessageInput}
      onPendingQueueExpandedChange={viewModel.setPendingQueueExpanded}
      onQueueMessage={viewModel.queueDraftMessage}
      onRemovePendingUpload={viewModel.removePendingUpload}
      onSelectModelConfig={viewModel.selectModelConfig}
      onSendMessage={viewModel.sendMessage}
      onSendPendingQueueMessage={viewModel.sendPendingQueueMessage}
      onUploadFiles={viewModel.uploadFiles}
      pendingQueueMessages={viewModel.pendingQueueMessages}
      pendingUploads={viewModel.pendingUploads}
      showInputProcessingStatus={showInputProcessingStatus}
      theme={viewModel.theme}
    />
  ) : (
    <ClassicChatInputSection
      attachmentPanelOpen={viewModel.attachmentPanelOpen}
      contextPercent={viewModel.contextStats.percent}
      inputProcessingStage={viewModel.inputProcessingStage}
      isLoading={viewModel.isStreaming || viewModel.isConnecting}
      isPendingQueueExpanded={viewModel.isPendingQueueExpanded}
      messageInput={viewModel.messageInput}
      modelSelector={viewModel.modelSelector}
      modelSelectorLoading={viewModel.modelSelectorLoading}
      onAttachmentPanelChange={viewModel.setAttachmentPanelOpen}
      onCancelMessage={viewModel.cancelCurrentMessage}
      onDeletePendingQueueMessage={viewModel.deletePendingQueueMessage}
      onEditPendingQueueMessage={viewModel.editPendingQueueMessage}
      onMessageInputChange={viewModel.setMessageInput}
      onPendingQueueExpandedChange={viewModel.setPendingQueueExpanded}
      onQueueMessage={viewModel.queueDraftMessage}
      onRemovePendingUpload={viewModel.removePendingUpload}
      onSelectModelConfig={viewModel.selectModelConfig}
      onSendMessage={viewModel.sendMessage}
      onSendPendingQueueMessage={viewModel.sendPendingQueueMessage}
      onUploadFiles={viewModel.uploadFiles}
      pendingQueueMessages={viewModel.pendingQueueMessages}
      pendingUploads={viewModel.pendingUploads}
      showInputProcessingStatus={showInputProcessingStatus}
      theme={viewModel.theme}
    />
  );

  return (
    <div className="chat-screen-content">
      <CharacterSelectorPanel
        activePrompt={viewModel.characterSelector?.active_prompt ?? null}
        loading={viewModel.characterSelectorLoading}
        onClose={() => viewModel.setCharacterSelectorOpen(false)}
        onSelectTarget={(target) => {
          void viewModel.switchActivePrompt(target);
        }}
        open={viewModel.characterSelectorOpen}
        selector={viewModel.characterSelector}
      />

      <ChatHistorySelector
        busy={viewModel.isBusy || viewModel.historyLoading}
        chats={viewModel.chats}
        onClose={() => viewModel.setHistoryOpen(false)}
        onCreateChat={viewModel.createConversation}
        onDeleteChat={viewModel.deleteConversation}
        onRenameChat={viewModel.renameConversation}
        onSearchChange={viewModel.setSearch}
        onSelectChat={viewModel.selectChat}
        open={viewModel.historyOpen}
        search={viewModel.search}
        selectedChatId={viewModel.selectedChatId}
        streaming={viewModel.isStreaming}
      />

      <div className={`chat-screen-frame ${overlayMode ? 'is-overlay-mode' : ''}`}>
        <div className={`chat-screen-main ${overlayMode ? 'is-overlay-mode' : ''}`}>
          <div className={`chat-screen-header-layer ${overlayMode ? 'is-overlay-mode' : ''}`} ref={headerRef}>
            <ChatScreenHeader
              activeCharacterAvatarUrl={viewModel.activeCharacterAvatarUrl}
              activeCharacterName={viewModel.activeCharacterName}
              contextCurrentValue={viewModel.contextStats.currentValue}
              contextLabel={`上下文 ${viewModel.contextStats.percent}%`}
              contextMaxValue={viewModel.contextStats.maxValue}
              contextPercent={viewModel.contextStats.percent}
              isConnecting={viewModel.isConnecting}
              isFloatingMode={isFloatingMode}
              isStreaming={viewModel.isStreaming}
              onCharacterSwitcherClick={() =>
                viewModel.setCharacterSelectorOpen(!viewModel.characterSelectorOpen)
              }
              onLaunchFloatingWindow={() => {
                void toggleFloatingWindow();
              }}
              onToggleChatHistorySelector={() => viewModel.setHistoryOpen(!viewModel.historyOpen)}
              runningTaskCount={viewModel.activeStreamingCount}
              showChatHistorySelector={viewModel.historyOpen}
            />
          </div>
          <ChatArea
            autoScrollToBottom={viewModel.autoScrollToBottom}
            bottomPadding={composerHeight}
            chatHistory={viewModel.messages}
            chatStyle={viewModel.activeChatStyle}
            currentChatId={viewModel.selectedChatId}
            hasMoreHistoryBefore={viewModel.hasMoreHistoryBefore}
            isLoading={viewModel.isStreaming}
            isConversationLoading={viewModel.isConnecting}
            isLoadingHistoryBefore={viewModel.isLoadingHistoryBefore}
            onLoadOlder={viewModel.loadOlderMessages}
            onAutoScrollToBottomChange={viewModel.setAutoScrollToBottom}
            theme={viewModel.theme}
            topPadding={overlayMode ? headerHeight + 4 : 0}
          />
        </div>

        <div
          className={[
            'chat-composer-host',
            viewModel.theme?.input.floating ? 'is-floating' : '',
            viewModel.theme?.input.transparent ? 'is-transparent' : '',
            viewModel.theme?.input.liquid_glass ? 'is-liquid-glass' : '',
            viewModel.theme?.input.water_glass ? 'is-water-glass' : '',
            viewModel.activeInputStyle === 'agent' ? 'is-agent' : 'is-classic'
          ]
            .filter(Boolean)
            .join(' ')}
          ref={composerHostRef}
        >
          {composer}
          {viewModel.error ? <div className="chat-inline-error">{viewModel.error}</div> : null}
        </div>
      </div>
    </div>
  );
}
