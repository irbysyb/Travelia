package com.travelia

data class JadwalModel(
    val id: String = "",
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "", // Format: "dd/MM/yyyy"
    val departureTime: String = "", // Format: "HH:mm"
    val busType: String = "Toyota Hiace Premio",
    val price: Long = 0,
    val availableSeats: Int = 0,
    var isActive: Boolean = true
)