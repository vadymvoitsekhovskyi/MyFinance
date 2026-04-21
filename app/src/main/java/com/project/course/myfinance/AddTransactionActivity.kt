package com.project.course.myfinance

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.course.myfinance.models.Transaction
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var editingTransactionId: String? = null
    private var originalDate: Long = 0L

    // Список категорій за замовчуванням
    private val categories = arrayOf(
        "Продукти", "Транспорт", "Спорт", "Техніка", "Дім",
        "Здоров'я", "Одяг", "Кафе/Ресторани", "Зарплата", "Подарунки", "Інше"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val radioGroupType = findViewById<RadioGroup>(R.id.radioGroupType)
        val rbExpense = findViewById<RadioButton>(R.id.rbExpense)
        val rbIncome = findViewById<RadioButton>(R.id.rbIncome)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etCategory = findViewById<AutoCompleteTextView>(R.id.etCategory)
        val etComment = findViewById<EditText>(R.id.etComment)
        val btnSave = findViewById<Button>(R.id.btnSaveTransaction)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnBack.setOnClickListener { finish() }

        // Налаштування випадаючого списку категорій
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        etCategory.setAdapter(adapter)

        editingTransactionId = intent.getStringExtra("EXTRA_ID")
        if (editingTransactionId != null) {
            tvTitle.text = "Редагування"
            btnSave.text = "Зберегти зміни"

            val type = intent.getStringExtra("EXTRA_TYPE") ?: "expense"
            val amount = intent.getDoubleExtra("EXTRA_AMOUNT", 0.0)
            val category = intent.getStringExtra("EXTRA_CATEGORY") ?: ""
            val comment = intent.getStringExtra("EXTRA_COMMENT") ?: ""
            originalDate = intent.getLongExtra("EXTRA_DATE", System.currentTimeMillis())

            if (type == "income") rbIncome.isChecked = true else rbExpense.isChecked = true
            etAmount.setText(amount.toString())
            etCategory.setText(category, false) // false - щоб не відкривався список автоматично при заповненні
            etComment.setText(comment)
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val comment = etComment.text.toString().trim()
            val type = if (rbExpense.isChecked) "expense" else "income"

            if (amountText.isBlank() || category.isBlank()) {
                Toast.makeText(this, "Введіть суму та оберіть категорію", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.replace(",", ".").toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Введіть коректну суму", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Збереження")
                .setMessage(if (editingTransactionId != null) "Зберегти внесені зміни?" else "Додати нову транзакцію?")
                .setPositiveButton("Так") { _, _ ->
                    saveTransactionToFirebase(type, amount, category, comment, progressBar, btnSave)
                }
                .setNegativeButton("Ні", null)
                .show()
        }
    }

    private fun saveTransactionToFirebase(
        type: String, amount: Double, category: String, comment: String,
        progressBar: ProgressBar, btnSave: Button
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Помилка: користувач не авторизований", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        val transactionId = editingTransactionId ?: UUID.randomUUID().toString()
        val transactionDate =
            if (editingTransactionId != null) originalDate else System.currentTimeMillis()

        val transaction = Transaction(
            id = transactionId,
            type = type,
            category = category,
            amount = amount,
            date = transactionDate,
            comment = comment
        )

        db.collection("users").document(currentUser.uid)
            .collection("transactions").document(transactionId)
            .set(transaction)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Успішно збережено", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}