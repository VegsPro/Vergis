package com.vergis.vegs.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.ai.GeminiService
import com.vergis.vegs.data.ChatMessage
import com.vergis.vegs.data.FirebaseRepository
import com.vergis.vegs.data.RoomInfo
import com.vergis.vegs.util.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FirebaseRepository()
    private val gemini = GeminiService()
    private val appContext: Context = app.applicationContext

    private val _room = MutableStateFlow<RoomInfo?>(null)
    val room: StateFlow<RoomInfo?> = _room.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private var observeJob: Job? = null
    private var chatJob: Job? = null
    private var eventsJob: Job? = null
    private var presenceJob: Job? = null
    private var advancing = false
    private var prefetching = false
    private var lastNotifiedAnswerCount = -1
    private var appInForeground = true
    private var lastEventTs = 0L

    private val _leaveToast = MutableStateFlow<String?>(null)
    val leaveToast: StateFlow<String?> = _leaveToast.asStateFlow()

    val currentUid: String? get() = repo.currentUser?.uid

    fun setForeground(value: Boolean) {
        appInForeground = value
    }

    fun observe(roomId: String) {
        observeJob?.cancel()
        chatJob?.cancel()
        lastNotifiedAnswerCount = -1
        observeJob = viewModelScope.launch {
            repo.observeRoom(roomId).collect { r ->
                val prev = _room.value
                _room.value = r
                if (r == null) return@collect

                // Notifications when someone answered while backgrounded
                val ansCount = r.answers.size
                if (prev != null &&
                    r.status == "playing" &&
                    ansCount > lastNotifiedAnswerCount &&
                    lastNotifiedAnswerCount >= 0 &&
                    !appInForeground
                ) {
                    val newUid = r.answers.keys - (prev.answers.keys)
                    val name = newUid.firstOrNull()?.let { r.players[it]?.name } ?: "Игрок"
                    Notifications.showAnswer(
                        appContext, name, ansCount, r.players.size, r.id
                    )
                }
                lastNotifiedAnswerCount = ansCount

                if (r.status == "waiting" &&
                    r.players.size >= r.maxPlayers &&
                    r.creatorId == currentUid &&
                    !advancing
                ) {
                    startGameIfNeeded(r)
                }

                // Prefetch next question once ≥1 answer
                if (r.status == "playing" &&
                    r.answers.isNotEmpty() &&
                    r.nextQuestion.isBlank() &&
                    r.creatorId == currentUid &&
                    !prefetching
                ) {
                    prefetchNext(r)
                }

                if (r.status == "playing" &&
                    r.players.isNotEmpty() &&
                    r.readyForNext.size >= r.players.size &&
                    r.creatorId == currentUid &&
                    !advancing
                ) {
                    advanceRoundIfNeeded(r)
                }
            }
        }
        chatJob = viewModelScope.launch {
            repo.observeChat(roomId).collect { _chat.value = it }
        }
        eventsJob?.cancel()
        lastEventTs = 0L
        eventsJob = viewModelScope.launch {
            repo.observeRoomEvents(roomId).collect { events ->
                val fresh = events.filter { it.timestamp > lastEventTs && it.uid != currentUid }
                if (fresh.isNotEmpty()) {
                    val e = fresh.last()
                    val msg = if (e.type == "afk") "${e.name} отошёл (AFK)" else "${e.name} вышел из комнаты"
                    _leaveToast.value = msg
                    if (!appInForeground) {
                        Notifications.showPlayerLeft(appContext, e.name, e.type)
                    }
                }
                lastEventTs = events.maxOfOrNull { it.timestamp } ?: lastEventTs
            }
        }
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                if (appInForeground) {
                    try { repo.heartbeat(roomId) } catch (_: Exception) {}
                }
                val r = _room.value ?: continue
                if (r.creatorId != currentUid) continue
                val now = System.currentTimeMillis()
                for (pl in r.players.values) {
                    if (pl.uid == currentUid) continue
                    val seen = pl.lastSeenAt.takeIf { it > 0 } ?: pl.joinedAt
                    if (now - seen > 30_000) {
                        try { repo.kickAfk(roomId, pl.uid, pl.name) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun startGameIfNeeded(room: RoomInfo) {
        advancing = true
        viewModelScope.launch {
            _loading.value = true
            try {
                val question = gemini.generateQuestion(room.theme, 1, room.players.size, room.usedQuestions)
                repo.startGame(room.id, question)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
                advancing = false
            }
        }
    }

    private fun prefetchNext(room: RoomInfo) {
        prefetching = true
        viewModelScope.launch {
            try {
                val nextRound = room.currentRound + 1
                val used = room.usedQuestions + listOfNotNull(room.currentQuestion.takeIf { it.isNotBlank() })
                val q = gemini.generateQuestion(room.theme, nextRound, room.players.size, used)
                if (q.isNotBlank()) repo.setNextQuestion(room.id, q)
            } catch (_: Exception) {
            } finally {
                prefetching = false
            }
        }
    }

    private fun advanceRoundIfNeeded(room: RoomInfo) {
        advancing = true
        viewModelScope.launch {
            _loading.value = true
            try {
                val next = room.currentRound + 1
                val used = room.usedQuestions + listOfNotNull(room.currentQuestion.takeIf { it.isNotBlank() })
                val question = room.nextQuestion.takeIf { it.isNotBlank() }
                    ?: gemini.generateQuestion(room.theme, next, room.players.size, used)
                repo.advanceRound(room.id, next, question)
                lastNotifiedAnswerCount = -1
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
                advancing = false
            }
        }
    }

    fun createRoom(name: String, maxPlayers: Int, theme: String, password: String, hidden: Boolean = false, friendsOnly: Boolean = false, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.createRoom(name, maxPlayers, theme, password, hidden, friendsOnly)
            _loading.value = false
            result.onSuccess { onCreated(it) }
                .onFailure { _error.value = it.message }
        }
    }

    fun submitAnswer(roomId: String, answer: String) {
        if (answer.isBlank()) return
        viewModelScope.launch {
            repo.submitAnswer(roomId, answer.trim())
        }
    }

    fun setReady(roomId: String) {
        viewModelScope.launch {
            repo.setReadyForNext(roomId)
        }
    }

    fun sendChat(roomId: String, text: String, name: String, avatarUrl: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.sendChatMessage(roomId, text.trim(), name, avatarUrl)
        }
    }

    fun leave(roomId: String, reason: String = "left") {
        viewModelScope.launch {
            repo.leaveRoom(roomId, reason)
        }
        observeJob?.cancel()
        chatJob?.cancel()
        eventsJob?.cancel()
        presenceJob?.cancel()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearLeaveToast() {
        _leaveToast.value = null
    }

    override fun onCleared() {
        observeJob?.cancel()
        chatJob?.cancel()
        eventsJob?.cancel()
        presenceJob?.cancel()
        super.onCleared()
    }
}
