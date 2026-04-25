import { useEffect, useMemo, useRef, useState } from 'react';
import {
  bootstrap,
  createChat,
  deleteChat,
  getCharacterSelector,
  getMessages,
  getModelSelector,
  getTheme,
  listChats,
  renameChat,
  selectModel as selectModelOnServer,
  selectChat as selectChatOnServer,
  setActivePrompt as setActivePromptOnServer,
  streamMessage,
  uploadAttachment
} from '../util/chatApi';
import {
  clearStoredToken,
  readStoredToken,
  writeStoredToken
} from '../util/ConfigurationStateHolder';
import type {
  ChatStyle,
  ContextStatsSnapshot,
  InputProcessingStage,
  InputStyle,
  PendingQueueMessageItem,
  WebActivePromptSnapshot,
  WebActivePromptTarget,
  WebBootstrapResponse,
  WebChatMessage,
  WebCharacterSelectorResponse,
  WebChatSummary,
  WebModelSelectorState,
  WebSelectModelResponse,
  WebThemeSnapshot,
  WebUploadedAttachment
} from '../util/chatTypes';
import {
  buildContextStats,
  buildVisibleChats,
  handleUnauthorizedError,
  normalizeError
} from './UiStateDelegate';

const INITIAL_MESSAGES_PAGE_SIZE = 24;

function buildCreateChatBinding(activePrompt: WebActivePromptSnapshot | null) {
  if (!activePrompt) {
    return {
      character_card_name: null,
      character_group_id: null
    };
  }

  if (activePrompt.type === 'character_group') {
    return {
      character_card_name: null,
      character_group_id: activePrompt.id
    };
  }

  return {
    character_card_name: activePrompt.name,
    character_group_id: null
  };
}

function formatOutgoingAttachments(uploads: WebUploadedAttachment[]) {
  return uploads.map((upload) => ({
    id: upload.attachment_id,
    file_name: upload.file_name,
    mime_type: upload.mime_type,
    file_size: upload.file_size
  }));
}

function mergeLatestConversationPage(
  existingMessages: WebChatMessage[],
  latestMessages: WebChatMessage[]
) {
  if (!latestMessages.length) {
    return existingMessages;
  }

  const latestIds = new Set(latestMessages.map((message) => message.id));
  const oldestLatestTimestamp = latestMessages[0]?.timestamp ?? Number.NEGATIVE_INFINITY;
  const preservedOlderMessages = existingMessages.filter(
    (message) => !latestIds.has(message.id) && message.timestamp < oldestLatestTimestamp
  );
  return preservedOlderMessages.concat(latestMessages);
}

function prependOlderMessages(
  existingMessages: WebChatMessage[],
  olderMessages: WebChatMessage[]
) {
  if (!olderMessages.length) {
    return existingMessages;
  }

  const existingIds = new Set(existingMessages.map((message) => message.id));
  return olderMessages
    .filter((message) => !existingIds.has(message.id))
    .concat(existingMessages);
}

function appendStreamingAssistantDelta(
  existingMessages: WebChatMessage[],
  assistantMessageId: string,
  delta: string,
  partialMessage?: WebChatMessage | null
) {
  let found = false;
  const nextMessages = existingMessages.map((message) => {
    if (message.id !== assistantMessageId) {
      return message;
    }

    found = true;
    return {
      ...message,
      ...(partialMessage
        ? {
            ...partialMessage,
            id: assistantMessageId
          }
        : {}),
      content_raw: partialMessage?.content_raw ?? message.content_raw + delta,
      content_blocks: partialMessage?.content_blocks ?? message.content_blocks,
      streaming: true
    };
  });

  if (found) {
    return nextMessages;
  }

  return nextMessages.concat({
    ...(partialMessage
      ? {
          ...partialMessage,
          id: assistantMessageId
        }
      : {
          id: assistantMessageId,
          sender: 'assistant' as const,
          content_raw: delta,
          timestamp: Date.now(),
          attachments: []
        }),
    streaming: true
  });
}

