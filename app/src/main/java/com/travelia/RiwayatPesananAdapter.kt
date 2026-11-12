package com.travelia

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemRiwayatPesananBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class RiwayatPesananAdapter(
    private val listTiket: MutableList<RiwayatPesanan>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RiwayatPesananAdapter.ViewHolder>() {

    var isDeleteMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                selectedItems.clear()
            }
            notifyDataSetChanged()
        }

    val selectedItems = mutableSetOf<RiwayatPesanan>()

    inner class ViewHolder(val binding: ItemRiwayatPesananBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RiwayatPesanan) {
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

            // 1. Header Information
            binding.tvBusType.text = item.tipeArmada.ifEmpty { "Toyota Hiace Premio" }

            val formattedDate = formatDateToIndonesian(item.tanggal)
            binding.tvDepartureDate.text = formattedDate

            val displayId = if (item.ticketId.isNotEmpty()) item.ticketId else item.id
            binding.tvIdPesanan.text = "ID: $displayId"

            binding.tvNamaPenumpang.text = item.namaPenumpang.ifEmpty { "Penumpang" }

            // 2. Logika Jam Keberangkatan & Status Sinkron
            setupTimeAndStatus(formattedDate, item.waktu)

            binding.tvAsal.text = item.asal
            binding.tvTujuan.text = item.tujuan

            // 3. Total Harga & Jumlah Kursi
            binding.tvTotalBayar.text = "${formatRupiah.format(item.harga)} (${item.jumlahKursi} Kursi)"

            // 4. Penanganan Mode Seleksi Hapus (CheckBox) - Cegah bug recycling view
            binding.cbSelect.setOnCheckedChangeListener(null)

            if (isDeleteMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = selectedItems.contains(item)
            } else {
                binding.cbSelect.visibility = View.GONE
                binding.cbSelect.isChecked = false
            }

            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(item)
                } else {
                    selectedItems.remove(item)
                }
                onSelectionChanged()
            }

            itemView.setOnClickListener {
                if (isDeleteMode) {
                    binding.cbSelect.isChecked = !binding.cbSelect.isChecked
                }
            }
        }

        private fun formatDateToIndonesian(dateStr: String): String {
            if (dateStr.isEmpty()) return "-"

            val possibleFormats = listOf(
                SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")),
                SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")),
                SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
            )

            for (sdf in possibleFormats) {
                try {
                    sdf.isLenient = false
                    val parsedDate = sdf.parse(dateStr)
                    if (parsedDate != null) {
                        val outputSdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                        return outputSdf.format(parsedDate)
                    }
                } catch (e: Exception) {}
            }
            return dateStr
        }

        private fun setupTimeAndStatus(tanggalStr: String, waktuStr: String) {
            try {
                val targetMillis = parseDateTimeToMillis(tanggalStr, waktuStr)
                val currentMillis = System.currentTimeMillis()
                val thirtyMinutesMillis = 30 * 60 * 1000L

                if (targetMillis == 0L) {
                    setInactiveState(waktuStr)
                    return
                }

                if (currentMillis < targetMillis) {
                    binding.tvDepartureTime.text = waktuStr
                    setActiveState()
                } else if (currentMillis <= targetMillis + thirtyMinutesMillis) {
                    binding.tvDepartureTime.text = "00:00"
                    setActiveState()
                } else {
                    binding.tvDepartureTime.text = waktuStr
                    setInactiveState(waktuStr)
                }
            } catch (e: Exception) {
                binding.tvDepartureTime.text = waktuStr
                setActiveState()
            }
        }

        private fun setActiveState() {
            binding.tvStatusTiket.text = "Aktif"
            binding.tvStatusTiket.setTextColor(Color.parseColor("#2E7D32")) // Hijau
            binding.tvStatusTiket.setBackgroundResource(R.drawable.bg_badge_active)

            binding.root.alpha = 1.0f
            binding.root.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        private fun setInactiveState(waktuStr: String) {
            binding.tvDepartureTime.text = waktuStr
            binding.tvStatusTiket.text = "Tidak Aktif"
            binding.tvStatusTiket.setTextColor(Color.parseColor("#757575")) // Abu-abu
            binding.tvStatusTiket.setBackgroundResource(R.drawable.bg_badge_inactive)

            binding.root.alpha = 0.75f
            binding.root.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        private fun parseDateTimeToMillis(dateStr: String, timeStr: String): Long {
            val fullStr = "$dateStr $timeStr"
            val formats = listOf(
                SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID")),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
            )

            for (sdf in formats) {
                try {
                    sdf.isLenient = false
                    val parsedDate = sdf.parse(fullStr)
                    if (parsedDate != null) {
                        return parsedDate.time
                    }
                } catch (_: Exception) {}
            }
            return 0L
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRiwayatPesananBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listTiket[position])
    }

    override fun getItemCount(): Int = listTiket.size

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(listTiket)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }
}