package com.project.course.myfinance

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        val tvTitle = findViewById<TextView>(R.id.tvSplashTitle)

        // 1. Встановлюємо початковий стан (невидимі та зміщені/зменшені)
        ivLogo.alpha = 0f
        ivLogo.scaleX = 0.3f
        ivLogo.scaleY = 0.3f

        tvTitle.alpha = 0f
        tvTitle.translationY = 50f

        // 2. Анімація логотипа (збільшення + плавна поява + пружинка)
        ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1000)
            .setInterpolator(OvershootInterpolator()) // Робить прикольний ефект відскоку
            .start()

        // 3. Анімація тексту (виїжджає знизу трохи пізніше за логотип)
        tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(400) // Чекаємо 400 мілісекунд перед стартом
            .start()

        // 4. Затримка 2.5 секунди, перевірка авторизації та перехід
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // Якщо вже увійшов - кидаємо на головний екран
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Якщо ні - на екран логіну
                startActivity(Intent(this, LoginActivity::class.java))
            }
            // Закриваємо SplashActivity, щоб користувач не міг повернутись сюди кнопкою "Назад"
            finish()
        }, 2500)
    }
}