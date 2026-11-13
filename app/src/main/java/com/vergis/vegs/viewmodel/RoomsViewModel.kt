package com.vergis.vegs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.data.FirebaseRepository
import com.vergis.vegs.data.RoomInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomsViewModel : ViewModel() {
    private val repo = FirebaseRepository()

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeRooms().collect {
                _rooms.value = it
                _loading.value = false
            }
        }
    }

    fun joinRoom(roomId: String, password: String, playerName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _joinError.value = null
            val result = repo.joinRoom(roomId, password, playerName)
            result.onSuccess {
                onSuccess()
            }.onFailure {
                _joinError.value = it.message
            }
        }
    }

    fun clearJoinError() {
        _joinError.value = null
    }
}
