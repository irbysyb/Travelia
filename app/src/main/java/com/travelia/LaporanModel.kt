package com.travelia

data class LaporanModel(
    val id: String = "",
    val userId: String = "",
    val scheduleId: String = "",
    val ticketId: String = "",
    val rute: String = "",
    val tanggalKeberangkatan: String = "",
    val jamKeberangkatan: String = "",
    val kategori: String = "",
    val deskripsi: String = "",
    val timestamp: Long = 0L
)