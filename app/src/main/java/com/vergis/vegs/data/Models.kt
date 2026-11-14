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
    val nextQuestion: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val players: Map<String, PlayerInfo> = emptyMap(),
    val answers: Map<String, String> = emptyMap(),
    val readyForNext: Map<String, Boolean> = emptyMap(),
    val usedQuestions: List<String> = emptyList(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val hidden: Boolean = false,
    val friendsOnly: Boolean = false
)

data class PlayerInfo(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String = ""
)

data class ChatMessage(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class FriendEntry(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val status: String = "accepted", // accepted | pending_out | pending_in
    val since: Long = System.currentTimeMillis()
)

data class RoomInvite(
    val id: String = "",
    val roomId: String = "",
    val roomName: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromAvatar: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class RoomEvent(
    val id: String = "",
    val type: String = "", // left | afk
    val uid: String = "",
    val name: String = "",
    val timestamp: Long = System.currentTimeMillis()
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
