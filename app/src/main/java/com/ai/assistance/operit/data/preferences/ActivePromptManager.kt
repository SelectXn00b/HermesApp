package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.model.ActivePrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class ActivePromptManager private constructor(context: Context) {

    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)

    val activePromptFlow: Flow<ActivePrompt> =
        combine(
            characterGroupCardManager.observeActiveCharacterGroupId(),
            characterCardManager.observeActiveCharacterCardId()
        ) { groupId, cardId ->
            when {
                !groupId.isNullOrBlank() -> ActivePrompt.CharacterGroup(groupId)
                !cardId.isNullOrBlank() -> ActivePrompt.CharacterCard(cardId)
                else -> ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            }
        }.distinctUntilChanged()

    suspend fun getActivePrompt(): ActivePrompt = activePromptFlow.first()

    suspend fun setActivePrompt(prompt: ActivePrompt) {
        when (prompt) {
            is ActivePrompt.CharacterGroup -> {
                characterGroupCardManager.setActiveCharacterGroupCard(prompt.id)
                characterCardManager.clearActiveCharacterCard()
            }
            is ActivePrompt.CharacterCard -> {
                characterCardManager.setActiveCharacterCard(prompt.id)
                characterGroupCardManager.setActiveCharacterGroupCard(null)
            }
        }
    }

    suspend fun activateForChatBinding(characterCardName: String?, characterGroupId: String?) {
        val normalizedGroupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedGroupId.isNullOrBlank()) {
            setActivePrompt(ActivePrompt.CharacterGroup(normalizedGroupId))
            return
        }

        val normalizedCardName = characterCardName?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedCardName != null) {
            val targetCard = characterCardManager.findCharacterCardByName(normalizedCardName)
            if (targetCard != null) {
                setActivePrompt(ActivePrompt.CharacterCard(targetCard.id))
                return
            }
        }

        setActivePrompt(ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID))
    }

    suspend fun resolveActiveCardIdForSend(): String {
        return when (val prompt = getActivePrompt()) {
            is ActivePrompt.CharacterCard -> prompt.id
            is ActivePrompt.CharacterGroup -> CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ActivePromptManager? = null

        fun getInstance(context: Context): ActivePromptManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivePromptManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
