package com.travelia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemTiketBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TiketAdapter(
    private val listTiket: List<TiketModel>,
    private val onItemClick: (TiketModel) -> Unit
) : RecyclerView.Adapter<TiketAdapter.TiketViewHolder>() {

    inner class TiketViewHolder(private val binding: ItemTiketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tiket: TiketModel) {
            // Rute & Waktu (Diperbaiki format tanda panahnya agar tidak multi-line error)
            binding.tvRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"
            binding.tvWaktu.text = tiket.waktu

            // Format Tanggal Keberangkatan (Contoh: 24 Januari 2026)
            binding.tvTanggal.text = formatTanggalIndo(tiket.tanggal)
            binding.tvSisaKursi.text = "Sisa Kursi: ${tiket.sisaKursi}"

            // Format Informasi Armada (Dipisah ke tvNamaArmada dan tvTipeArmada)
            binding.tvNamaArmada.text = if (tiket.namaArmada.isNotEmpty()) tiket.namaArmada else "Travelia Express"
            binding.tvTipeArmada.text = if (tiket.tipeArmada.isNotEmpty()) tiket.tipeArmada else "Toyota Hiace Premio"

            // Format Harga Rupiah (Contoh: Rp 180.000)
            val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
            val hargaFormatted = "Rp ${formatter.format(tiket.harga.toLong())}"
            binding.tvHarga.text = hargaFormatted

            // Action Klik Tombol Pesan
            binding.btnPesan.setOnClickListener {
                onItemClick(tiket)
            }
        }
    }

    private fun formatTanggalIndo(tanggalStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
            val date = inputFormat.parse(tanggalStr)
            val outputFormat = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
            if (date != null) outputFormat.format(date) else tanggalStr
        } catch (e: Exception) {
            tanggalStr
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TiketViewHolder {
        val binding = ItemTiketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TiketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TiketViewHolder, position: Int) {
        holder.bind(listTiket[position])
    }

    override fun getItemCount(): Int = listTiket.size
}