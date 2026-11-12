package com.travelia

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.travelia.databinding.ItemMyTiketBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TiketSayaAdapter(
    private val listTiket: List<UserTiketModel>,
    private val onStatusClick: (UserTiketModel, Int) -> Unit,
    private val onHapusClick: (UserTiketModel, Int) -> Unit
) : RecyclerView.Adapter<TiketSayaAdapter.TiketViewHolder>() {

    inner class TiketViewHolder(val binding: ItemMyTiketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tiket: UserTiketModel, position: Int) {
            // Menggunakan field dari UserTiketModel
            binding.tvRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"

            // Menggunakan ticketId asli dari koleksi admin tickets (contoh: TK03-6781)
            binding.tvIdTiket.text = "ID: ${tiket.ticketId ?: tiket.documentId?.takeLast(8)?.uppercase() ?: "-"}"

            binding.tvTanggal.text = tiket.tanggal
            binding.tvJam.text = "Jam ${tiket.waktu}"
            binding.tvJumlahKursi.text = "Jumlah Kursi: ${tiket.jumlahKursi}"

            // Format Harga ke Rupiah
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            binding.tvHarga.text = formatRupiah.format(tiket.harga)

            // Status Button
            binding.btnStatus.text = tiket.status
            val colorHex = if (tiket.status.equals("Belum Bayar", ignoreCase = true)) {
                "#FF7A22" // Oranye
            } else {
                "#008855" // Hijau
            }
            binding.btnStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))

            // Logika Visibilitas Tombol Hapus (Berdasarkan Waktu/Expired)
            val currentTime = System.currentTimeMillis()
            val formats = listOf(
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")),
                SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID"))
            )

            val fullDateStr = "${tiket.tanggal.trim()} ${tiket.waktu.trim()}"
            var ticketTime = 0L

            for (sdf in formats) {
                try {
                    sdf.isLenient = false
                    val parsedDate = sdf.parse(fullDateStr)
                    if (parsedDate != null) {
                        ticketTime = parsedDate.time
                        break
                    }
                } catch (_: Exception) {}
            }

            val isExpired = ticketTime in 1..<currentTime

            // Tampilkan tombol hapus jika Belum Bayar ATAU sudah Expired
            if (tiket.status.equals("Belum Bayar", ignoreCase = true) || isExpired) {
                binding.btnHapus.visibility = View.VISIBLE
            } else {
                binding.btnHapus.visibility = View.GONE
            }

            // Listeners
            binding.btnStatus.setOnClickListener {
                if (tiket.status.equals("Belum Bayar", ignoreCase = true)) {
                    onStatusClick(tiket, position)
                }
            }

            binding.btnHapus.setOnClickListener {
                onHapusClick(tiket, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TiketViewHolder {
        val binding = ItemMyTiketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TiketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TiketViewHolder, position: Int) {
        holder.bind(listTiket[position], position)
    }

    override fun getItemCount(): Int = listTiket.size
}