package com.travelia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemLaporanAdminBinding

class AdminLaporanAdapter(
    private val listLaporan: List<LaporanModel>,
    private val onDeleteClick: (LaporanModel) -> Unit
) : RecyclerView.Adapter<AdminLaporanAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLaporanAdminBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LaporanModel) {
            // Menampilkan ticketId asli (cth: TK09-3368), fallback ke scheduleId jika kosong
            val displayTicketId = if (item.ticketId.isNotEmpty()) item.ticketId else (if (item.scheduleId.isNotEmpty()) item.scheduleId else "-")
            binding.tvIdTiketLaporan.text = "ID: $displayTicketId"

            binding.tvKategoriLaporan.text = item.kategori
            binding.tvRuteLaporan.text = item.rute
            binding.tvJadwalLaporan.text = "Jadwal: ${item.tanggalKeberangkatan} (${item.jamKeberangkatan})"
            binding.tvDeskripsiLaporan.text = item.deskripsi

            binding.btnHapusLaporan.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLaporanAdminBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listLaporan[position])
    }

    override fun getItemCount(): Int = listLaporan.size
}