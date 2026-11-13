package com.vergis.vegs.data

data class RoomInfo(
    val id: String = "",
    val name: String = "",
    val creatorId: String = "",
    val maxPlayers: Int = 2,
    val theme: String = "Для друзей",
    val password: String = "",
    val status: String = "waiting",
    val currentRound: Int = 0,
    val currentQuestion: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val players: Map<String, PlayerInfo> = emptyMap(),
    val answers: Map<String, String> = emptyMap(),
    val readyForNext: Map<String, Boolean> = emptyMap(),
    val usedQuestions: List<String> = emptyList(),
    val lastActivityAt: Long = System.currentTimeMillis()
)

data class PlayerInfo(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val joinedAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String = ""
)

val DEFAULT_THEMES = listOf(
    "Для пар",
    "Для друзей",
    "Для вечеринки",
    "Для семьи",
    "Конкурентный",
    "Расслабленный",
    "Глубокие разговоры",
    "Смешные вопросы"
)
