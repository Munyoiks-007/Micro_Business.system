package com.example.microbusinesssystem.data.local

enum class SaleType {
    MANUAL,
    MPESA
}

enum class PaymentStatus {
    INITIATED,
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNCERTAIN
}
