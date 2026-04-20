package com.project.course.myfinance.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Цей клас відповідає виключно за спілкування з Firebase.
 * Він не знає про інтерфейс, він просто робить запити і повертає результат.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Отримати поточного користувача
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Реєстрація (залишаємо name)
    suspend fun register(email: String, password: String, name: String = ""): FirebaseUser? {
        // .await() працює завдяки бібліотеці kotlinx-coroutines-play-services
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user

        // Встановлюємо ім'я одразу після створення, якщо воно вказано
        if (user != null && name.isNotBlank()) {
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdates).await()
        }

        return user
    }

    // Авторизація (прибрали name)
    suspend fun login(email: String, password: String): FirebaseUser? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user
    }

    // Вихід з акаунту
    fun logout() {
        auth.signOut()
    }
}