package com.project.course.myfinance

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class StatisticsActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var progressBar: ProgressBar
    private lateinit var cardTrend: CardView
    private lateinit var ivTrendArrow: ImageView
    private lateinit var tvTrendText: TextView
    private lateinit var yearlyBarChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)
        progressBar = findViewById(R.id.progressBar)
        cardTrend = findViewById(R.id.cardTrend)
        ivTrendArrow = findViewById(R.id.ivTrendArrow)
        tvTrendText = findViewById(R.id.tvTrendText)
        yearlyBarChart = findViewById(R.id.yearlyBarChart)

        setupCharts()
        loadData()
    }

    private fun setupCharts() {
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.legend.isWordWrapEnabled = true

        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.axisRight.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.granularity = 1f
        barChart.animateY(1000)
    }

    private fun loadData() {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).collection("transactions")
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                val transactions = snapshot.toObjects(Transaction::class.java)

                if (transactions.isEmpty()) {
                    Toast.makeText(this, "Немає даних для статистики", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                buildTrendIndicator(transactions)
                buildPieChart(transactions)
                buildBarChart(transactions)
                buildYearlyBarChart(transactions)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildYearlyBarChart(transactions: List<Transaction>) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val cal = java.util.Calendar.getInstance()

        val yearlyTransactions = transactions.filter {
            cal.timeInMillis = it.date
            cal.get(java.util.Calendar.YEAR) == currentYear
        }

        val monthNames = arrayOf("Січ", "Лют", "Бер", "Кві", "Тра", "Чер", "Лип", "Сер", "Вер", "Жов", "Лис", "Гру")
        val incomeEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()

        for (month in 0..11) {
            val monthTransactions = yearlyTransactions.filter {
                cal.timeInMillis = it.date
                cal.get(java.util.Calendar.MONTH) == month
            }
            val mIncome = monthTransactions.filter { it.type == "income" }.sumOf { it.amount }.toFloat()
            val mExpense = monthTransactions.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()

            incomeEntries.add(BarEntry(month.toFloat(), mIncome))
            expenseEntries.add(BarEntry(month.toFloat(), mExpense))
        }

        val incomeDataSet = BarDataSet(incomeEntries, "Дохід")
        incomeDataSet.color = Color.parseColor("#4CAF50")

        val expenseDataSet = BarDataSet(expenseEntries, "Витрата")
        expenseDataSet.color = Color.parseColor("#F44336")

        val data = BarData(incomeDataSet, expenseDataSet)
        val barSpace = 0.05f
        val groupSpace = 0.30f
        data.barWidth = 0.30f

        yearlyBarChart.data = data
        yearlyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthNames)
        yearlyBarChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        yearlyBarChart.xAxis.setDrawGridLines(false)
        yearlyBarChart.xAxis.axisMinimum = -0.5f
        yearlyBarChart.xAxis.axisMaximum = 12f
        yearlyBarChart.axisRight.isEnabled = false
        yearlyBarChart.description.isEnabled = false
        yearlyBarChart.setDrawGridBackground(false)

        yearlyBarChart.groupBars(-0.5f, groupSpace, barSpace)
        yearlyBarChart.invalidate()
        yearlyBarChart.animateY(1000)
    }

    private fun buildTrendIndicator(transactions: List<Transaction>) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val recentTransactions = transactions.filter { it.date >= thirtyDaysAgo }

        if (recentTransactions.isEmpty()) {
            cardTrend.visibility = View.GONE
            return
        }

        cardTrend.visibility = View.VISIBLE

        val recentIncome = recentTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val recentExpense = recentTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        val difference = recentIncome - recentExpense

        if (difference >= 0) {
            ivTrendArrow.setImageResource(android.R.drawable.arrow_up_float)
            ivTrendArrow.setColorFilter(Color.parseColor("#4CAF50"))
            tvTrendText.text = String.format(Locale.US, "Чудово! Ваші доходи перевищують витрати на %.2f ₴.", difference)
        } else {
            ivTrendArrow.setImageResource(android.R.drawable.arrow_down_float)
            ivTrendArrow.setColorFilter(Color.parseColor("#F44336"))
            tvTrendText.text = String.format(Locale.US, "Увага! Ваші витрати перевищують доходи на %.2f ₴.", abs(difference))
        }
    }

    private fun buildPieChart(transactions: List<Transaction>) {
        val expenses = transactions.filter { it.type == "expense" }

        val groupedByCategory = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .filter { it.value > 0 }

        val entries = ArrayList<PieEntry>()
        for ((category, amount) in groupedByCategory) {
            entries.add(PieEntry(amount.toFloat(), category))
        }

        val dataSet = PieDataSet(entries, "")
        val colors = java.util.ArrayList<Int>()
        colors.addAll(ColorTemplate.MATERIAL_COLORS.toList())
        colors.addAll(ColorTemplate.VORDIPLOM_COLORS.toList())
        colors.addAll(ColorTemplate.JOYFUL_COLORS.toList())
        colors.addAll(ColorTemplate.COLORFUL_COLORS.toList())
        colors.addAll(ColorTemplate.LIBERTY_COLORS.toList())
        colors.addAll(ColorTemplate.PASTEL_COLORS.toList())
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f

        val data = PieData(dataSet)
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.WHITE)

        pieChart.data = data
        pieChart.invalidate()
        pieChart.animateY(1000)
    }

    private fun buildBarChart(transactions: List<Transaction>) {
        val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
        val groupedByDate = transactions.groupBy { sdf.format(Date(it.date)) }
        val incomeEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()
        val xAxisLabels = ArrayList<String>()
        var index = 0f

        for ((dateLabel, dayTransactions) in groupedByDate) {
            xAxisLabels.add(dateLabel)

            val dailyIncome = dayTransactions.filter { it.type == "income" }.sumOf { it.amount }.toFloat()
            val dailyExpense = dayTransactions.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()

            incomeEntries.add(BarEntry(index, dailyIncome))
            expenseEntries.add(BarEntry(index, dailyExpense))
            index++
        }

        val incomeDataSet = BarDataSet(incomeEntries, "Дохід")
        incomeDataSet.color = Color.parseColor("#4CAF50")

        val expenseDataSet = BarDataSet(expenseEntries, "Витрата")
        expenseDataSet.color = Color.parseColor("#F44336")

        val data = BarData(incomeDataSet, expenseDataSet)
        val barSpace = 0.05f
        val groupSpace = 0.30f
        val barWidth = 0.30f
        data.barWidth = barWidth

        barChart.data = data
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels)

        if (xAxisLabels.size > 0) {
            barChart.groupBars(0f, groupSpace, barSpace)
            barChart.xAxis.axisMinimum = 0f
            barChart.xAxis.axisMaximum = index
        }

        barChart.invalidate()
    }
}