function finalizeStreamingAssistantMessage(
  existingMessages: WebChatMessage[],
  assistantMessageId: string,
  finalMessage?: WebChatMessage | null
) {
  let found = false;
  const nextMessages = existingMessages.map((message) => {
    if (message.id !== assistantMessageId) {
      return message;
    }

    found = true;
    return finalMessage
      ? {
          ...finalMessage,
          streaming: false
        }
      : {
          ...message,
          streaming: false
        };
  });

  if (found || !finalMessage) {
    return nextMessages;
  }

  return nextMessages.concat({
    ...finalMessage,
    streaming: false
  });
}

export interface ChatViewModelState {
  token: string;
  tokenDraft: string;
  boot: WebBootstrapResponse | null;
  theme: WebThemeSnapshot | null;
  characterSelector: WebCharacterSelectorResponse | null;
  characterSelectorOpen: boolean;
  characterSelectorLoading: boolean;
  modelSelector: WebModelSelectorState | null;
  modelSelectorLoading: boolean;
  chats: WebChatSummary[];
  visibleChats: WebChatSummary[];
  selectedChatId: string | null;
  selectedChat: WebChatSummary | null;
  messages: WebChatMessage[];
  messageInput: string;
  search: string;
  pendingUploads: WebUploadedAttachment[];
  pendingQueueMessages: PendingQueueMessageItem[];
  isPendingQueueExpanded: boolean;
  error: string | null;
  isConnecting: boolean;
  isBusy: boolean;
  isStreaming: boolean;
  historyOpen: boolean;
  historyLoading: boolean;
  hasMoreHistoryBefore: boolean;
  isLoadingHistoryBefore: boolean;
  attachmentPanelOpen: boolean;
  inputProcessingStage: InputProcessingStage;
  activeChatStyle: ChatStyle;
  activeInputStyle: InputStyle;
  activeCharacterName: string;
  activeCharacterAvatarUrl?: string | null;
  showConnectionOverlay: boolean;
  activeStreamingCount: number;
  contextStats: ContextStatsSnapshot;
  autoScrollToBottom: boolean;
}

export interface ChatViewModelActions {
  setTokenDraft: (value: string) => void;
  submitToken: () => void;
  createConversation: (options?: { group?: string | null }) => Promise<void>;
  renameConversation: (chat: WebChatSummary, title: string) => Promise<void>;
  deleteConversation: (chat: WebChatSummary) => Promise<void>;
  selectChat: (chatId: string) => void;
  setMessageInput: (value: string) => void;
  setSearch: (value: string) => void;
  uploadFiles: (files: FileList | File[]) => Promise<void>;
  removePendingUpload: (attachmentId: string) => void;
  sendMessage: () => Promise<void>;
  cancelCurrentMessage: () => void;
  setHistoryOpen: (value: boolean) => void;
  setCharacterSelectorOpen: (value: boolean) => void;
  switchActivePrompt: (target: WebActivePromptTarget) => Promise<void>;
  selectModelConfig: (
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch?: boolean
  ) => Promise<WebSelectModelResponse | null>;
  loadOlderMessages: () => Promise<void>;
  setAttachmentPanelOpen: (value: boolean) => void;
  queueDraftMessage: () => void;
  setPendingQueueExpanded: (value: boolean) => void;
  deletePendingQueueMessage: (id: number) => void;
  editPendingQueueMessage: (id: number) => void;
  sendPendingQueueMessage: (id: number) => Promise<void>;
  setAutoScrollToBottom: (value: boolean) => void;
}

export type ChatViewModel = ChatViewModelState & ChatViewModelActions;

