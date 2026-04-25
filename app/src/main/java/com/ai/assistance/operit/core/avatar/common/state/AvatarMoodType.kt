package com.ai.assistance.operit.core.avatar.common.state

data class AvatarCustomMoodDefinition(
    val key: String,
    val promptHint: String
)

data class AvatarMoodTypeDefinition(
    val key: String,
    val displayName: String,
    val promptHint: String,
    val fallbackEmotion: AvatarEmotion? = null,
    val builtIn: Boolean = false
)

object AvatarMoodTypes {
    val builtInDefinitions: List<AvatarMoodTypeDefinition> =
        listOf(
            AvatarMoodTypeDefinition(
                key = "angry",
                displayName = "Angry",
                promptHint = "用户表达侮辱、不公、责备、生气或强烈不满时使用。",
                fallbackEmotion = AvatarEmotion.SAD,
                builtIn = true
            ),
            AvatarMoodTypeDefinition(
                key = "happy",
                displayName = "Happy",
                promptHint = "用户表达明确表扬、达成目标、收到礼物或明显开心时使用。",
                fallbackEmotion = AvatarEmotion.HAPPY,
                builtIn = true
            ),
            AvatarMoodTypeDefinition(
                key = "shy",
                displayName = "Shy",
                promptHint = "用户夸奖、暧昧、戳中可爱点，角色表现害羞时使用。",
                fallbackEmotion = AvatarEmotion.CONFUSED,
                builtIn = true
            ),
            AvatarMoodTypeDefinition(
                key = "aojiao",
                displayName = "Aojiao",
                promptHint = "用户调侃、轻微争执、角色嘴硬又软化时使用。",
                fallbackEmotion = AvatarEmotion.CONFUSED,
                builtIn = true
            ),
            AvatarMoodTypeDefinition(
                key = "cry",
                displayName = "Cry",
                promptHint = "用户失落、难过、受挫、哭诉或道歉后低落时使用。",
                fallbackEmotion = AvatarEmotion.SAD,
                builtIn = true
            )
        )

    private val reservedKeys: Set<String> =
        builtInDefinitions.map { it.key }.toSet() +
            AvatarEmotion.values().map { it.name.lowercase() }.toSet()

    private val customKeyRegex = Regex("^[a-z][a-z0-9_\\-]{0,31}$")

    fun normalizeKey(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(' ', '_')
            .replace(Regex("[^a-z0-9_\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-')
    }

    fun isValidCustomKey(key: String): Boolean {
        return customKeyRegex.matches(key) && key !in reservedKeys
    }

    fun findBuiltInDefinition(key: String): AvatarMoodTypeDefinition? {
        val normalized = normalizeKey(key)
        return builtInDefinitions.firstOrNull { it.key == normalized }
    }

    fun builtInFallbackEmotion(key: String): AvatarEmotion? {
        return findBuiltInDefinition(key)?.fallbackEmotion
    }

    fun sanitizeCustomDefinitions(
        definitions: List<AvatarCustomMoodDefinition>
    ): List<AvatarCustomMoodDefinition> {
        val normalizedKeys = LinkedHashSet<String>()
        val result = mutableListOf<AvatarCustomMoodDefinition>()
        definitions.forEach { definition ->
            val normalizedKey = normalizeKey(definition.key)
            val normalizedHint = definition.promptHint.trim()
            if (!isValidCustomKey(normalizedKey) || normalizedHint.isBlank()) {
                return@forEach
            }
            if (!normalizedKeys.add(normalizedKey)) {
                return@forEach
            }
            result += AvatarCustomMoodDefinition(
                key = normalizedKey,
                promptHint = normalizedHint
            )
        }
        return result
    }
}
