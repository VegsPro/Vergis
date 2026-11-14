package com.vergis.vegs.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.data.FirebaseRepository
import com.vergis.vegs.data.FriendEntry
import com.vergis.vegs.data.RoomInvite
import com.vergis.vegs.data.UserProfile
import com.vergis.vegs.util.ChatSounds
import com.vergis.vegs.util.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FirebaseRepository()
    private val ctx: Context = app.applicationContext

    private val _friends = MutableStateFlow<List<FriendEntry>>(emptyList())
    val friends: StateFlow<List<FriendEntry>> = _friends.asStateFlow()

    private val _requests = MutableStateFlow<List<FriendEntry>>(emptyList())
    val requests: StateFlow<List<FriendEntry>> = _requests.asStateFlow()

    private val _invites = MutableStateFlow<List<RoomInvite>>(emptyList())
    val invites: StateFlow<List<RoomInvite>> = _invites.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var jobs = mutableListOf<Job>()
    private var lastInviteCount = 0

    fun start() {
        stop()
        jobs += viewModelScope.launch {
            repo.observeFriends().collect { _friends.value = it }
        }
        jobs += viewModelScope.launch {
            repo.observeFriendRequests().collect { _requests.value = it }
        }
        jobs += viewModelScope.launch {
            repo.observeInvites().collect { list ->
                if (list.size > lastInviteCount && lastInviteCount >= 0 && list.isNotEmpty()) {
                    val inv = list.first()
                    Notifications.showInvite(ctx, inv.fromName, inv.roomName, inv.roomId)
                    ChatSounds.playMessage(ctx)
                }
                lastInviteCount = list.size
                _invites.value = list
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun search(query: String) {
        viewModelScope.launch {
            _loading.value = true
            _searchResults.value = repo.searchUsers(query)
            _loading.value = false
        }
    }

    fun addFriend(uid: String) {
        viewModelScope.launch {
            val r = repo.sendFriendRequest(uid)
            _message.value = r.exceptionOrNull()?.message ?: "Заявка отправлена"
            if (r.isSuccess) search(_searchResults.value.firstOrNull()?.displayName ?: "")
        }
    }

    fun accept(uid: String) {
        viewModelScope.launch { repo.acceptFriend(uid) }
    }

    fun reject(uid: String) {
        viewModelScope.launch { repo.rejectFriend(uid) }
    }

    fun remove(uid: String) {
        viewModelScope.launch { repo.removeFriend(uid) }
    }

    fun inviteToRoom(friendUid: String, roomId: String, roomName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val r = repo.sendRoomInvite(friendUid, roomId, roomName)
            _message.value = r.exceptionOrNull()?.message ?: "Приглашение отправлено"
            onDone()
        }
    }

    fun dismissInvite(id: String) {
        viewModelScope.launch { repo.dismissInvite(id) }
    }

    fun clearMessage() { _message.value = null }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