export function useChatViewModel(): ChatViewModel {
  const [token, setToken] = useState<string>(() => readStoredToken());
  const [tokenDraft, setTokenDraft] = useState<string>(() => readStoredToken());
  const [boot, setBoot] = useState<WebBootstrapResponse | null>(null);
  const [theme, setTheme] = useState<WebThemeSnapshot | null>(null);
  const [characterSelector, setCharacterSelector] = useState<WebCharacterSelectorResponse | null>(null);
  const [characterSelectorOpen, setCharacterSelectorOpenState] = useState(false);
  const [characterSelectorLoading, setCharacterSelectorLoading] = useState(false);
  const [modelSelector, setModelSelector] = useState<WebModelSelectorState | null>(null);
  const [modelSelectorLoading, setModelSelectorLoading] = useState(false);
  const [chats, setChats] = useState<WebChatSummary[]>([]);
  const [selectedChatId, setSelectedChatId] = useState<string | null>(null);
  const [messages, setMessages] = useState<WebChatMessage[]>([]);
  const [messageInput, setMessageInput] = useState('');
  const [search, setSearch] = useState('');
  const [pendingUploads, setPendingUploads] = useState<WebUploadedAttachment[]>([]);
  const [pendingQueueMessages, setPendingQueueMessages] = useState<PendingQueueMessageItem[]>([]);
  const [isPendingQueueExpanded, setPendingQueueExpanded] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setConnecting] = useState(Boolean(token));
  const [isBusy, setBusy] = useState(false);
  const [isStreaming, setStreaming] = useState(false);
  const [historyOpen, setHistoryOpenState] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [hasMoreHistoryBefore, setHasMoreHistoryBefore] = useState(false);
  const [isLoadingHistoryBefore, setLoadingHistoryBefore] = useState(false);
  const [attachmentPanelOpen, setAttachmentPanelOpen] = useState(false);
  const [inputProcessingStage, setInputProcessingStage] = useState<InputProcessingStage>('idle');
  const [autoScrollToBottom, setAutoScrollToBottom] = useState(true);
  const streamAbortRef = useRef<AbortController | null>(null);
  const queueIdRef = useRef(1);
  const skipNextConversationLoadRef = useRef(false);

  function handleApiFailure(loadError: unknown) {
    if (handleUnauthorizedError(loadError, () => setToken(''))) {
      setTokenDraft('');
      clearStoredToken();
    }
    setError(normalizeError(loadError));
  }

  async function refreshCharacterSelector(currentToken: string) {
    setCharacterSelectorLoading(true);
    try {
      const selectorData = await getCharacterSelector(currentToken);
      setCharacterSelector(selectorData);
    } finally {
      setCharacterSelectorLoading(false);
    }
  }

  async function refreshModelSelector(currentToken: string) {
    setModelSelectorLoading(true);
    try {
      const selectorData = await getModelSelector(currentToken);
      setModelSelector(selectorData);
      return selectorData;
    } finally {
      setModelSelectorLoading(false);
    }
  }

  async function refreshChats(currentToken: string) {
    const chatList = await listChats(currentToken);
    setChats(chatList);
    setHistoryLoaded(true);
    return chatList;
  }

  async function ensureChatsLoaded(currentToken: string, force = false) {
    if (historyLoading || (!force && historyLoaded)) {
      return;
    }

    setHistoryLoading(true);
    try {
      await refreshChats(currentToken);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function loadBootstrap(currentToken: string, preferredChatId?: string | null) {
    setConnecting(true);
    setInputProcessingStage('connecting');

    try {
      const [bootData, selectorData, modelSelectorData] = await Promise.all([
        bootstrap(currentToken),
        getCharacterSelector(currentToken),
        getModelSelector(currentToken)
      ]);
      setBoot(bootData);
      setCharacterSelector(selectorData);
      setModelSelector(modelSelectorData);
      const nextChatId = preferredChatId ?? bootData.current_chat_id ?? null;
      setSelectedChatId(nextChatId);
      if (!nextChatId) {
        setTheme(null);
        setMessages([]);
        setHasMoreHistoryBefore(false);
      }
      setError(null);
    } finally {
      setConnecting(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  async function loadConversation(
    currentToken: string,
    chatId: string,
    mode: 'replace' | 'merge-latest' = 'replace'
  ) {
    setConnecting(true);
    setInputProcessingStage((currentStage) => (isStreaming ? currentStage : 'connecting'));

    try {
      if (mode === 'replace') {
        await selectChatOnServer(currentToken, chatId);
      }
      const [themeData, messagePage, selectorData, modelSelectorData] = await Promise.all([
        getTheme(currentToken, chatId),
        getMessages(currentToken, chatId, { limit: INITIAL_MESSAGES_PAGE_SIZE }),
        getCharacterSelector(currentToken),
        getModelSelector(currentToken)
      ]);
      setTheme(themeData);
      setCharacterSelector(selectorData);
      setModelSelector(modelSelectorData);
      setMessages((currentMessages) =>
        mode === 'merge-latest'
          ? mergeLatestConversationPage(currentMessages, messagePage.messages)
          : messagePage.messages
      );
      setHasMoreHistoryBefore(messagePage.has_more_before);
      setError(null);
    } finally {
      setConnecting(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  useEffect(() => {
    if (!token) {
      setBoot(null);
      setTheme(null);
      setCharacterSelector(null);
      setCharacterSelectorOpenState(false);
      setCharacterSelectorLoading(false);
      setModelSelector(null);
      setModelSelectorLoading(false);
      setChats([]);
      setSelectedChatId(null);
      setMessages([]);
      setPendingUploads([]);
      setPendingQueueMessages([]);
      setConnecting(false);
      setHistoryLoaded(false);
      setHistoryLoading(false);
      setHasMoreHistoryBefore(false);
      setLoadingHistoryBefore(false);
      setInputProcessingStage('idle');
      return;
    }

    void loadBootstrap(token).catch((loadError: unknown) => {
      handleApiFailure(loadError);
      setConnecting(false);
      setInputProcessingStage('idle');
    });
  }, [token]);

  useEffect(() => {
    if (!token || !selectedChatId) {
      return;
    }
    if (skipNextConversationLoadRef.current) {
      skipNextConversationLoadRef.current = false;
      return;
    }

    void loadConversation(token, selectedChatId).catch((loadError: unknown) => {
      handleApiFailure(loadError);
    });
  }, [selectedChatId, token]);

  async function loadOlderMessages() {
    if (!token || !selectedChatId || !hasMoreHistoryBefore || isLoadingHistoryBefore) {
      return;
    }

    const oldestTimestamp = messages[0]?.timestamp;
    if (!oldestTimestamp) {
      return;
    }

    setLoadingHistoryBefore(true);
    try {
      const page = await getMessages(token, selectedChatId, {
        beforeTimestamp: oldestTimestamp,
        limit: INITIAL_MESSAGES_PAGE_SIZE
      });
      setMessages((currentMessages) => prependOlderMessages(currentMessages, page.messages));
      setHasMoreHistoryBefore(page.has_more_before);
    } catch (loadError: unknown) {
      handleApiFailure(loadError);
    } finally {
      setLoadingHistoryBefore(false);
    }
  }

  async function sendPreparedMessage(text: string, uploadsSnapshot: WebUploadedAttachment[]) {
    if (!token || isStreaming || (!text.trim() && uploadsSnapshot.length === 0)) {
      return;
    }

    setBusy(true);
    setStreaming(true);
    setError(null);
    setInputProcessingStage('streaming');
    let targetChatId = selectedChatId;

    try {
      if (!targetChatId) {
        const chat = await createChat(token, {
          ...buildCreateChatBinding(characterSelector?.active_prompt ?? null),
          set_current: true
        });
        targetChatId = chat.id;
        skipNextConversationLoadRef.current = true;
        setSelectedChatId(chat.id);
        if (historyLoaded) {
          setChats((currentChats) => [chat, ...currentChats.filter((item) => item.id !== chat.id)]);
        }
      }

      if (!targetChatId) {
        throw new Error('无法创建会话');
      }

      const optimisticUserMessage: WebChatMessage = {
        id: `local-user-${Date.now()}`,
        sender: 'user',
        content_raw: text.trim(),
        display_content: text.trim(),
        timestamp: Date.now(),
        attachments: formatOutgoingAttachments(uploadsSnapshot)
      };

      const optimisticAssistantMessage: WebChatMessage = {
        id: `local-assistant-${Date.now()}`,
        sender: 'assistant',
        content_raw: '',
        timestamp: Date.now() + 1,
        attachments: [],
        streaming: true
      };

      setMessages((currentMessages) =>
        currentMessages.concat(optimisticUserMessage, optimisticAssistantMessage)
      );
      setAutoScrollToBottom(true);
      streamAbortRef.current = new AbortController();

      await streamMessage(
        token,
        targetChatId,
        {
          message: text,
          attachment_ids: uploadsSnapshot.map((item) => item.attachment_id),
          return_tool_status: true
        },
        {
          onEvent: (event) => {
            if (event.event === 'user_message' && event.message) {
              setMessages((currentMessages) =>
                currentMessages.map((item) =>
                  item.id === optimisticUserMessage.id ? event.message ?? item : item
                )
              );
            }

            if (event.event === 'assistant_delta') {
              setMessages((currentMessages) =>
                appendStreamingAssistantDelta(
                  currentMessages,
                  optimisticAssistantMessage.id,
                  event.delta ?? '',
                  event.message
                )
              );
            }

            if (event.event === 'assistant_done') {
              setMessages((currentMessages) =>
                finalizeStreamingAssistantMessage(
                  currentMessages,
                  optimisticAssistantMessage.id,
                  event.message
                )
              );
            }

            if (event.event === 'error' && event.error) {
              setMessages((currentMessages) =>
                finalizeStreamingAssistantMessage(currentMessages, optimisticAssistantMessage.id)
              );
              setError(event.error);
            }
          }
        },
        streamAbortRef.current.signal
      );
    } catch (sendError: unknown) {
      const domException = sendError as DOMException;
      if (domException.name !== 'AbortError') {
        handleApiFailure(sendError);
      }
    } finally {
      setBusy(false);
      setStreaming(false);
      setInputProcessingStage('idle');
      streamAbortRef.current = null;
      setAttachmentPanelOpen(false);
      if (token && targetChatId) {
        await loadConversation(token, targetChatId, 'merge-latest');
        if (historyLoaded) {
          await ensureChatsLoaded(token, true);
        }
      }
    }
  }

  function submitToken() {
    const normalizedToken = tokenDraft.trim();
    if (!normalizedToken) {
      setError('请输入 Bearer Token');
      return;
    }

    writeStoredToken(normalizedToken);
    setToken(normalizedToken);
    setError(null);
  }

  async function createConversation(options?: { group?: string | null }) {
    if (!token) {
      return;
    }
    setBusy(true);

    try {
      const chat = await createChat(token, {
        group: options?.group ?? null,
        ...buildCreateChatBinding(characterSelector?.active_prompt ?? null),
        set_current: true
      });
      if (historyLoaded) {
        await ensureChatsLoaded(token, true);
      }
      setMessages([]);
      setTheme(null);
      setHasMoreHistoryBefore(false);
      setSelectedChatId(chat.id);
      setHistoryOpenState(false);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    } finally {
      setBusy(false);
    }
  }

  async function renameConversation(chat: WebChatSummary, title: string) {
    if (!token) {
      return;
    }

    const nextTitle = title.trim();
    if (!nextTitle || nextTitle === chat.title) {
      return;
    }

    try {
      const updated = await renameChat(token, chat.id, nextTitle);
      setChats((currentChats) =>
        currentChats.map((item) =>
          item.id === updated.id
            ? {
                ...item,
                ...updated,
                title: nextTitle
              }
            : item
        )
      );
      await refreshChats(token);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  async function deleteConversation(chat: WebChatSummary) {
    if (!token) {
      return;
    }

    try {
      await deleteChat(token, chat.id);
      setChats((currentChats) => currentChats.filter((item) => item.id !== chat.id));
      const nextChats = await refreshChats(token);
      const fallbackId = nextChats[0]?.id ?? null;
      const hasSelectedChat = selectedChatId
        ? nextChats.some((item) => item.id === selectedChatId)
        : false;
      if (selectedChatId === chat.id || !hasSelectedChat) {
        setMessages([]);
        setTheme(null);
        setHasMoreHistoryBefore(false);
        setSelectedChatId(fallbackId);
      }
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    }
  }

  function selectChat(chatId: string) {
    if (chatId === selectedChatId) {
      setHistoryOpenState(false);
      return;
    }

    setMessages([]);
    setTheme(null);
    setHasMoreHistoryBefore(false);
    setHistoryOpenState(false);
    setAutoScrollToBottom(true);
    setSelectedChatId(chatId);
  }

  async function uploadFiles(files: FileList | File[]) {
    if (!token) {
      return;
    }

    const normalizedFiles = Array.from(files);
    if (!normalizedFiles.length) {
      return;
    }

    setBusy(true);
    setInputProcessingStage('uploading');

    try {
      const uploaded: WebUploadedAttachment[] = [];
      for (const file of normalizedFiles) {
        uploaded.push(await uploadAttachment(token, file));
      }
      setPendingUploads((currentUploads) => currentUploads.concat(uploaded));
      setAttachmentPanelOpen(false);
    } catch (uploadError: unknown) {
      handleApiFailure(uploadError);
    } finally {
      setBusy(false);
      setInputProcessingStage(isStreaming ? 'streaming' : 'idle');
    }
  }

  function removePendingUpload(attachmentId: string) {
    setPendingUploads((currentUploads) =>
      currentUploads.filter((item) => item.attachment_id !== attachmentId)
    );
  }

  async function sendMessage() {
    const outgoingText = messageInput.trim();
    const uploadsSnapshot = [...pendingUploads];
    setMessageInput('');
    setPendingUploads([]);
    await sendPreparedMessage(outgoingText, uploadsSnapshot);
  }

  function cancelCurrentMessage() {
    streamAbortRef.current?.abort();
    setStreaming(false);
    setInputProcessingStage('idle');
  }

  function setHistoryOpen(value: boolean) {
    if (value) {
      setCharacterSelectorOpenState(false);
    }
    setHistoryOpenState(value);
    if (value && token) {
      void ensureChatsLoaded(token).catch((loadError: unknown) => {
        handleApiFailure(loadError);
      });
    }
  }

  function setCharacterSelectorOpen(value: boolean) {
    if (value) {
      setHistoryOpenState(false);
    }
    setCharacterSelectorOpenState(value);
    if (value && token) {
      void refreshCharacterSelector(token).catch((loadError: unknown) => {
        handleApiFailure(loadError);
      });
    }
  }

  async function switchActivePrompt(target: WebActivePromptTarget) {
    if (!token) {
      return;
    }

    setCharacterSelectorLoading(true);
    try {
      const selectorData = await setActivePromptOnServer(token, target);
      setCharacterSelector(selectorData);
      const requests: Array<Promise<unknown>> = [refreshModelSelector(token)];
      if (selectedChatId) {
        requests.push(
          getTheme(token, selectedChatId).then((themeData) => {
            setTheme(themeData);
            return null;
          })
        );
      }
      await Promise.all(requests);
      setCharacterSelectorOpenState(false);
      setError(null);
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
    } finally {
      setCharacterSelectorLoading(false);
    }
  }

  function queueDraftMessage() {
    const draftText = messageInput.trim();
    if (!draftText) {
      return;
    }

    const nextItem: PendingQueueMessageItem = {
      id: queueIdRef.current,
      text: draftText
    };

    queueIdRef.current += 1;
    setPendingQueueMessages((currentQueue) => currentQueue.concat(nextItem));
    setPendingQueueExpanded(true);
    setMessageInput('');
  }

  function deletePendingQueueMessage(id: number) {
    setPendingQueueMessages((currentQueue) => currentQueue.filter((item) => item.id !== id));
  }

  function editPendingQueueMessage(id: number) {
    setPendingQueueMessages((currentQueue) => {
      const target = currentQueue.find((item) => item.id === id);
      if (target) {
        setMessageInput(target.text);
      }
      return currentQueue.filter((item) => item.id !== id);
    });
  }

  async function sendPendingQueueMessage(id: number) {
    const target = pendingQueueMessages.find((item) => item.id === id);
    if (!target) {
      return;
    }

    setPendingQueueMessages((currentQueue) => currentQueue.filter((item) => item.id !== id));
    await sendPreparedMessage(target.text, []);
  }

  const visibleChats = useMemo(() => {
    return buildVisibleChats(chats, search);
  }, [chats, search]);

  const selectedChat = useMemo(
    () => chats.find((item) => item.id === selectedChatId) ?? null,
    [chats, selectedChatId]
  );

  const activeChatStyle: ChatStyle =
    theme?.chat_style === 'bubble' || boot?.default_chat_style === 'bubble' ? 'bubble' : 'cursor';
  const activeInputStyle: InputStyle =
    theme?.input.style === 'agent' || boot?.default_input_style === 'agent' ? 'agent' : 'classic';

  const activeCharacterName = characterSelector?.active_prompt.name ?? '当前角色';
  const activeCharacterAvatarUrl = characterSelector?.active_prompt.avatar_url ?? null;

  const activeStreamingCount = useMemo(
    () => chats.filter((chat) => chat.active_streaming).length,
    [chats]
  );

  const contextStats = useMemo<ContextStatsSnapshot>(() => {
    return buildContextStats(messages, messageInput);
  }, [messageInput, messages]);

  async function selectModelConfig(
    configId: string,
    modelIndex: number,
    confirmCharacterCardSwitch = false
  ) {
    if (!token) {
      return null;
    }

    setModelSelectorLoading(true);
    try {
      const response = await selectModelOnServer(token, {
        config_id: configId,
        model_index: modelIndex,
        confirm_character_card_switch: confirmCharacterCardSwitch
      });
      setModelSelector(response.selector);
      setError(null);
      return response;
    } catch (actionError: unknown) {
      handleApiFailure(actionError);
      return null;
    } finally {
      setModelSelectorLoading(false);
    }
  }

  return {
    token,
    tokenDraft,
    boot,
    theme,
    characterSelector,
    characterSelectorOpen,
    characterSelectorLoading,
    modelSelector,
    modelSelectorLoading,
    chats,
    visibleChats,
    selectedChatId,
    selectedChat,
    messages,
    messageInput,
    search,
    pendingUploads,
    pendingQueueMessages,
    isPendingQueueExpanded,
    error,
    isConnecting,
    isBusy,
    isStreaming,
    historyOpen,
    historyLoading,
    hasMoreHistoryBefore,
    isLoadingHistoryBefore,
    attachmentPanelOpen,
    inputProcessingStage,
    activeChatStyle,
    activeInputStyle,
    activeCharacterName,
    activeCharacterAvatarUrl,
    showConnectionOverlay: !token,
    activeStreamingCount,
    contextStats,
    autoScrollToBottom,
    setTokenDraft,
    submitToken,
    createConversation,
    renameConversation,
    deleteConversation,
    selectChat,
    setMessageInput,
    setSearch,
    uploadFiles,
    removePendingUpload,
    sendMessage,
    cancelCurrentMessage,
    setHistoryOpen,
    setCharacterSelectorOpen,
    switchActivePrompt,
    selectModelConfig,
    loadOlderMessages,
    setAttachmentPanelOpen,
    queueDraftMessage,
    setPendingQueueExpanded,
    deletePendingQueueMessage,
    editPendingQueueMessage,
    sendPendingQueueMessage,
    setAutoScrollToBottom
  };
}
