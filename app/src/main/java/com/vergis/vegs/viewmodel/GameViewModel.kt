package com.vergis.vegs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.ai.GeminiService
import com.vergis.vegs.data.FirebaseRepository
import com.vergis.vegs.data.RoomInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val repo = FirebaseRepository()
    private val gemini = GeminiService()

    private val _room = MutableStateFlow<RoomInfo?>(null)
    val room: StateFlow<RoomInfo?> = _room.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _myAnswer = MutableStateFlow("")
    val myAnswer: StateFlow<String> = _myAnswer.asStateFlow()

    private var observeJob: Job? = null
    private var lastProcessedRound = -1
    private var advancing = false

    val currentUid: String? get() = repo.currentUser?.uid

    fun observe(roomId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repo.observeRoom(roomId).collect { r ->
                _room.value = r
                // Auto-start if full and creator
                if (r != null && r.status == "waiting" &&
                    r.players.size >= r.maxPlayers &&
                    r.creatorId == currentUid &&
                    !advancing
                ) {
                    startGameIfNeeded(r)
                }
                // Auto-advance when all ready
                if (r != null && r.status == "playing" &&
                    r.players.isNotEmpty() &&
                    r.readyForNext.size >= r.players.size &&
                    r.creatorId == currentUid &&
                    !advancing
                ) {
                    advanceRoundIfNeeded(r)
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

    private fun advanceRoundIfNeeded(room: RoomInfo) {
        advancing = true
        viewModelScope.launch {
            _loading.value = true
            try {
                val next = room.currentRound + 1
                val question = gemini.generateQuestion(room.theme, next, room.players.size, room.usedQuestions)
                repo.advanceRound(room.id, next, question)
                lastProcessedRound = next
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
                advancing = false
            }
        }
    }

    fun createRoom(name: String, maxPlayers: Int, theme: String, password: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.createRoom(name, maxPlayers, theme, password)
            _loading.value = false
            result.onSuccess { onCreated(it) }
                .onFailure { _error.value = it.message }
        }
    }

    fun submitAnswer(roomId: String, answer: String) {
        if (answer.isBlank()) return
        viewModelScope.launch {
            repo.submitAnswer(roomId, answer.trim())
            _myAnswer.value = answer.trim()
        }
    }

    fun setReady(roomId: String) {
        viewModelScope.launch {
            repo.setReadyForNext(roomId)
        }
    }

    fun leave(roomId: String) {
        viewModelScope.launch {
            repo.leaveRoom(roomId)
        }
        observeJob?.cancel()
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
