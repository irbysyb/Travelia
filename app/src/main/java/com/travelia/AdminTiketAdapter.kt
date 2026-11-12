package com.travelia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemAdminTiketBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class AdminTiketAdapter(
    private val listTiket: List<TiketModel>,
    private val onEditClick: (TiketModel) -> Unit,
    private val onDeleteClick: (TiketModel) -> Unit
) : RecyclerView.Adapter<AdminTiketAdapter.AdminViewHolder>() {

    inner class AdminViewHolder(private val binding: ItemAdminTiketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tiket: TiketModel) {
            val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
            val hargaFormatted = "Rp ${formatter.format(tiket.harga.toLong())}"

            binding.tvAdminItemRute.text = "${tiket.asal} - ${tiket.tujuan} (${tiket.id})"

            // Format Tanggal Indonesia (Contoh: 24 Januari 2026)
            binding.tvAdminItemInfo.text = "${formatTanggalIndo(tiket.tanggal)} • Jam: ${tiket.waktu}"
            binding.tvAdminItemHargaKursi.text = "$hargaFormatted | Sisa Kursi: ${tiket.sisaKursi}"

            binding.btnEditTiket.setOnClickListener {
                onEditClick(tiket)
            }

            binding.btnHapusTiket.setOnClickListener {
                onDeleteClick(tiket)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val binding = ItemAdminTiketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdminViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        holder.bind(listTiket[position])
    }

    override fun getItemCount(): Int = listTiket.size
}