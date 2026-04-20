package com.project.course.myfinance.models

data class Transaction(
    val id: String = "",
    val type: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val comment: String = ""
)