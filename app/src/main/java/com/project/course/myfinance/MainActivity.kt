package com.project.course.myfinance

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var tvTotalBalance: TextView
    private lateinit var rvTransactions: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)
        // btnLogout видалено!
        val fabAddTransaction = findViewById<FloatingActionButton>(R.id.fabAddTransaction)
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        rvTransactions = findViewById(R.id.rvTransactions)

        tvUserEmail.text = auth.currentUser?.email

        // Налаштування списку та передача лямбди для обробки кліку
        transactionAdapter = TransactionAdapter(emptyList()) { clickedTransaction ->
            showTransactionDetailsBottomSheet(clickedTransaction)
        }
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter

        fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        loadTransactions()
    }

    private fun loadTransactions() {
        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val transactionsList = mutableListOf<Transaction>()
                    var totalBalance = 0.0

                    for (document in snapshot.documents) {
                        val transaction = document.toObject(Transaction::class.java)

                        if (transaction != null) {
                            transactionsList.add(transaction)

                            if (transaction.type == "income") {
                                totalBalance += transaction.amount
                            } else {
                                totalBalance -= transaction.amount
                            }
                        }
                    }

                    transactionAdapter.updateData(transactionsList)
                    tvTotalBalance.text = String.format("%.2f ₴", totalBalance)
                }
            }
    }

    // --- ДОДАНО ---
    // Функція для показу модального вікна знизу
    private fun showTransactionDetailsBottomSheet(transaction: Transaction) {
        // Створюємо діалог і підключаємо до нього наш новий дизайн
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_transaction, null)
        bottomSheetDialog.setContentView(view)

        // Знаходимо елементи всередині дизайну діалогу
        val bsType = view.findViewById<TextView>(R.id.bsType)
        val bsAmount = view.findViewById<TextView>(R.id.bsAmount)
        val bsCategoryValue = view.findViewById<TextView>(R.id.bsCategoryValue)
        val bsDateValue = view.findViewById<TextView>(R.id.bsDateValue)
        val bsCommentValue = view.findViewById<TextView>(R.id.bsCommentValue)
        val bsCommentLabel = view.findViewById<TextView>(R.id.bsCommentLabel)
        val divider3 = view.findViewById<View>(R.id.divider3)

        // Заповнюємо даними
        bsCategoryValue.text = transaction.category

        // Форматуємо повну дату та час
        val fullDateFormatter = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("uk", "UA"))
        fullDateFormatter.timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        bsDateValue.text = fullDateFormatter.format(Date(transaction.date))

        if (transaction.comment.isNotBlank()) {
            bsCommentValue.text = transaction.comment
            bsCommentValue.visibility = View.VISIBLE
            bsCommentLabel.visibility = View.VISIBLE
            divider3.visibility = View.VISIBLE
        } else {
            bsCommentValue.visibility = View.GONE
            bsCommentLabel.visibility = View.GONE
            divider3.visibility = View.GONE
        }

        if (transaction.type == "income") {
            bsType.text = "Дохід"
            bsType.setTextColor(Color.parseColor("#4CAF50"))
            bsAmount.text = "+ ${transaction.amount} ₴"
            bsAmount.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            bsType.text = "Витрата"
            bsType.setTextColor(Color.parseColor("#F44336"))
            bsAmount.text = "- ${transaction.amount} ₴"
            bsAmount.setTextColor(Color.parseColor("#000000")) // Можемо зробити суму чорною для контрасту у вікні
        }

        // Показуємо діалог
        bottomSheetDialog.show()
    }
}