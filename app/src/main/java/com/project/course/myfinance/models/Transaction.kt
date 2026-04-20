package com.project.course.myfinance.models

data class Transaction(
    val id: String = "", // Унікальний ідентифікатор транзакції
    val type: String = "", // Тип: "income" (дохід) або "expense" (витрата)
    val category: String = "", // Категорія: "Їжа", "Зарплата", "Транспорт"
    val amount: Double = 0.0, // Сума
    val date: Long = System.currentTimeMillis(), // Дата у мілісекундах (зручно для сортування)
    val comment: String = "" // Коментар користувача
)