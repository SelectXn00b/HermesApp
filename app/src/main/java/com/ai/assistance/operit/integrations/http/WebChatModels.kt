package com.ai.assistance.operit.integrations.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebBootstrapResponse(
    @SerialName("version_name")
    val versionName: String,
    @SerialName("current_chat_id")
    val currentChatId: String? = null,
    @SerialName("default_chat_style")
    val defaultChatStyle: String,
    @SerialName("default_input_style")
    val defaultInputStyle: String,
    @SerialName("show_thinking_process")
    val showThinkingProcess: Boolean,
    @SerialName("show_status_tags")
    val showStatusTags: Boolean,
    @SerialName("show_input_processing_status")
    val showInputProcessingStatus: Boolean,
    @SerialName("capabilities")
    val capabilities: WebCapabilities
)

@Serializable
data class WebCapabilities(
    @SerialName("attachments")
    val attachments: Boolean,
    @SerialName("per_chat_theme")
    val perChatTheme: Boolean,
    @SerialName("structured_render")
    val structuredRender: Boolean,
    @SerialName("streaming")
    val streaming: Boolean,
    @SerialName("rename_chat")
    val renameChat: Boolean,
    @SerialName("delete_chat")
    val deleteChat: Boolean
)

@Serializable
data class WebChatSummary(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("group")
    val group: String? = null,
    @SerialName("character_card_name")
    val characterCardName: String? = null,
    @SerialName("character_group_id")
    val characterGroupId: String? = null,
    @SerialName("character_group_name")
    val characterGroupName: String? = null,
    @SerialName("binding_avatar_url")
    val bindingAvatarUrl: String? = null,
    @SerialName("parent_chat_id")
    val parentChatId: String? = null,
    @SerialName("active_streaming")
    val activeStreaming: Boolean = false,
    @SerialName("locked")
    val locked: Boolean = false
)

@Serializable
data class WebMessageAttachment(
    @SerialName("id")
    val id: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_size")
    val fileSize: Long? = null,
    @SerialName("content")
    val content: String? = null,
    @SerialName("asset_url")
    val assetUrl: String? = null
)

@Serializable
data class WebChatMessage(
    @SerialName("id")
    val id: String,
    @SerialName("sender")
    val sender: String,
    @SerialName("content_raw")
    val contentRaw: String,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("role_name")
    val roleName: String? = null,
    @SerialName("provider")
    val provider: String? = null,
    @SerialName("model_name")
    val modelName: String? = null,
    @SerialName("display_content")
    val displayContent: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("display_name_is_proxy")
    val displayNameIsProxy: Boolean = false,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("reply_preview")
    val replyPreview: WebReplyPreview? = null,
    @SerialName("image_links")
    val imageLinks: List<WebMessageImageLink> = emptyList(),
    @SerialName("content_blocks")
    val contentBlocks: List<WebMessageContentBlock>? = null,
    @SerialName("attachments")
    val attachments: List<WebMessageAttachment> = emptyList()
)

@Serializable
data class WebReplyPreview(
    @SerialName("sender")
    val sender: String,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("content")
    val content: String
)

@Serializable
data class WebMessageImageLink(
    @SerialName("id")
    val id: String,
    @SerialName("asset_url")
    val assetUrl: String? = null,
    @SerialName("expired")
    val expired: Boolean = false
)

@Serializable
data class WebMessageContentBlock(
    @SerialName("kind")
    val kind: String,
    @SerialName("content")
    val content: String? = null,
    @SerialName("xml")
    val xml: String? = null,
    @SerialName("tag_name")
    val tagName: String? = null,
    @SerialName("raw_tag_name")
    val rawTagName: String? = null,
    @SerialName("attrs")
    val attrs: Map<String, String> = emptyMap(),
    @SerialName("closed")
    val closed: Boolean = true,
    @SerialName("group_type")
    val groupType: String? = null,
    @SerialName("children")
    val children: List<WebMessageContentBlock>? = null
)

@Serializable
data class WebChatMessagesPage(
    @SerialName("messages")
    val messages: List<WebChatMessage>,
    @SerialName("has_more_before")
    val hasMoreBefore: Boolean,
    @SerialName("next_before_timestamp")
    val nextBeforeTimestamp: Long? = null
)

