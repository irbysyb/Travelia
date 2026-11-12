package com.travelia

data class RiwayatPesanan(
    var id: String = "",
    var userId: String = "",
    var ticketId: String = "",
    var asal: String = "",
    var tujuan: String = "",
    var tanggal: String = "",
    var waktu: String = "",
    var harga: Long = 0,
    var jumlahKursi: Int = 1,
    var tipeArmada: String = "Toyota Hiace Premio",
    var namaPenumpang: String = ""
)