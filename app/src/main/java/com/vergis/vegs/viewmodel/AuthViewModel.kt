package com.vergis.vegs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.data.FirebaseRepository
import com.vergis.vegs.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repo = FirebaseRepository()

    private val _isLoggedIn = MutableStateFlow(repo.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatarUrl = MutableStateFlow("")
    val avatarUrl: StateFlow<String> = _avatarUrl.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        val uid = repo.currentUser?.uid ?: return
        viewModelScope.launch {
            val p = repo.getUserProfile(uid)
            _profile.value = p
            _displayName.value = p?.displayName?.takeIf { it.isNotBlank() }
                ?: repo.currentUser?.displayName.orEmpty()
            _avatarUrl.value = p?.avatarUrl.orEmpty()
            _isLoggedIn.value = true
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.login(email.trim(), password)
            _loading.value = false
            result.onSuccess {
                _isLoggedIn.value = true
                refreshProfile()
            }.onFailure {
                _error.value = it.message ?: "Ошибка входа"
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            if (name.isBlank()) {
                _error.value = "Введите имя"
                _loading.value = false
                return@launch
            }
            val result = repo.register(email.trim(), password, name.trim())
            _loading.value = false
            result.onSuccess {
                _isLoggedIn.value = true
                _displayName.value = name.trim()
                refreshProfile()
            }.onFailure {
                _error.value = it.message ?: "Ошибка регистрации"
            }
        }
    }

    fun updateName(name: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.updateDisplayName(name.trim())
            _loading.value = false
            result.onSuccess {
                _displayName.value = name.trim()
                refreshProfile()
                onDone()
            }.onFailure {
                _error.value = it.message
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.uploadAvatar(bytes)
            _loading.value = false
            result.onSuccess { url ->
                _avatarUrl.value = url
                refreshProfile()
                onDone()
            }.onFailure {
                _error.value = it.message ?: "Не удалось загрузить аватар"
            }
        }
    }

    fun logout() {
        repo.logout()
        _isLoggedIn.value = false
        _displayName.value = ""
        _avatarUrl.value = ""
        _profile.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(msg: String) {
        _error.value = msg
    }
}
