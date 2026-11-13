package com.vergis.vegs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vergis.vegs.data.FirebaseRepository
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

    private val _displayName = MutableStateFlow(repo.currentUser?.displayName.orEmpty())
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repo.login(email.trim(), password)
            _loading.value = false
            result.onSuccess {
                _isLoggedIn.value = true
                _displayName.value = repo.currentUser?.displayName.orEmpty()
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
            }.onFailure {
                _error.value = it.message ?: "Ошибка регистрации"
            }
        }
    }

    fun logout() {
        repo.logout()
        _isLoggedIn.value = false
        _displayName.value = ""
    }

    fun clearError() {
        _error.value = null
    }
}
