package com.project.course.myfinance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.project.course.myfinance.auth.AuthState
import com.project.course.myfinance.auth.AuthViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnRegister.isEnabled = false
                }
                is AuthState.Success -> {
                    progressBar.visibility = View.GONE
                    // Після реєстрації відразу переходимо на головний екран
                    val intent = Intent(this, MainActivity::class.java)
                    // Очищаємо стек екранів, щоб користувач не міг повернутись на реєстрацію
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                is AuthState.Error -> {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is AuthState.Idle -> {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                }
            }
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            viewModel.register(email, password)
        }

        tvGoToLogin.setOnClickListener {
            finish() // Просто закриваємо цей екран і повертаємось на екран Логіну
        }
    }
}