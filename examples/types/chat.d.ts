/**
 * Chat Manager Tool definitions for Assistance Package
 * 
 * This file provides type definitions for chat management operations,
 * including creating chats, listing chats, switching between chats, and sending messages.
 */

import {
    ChatServiceStartResultData,
    ChatCreationResultData,
    ChatListResultData,
    ChatFindResultData,
    AgentStatusResultData,
    ChatSwitchResultData,
    ChatTitleUpdateResultData,
    ChatDeleteResultData,
    MessageSendResultData,
    ChatMessagesResultData,
    CharacterCardListResultData
} from './results';

/**
 * Chat Manager namespace
 * Provides methods for managing chat conversations
 */
export namespace Chat {
    interface SendMessageAdvancedParams {
        message: string;
        chatId?: string;
        chatHistory?: Array<[string, string]>;
        workspacePath?: string;
        functionType?: string;
        promptFunctionType?: string;
        enableThinking?: boolean;
        thinkingGuidance?: boolean;
        enableMemoryQuery?: boolean;
        maxTokens: number;
        tokenUsageThreshold: number;
        customSystemPromptTemplate?: string;
        isSubTask?: boolean;
        stream?: boolean;
    }

    /**
     * Start the chat service (floating window)
     * @returns Promise resolving to service start result
     */
    function startService(): Promise<ChatServiceStartResultData>;

    /**
     * Create a new chat conversation
     * @param group - Optional group name for the new chat
     * @param setAsCurrentChat - Optional, whether to switch to the new chat (default true)
     * @param characterCardId - Optional character card id to bind for the new chat
     * @returns Promise resolving to the new chat creation result
     */
    function createNew(group?: string, setAsCurrentChat?: boolean, characterCardId?: string): Promise<ChatCreationResultData>;

    /**
     * List all chat conversations
     * @returns Promise resolving to the list of all chats
     */
    function listAll(): Promise<ChatListResultData>;

    /**
     * List chat conversations with filters
     */
    function listChats(params?: {
        query?: string;
        match?: 'contains' | 'exact' | 'regex';
        limit?: number;
        sort_by?: 'updatedAt' | 'createdAt' | 'messageCount';
        sort_order?: 'asc' | 'desc';
    }): Promise<ChatListResultData>;

    /**
     * Find a chat by title or id
     */
    function findChat(params: {
        query: string;
        match?: 'contains' | 'exact' | 'regex';
        index?: number;
    }): Promise<ChatFindResultData>;

    /**
     * Check chat input processing status
     */
    function agentStatus(chatId: string): Promise<AgentStatusResultData>;

    /**
     * Switch to a specific chat conversation
     * @param chatId - The ID of the chat to switch to
     * @returns Promise resolving to the chat switch result
     */
    function switchTo(chatId: string): Promise<ChatSwitchResultData>;

    /**
     * Update chat title
     */
    function updateTitle(chatId: string, title: string): Promise<ChatTitleUpdateResultData>;

    /**
     * Delete a chat conversation by id
     */
    function deleteChat(chatId: string): Promise<ChatDeleteResultData>;

    /**
     * Send a message to the AI
     * @param message - The message content to send
     * @param chatId - Optional chat ID to send the message to (defaults to current chat)
     * @param roleCardId - Optional role card ID to use for this send
     * @param senderName - Optional display name when AI sends as user
     * @returns Promise resolving to the message send result
     */
    function sendMessage(message: string, chatId?: string, roleCardId?: string, senderName?: string): Promise<MessageSendResultData>;

    /**
     * Send a message to AI with advanced controls.
     * @param params - Advanced send parameters
     * @returns Promise resolving to the message send result
     */
    function sendMessageAdvanced(params: SendMessageAdvancedParams): Promise<MessageSendResultData>;

    /**
     * List all character cards
     */
    function listCharacterCards(): Promise<CharacterCardListResultData>;

    /**
     * Get messages from a specific chat
     * @param chatId - The ID of the chat to read
     * @param options - Optional order/limit
     */
    function getMessages(chatId: string, options?: { order?: 'asc' | 'desc'; limit?: number }): Promise<ChatMessagesResultData>;
}
