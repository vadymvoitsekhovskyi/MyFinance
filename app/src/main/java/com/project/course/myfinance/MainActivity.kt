package com.project.course.myfinance

import android.app.DatePickerDialog
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
import com.bumptech.glide.Glide
import android.graphics.drawable.Drawable
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var tvTotalBalance: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var topPanel: LinearLayout
    private lateinit var selectionPanel: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvProfileLetter: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivBalanceLogo: ImageView
    private var currentTransactions: List<Transaction> = emptyList()
    private var currentLimit: Long = 30L
    private var isAllLoaded = false
    private var isScrollDownRequested = false
    private var snapshotListener: ListenerRegistration? = null
    private var balanceListener: ListenerRegistration? = null
    private var currentTypeFilter = "all"
    private var currentCategoryFilter = "all"
    private var currentSortOrder = "desc"
    private var currentSpecificDateFilter: Long? = null
    private lateinit var btnFilterType: Button
    private lateinit var btnFilterCategory: Button
    private lateinit var btnFilterSpecificDate: Button
    private lateinit var btnSortDate: Button
    private var isBalanceVisible = false
    private var currentTotalBalance = 0.0
    private var dailyBalancesMap = mapOf<String, Double>()

    private val animatedIcons = listOf(
        R.drawable.icon_anim_1,
        R.drawable.icon_anim_2,
        R.drawable.icon_anim_3
    )

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
        val scrollButtonsContainer = findViewById<LinearLayout>(R.id.scrollButtonsContainer)
        val fabScrollUp = findViewById<FloatingActionButton>(R.id.fabScrollUp)
        val fabScrollDown = findViewById<FloatingActionButton>(R.id.fabScrollDown)

        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        rvTransactions = findViewById(R.id.rvTransactions)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        topPanel = findViewById(R.id.topPanel)
        selectionPanel = findViewById(R.id.selectionPanel)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        tvProfileLetter = findViewById(R.id.tvProfileLetter)
        ivProfile = findViewById(R.id.ivProfile)
        ivBalanceLogo = findViewById(R.id.ivBalanceLogo)

        val cvProfile = findViewById<CardView>(R.id.cvProfile)
        val layoutBalanceText = findViewById<LinearLayout>(R.id.layoutBalanceText)
        val btnCloseSelection = findViewById<ImageView>(R.id.btnCloseSelection)
        val btnSelectAll = findViewById<ImageView>(R.id.btnSelectAll)
        val btnDeleteSelected = findViewById<ImageView>(R.id.btnDeleteSelected)

        btnFilterType = findViewById(R.id.btnFilterType)
        btnFilterCategory = findViewById(R.id.btnFilterCategory)
        btnFilterSpecificDate = findViewById(R.id.btnFilterSpecificDate)
        btnSortDate = findViewById(R.id.btnSortDate)

        tvTotalBalance.text = "••••• ₴"

        layoutBalanceText.setOnClickListener {
            toggleBalanceVisibility()
        }

        btnFilterType.setOnClickListener { showTypeFilterDialog() }
        btnFilterCategory.setOnClickListener { showCategoryFilterDialog() }
        btnFilterSpecificDate.setOnClickListener { showSpecificDatePicker() }
        btnSortDate.setOnClickListener { showSortDialog() }

        cvProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val btnStatistics = findViewById<ImageView>(R.id.btnStatistics)
        btnStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        fabScrollUp.setOnClickListener {
            rvTransactions.smoothScrollToPosition(0)

            if (isAllLoaded) {
                isAllLoaded = false
                currentLimit = 10L
                loadTransactions()
            }
        }

        fabScrollDown.setOnClickListener {
            isScrollDownRequested = true
            isAllLoaded = true
            loadTransactions()
        }

        transactionAdapter = TransactionAdapter(emptyList(), onItemClick = { transaction ->
            if (transactionAdapter.selectedIds.isNotEmpty()) {
                toggleSelection(transaction.id)
            } else {
                showTransactionDetailsBottomSheet(transaction)
            }
        }, onItemLongClick = { transaction ->
            toggleSelection(transaction.id)
        })

        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter

        rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (recyclerView.computeVerticalScrollOffset() > 0) {
                    scrollButtonsContainer.visibility = View.VISIBLE
                } else {
                    scrollButtonsContainer.visibility = View.GONE
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!isAllLoaded) {
                        currentLimit += 10L
                        loadTransactions()
                    }
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

        loadTotalBalance()
        loadTransactions()
        updateProfileIcon()
        startAnimatedIconsLoop()
    }

    private fun toggleBalanceVisibility() {
        if (isBalanceVisible) {
            isBalanceVisible = false
            tvTotalBalance.text = "••••• ₴"
        } else {
            isBalanceVisible = true
            tvTotalBalance.text = String.format(Locale.US, "%.2f ₴", currentTotalBalance)
        }
    }

    private fun showTypeFilterDialog() {
        val options = arrayOf("Всі", "Витрати", "Доходи")
        val checkedItem = when (currentTypeFilter) {
            "expense" -> 1; "income" -> 2; else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Оберіть тип")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentTypeFilter = when (which) {
                    1 -> "expense"; 2 -> "income"; else -> "all"
                }
                btnFilterType.text = "Тип: ${options[which]}"
                currentLimit = 100L
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun showCategoryFilterDialog() {
        val categories = currentTransactions.map { it.category }.distinct().sorted().toMutableList()
        categories.add(0, "Всі")
        val options = categories.toTypedArray()
        val checkedItem =
            if (currentCategoryFilter == "all") 0 else options.indexOf(currentCategoryFilter)
                .takeIf { it != -1 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Оберіть категорію")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentCategoryFilter = if (which == 0) "all" else options[which]

                var shortCat = options[which]
                if (shortCat.length > 10 && which != 0) shortCat = shortCat.substring(0, 10) + "..."

                btnFilterCategory.text = if (which == 0) "Категорія: Всі" else shortCat
                currentLimit = 100L
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun showSpecificDatePicker() {
        val calendar = Calendar.getInstance()

        if (currentSpecificDateFilter != null) {
            calendar.timeInMillis = currentSpecificDateFilter!!
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog =
            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                currentSpecificDateFilter = selectedCalendar.timeInMillis

                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                btnFilterSpecificDate.text = "Дата: ${sdf.format(selectedCalendar.time)}"

                currentLimit = 10L
                loadTransactions()
            }, year, month, day)

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

        datePickerDialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Очистити") { _, _ ->
            currentSpecificDateFilter = null
            btnFilterSpecificDate.text = "Дата: Всі"
            currentLimit = 10L
            loadTransactions()
        }

        datePickerDialog.show()
    }

    private fun showSortDialog() {
        val options = arrayOf("Спочатку нові (↓)", "Спочатку старі (↑)")
        val checkedItem = if (currentSortOrder == "desc") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Сортування за датою")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentSortOrder = if (which == 0) "desc" else "asc"
                btnSortDate.text = if (which == 0) "Сорт: Нові" else "Сорт: Старі"
                currentLimit = 100L
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun applyFiltersAndSort() {
        var result = currentTransactions

        if (currentTypeFilter != "all") {
            result = result.filter { it.type == currentTypeFilter }
        }

        if (currentCategoryFilter != "all") {
            result = result.filter { it.category == currentCategoryFilter }
        }

        result = if (currentSortOrder == "desc") {
            result.sortedByDescending { it.date }
        } else {
            result.sortedBy { it.date }
        }

        transactionAdapter.updateData(result)
        updateFilterButtonsStyle()

        if (result.isEmpty()) {
            rvTransactions.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvTransactions.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun updateFilterButtonsStyle() {
        val colorActiveText = Color.WHITE
        val colorActiveBg = Color.parseColor("#2E7D32")
        val colorInactiveText = Color.parseColor("#757575")
        val colorTransparent = Color.TRANSPARENT

        if (currentTypeFilter != "all") {
            btnFilterType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorActiveBg)
            btnFilterType.setTextColor(colorActiveText)
        } else {
            btnFilterType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorTransparent)
            btnFilterType.setTextColor(colorInactiveText)
        }

        if (currentCategoryFilter != "all") {
            btnFilterCategory.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorActiveBg)
            btnFilterCategory.setTextColor(colorActiveText)
        } else {
            btnFilterCategory.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorTransparent)
            btnFilterCategory.setTextColor(colorInactiveText)
        }

        if (currentSpecificDateFilter != null) {
            btnFilterSpecificDate.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorActiveBg)
            btnFilterSpecificDate.setTextColor(colorActiveText)
        } else {
            btnFilterSpecificDate.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorTransparent)
            btnFilterSpecificDate.setTextColor(colorInactiveText)
        }

        if (currentSortOrder != "desc") {
            btnSortDate.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorActiveBg)
            btnSortDate.setTextColor(colorActiveText)
        } else {
            btnSortDate.backgroundTintList =
                android.content.res.ColorStateList.valueOf(colorTransparent)
            btnSortDate.setTextColor(colorInactiveText)
        }
    }

    private fun startAnimatedIconsLoop() {
        if (animatedIcons.isNotEmpty()) {
            animatedIcons.forEach {
                Glide.with(this).asGif().load(it).preload()
            }
            playNextAnimatedIcon(0)
        }
    }

    private fun playNextAnimatedIcon(currentIndex: Int) {
        if (isDestroyed || isFinishing) return

        var request = Glide.with(this)
            .asGif()
            .load(animatedIcons[currentIndex])

        if (ivBalanceLogo.drawable != null) {
            request = request.placeholder(ivBalanceLogo.drawable)
        }

        if (currentIndex == 0) {
            request = request.transition(DrawableTransitionOptions.withCrossFade(400))
        }

        request.listener(object : RequestListener<GifDrawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<GifDrawable>,
                isFirstResource: Boolean
            ): Boolean {
                ivBalanceLogo.postDelayed({
                    playNextAnimatedIcon((currentIndex + 1) % animatedIcons.size)
                }, 1000)
                return false
            }

            override fun onResourceReady(
                resource: GifDrawable,
                model: Any,
                target: Target<GifDrawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                resource.setLoopCount(1)
                resource.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        resource.unregisterAnimationCallback(this)
                        playNextAnimatedIcon((currentIndex + 1) % animatedIcons.size)
                    }
                })
                return false
            }
        })
            .into(ivBalanceLogo)
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

        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            if (document != null && document.contains("avatarBase64")) {
                val base64String = document.getString("avatarBase64")
                if (!base64String.isNullOrEmpty()) {
                    try {
                        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                        val decodedBitmap =
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        ivProfile.setImageBitmap(decodedBitmap)
                        ivProfile.visibility = View.VISIBLE
                        tvProfileLetter.visibility = View.GONE
                    } catch (e: Exception) {
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

    private fun loadTotalBalance() {
        val currentUser = auth.currentUser ?: return

        balanceListener?.remove()
        balanceListener =
            db.collection("users").document(currentUser.uid).collection("transactions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val allTrans =
                        snapshot.documents.mapNotNull { it.toObject(Transaction::class.java) }

                    val sortedTrans = allTrans.sortedBy { it.date }
                    var totalBalance = 0.0
                    val balancesMap = mutableMapOf<String, Double>()

                    val exactDateFormatter =
                        SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply {
                            this.timeZone = TimeZone.getTimeZone("Europe/Kyiv")
                        }

                    for (t in sortedTrans) {
                        if (t.type == "income") totalBalance += t.amount else totalBalance -= t.amount

                        val dateStr = exactDateFormatter.format(Date(t.date))
                        balancesMap[dateStr] = totalBalance
                    }

                    currentTotalBalance = totalBalance
                    dailyBalancesMap = balancesMap

                    if (isBalanceVisible) {
                        tvTotalBalance.text =
                            String.format(Locale.US, "%.2f ₴", currentTotalBalance)
                    }

                    if (::transactionAdapter.isInitialized) {
                        transactionAdapter.updateDailyBalances(dailyBalancesMap)
                    }
                }
    }

    private fun loadTransactions() {
        val currentUser = auth.currentUser ?: return

        snapshotListener?.remove()

        var query: Query =
            db.collection("users").document(currentUser.uid).collection("transactions")

        if (currentSpecificDateFilter != null) {
            val startOfDay = currentSpecificDateFilter!!
            val endOfDay = startOfDay + (24L * 60L * 60L * 1000L) - 1L
            query = query.whereGreaterThanOrEqualTo("date", startOfDay)
                .whereLessThanOrEqualTo("date", endOfDay)
        }

        val orderedQuery = query.orderBy("date", Query.Direction.DESCENDING)
        val limitedQuery = if (isAllLoaded) orderedQuery else orderedQuery.limit(currentLimit)

        snapshotListener = limitedQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT)
                        .show()
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
                    applyFiltersAndSort()

                    if (isScrollDownRequested) {
                        isScrollDownRequested = false
                        val lastIndex = transactionAdapter.itemCount - 1
                        if (lastIndex >= 0) {
                            rvTransactions.post {
                                rvTransactions.smoothScrollToPosition(lastIndex)
                            }
                        }
                    }

                    val validSelectedIds =
                        transactionAdapter.selectedIds.filter { id -> transactionsList.any { it.id == id } }
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

        val fullDateFormatter = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("uk", "UA"))
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

        AlertDialog.Builder(this).setTitle("Підтвердження видалення").setMessage(message)
            .setPositiveButton("Видалити") { _, _ ->
                deleteTransactions(transactionIds)
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun deleteTransactions(transactionIds: List<String>) {
        val currentUser = auth.currentUser ?: return
        val batch = db.batch()

        transactionIds.forEach { id ->
            val docRef = db.collection("users").document(currentUser.uid).collection("transactions")
                .document(id)
            batch.delete(docRef)
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Успішно видалено", Toast.LENGTH_SHORT).show()
            clearSelection()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Помилка видалення: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}