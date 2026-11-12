
package com.travelia

data class TiketModel(
    var id: String = "", // Ubah dari 'val' menjadi 'var'
    val asal: String = "",
    val tujuan: String = "",
    val tanggal: String = "",
    val waktu: String = "",
    val harga: Double = 0.0,
    val sisaKursi: Int = 0,
    val jumlahKursi: Int = 0,
    val namaArmada: String = "",
    val tipeArmada: String = "",
    val waktuMenit: Int = 0
)