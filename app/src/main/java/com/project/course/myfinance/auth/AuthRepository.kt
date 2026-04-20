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

    // Реєстрація (використовуємо suspend, щоб викликати з корутин без колбеків)
    suspend fun register(email: String, password: String): FirebaseUser? {
        // .await() працює завдяки бібліотеці kotlinx-coroutines-play-services
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user
    }

    // Авторизація
    suspend fun login(email: String, password: String): FirebaseUser? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user
    }

    // Вихід з акаунту
    fun logout() {
        auth.signOut()
    }
}