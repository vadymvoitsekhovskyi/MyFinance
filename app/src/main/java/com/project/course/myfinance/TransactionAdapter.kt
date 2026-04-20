package com.project.course.myfinance

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    val selectedIds = mutableSetOf<String>()

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvYearHeader: TextView = itemView.findViewById(R.id.tvYearHeader)
        val layoutDateHeader: LinearLayout = itemView.findViewById(R.id.layoutDateHeader)
        val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        val tvDateSum: TextView = itemView.findViewById(R.id.tvDateSum)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val cardView: CardView = itemView.findViewById(R.id.cardTransaction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        val timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        val yearFormatter =
            SimpleDateFormat("yyyy", Locale("uk", "UA")).apply { this.timeZone = timeZone }
        val dayMonthFormatter =
            SimpleDateFormat("dd MMMM", Locale("uk", "UA")).apply { this.timeZone = timeZone }
        val exactDateFormatter =
            SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply { this.timeZone = timeZone }
        val timeFormatter =
            SimpleDateFormat("HH:mm", Locale.getDefault()).apply { this.timeZone = timeZone }

        val currentDateObj = Date(transaction.date)
        val currentYearStr = yearFormatter.format(currentDateObj)
        val currentDayMonthStr = dayMonthFormatter.format(currentDateObj)
        val currentExactDateStr = exactDateFormatter.format(currentDateObj)

        holder.tvYearHeader.visibility = View.GONE
        holder.layoutDateHeader.visibility = View.GONE

        var showYear = false
        var showDate = false

        if (position == 0) {
            showYear = true
            showDate = true
        } else {
            val prevDateObj = Date(transactions[position - 1].date)
            val prevYearStr = yearFormatter.format(prevDateObj)
            val prevExactDateStr = exactDateFormatter.format(prevDateObj)

            if (currentYearStr != prevYearStr) showYear = true
            if (currentExactDateStr != prevExactDateStr) showDate = true
        }

        if (showYear) {
            holder.tvYearHeader.visibility = View.VISIBLE
            holder.tvYearHeader.text = currentYearStr
        }

        if (showDate) {
            holder.layoutDateHeader.visibility = View.VISIBLE
            holder.tvDateHeader.text = currentDayMonthStr

            var daySum = 0.0

            transactions.forEach { t ->
                if (exactDateFormatter.format(Date(t.date)) == currentExactDateStr) {
                    if (t.type == "income") daySum += t.amount else daySum -= t.amount
                }
            }

            if (daySum > 0) {
                holder.tvDateSum.text = String.format(Locale.US, "+ %.2f ₴", daySum)
                holder.tvDateSum.setTextColor(Color.parseColor("#4CAF50"))
            } else if (daySum < 0) {
                holder.tvDateSum.text = String.format(Locale.US, "- %.2f ₴", abs(daySum))
                holder.tvDateSum.setTextColor(Color.parseColor("#F44336"))
            } else {
                holder.tvDateSum.text = "0.00 ₴"
                holder.tvDateSum.setTextColor(Color.parseColor("#757575"))
            }
        }

        holder.tvCategory.text = transaction.category
        holder.tvTime.text = timeFormatter.format(currentDateObj)

        if (transaction.comment.isNotBlank()) {
            holder.tvComment.text = transaction.comment
            holder.tvComment.visibility = View.VISIBLE
        } else {
            holder.tvComment.visibility = View.GONE
        }

        if (transaction.type == "income") {
            holder.tvAmount.text = String.format(Locale.US, "+ %.2f ₴", transaction.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            holder.tvAmount.text = String.format(Locale.US, "- %.2f ₴", transaction.amount)
            holder.tvAmount.setTextColor(Color.parseColor("#F44336"))
        }

        if (selectedIds.contains(transaction.id)) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            onItemClick(transaction)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(transaction)
            true
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(transactions.map { it.id })
        notifyDataSetChanged()
    }
}