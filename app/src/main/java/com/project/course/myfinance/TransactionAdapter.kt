package com.project.course.myfinance

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.course.myfinance.models.Transaction

// Адаптер керує списком на головному екрані (RecyclerView)
class TransactionAdapter(private var transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    // Цей клас знаходить елементи дизайну всередині item_transaction.xml
    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    // Тут ми беремо дані з об'єкта і вставляємо в текст на екрані
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        holder.tvCategory.text = transaction.category

        // Якщо коментар є - показуємо, якщо ні - ховаємо це поле
        if (transaction.comment.isNotBlank()) {
            holder.tvComment.text = transaction.comment
            holder.tvComment.visibility = View.VISIBLE
        } else {
            holder.tvComment.visibility = View.GONE
        }

        // Фарбуємо суму в залежності від типу (дохід/витрата)
        if (transaction.type == "income") {
            holder.tvAmount.text = "+ ${transaction.amount} ₴"
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50")) // Зелений
        } else {
            holder.tvAmount.text = "- ${transaction.amount} ₴"
            holder.tvAmount.setTextColor(Color.parseColor("#F44336")) // Червоний
        }
    }

    override fun getItemCount(): Int = transactions.size

    // Функція для оновлення списку, коли приходять нові дані з Firebase
    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged() // Кажемо списку "Оновись!"
    }
}