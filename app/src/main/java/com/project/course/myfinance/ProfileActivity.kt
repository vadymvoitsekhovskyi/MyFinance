package com.project.course.myfinance

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvBigProfileLetter: TextView
    private lateinit var ivBigProfile: ImageView
    private lateinit var tvEmailDisplay: TextView
    private lateinit var tvNameDisplay: TextView
    private lateinit var progressBar: ProgressBar

    // Лаунчер для вибору фотографії
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveAvatarToFirestoreAsBase64(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        tvBigProfileLetter = findViewById(R.id.tvBigProfileLetter)
        ivBigProfile = findViewById(R.id.ivBigProfile)
        tvEmailDisplay = findViewById(R.id.tvEmailDisplay)
        tvNameDisplay = findViewById(R.id.tvNameDisplay)
        progressBar = findViewById(R.id.progressBar)
        val cvBigProfile = findViewById<CardView>(R.id.cvBigProfile)

        val btnChangeName = findViewById<Button>(R.id.btnChangeName)
        val btnChangeEmail = findViewById<Button>(R.id.btnChangeEmail)
        val btnChangePassword = findViewById<Button>(R.id.btnChangePassword)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnDeleteAccount = findViewById<Button>(R.id.btnDeleteAccount)

        btnBack.setOnClickListener { finish() }

        updateUI()
        loadAvatarFromFirestore()

        // Клік по аватарці
        cvBigProfile.setOnClickListener { showAvatarOptionsDialog() }

        btnChangeName.setOnClickListener { showChangeNameDialog() }
        btnChangeEmail.setOnClickListener { showChangeEmailDialog() }
        btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        btnLogout.setOnClickListener { showLogoutDialog() }
        btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }
    }

    private fun updateUI() {
        val user = auth.currentUser ?: return
        tvEmailDisplay.text = user.email

        val name = user.displayName
        if (!name.isNullOrBlank()) {
            tvNameDisplay.text = name
            tvBigProfileLetter.text = name.first().uppercase()
        } else {
            tvNameDisplay.text = "Ім'я не вказано"
            tvBigProfileLetter.text = user.email?.first()?.uppercase() ?: "U"
        }
    }

    private fun showAvatarOptionsDialog() {
        // Перевіряємо, чи встановлена та видима аватарка в даний момент
        val hasPhoto = ivBigProfile.visibility == View.VISIBLE

        // Формуємо список опцій залежно від наявності фото
        val options = if (hasPhoto) {
            arrayOf("Замінити", "Видалити")
        } else {
            arrayOf("Встановити")
        }

        AlertDialog.Builder(this)
            .setTitle("Фото профілю")
            .setItems(options) { _, which ->
                if (hasPhoto) {
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*") // Замінити
                        1 -> deleteAvatar() // Видалити
                    }
                } else {
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*") // Встановити
                    }
                }
            }
            .show()
    }

    // Стискаємо і зберігаємо картинку як текст
    private fun saveAvatarToFirestoreAsBase64(imageUri: Uri) {
        setLoading(true)
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Зменшуємо розмір, щоб зберегти місце в базі
            val ratio: Float = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val width = 300
            val height = (width / ratio).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)

            val outputStream = ByteArrayOutputStream()
            // Якість 60%, щоб текст був не надто довгим
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)

            val user = auth.currentUser ?: return

            // Зберігаємо в документ користувача
            val data = hashMapOf("avatarBase64" to base64String)
            db.collection("users").document(user.uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    setLoading(false)
                    Toast.makeText(this, "Фото оновлено!", Toast.LENGTH_SHORT).show()
                    loadAvatarFromFirestore() // Перезавантажуємо
                }
                .addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(this, "Помилка збереження", Toast.LENGTH_SHORT).show()
                }

        } catch (e: Exception) {
            setLoading(false)
            Toast.makeText(this, "Помилка обробки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAvatarFromFirestore() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.contains("avatarBase64")) {
                    val base64String = document.getString("avatarBase64")
                    if (!base64String.isNullOrEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                            val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            ivBigProfile.setImageBitmap(decodedBitmap)
                            ivBigProfile.visibility = View.VISIBLE
                            tvBigProfileLetter.visibility = View.GONE
                        } catch (e: Exception) {
                            // Якщо щось пішло не так при декодуванні
                        }
                    } else {
                        ivBigProfile.visibility = View.GONE
                        tvBigProfileLetter.visibility = View.VISIBLE
                    }
                }
            }
    }

    private fun deleteAvatar() {
        val user = auth.currentUser ?: return
        setLoading(true)

        val updates = hashMapOf<String, Any>(
            "avatarBase64" to FieldValue.delete() // Видаляємо поле з бази
        )

        db.collection("users").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Фото видалено", Toast.LENGTH_SHORT).show()
                ivBigProfile.visibility = View.GONE
                tvBigProfileLetter.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                setLoading(false)
            }
    }

    // --- Далі старі функції ---

    private fun showChangeNameDialog() {
        val input = EditText(this)
        input.hint = "Нове ім'я"
        input.setText(auth.currentUser?.displayName)

        AlertDialog.Builder(this)
            .setTitle("Змінити ім'я")
            .setView(input)
            .setPositiveButton("Зберегти") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    setLoading(true)
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()

                    auth.currentUser?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { task ->
                            setLoading(false)
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Ім'я оновлено", Toast.LENGTH_SHORT).show()
                                updateUI()
                            } else {
                                Toast.makeText(this, "Помилка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showChangeEmailDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val emailInput = EditText(this)
        emailInput.hint = "Новий Email"
        emailInput.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        layout.addView(emailInput)

        val passInput = EditText(this)
        passInput.hint = "Поточний пароль"
        passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(passInput)

        val cbShowPassword = android.widget.CheckBox(this)
        cbShowPassword.text = "Показати пароль"
        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val type = if (isChecked) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passInput.inputType = type
            passInput.setSelection(passInput.text.length)
        }
        layout.addView(cbShowPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Змінити Email")
            .setView(layout)
            .setPositiveButton("Зберегти", null)
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val newEmail = emailInput.text.toString().trim()
                val password = passInput.text.toString().trim()

                if (newEmail.isNotEmpty() && password.isNotEmpty()) {
                    reAuthenticateAndExecute(password, { user ->
                        user.verifyBeforeUpdateEmail(newEmail).addOnCompleteListener { task ->
                            setLoading(false)
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Лист для підтвердження відправлено на новий email. Перезайдіть після підтвердження.", Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                                auth.signOut()
                                goToLogin()
                            } else {
                                Toast.makeText(this, "Помилка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, {})
                } else {
                    Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val oldPassInput = EditText(this)
        oldPassInput.hint = "Поточний пароль"
        oldPassInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(oldPassInput)

        val newPassInput = EditText(this)
        newPassInput.hint = "Новий пароль (від 6 символів)"
        newPassInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(newPassInput)

        val confirmPassInput = EditText(this)
        confirmPassInput.hint = "Підтвердіть новий пароль"
        confirmPassInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(confirmPassInput)

        val cbShowPassword = android.widget.CheckBox(this)
        cbShowPassword.text = "Показати паролі"
        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val type = if (isChecked) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            oldPassInput.inputType = type
            newPassInput.inputType = type
            confirmPassInput.inputType = type

            oldPassInput.setSelection(oldPassInput.text.length)
            newPassInput.setSelection(newPassInput.text.length)
            confirmPassInput.setSelection(confirmPassInput.text.length)
        }
        layout.addView(cbShowPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Змінити пароль")
            .setView(layout)
            .setPositiveButton("Зберегти", null)
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val oldPass = oldPassInput.text.toString().trim()
                val newPass = newPassInput.text.toString().trim()
                val confirmPass = confirmPassInput.text.toString().trim()

                if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, "Новий пароль має бути від 6 символів", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, "Паролі не співпадають!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                reAuthenticateAndExecute(oldPass, { user ->
                    user.updatePassword(newPass).addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Пароль оновлено! Увійдіть знову.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            auth.signOut()
                            goToLogin()
                        } else {
                            Toast.makeText(this, "Помилка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, {})
            }
        }
        dialog.show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Вихід")
            .setMessage("Ви впевнені, що хочете вийти з акаунта?")
            .setPositiveButton("Вийти") { _, _ ->
                auth.signOut()
                goToLogin()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showDeleteAccountDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val input = EditText(this)
        input.hint = "Введіть пароль для підтвердження"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(input)

        val cbShowPassword = android.widget.CheckBox(this)
        cbShowPassword.text = "Показати пароль"
        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val type = if (isChecked) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            input.inputType = type
            input.setSelection(input.text.length)
        }
        layout.addView(cbShowPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Видалити акаунт")
            .setMessage("Увага! Це назавжди видалить ваш акаунт та всі ваші транзакції.")
            .setView(layout)
            .setPositiveButton("Видалити", null)
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.setOnShowListener {
            val btnDelete = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnDelete.setOnClickListener {
                val password = input.text.toString().trim()
                if (password.isNotEmpty()) {
                    reAuthenticateAndExecute(password, { user ->
                        deleteUserDataAndAccount()
                        dialog.dismiss()
                    }, {})
                } else {
                    Toast.makeText(this, "Пароль обов'язковий", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun reAuthenticateAndExecute(
        password: String,
        onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit,
        onFailure: () -> Unit
    ) {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            setLoading(true)
            val credential = EmailAuthProvider.getCredential(user.email!!, password)
            user.reauthenticate(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess(user)
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Помилка авторизації: Невірний пароль", Toast.LENGTH_LONG).show()
                    onFailure()
                }
            }
        }
    }

    private fun deleteUserDataAndAccount() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        db.collection("users").document(uid).collection("transactions").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }

                // Видаляємо також і основний документ користувача (де зберігається аватарка)
                batch.delete(db.collection("users").document(uid))

                batch.commit().addOnCompleteListener {
                    user.delete().addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Акаунт успішно видалено", Toast.LENGTH_SHORT).show()
                            goToLogin()
                        } else {
                            Toast.makeText(this, "Помилка видалення акаунта: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Помилка доступу: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}