package com.project.course.myfinance

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var tvTotalBalance: TextView
    private lateinit var rvTransactions: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Перевірка авторизації
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Ініціалізація елементів екрану
        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)
        val btnLogout = findViewById<ImageView>(R.id.btnLogout)
        val fabAddTransaction = findViewById<FloatingActionButton>(R.id.fabAddTransaction)
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        rvTransactions = findViewById(R.id.rvTransactions)

        tvUserEmail.text = auth.currentUser?.email

        // Налаштування списку (RecyclerView)
        transactionAdapter = TransactionAdapter(emptyList())
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter

        // Кнопки
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        // Завантажуємо дані з Firebase
        loadTransactions()
    }

    private fun loadTransactions() {
        val currentUser = auth.currentUser ?: return

        // Ми ставимо SnapshotListener. Це означає, що як тільки в базі щось зміниться
        // (наприклад, ти додаси транзакцію), цей код автоматично виконається знову
        // і екран миттєво оновиться!
        db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING) // Сортуємо від найновіших до найстаріших
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val transactionsList = mutableListOf<Transaction>()
                    var totalBalance = 0.0

                    // Перебираємо всі документи, які повернув Firebase
                    for (document in snapshot.documents) {
                        // Перетворюємо документ з бази в наш клас Transaction
                        val transaction = document.toObject(Transaction::class.java)

                        if (transaction != null) {
                            transactionsList.add(transaction)

                            // Рахуємо баланс
                            if (transaction.type == "income") {
                                totalBalance += transaction.amount
                            } else {
                                totalBalance -= transaction.amount
                            }
                        }
                    }

                    // Оновлюємо список на екрані
                    transactionAdapter.updateData(transactionsList)

                    // Оновлюємо текст балансу
                    // Використовуємо String.format, щоб було завжди 2 знаки після коми
                    tvTotalBalance.text = String.format("%.2f ₴", totalBalance)
                }
            }
    }
}