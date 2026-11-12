package com.travelia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemJadwalTerdekatBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class JadwalTerdekatAdapter(
    private val listTiket: List<TiketModel>,
    private val onItemClick: (TiketModel) -> Unit
) : RecyclerView.Adapter<JadwalTerdekatAdapter.JadwalViewHolder>() {

    inner class JadwalViewHolder(private val binding: ItemJadwalTerdekatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tiket: TiketModel) {
            // Rute
            binding.tvRute.text = "${tiket.asal} - ${tiket.tujuan}"

            // Jam (Teks warna biru di kanan atas)
            binding.tvJam.text = tiket.waktu

            // Tanggal
            binding.tvDate.text = formatTanggalIndo(tiket.tanggal)

            // Sisa Kursi (Teks hijau di kanan)
            binding.tvSisaKursi.text = "Sisa Kursi: ${tiket.sisaKursi}"

            // Harga (Teks oranye tebal)
            val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
            binding.tvHarga.text = "Rp ${formatter.format(tiket.harga.toLong())}"

            // Tombol
            binding.btnPesanSekarang.setOnClickListener {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JadwalViewHolder {
        val binding = ItemJadwalTerdekatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return JadwalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JadwalViewHolder, position: Int) {
        holder.bind(listTiket[position])
    }

    override fun getItemCount(): Int = listTiket.size
}