@Serializable
data class WebThemeSnapshot(
    @SerialName("source")
    val source: String,
    @SerialName("source_id")
    val sourceId: String? = null,
    @SerialName("theme_mode")
    val themeMode: String,
    @SerialName("use_system_theme")
    val useSystemTheme: Boolean,
    @SerialName("use_custom_colors")
    val useCustomColors: Boolean,
    @SerialName("primary_color")
    val primaryColor: String? = null,
    @SerialName("secondary_color")
    val secondaryColor: String? = null,
    @SerialName("palette")
    val palette: WebThemePalette,
    @SerialName("background")
    val background: WebThemeBackground,
    @SerialName("header")
    val header: WebHeaderTheme,
    @SerialName("input")
    val input: WebInputTheme,
    @SerialName("font")
    val font: WebFontTheme,
    @SerialName("chat_style")
    val chatStyle: String,
    @SerialName("show_thinking_process")
    val showThinkingProcess: Boolean,
    @SerialName("show_status_tags")
    val showStatusTags: Boolean,
    @SerialName("show_input_processing_status")
    val showInputProcessingStatus: Boolean,
    @SerialName("display")
    val display: WebDisplayPreferences,
    @SerialName("bubble")
    val bubble: WebBubbleTheme,
    @SerialName("avatars")
    val avatars: WebAvatarTheme
)

@Serializable
data class WebThemePalette(
    @SerialName("background_color")
    val backgroundColor: String,
    @SerialName("surface_color")
    val surfaceColor: String,
    @SerialName("surface_variant_color")
    val surfaceVariantColor: String,
    @SerialName("surface_container_color")
    val surfaceContainerColor: String,
    @SerialName("surface_container_high_color")
    val surfaceContainerHighColor: String,
    @SerialName("primary_color")
    val primaryColor: String,
    @SerialName("secondary_color")
    val secondaryColor: String,
    @SerialName("primary_container_color")
    val primaryContainerColor: String,
    @SerialName("on_primary_container_color")
    val onPrimaryContainerColor: String,
    @SerialName("on_surface_color")
    val onSurfaceColor: String,
    @SerialName("on_surface_variant_color")
    val onSurfaceVariantColor: String,
    @SerialName("outline_color")
    val outlineColor: String,
    @SerialName("outline_variant_color")
    val outlineVariantColor: String
)

@Serializable
data class WebThemeBackground(
    @SerialName("type")
    val type: String,
    @SerialName("asset_url")
    val assetUrl: String? = null,
    @SerialName("opacity")
    val opacity: Float = 0.0f
)

@Serializable
data class WebHeaderTheme(
    @SerialName("transparent")
    val transparent: Boolean,
    @SerialName("overlay")
    val overlay: Boolean
)

@Serializable
data class WebInputTheme(
    @SerialName("style")
    val style: String,
    @SerialName("transparent")
    val transparent: Boolean,
    @SerialName("floating")
    val floating: Boolean,
    @SerialName("liquid_glass")
    val liquidGlass: Boolean,
    @SerialName("water_glass")
    val waterGlass: Boolean
)

@Serializable
data class WebFontTheme(
    @SerialName("type")
    val type: String,
    @SerialName("system_font_name")
    val systemFontName: String? = null,
    @SerialName("custom_font_asset_url")
    val customFontAssetUrl: String? = null,
    @SerialName("scale")
    val scale: Float
)

@Serializable
data class WebBubbleTheme(
    @SerialName("show_avatar")
    val showAvatar: Boolean,
    @SerialName("wide_layout")
    val wideLayout: Boolean,
    @SerialName("cursor_user_follow_theme")
    val cursorUserFollowTheme: Boolean,
    @SerialName("cursor_user_color")
    val cursorUserColor: String? = null,
    @SerialName("user_bubble_color")
    val userBubbleColor: String? = null,
    @SerialName("assistant_bubble_color")
    val assistantBubbleColor: String? = null,
    @SerialName("user_text_color")
    val userTextColor: String? = null,
    @SerialName("assistant_text_color")
    val assistantTextColor: String? = null,
    @SerialName("cursor_user_liquid_glass")
    val cursorUserLiquidGlass: Boolean,
    @SerialName("cursor_user_water_glass")
    val cursorUserWaterGlass: Boolean,
    @SerialName("user_liquid_glass")
    val userLiquidGlass: Boolean,
    @SerialName("user_water_glass")
    val userWaterGlass: Boolean,
    @SerialName("assistant_liquid_glass")
    val assistantLiquidGlass: Boolean,
    @SerialName("assistant_water_glass")
    val assistantWaterGlass: Boolean,
    @SerialName("user_rounded")
    val userRounded: Boolean,
    @SerialName("assistant_rounded")
    val assistantRounded: Boolean,
    @SerialName("user_padding_left")
    val userPaddingLeft: Float,
    @SerialName("user_padding_right")
    val userPaddingRight: Float,
    @SerialName("assistant_padding_left")
    val assistantPaddingLeft: Float,
    @SerialName("assistant_padding_right")
    val assistantPaddingRight: Float,
    @SerialName("user_image")
    val userImage: WebBubbleImageTheme,
    @SerialName("assistant_image")
    val assistantImage: WebBubbleImageTheme
)

