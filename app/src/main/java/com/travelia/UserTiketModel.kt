package com.travelia

data class UserTiketModel(
    val documentId: String? = null,
    val ticketId: String? = null, // ID asli dari koleksi tickets (contoh: TK03-6781)
    val asal: String = "",
    val tujuan: String = "",
    val tanggal: String = "",
    val waktu: String = "",
    val harga: Double = 0.0,
    val jumlahKursi: Int = 1,
    val status: String = "Belum Bayar",
    val namaArmada: String = "",
    val tipeArmada: String = ""
)