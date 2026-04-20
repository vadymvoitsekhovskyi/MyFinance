package com.project.course.myfinance

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.course.myfinance.models.Transaction
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val radioGroupType = findViewById<RadioGroup>(R.id.radioGroupType)
        val rbExpense = findViewById<RadioButton>(R.id.rbExpense)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etCategory = findViewById<EditText>(R.id.etCategory)
        val etComment = findViewById<EditText>(R.id.etComment)
        val btnSave = findViewById<Button>(R.id.btnSaveTransaction)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnSave.setOnClickListener {
            // Додаємо .trim(), щоб відрізати випадкові пробіли, які можуть блокувати український текст
            val amountText = etAmount.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val comment = etComment.text.toString().trim()

            val type = if (rbExpense.isChecked) "expense" else "income"

            if (amountText.isBlank() || category.isBlank()) {
                Toast.makeText(this, "Введіть суму та категорію!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Замінюємо кому на крапку, бо toDoubleOrNull() розуміє тільки крапку!
            val amount = amountText.replace(",", ".").toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Введіть коректну суму!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Помилка: Користувач не авторизований", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnSave.isEnabled = false

            val transactionId = UUID.randomUUID().toString()

            val transaction = Transaction(
                id = transactionId,
                type = type,
                category = category,
                amount = amount,
                date = System.currentTimeMillis(),
                comment = comment
            )

            db.collection("users").document(currentUser.uid)
                .collection("transactions").document(transactionId)
                .set(transaction)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Успішно додано!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}