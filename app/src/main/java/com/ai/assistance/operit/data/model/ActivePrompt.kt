package com.ai.assistance.operit.data.model

sealed interface ActivePrompt {
    data class CharacterCard(val id: String) : ActivePrompt
    data class CharacterGroup(val id: String) : ActivePrompt
}
