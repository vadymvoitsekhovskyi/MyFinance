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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.course.myfinance.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvBigProfileLetter: TextView
    private lateinit var ivBigProfile: ImageView
    private lateinit var tvEmailDisplay: TextView
    private lateinit var tvNameDisplay: TextView
    private lateinit var progressBar: ProgressBar

    // Змінна для збереження вибраного формату при експорті
    private var currentExportFormat: String = "csv"
    private val gson = Gson()

    // Лаунчери для фото та файлів
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveAvatarToFirestoreAsBase64(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        uri?.let { performExport(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { performImport(it) }
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
        val btnExportData = findViewById<Button>(R.id.btnExportData)
        val btnImportData = findViewById<Button>(R.id.btnImportData)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnDeleteAccount = findViewById<Button>(R.id.btnDeleteAccount)

        btnBack.setOnClickListener { finish() }

        updateUI()
        loadAvatarFromFirestore()

        cvBigProfile.setOnClickListener { showAvatarOptionsDialog() }
        btnChangeName.setOnClickListener { showChangeNameDialog() }
        btnChangeEmail.setOnClickListener { showChangeEmailDialog() }
        btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        // Нові обробники для експорту/імпорту
        btnExportData.setOnClickListener { showExportDialog() }
        btnImportData.setOnClickListener { showImportDialog() }

        btnLogout.setOnClickListener { showLogoutDialog() }
        btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }
    }

    // ==========================================
    // ЛОГІКА ЕКСПОРТУ ТА ІМПОРТУ
    // ==========================================

    private fun showExportDialog() {
        val formats = arrayOf("CSV", "JSON")
        AlertDialog.Builder(this)
            .setTitle("Оберіть формат експорту")
            .setItems(formats) { _, which ->
                currentExportFormat = if (which == 0) "csv" else "json"
                val fileName = "myfinance_backup_${System.currentTimeMillis()}.$currentExportFormat"
                exportLauncher.launch(fileName)
            }
            .show()
    }

    private fun showImportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Імпорт даних")
            .setMessage("Оберіть файл формату CSV або JSON. Зверніть увагу, що нові транзакції будуть додані до вашого поточного списку.")
            .setPositiveButton("Вибрати файл") { _, _ ->
                importLauncher.launch("*/*")
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun performExport(uri: Uri) {
        val user = auth.currentUser ?: return
        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Отримуємо всі транзакції користувача
                val snapshot = db.collection("users").document(user.uid)
                    .collection("transactions").get().await()

                val transactions = snapshot.toObjects(Transaction::class.java)

                // Формуємо текст залежно від формату
                val content = if (currentExportFormat == "json") {
                    gson.toJson(transactions)
                } else {
                    buildCsvString(transactions)
                }

                // Записуємо у файл
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@ProfileActivity, "Дані успішно експортовано", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@ProfileActivity, "Помилка експорту: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Читаємо вміст файлу
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) throw Exception("Файл порожній або його неможливо прочитати")

                // Визначаємо формат по контенту (починається з [ - це масив JSON)
                val isJson = content.trimStart().startsWith("[")

                val importedTransactions = if (isJson) {
                    val type = object : TypeToken<List<Transaction>>() {}.type
                    gson.fromJson<List<Transaction>>(content, type)
                } else {
                    parseCsvString(content)
                }

                if (importedTransactions.isEmpty()) {
                    throw Exception("Не знайдено коректних даних у файлі")
                }

                // Зберігаємо транзакції в базу порціями (батчами) по 500 штук
                val user = auth.currentUser ?: return@launch
                val chunks = importedTransactions.chunked(500)

                for (chunk in chunks) {
                    val batch = db.batch()
                    for (t in chunk) {
                        val docRef = db.collection("users").document(user.uid)
                            .collection("transactions").document(t.id)
                        batch.set(docRef, t)
                    }
                    batch.commit().await()
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@ProfileActivity, "Успішно імпортовано ${importedTransactions.size} транзакцій!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@ProfileActivity, "Помилка імпорту: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Допоміжний метод: Створення CSV з об'єктів
    private fun buildCsvString(transactions: List<Transaction>): String {
        val builder = java.lang.StringBuilder()
        builder.append("id,type,category,amount,date,comment\n") // Заголовок

        transactions.forEach { t ->
            // Екрануємо лапки, якщо вони є у тексті
            val safeCategory = t.category.replace("\"", "\"\"").let { "\"$it\"" }
            val safeComment = t.comment.replace("\"", "\"\"").let { "\"$it\"" }

            builder.append("${t.id},${t.type},$safeCategory,${t.amount},${t.date},$safeComment\n")
        }
        return builder.toString()
    }

    // Допоміжний метод: Парсинг CSV в об'єкти
    private fun parseCsvString(csv: String): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val lines = csv.lines()
        if (lines.size <= 1) return list

        // Регулярний вираз для розбиття по комі, ігноруючи коми всередині лапок
        val csvRegex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

        for (i in 1 until lines.size) { // Починаємо з 1, пропускаючи заголовок
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val tokens = line.split(csvRegex).map { it.trim('\"') }
            if (tokens.size >= 5) {
                try {
                    list.add(Transaction(
                        id = tokens[0],
                        type = tokens[1],
                        category = tokens[2],
                        amount = tokens[3].toDoubleOrNull() ?: 0.0,
                        date = tokens[4].toLongOrNull() ?: System.currentTimeMillis(),
                        comment = if (tokens.size > 5) tokens[5] else ""
                    ))
                } catch (e: Exception) {
                    // Пропускаємо некоректні рядки
                }
            }
        }
        return list
    }

    // ==========================================
    // СТАРИЙ КОД (ЗАЛИШИВСЯ БЕЗ ЗМІН ДЛЯ СТАБІЛЬНОСТІ)
    // ==========================================

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
        val hasPhoto = ivBigProfile.visibility == View.VISIBLE
        val options = if (hasPhoto) arrayOf("Замінити", "Видалити") else arrayOf("Встановити")

        AlertDialog.Builder(this)
            .setTitle("Фото профілю")
            .setItems(options) { _, which ->
                if (hasPhoto) {
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*")
                        1 -> deleteAvatar()
                    }
                } else {
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }

    private fun saveAvatarToFirestoreAsBase64(imageUri: Uri) {
        setLoading(true)
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            val ratio: Float = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val width = 300
            val height = (width / ratio).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)

            val user = auth.currentUser ?: return

            val data = hashMapOf("avatarBase64" to base64String)
            db.collection("users").document(user.uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    setLoading(false)
                    Toast.makeText(this, "Фото оновлено!", Toast.LENGTH_SHORT).show()
                    loadAvatarFromFirestore()
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
                            // Ігноруємо
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

        val updates = hashMapOf<String, Any>("avatarBase64" to FieldValue.delete())

        db.collection("users").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Фото видалено", Toast.LENGTH_SHORT).show()
                ivBigProfile.visibility = View.GONE
                tvBigProfileLetter.visibility = View.VISIBLE
            }
            .addOnFailureListener { setLoading(false) }
    }

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