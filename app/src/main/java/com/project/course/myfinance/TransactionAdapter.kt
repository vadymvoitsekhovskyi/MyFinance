package com.project.course.myfinance

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Додаємо onClickListener у конструктор адаптера
class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit // Лямбда-функція для обробки кліку
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("uk", "UA"))
        dateFormatter.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeFormatter.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

        val dateObj = Date(transaction.date)
        val currentDateStr = dateFormatter.format(dateObj)

        holder.tvCategory.text = transaction.category
        holder.tvTime.text = timeFormatter.format(dateObj)

        if (transaction.comment.isNotBlank()) {
            holder.tvComment.text = transaction.comment
            holder.tvComment.visibility = View.VISIBLE
        } else {
            holder.tvComment.visibility = View.GONE
        }

        if (transaction.type == "income") {
            holder.tvAmount.text = "+ ${transaction.amount} ₴"
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            holder.tvAmount.text = "- ${transaction.amount} ₴"
            holder.tvAmount.setTextColor(Color.parseColor("#F44336"))
        }

        if (position == 0) {
            holder.tvDateHeader.visibility = View.VISIBLE
            holder.tvDateHeader.text = currentDateStr
        } else {
            val previousTransaction = transactions[position - 1]
            val previousDateStr = dateFormatter.format(Date(previousTransaction.date))

            if (currentDateStr != previousDateStr) {
                holder.tvDateHeader.visibility = View.VISIBLE
                holder.tvDateHeader.text = currentDateStr
            } else {
                holder.tvDateHeader.visibility = View.GONE
            }
        }

        // --- ДОДАНО ---
        // Вішаємо слухач кліку на весь елемент itemView
        holder.itemView.setOnClickListener {
            onItemClick(transaction) // Викликаємо функцію, передану з MainActivity
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}