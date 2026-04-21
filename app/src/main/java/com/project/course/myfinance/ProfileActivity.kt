package com.project.course.myfinance

import android.app.Activity
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
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ProfileActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var tvBigProfileLetter: TextView
    private lateinit var ivBigProfile: ImageView
    private lateinit var tvEmailDisplay: TextView
    private lateinit var tvNameDisplay: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutAccount: LinearLayout
    private lateinit var layoutData: LinearLayout
    private lateinit var layoutSystem: LinearLayout
    private var currentExportFormat: String = "csv"
    private val gson = Gson()
    private val WEB_CLIENT_ID = "789190818561-pfb6m4vr207b90s5r1ku8pm07bl070i2.apps.googleusercontent.com"
    private lateinit var googleSignInClient: GoogleSignInClient
    private var onGoogleReAuthSuccess: (() -> Unit)? = null

    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            uriContent?.let { saveAvatarToFirestoreAsBase64(it) }
        } else {
            val exception = result.error
            if (exception != null) {
                Toast.makeText(this, "Помилка редагування фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            uri?.let { performExport(it) }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { performImport(it) }
        }

    private val googleReAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        setLoading(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    setLoading(true)
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.currentUser?.reauthenticate(credential)?.addOnCompleteListener { reAuthTask ->
                        if (reAuthTask.isSuccessful) {
                            onGoogleReAuthSuccess?.invoke()
                        } else {
                            setLoading(false)
                            Toast.makeText(this, "Помилка перевірки акаунта", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Вхід скасовано або помилка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

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

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        layoutAccount = findViewById(R.id.layoutAccount)
        layoutData = findViewById(R.id.layoutData)
        layoutSystem = findViewById(R.id.layoutSystem)

        val user = auth.currentUser
        val hasPassword = user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
        btnChangePassword.text = if (hasPassword) "Змінити пароль" else "Створити пароль"

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                layoutAccount.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                layoutData.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                layoutSystem.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnBack.setOnClickListener { finish() }
        updateUI()
        loadAvatarFromFirestore()
        cvBigProfile.setOnClickListener { showAvatarOptionsDialog() }
        btnChangeName.setOnClickListener { showChangeNameDialog() }
        btnChangeEmail.setOnClickListener { showChangeEmailDialog() }
        btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        btnExportData.setOnClickListener { showExportDialog() }
        btnImportData.setOnClickListener { showImportDialog() }
        btnLogout.setOnClickListener { showLogoutDialog() }
        btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }
    }

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
                val snapshot = db.collection("users").document(user.uid)
                    .collection("transactions").get().await()

                val transactions = snapshot.toObjects(Transaction::class.java)

                val content = if (currentExportFormat == "json") {
                    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

                    val jsonList = transactions.map { t ->
                        mapOf(
                            "id" to t.id,
                            "type" to t.type,
                            "category" to t.category,
                            "amount" to t.amount,
                            "date" to sdf.format(Date(t.date)),
                            "comment" to t.comment
                        )
                    }
                    gson.toJson(jsonList)
                } else {
                    buildCsvString(transactions)
                }

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    if (currentExportFormat == "csv") {
                        outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    }
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(
                        this@ProfileActivity,
                        "Дані успішно експортовано",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(
                        this@ProfileActivity,
                        "Помилка експорту: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) throw Exception("Файл порожній або його неможливо прочитати")

                val cleanContent = if (content.startsWith("\uFEFF")) content.substring(1) else content
                val isJson = cleanContent.trimStart().startsWith("[")

                val importedTransactions = if (isJson) {
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val jsonList: List<Map<String, Any>> = gson.fromJson(cleanContent, type)

                    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

                    jsonList.map { map ->
                        val dateVal = map["date"]
                        val parsedDateLong = when (dateVal) {
                            is String -> try {
                                sdf.parse(dateVal)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                            is Number -> dateVal.toLong()
                            else -> System.currentTimeMillis()
                        }

                        Transaction(
                            id = map["id"] as? String ?: "",
                            type = map["type"] as? String ?: "",
                            category = map["category"] as? String ?: "",
                            amount = (map["amount"] as? Double) ?: 0.0,
                            date = parsedDateLong,
                            comment = map["comment"] as? String ?: ""
                        )
                    }
                } else {
                    parseCsvString(cleanContent)
                }

                if (importedTransactions.isEmpty()) {
                    throw Exception("Не знайдено коректних даних у файлі")
                }

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
                    Toast.makeText(
                        this@ProfileActivity,
                        "Успішно імпортовано ${importedTransactions.size} транзакцій",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(
                        this@ProfileActivity,
                        "Помилка імпорту: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun buildCsvString(transactions: List<Transaction>): String {
        val builder = java.lang.StringBuilder()
        builder.append("id,type,category,amount,date,comment\n")

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

        transactions.forEach { t ->
            val safeCategory = t.category.replace("\"", "\"\"").let { "\"$it\"" }
            val safeComment = t.comment.replace("\"", "\"\"").let { "\"$it\"" }
            val formattedDate = sdf.format(Date(t.date))

            builder.append("${t.id},${t.type},$safeCategory,${t.amount},$formattedDate,$safeComment\n")
        }
        return builder.toString()
    }

    private fun parseCsvString(csv: String): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val lines = csv.lines()
        if (lines.size <= 1) return list

        val csvRegex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val tokens = line.split(csvRegex).map { it.trim('\"') }
            if (tokens.size >= 5) {
                try {
                    val parsedDateLong = try {
                        sdf.parse(tokens[4])?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        tokens[4].toLongOrNull() ?: System.currentTimeMillis()
                    }

                    list.add(
                        Transaction(
                            id = tokens[0],
                            type = tokens[1],
                            category = tokens[2],
                            amount = tokens[3].toDoubleOrNull() ?: 0.0,
                            date = parsedDateLong,
                            comment = if (tokens.size > 5) tokens[5] else ""
                        )
                    )
                } catch (e: Exception) {
                }
            }
        }
        return list
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
        val hasPhoto = ivBigProfile.visibility == View.VISIBLE
        val options = if (hasPhoto) arrayOf("Змінити/Редагувати", "Видалити") else arrayOf("Встановити")

        AlertDialog.Builder(this)
            .setTitle("Фото профілю")
            .setItems(options) { _, which ->
                if (hasPhoto) {
                    when (which) {
                        0 -> launchCropper()
                        1 -> deleteAvatar()
                    }
                } else {
                    when (which) {
                        0 -> launchCropper()
                    }
                }
            }
            .show()
    }

    private fun launchCropper() {
        cropImageLauncher.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeGallery = true,
                    imageSourceIncludeCamera = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    fixAspectRatio = true
                )
            )
        )
    }

    private fun saveAvatarToFirestoreAsBase64(imageUri: Uri) {
        setLoading(true)
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val size = 800
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)
            val user = auth.currentUser ?: return
            val data = hashMapOf("avatarBase64" to base64String)

            db.collection("users").document(user.uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    setLoading(false)
                    Toast.makeText(this, "Фото оновлено", Toast.LENGTH_SHORT).show()
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
                            val decodedBitmap =
                                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            ivBigProfile.setImageBitmap(decodedBitmap)
                            ivBigProfile.visibility = View.VISIBLE
                            tvBigProfileLetter.visibility = View.GONE
                        } catch (e: Exception) {
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
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val input = EditText(this)
        input.hint = "Нове ім'я"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.setText(auth.currentUser?.displayName)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Змінити ім'я")
            .setView(layout)
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
                                Toast.makeText(
                                    this,
                                    "Помилка: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showChangeEmailDialog() {
        val user = auth.currentUser ?: return
        val hasPassword = user.providerData.any { it.providerId == com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 40, 64, 10)
        }

        val tilEmail = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Новий email"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        val emailInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        tilEmail.addView(emailInput)
        layout.addView(tilEmail)

        var passInput: com.google.android.material.textfield.TextInputEditText? = null
        if (hasPassword) {
            val tilPass = com.google.android.material.textfield.TextInputLayout(this).apply {
                hint = "Поточний пароль"
                endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            passInput = com.google.android.material.textfield.TextInputEditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            tilPass.addView(passInput)
            layout.addView(tilPass)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Змінити email")
            .setView(layout)
            .setPositiveButton("Зберегти", null)
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val newEmail = emailInput.text.toString().trim()
                if (newEmail.isEmpty()) {
                    Toast.makeText(this, "Введіть новий email", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (hasPassword) {
                    val password = passInput?.text.toString().trim()
                    if (password.isNotEmpty()) {
                        reAuthenticateAndExecute(password, { u ->
                            updateEmail(u, newEmail, dialog)
                        }, {})
                    } else {
                        Toast.makeText(this, "Введіть пароль", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(true)
                    onGoogleReAuthSuccess = {
                        updateEmail(user, newEmail, dialog)
                    }
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleReAuthLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun updateEmail(user: FirebaseUser, newEmail: String, dialog: AlertDialog) {
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
    }

    private fun showChangePasswordDialog() {
        val user = auth.currentUser ?: return
        val hasPassword = user.providerData.any { it.providerId == com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 40, 64, 10)
        }

        var oldPassInput: com.google.android.material.textfield.TextInputEditText? = null
        if (hasPassword) {
            val tilOldPass = com.google.android.material.textfield.TextInputLayout(this).apply {
                hint = "Поточний пароль"
                endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
            }
            oldPassInput = com.google.android.material.textfield.TextInputEditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            tilOldPass.addView(oldPassInput)
            layout.addView(tilOldPass)
        }

        val tilNewPass = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = if (hasPassword) "Новий пароль" else "Створіть пароль"
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        val newPassInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        tilNewPass.addView(newPassInput)
        layout.addView(tilNewPass)

        val tilConfirmPass = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Підтвердіть пароль"
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val confirmPassInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        tilConfirmPass.addView(confirmPassInput)
        layout.addView(tilConfirmPass)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (hasPassword) "Змінити пароль" else "Створити пароль")
            .setView(layout)
            .setPositiveButton("Зберегти", null)
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val oldPass = oldPassInput?.text?.toString()?.trim() ?: ""
                val newPass = newPassInput.text.toString().trim()
                val confirmPass = confirmPassInput.text.toString().trim()

                if ((hasPassword && oldPass.isEmpty()) || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(this, "Заповніть всі поля", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, "Пароль має бути від 6 символів", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, "Паролі не співпадають", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (hasPassword) {
                    reAuthenticateAndExecute(oldPass, { u ->
                        u.updatePassword(newPass).addOnCompleteListener { task ->
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
                } else {
                    setLoading(true)
                    onGoogleReAuthSuccess = {
                        user.updatePassword(newPass).addOnCompleteListener { task ->
                            setLoading(false)
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Пароль створено! Тепер можна входити і за паролем.", Toast.LENGTH_LONG).show()
                                findViewById<Button>(R.id.btnChangePassword).text = "Змінити пароль"
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this, "Помилка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleReAuthLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
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
                googleSignInClient.signOut()
                goToLogin()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showDeleteAccountDialog() {
        val user = auth.currentUser ?: return
        val hasPassword = user.providerData.any { it.providerId == com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID }

        if (hasPassword) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 40, 64, 10)
            }

            val tilPass = com.google.android.material.textfield.TextInputLayout(this).apply {
                hint = "Введіть пароль для підтвердження"
                endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val input = com.google.android.material.textfield.TextInputEditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            tilPass.addView(input)
            layout.addView(tilPass)

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
                        reAuthenticateAndExecute(password, { _ ->
                            deleteUserDataAndAccount()
                            dialog.dismiss()
                        }, {})
                    } else {
                        Toast.makeText(this, "Пароль обов'язковий", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Видалити акаунт")
                .setMessage("Увага! Це назавжди видалить ваш акаунт та всі ваші транзакції.\n\nДля підтвердження необхідно буде вибрати ваш Google акаунт.")
                .setPositiveButton("Підтвердити") { _, _ ->
                    setLoading(true)
                    onGoogleReAuthSuccess = {
                        deleteUserDataAndAccount()
                    }
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleReAuthLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }
    }

    private fun reAuthenticateAndExecute(
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
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
                    Toast.makeText(this, "Помилка авторизації: невірний пароль", Toast.LENGTH_LONG)
                        .show()
                    onFailure()
                }
            }
        }
    }

    private fun deleteUserDataAndAccount() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        setLoading(true)

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
                            Toast.makeText(this, "Акаунт успішно видалено", Toast.LENGTH_SHORT)
                                .show()
                            googleSignInClient.signOut()
                            goToLogin()
                        } else {
                            Toast.makeText(
                                this,
                                "Помилка видалення акаунта: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
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