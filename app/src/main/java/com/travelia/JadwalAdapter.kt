package com.travelia

import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.travelia.databinding.ItemJadwalBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class JadwalAdapter(
    private var listJadwal: MutableList<JadwalModel>,
    private val onItemClick: (JadwalModel, ActionType) -> Unit
) : RecyclerView.Adapter<JadwalAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemJadwalBinding) : RecyclerView.ViewHolder(binding.root) {
        var countDownTimer: CountDownTimer? = null

        fun bind(item: JadwalModel) {
            binding.tvRute.text = "${item.origin} ➔ ${item.destination}"
            binding.tvBusType.text = item.busType
            binding.tvDepartureDate.text = item.departureDate // Tanggal Indonesia
            binding.tvDepartureTime.text = item.departureTime // Jam Keberangkatan

            // Hentikan timer lama jika ViewHolder didaur ulang
            countDownTimer?.cancel()

            // Cek status WorkManager secara real-time
            val workName = "reminder_schedule_${item.id}"
            WorkManager.getInstance(binding.root.context)
                .getWorkInfosForUniqueWorkLiveData(workName)
                .observeForever { workInfos ->
                    val isEnqueued = workInfos?.any { it.state == WorkInfo.State.ENQUEUED } == true
                    if (isEnqueued) {
                        binding.btnNotification.setColorFilter(Color.parseColor("#1E88E5"))
                    } else {
                        binding.btnNotification.setColorFilter(Color.parseColor("#9E9E9E"))
                    }
                }

            // Hitung selisih waktu keberangkatan
            val targetMillis = parseDateTimeToMillis(item.departureDate, item.departureTime)
            val currentMillis = System.currentTimeMillis()
            val diffMillis = targetMillis - currentMillis

            // Toleransi waktu 30 menit setelah keberangkatan untuk tombol Lapor
            val thirtyMinutesMillis = 30 * 60 * 1000L

            if (diffMillis > 0) {
                // STATUS 1: AKTIF (Belum Berangkat - Countdown Berjalan)
                binding.tvStatusAktif.text = "Aktif"
                binding.tvStatusAktif.setTextColor(Color.parseColor("#4CAF50"))
                binding.btnDelete.visibility = View.GONE
                binding.btnLaporPerjalanan.visibility = View.GONE
                binding.btnNotification.visibility = View.VISIBLE

                countDownTimer = object : CountDownTimer(diffMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
                        val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished) % 24
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                        binding.tvCountdown.text = String.format(
                            Locale.getDefault(),
                            "%d Hari %02d:%02d:%02d",
                            days, hours, minutes, seconds
                        )
                    }

                    override fun onFinish() {
                        updateTripStatePostDeparture(targetMillis, binding)
                    }
                }.start()

            } else if (currentMillis <= targetMillis + thirtyMinutesMillis) {
                // STATUS 2: DALAM PERJALANAN (0 hingga 30 menit setelah jam keberangkatan)
                setOngoingTripState(binding)
            } else {
                // STATUS 3: SELESAI (> 30 menit setelah keberangkatan, tombol hapus muncul kembali)
                setFinishedTripState(binding)
            }

            // Listener Klik Seluruh Kartu
            binding.root.setOnClickListener {
                onItemClick(item, ActionType.CLICK_CARD)
            }

            // Listener Tombol-tombol Spesifik
            binding.btnNotification.setOnClickListener {
                onItemClick(item, ActionType.CLICK_NOTIFICATION)
            }

            binding.btnDelete.setOnClickListener {
                onItemClick(item, ActionType.CLICK_DELETE)
            }

            binding.btnLaporPerjalanan.setOnClickListener {
                onItemClick(item, ActionType.CLICK_LAPOR)
            }
        }

        private fun setOngoingTripState(binding: ItemJadwalBinding) {
            binding.tvCountdown.text = "00:00:00"
            binding.tvStatusAktif.text = "Dalam Perjalanan"
            binding.tvStatusAktif.setTextColor(Color.parseColor("#D32F2F"))

            // Sembunyikan Notifikasi & Delete, Munculkan Tombol Lapor
            binding.btnNotification.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
            binding.btnLaporPerjalanan.visibility = View.VISIBLE
        }

        private fun setFinishedTripState(binding: ItemJadwalBinding) {
            binding.tvCountdown.text = "Perjalanan Selesai"
            binding.tvStatusAktif.text = "Selesai"
            binding.tvStatusAktif.setTextColor(Color.parseColor("#757575"))

            // Sembunyikan Notifikasi & Lapor, Munculkan Kembali Tombol Hapus
            binding.btnNotification.visibility = View.GONE
            binding.btnLaporPerjalanan.visibility = View.GONE
            binding.btnDelete.visibility = View.VISIBLE
        }

        private fun updateTripStatePostDeparture(targetMillis: Long, binding: ItemJadwalBinding) {
            val currentMillis = System.currentTimeMillis()
            val thirtyMinutesMillis = 30 * 60 * 1000L
            if (currentMillis <= targetMillis + thirtyMinutesMillis) {
                setOngoingTripState(binding)
            } else {
                setFinishedTripState(binding)
            }
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
        val binding = ItemJadwalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listJadwal[position])
    }

    override fun getItemCount(): Int = listJadwal.size

    fun filterList(newList: List<JadwalModel>) {
        listJadwal = newList.toMutableList()
        notifyDataSetChanged()
    }
}