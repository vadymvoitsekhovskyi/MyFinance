package com.project.course.myfinance

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private lateinit var topPanel: LinearLayout
    private lateinit var selectionPanel: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvProfileLetter: TextView
    private lateinit var ivProfile: ImageView

    private var currentTransactions: List<Transaction> = emptyList()

    // Змінні для пагінації
    private var currentLimit: Long = 10L
    private var snapshotListener: ListenerRegistration? = null
    private var balanceListener: ListenerRegistration? = null

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

        val fabAddTransaction = findViewById<FloatingActionButton>(R.id.fabAddTransaction)
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        rvTransactions = findViewById(R.id.rvTransactions)
        topPanel = findViewById(R.id.topPanel)
        selectionPanel = findViewById(R.id.selectionPanel)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        tvProfileLetter = findViewById(R.id.tvProfileLetter)
        ivProfile = findViewById(R.id.ivProfile)
        val cvProfile = findViewById<CardView>(R.id.cvProfile)

        val btnCloseSelection = findViewById<ImageView>(R.id.btnCloseSelection)
        val btnSelectAll = findViewById<ImageView>(R.id.btnSelectAll)
        val btnDeleteSelected = findViewById<ImageView>(R.id.btnDeleteSelected)

        cvProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        transactionAdapter = TransactionAdapter(
            emptyList(),
            onItemClick = { transaction ->
                if (transactionAdapter.selectedIds.isNotEmpty()) {
                    toggleSelection(transaction.id)
                } else {
                    showTransactionDetailsBottomSheet(transaction)
                }
            },
            onItemLongClick = { transaction ->
                toggleSelection(transaction.id)
            }
        )

        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter

        // Слухач для пагінації (завантаження додаткових транзакцій при скролі вниз)
        rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // Якщо неможливо скролити вниз (дійшли до кінця)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    currentLimit += 10L // Збільшуємо ліміт на 10 сторінок
                    loadTransactions() // Завантажуємо оновлений список
                }
            }
        })

        fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        btnCloseSelection.setOnClickListener { clearSelection() }
        btnSelectAll.setOnClickListener {
            transactionAdapter.selectAll()
            updateSelectionUI()
        }
        btnDeleteSelected.setOnClickListener {
            showDeleteConfirmationDialog(transactionAdapter.selectedIds.toList())
        }

        loadTotalBalance() // Спочатку завантажуємо загальний баланс з усієї бази
        loadTransactions() // Завантажуємо перші 10 транзакцій
        updateProfileIcon()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            updateProfileIcon()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
        balanceListener?.remove()
    }

    private fun updateProfileIcon() {
        val user = auth.currentUser ?: return

        val nameToUse = user.displayName.takeIf { !it.isNullOrBlank() } ?: user.email ?: "U"
        if (nameToUse.isNotEmpty()) {
            tvProfileLetter.text = nameToUse.first().uppercase()
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.contains("avatarBase64")) {
                    val base64String = document.getString("avatarBase64")
                    if (!base64String.isNullOrEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                            val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            ivProfile.setImageBitmap(decodedBitmap)
                            ivProfile.visibility = View.VISIBLE
                            tvProfileLetter.visibility = View.GONE
                        } catch (e: Exception) {
                            // Ігноруємо помилки декодування
                        }
                    } else {
                        ivProfile.visibility = View.GONE
                        tvProfileLetter.visibility = View.VISIBLE
                    }
                } else {
                    ivProfile.visibility = View.GONE
                    tvProfileLetter.visibility = View.VISIBLE
                }
            }
    }

    private fun toggleSelection(id: String) {
        transactionAdapter.toggleSelection(id)
        updateSelectionUI()
    }

    private fun clearSelection() {
        transactionAdapter.clearSelection()
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val count = transactionAdapter.selectedIds.size
        if (count > 0) {
            topPanel.visibility = View.GONE
            selectionPanel.visibility = View.VISIBLE
            tvSelectedCount.text = "Вибрано: $count"
        } else {
            topPanel.visibility = View.VISIBLE
            selectionPanel.visibility = View.GONE
        }
    }

    // Окремий запит для правильного відображення загального балансу (всі транзакції)
    private fun loadTotalBalance() {
        val currentUser = auth.currentUser ?: return

        balanceListener?.remove()
        balanceListener = db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                var totalBalance = 0.0
                for (doc in snapshot.documents) {
                    val type = doc.getString("type") ?: "expense"
                    val amount = doc.getDouble("amount") ?: 0.0
                    if (type == "income") totalBalance += amount else totalBalance -= amount
                }
                tvTotalBalance.text = String.format(Locale.US, "%.2f ₴", totalBalance)
            }
    }

    // Запит для відображення списку з ПАГІНАЦІЄЮ (limit)
    private fun loadTransactions() {
        val currentUser = auth.currentUser ?: return

        snapshotListener?.remove() // Видаляємо попередній слухач перед встановленням нового з більшим лімітом

        snapshotListener = db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(currentLimit) // Встановлюємо пагінацію (10, 20, 30...)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val transactionsList = mutableListOf<Transaction>()

                    for (document in snapshot.documents) {
                        val transaction = document.toObject(Transaction::class.java)
                        if (transaction != null) {
                            transactionsList.add(transaction)
                        }
                    }

                    currentTransactions = transactionsList
                    transactionAdapter.updateData(transactionsList)

                    val validSelectedIds = transactionAdapter.selectedIds.filter { id -> transactionsList.any { it.id == id } }
                    transactionAdapter.selectedIds.clear()
                    transactionAdapter.selectedIds.addAll(validSelectedIds)
                    updateSelectionUI()
                }
            }
    }

    private fun showTransactionDetailsBottomSheet(transaction: Transaction) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_transaction, null)
        bottomSheetDialog.setContentView(view)

        val bsType = view.findViewById<TextView>(R.id.bsType)
        val bsAmount = view.findViewById<TextView>(R.id.bsAmount)
        val bsCategoryValue = view.findViewById<TextView>(R.id.bsCategoryValue)
        val bsDateValue = view.findViewById<TextView>(R.id.bsDateValue)
        val bsCommentValue = view.findViewById<TextView>(R.id.bsCommentValue)
        val bsCommentLabel = view.findViewById<TextView>(R.id.bsCommentLabel)
        val divider3 = view.findViewById<View>(R.id.divider3)

        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        bsCategoryValue.text = transaction.category

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
            bsAmount.text = String.format(Locale.US, "+ %.2f ₴", transaction.amount)
            bsAmount.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            bsType.text = "Витрата"
            bsType.setTextColor(Color.parseColor("#F44336"))
            bsAmount.text = String.format(Locale.US, "- %.2f ₴", transaction.amount)
            bsAmount.setTextColor(Color.parseColor("#000000"))
        }

        btnEdit.setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, AddTransactionActivity::class.java).apply {
                putExtra("EXTRA_ID", transaction.id)
                putExtra("EXTRA_TYPE", transaction.type)
                putExtra("EXTRA_AMOUNT", transaction.amount)
                putExtra("EXTRA_CATEGORY", transaction.category)
                putExtra("EXTRA_COMMENT", transaction.comment)
                putExtra("EXTRA_DATE", transaction.date)
            }
            startActivity(intent)
        }

        btnDelete.setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeleteConfirmationDialog(listOf(transaction.id))
        }

        bottomSheetDialog.show()
    }

    private fun showDeleteConfirmationDialog(transactionIds: List<String>) {
        val message = if (transactionIds.size == 1) {
            "Ви впевнені, що хочете видалити цю транзакцію?"
        } else {
            "Ви впевнені, що хочете видалити ${transactionIds.size} транзакцій?"
        }

        AlertDialog.Builder(this)
            .setTitle("Підтвердження видалення")
            .setMessage(message)
            .setPositiveButton("Видалити") { _, _ ->
                deleteTransactions(transactionIds)
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun deleteTransactions(transactionIds: List<String>) {
        val currentUser = auth.currentUser ?: return
        val batch = db.batch()

        transactionIds.forEach { id ->
            val docRef = db.collection("users").document(currentUser.uid)
                .collection("transactions").document(id)
            batch.delete(docRef)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Успішно видалено!", Toast.LENGTH_SHORT).show()
                clearSelection()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Помилка видалення: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}