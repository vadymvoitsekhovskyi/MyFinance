package com.project.course.myfinance.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// Клас для опису станів екрану
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel керує логікою екрану.
 * Бере дані з UI -> передає в Repository -> повертає стан в UI.
 */
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    // Змінна, яку буде слухати наш екран (Activity)
    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    // В реєстрації залишаємо поле name
    fun register(email: String, password: String, name: String = "") {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Заповніть всі поля")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Пароль має бути не менше 6 символів")
            return
        }

        _authState.value = AuthState.Loading

        // Запускаємо фонову задачу (корутину)
        viewModelScope.launch {
            try {
                repository.register(email, password, name)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Помилка реєстрації")
            }
        }
    }

    // У вході прибираємо name
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Заповніть всі поля")
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                repository.login(email, password)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Невірний логін або пароль")
            }
        }
    }
}