@Serializable
data class WebBubbleImageTheme(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("asset_url")
    val assetUrl: String? = null,
    @SerialName("render_mode")
    val renderMode: String? = null
)

@Serializable
data class WebAvatarTheme(
    @SerialName("shape")
    val shape: String,
    @SerialName("corner_radius")
    val cornerRadius: Float? = null,
    @SerialName("user_avatar_url")
    val userAvatarUrl: String? = null,
    @SerialName("assistant_avatar_url")
    val assistantAvatarUrl: String? = null
)

@Serializable
data class WebDisplayPreferences(
    @SerialName("show_user_name")
    val showUserName: Boolean,
    @SerialName("show_role_name")
    val showRoleName: Boolean,
    @SerialName("show_model_name")
    val showModelName: Boolean,
    @SerialName("show_model_provider")
    val showModelProvider: Boolean,
    @SerialName("tool_collapse_mode")
    val toolCollapseMode: String,
    @SerialName("global_user_name")
    val globalUserName: String? = null
)

@Serializable
data class WebUploadedAttachment(
    @SerialName("attachment_id")
    val attachmentId: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_size")
    val fileSize: Long
)

@Serializable
data class WebActivePromptSnapshot(
    @SerialName("type")
    val type: String,
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class WebCharacterCardSelectorItem(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String = "",
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long
)

@Serializable
data class WebCharacterGroupSelectorItem(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String = "",
    @SerialName("member_count")
    val memberCount: Int,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long
)

@Serializable
data class WebCharacterSelectorResponse(
    @SerialName("active_prompt")
    val activePrompt: WebActivePromptSnapshot,
    @SerialName("cards")
    val cards: List<WebCharacterCardSelectorItem>,
    @SerialName("groups")
    val groups: List<WebCharacterGroupSelectorItem>
)

@Serializable
data class WebModelSelectorState(
    @SerialName("current_config_id")
    val currentConfigId: String,
    @SerialName("current_config_name")
    val currentConfigName: String? = null,
    @SerialName("current_model_index")
    val currentModelIndex: Int,
    @SerialName("current_model_name")
    val currentModelName: String,
    @SerialName("locked_by_character_card")
    val lockedByCharacterCard: Boolean,
    @SerialName("locked_character_card_id")
    val lockedCharacterCardId: String? = null,
    @SerialName("locked_character_card_name")
    val lockedCharacterCardName: String? = null,
    @SerialName("configs")
    val configs: List<WebModelSelectorConfig> = emptyList()
)

@Serializable
data class WebModelSelectorConfig(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("model_name")
    val modelName: String,
    @SerialName("models")
    val models: List<String> = emptyList(),
    @SerialName("selected")
    val selected: Boolean = false,
    @SerialName("selected_model_index")
    val selectedModelIndex: Int? = null
)

@Serializable
data class WebChatStreamEvent(
    @SerialName("event")
    val event: String,
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("message")
    val message: WebChatMessage? = null,
    @SerialName("delta")
    val delta: String? = null,
    @SerialName("error")
    val error: String? = null
)

@Serializable
data class WebCreateChatRequest(
    @SerialName("title")
    val title: String? = null,
    @SerialName("group")
    val group: String? = null,
    @SerialName("character_card_name")
    val characterCardName: String? = null,
    @SerialName("character_group_id")
    val characterGroupId: String? = null,
    @SerialName("set_current")
    val setCurrent: Boolean = true
)

@Serializable
data class WebUpdateChatRequest(
    @SerialName("title")
    val title: String? = null
)

@Serializable
data class WebSendMessageRequest(
    @SerialName("message")
    val message: String? = null,
    @SerialName("attachment_ids")
    val attachmentIds: List<String> = emptyList(),
    @SerialName("return_tool_status")
    val returnToolStatus: Boolean = true
)

@Serializable
data class WebSetActivePromptRequest(
    @SerialName("type")
    val type: String,
    @SerialName("id")
    val id: String
)

@Serializable
data class WebSelectModelRequest(
    @SerialName("config_id")
    val configId: String,
    @SerialName("model_index")
    val modelIndex: Int = 0,
    @SerialName("confirm_character_card_switch")
    val confirmCharacterCardSwitch: Boolean = false
)

@Serializable
data class WebSelectModelResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("requires_character_card_switch_confirmation")
    val requiresCharacterCardSwitchConfirmation: Boolean = false,
    @SerialName("selector")
    val selector: WebModelSelectorState
)

@Serializable
data class WebActionResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("chat_id")
    val chatId: String? = null,
    @SerialName("deleted")
    val deleted: Boolean? = null
)

@Serializable
data class WebErrorResponse(
    @SerialName("error")
    val error: String
)
