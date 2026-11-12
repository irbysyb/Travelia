package com.travelia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityJadwalBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class JadwalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJadwalBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val fullJadwalList = mutableListOf<JadwalModel>()
    private lateinit var adapter: JadwalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityJadwalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupRecyclerView()
        setupListeners()
        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, null)

        loadJadwalFromSchedules()
    }

    private fun setupRecyclerView() {
        adapter = JadwalAdapter(fullJadwalList) { selectedJadwal, actionType ->
            when (actionType) {
                ActionType.CLICK_CARD -> {
                }

                ActionType.CLICK_NOTIFICATION -> {
                    val rute = "${selectedJadwal.origin} ke ${selectedJadwal.destination}"
                    toggleNotificationReminder(selectedJadwal, rute, selectedJadwal.departureDate, selectedJadwal.departureTime)
                }

                ActionType.CLICK_DELETE -> {
                    konfirmasiHapusJadwal(selectedJadwal)
                }

                ActionType.CLICK_LAPOR -> {
                    showBottomSheetLaporan(selectedJadwal)
                }
            }
        }

        binding.rvJadwal.layoutManager = LinearLayoutManager(this)
        binding.rvJadwal.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        binding.etSearchRute.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterData(query: String) {
        val filtered = fullJadwalList.filter {
            it.origin.contains(query, ignoreCase = true) ||
                    it.destination.contains(query, ignoreCase = true)
        }
        adapter.filterList(filtered)
    }

    private fun loadJadwalFromSchedules() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("schedules")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                fullJadwalList.clear()
                val mapJadwalUnik = mutableMapOf<String, JadwalModel>()

                for (doc in querySnapshot.documents) {
                    val docId = doc.id
                    val ticketId = doc.getString("ticketId") ?: "" // Mengambil ticketId asli (seperti TK64-8483)
                    val origin = doc.getString("asal") ?: doc.getString("origin").orEmpty()
                    val destination = doc.getString("tujuan") ?: doc.getString("destination").orEmpty()
                    val rawTanggal = doc.getString("tanggal") ?: "30/10/2026"
                    val tanggalFormatted = formatTanggalIndo(rawTanggal)
                    val time = doc.getString("jam") ?: doc.getString("waktu") ?: "08:00"
                    val jumlahKursi = doc.getLong("jumlahKursi")?.toInt() ?: 1
                    val price = doc.getDouble("harga")?.toLong() ?: doc.getLong("price") ?: 0L

                    val uniqueKey = "${origin}_${destination}_${tanggalFormatted}_${time}"

                    if (mapJadwalUnik.containsKey(uniqueKey)) {
                        val existingItem = mapJadwalUnik[uniqueKey]!!
                        mapJadwalUnik[uniqueKey] = existingItem.copy(
                            availableSeats = existingItem.availableSeats + jumlahKursi,
                            price = existingItem.price + price
                        )
                    } else {
                        // Jika ticketId dari document kosong, gunakan fallback id document
                        val realId = if (ticketId.isNotEmpty()) ticketId else docId
                        mapJadwalUnik[uniqueKey] = JadwalModel(
                            id = realId,
                            origin = origin,
                            destination = destination,
                            departureDate = tanggalFormatted,
                            departureTime = time,
                            busType = "Toyota Hiace Premio",
                            price = price,
                            availableSeats = jumlahKursi,
                            isActive = true
                        )
                    }
                }

                // --- PENGURUTAN: Jadwal terdekat dengan sekarang berada di posisi paling atas ---
                val sortedList = mapJadwalUnik.values.sortedBy { jadwal ->
                    parseDateTimeToMillisForSorting(jadwal.departureDate, jadwal.departureTime)
                }

                fullJadwalList.addAll(sortedList)
                adapter.filterList(fullJadwalList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseDateTimeToMillisForSorting(dateStr: String, timeStr: String): Long {
        val formats = listOf(
            SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("d MMMM yyyy HH:mm", Locale.US),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("id", "ID"))
        )

        val fullStr = "$dateStr $timeStr"
        for (sdf in formats) {
            try {
                sdf.isLenient = false
                val parsedDate = sdf.parse(fullStr)
                if (parsedDate != null) {
                    return parsedDate.time
                }
            } catch (_: Exception) {}
        }

        // Pengaman manual untuk membaca format teks bulan Indonesia
        return parseCustomIndonesianDate(dateStr, timeStr)
    }

    private fun parseCustomIndonesianDate(dateStr: String, timeStr: String): Long {
        try {
            val months = mapOf(
                "januari" to 1, "februari" to 2, "maret" to 3, "april" to 4,
                "mei" to 5, "juni" to 6, "juli" to 7, "agustus" to 8,
                "september" to 9, "oktober" to 10, "november" to 11, "desember" to 12
            )
            val parts = dateStr.lowercase(Locale.getDefault()).split(" ")
            if (parts.size >= 3) {
                val day = parts[0].toInt()
                val monthName = parts[1]
                val year = parts[2].toInt()
                val month = months[monthName] ?: 1

                val timeParts = timeStr.split(":")
                val hour = if (timeParts.isNotEmpty()) timeParts[0].toInt() else 0
                val minute = if (timeParts.size > 1) timeParts[1].toInt() else 0

                val calendar = Calendar.getInstance().apply {
                    set(year, month - 1, day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return calendar.timeInMillis
            }
        } catch (_: Exception) {}
        return Long.MAX_VALUE
    }

    private fun formatTanggalIndo(tanggalStr: String): String {
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")),
            SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")),
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
        )
        for (sdf in formats) {
            try {
                sdf.isLenient = false
                val date = sdf.parse(tanggalStr)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                    return outputFormat.format(date)
                }
            } catch (_: Exception) {}
        }
        return tanggalStr
    }

    private fun konfirmasiHapusJadwal(jadwal: JadwalModel) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah Anda yakin ingin menghapus jadwal perjalanan ini?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("schedules")
                    .whereEqualTo("userId", auth.currentUser?.uid)
                    .whereEqualTo("asal", jadwal.origin)
                    .whereEqualTo("tujuan", jadwal.destination)
                    .whereEqualTo("waktu", jadwal.departureTime)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val batch = db.batch()
                        for (doc in snapshot.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().addOnSuccessListener {
                            Toast.makeText(this, "Jadwal berhasil dihapus!", Toast.LENGTH_SHORT).show()
                            loadJadwalFromSchedules()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleNotificationReminder(jadwal: JadwalModel, rute: String, dateStr: String, timeStr: String) {
        val workName = "reminder_schedule_${jadwal.id}"
        val workManager = WorkManager.getInstance(this)

        val workInfosFuture = workManager.getWorkInfosForUniqueWork(workName)
        try {
            val workInfos = workInfosFuture.get()
            val isAlreadyScheduled = workInfos?.any { it.state == WorkInfo.State.ENQUEUED } == true

            if (isAlreadyScheduled) {
                AlertDialog.Builder(this)
                    .setTitle("Batalkan Pengingat")
                    .setMessage("Apakah Anda yakin ingin membatalkan pengingat untuk perjalanan $rute?")
                    .setPositiveButton("Ya, Batalkan") { _, _ ->
                        workManager.cancelUniqueWork(workName)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Pengingat berhasil dibatalkan.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Tidak", null)
                    .show()
            } else {
                val departureMillis = parseDateTimeToMillisForSorting(dateStr, timeStr)
                val notifyTimeMillis = departureMillis - TimeUnit.HOURS.toMillis(1)
                val delayInMillis = notifyTimeMillis - System.currentTimeMillis()

                if (delayInMillis <= 0) {
                    Toast.makeText(this, "Keberangkatan kurang dari 1 jam lagi atau sudah lewat!", Toast.LENGTH_SHORT).show()
                    return
                }

                val inputData = Data.Builder()
                    .putString("EXTRA_RUTE", rute)
                    .putString("EXTRA_JAM", timeStr)
                    .build()

                val reminderRequest = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
                    .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .build()

                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, reminderRequest)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Pengingat dipasang: 1 jam sebelum keberangkatan!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses pengingat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBottomSheetLaporan(jadwal: JadwalModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_laporan, null)
        dialog.setContentView(view)

        val tvRute = view.findViewById<TextView>(R.id.tvSheetRute)
        val tvId = view.findViewById<TextView>(R.id.tvSheetId)
        val tvTanggal = view.findViewById<TextView>(R.id.tvSheetTanggal)
        val tvWaktu = view.findViewById<TextView>(R.id.tvSheetWaktu)
        val etKategori = view.findViewById<TextInputEditText>(R.id.etKategoriKendala)
        val etDeskripsi = view.findViewById<TextInputEditText>(R.id.etDeskripsiKeluhan)
        val btnKirim = view.findViewById<Button>(R.id.btnKirimLaporan)

        tvRute?.text = "${jadwal.origin} ➔ ${jadwal.destination}"
        tvId?.text = "ID: ${jadwal.id.uppercase()}"
        tvTanggal?.text = jadwal.departureDate
        tvWaktu?.text = "Jam: ${jadwal.departureTime}"

        btnKirim?.setOnClickListener {
            val kategori = etKategori?.text.toString().trim()
            val deskripsi = etDeskripsi?.text.toString().trim()

            if (kategori.isNullOrEmpty() || deskripsi.isNullOrEmpty()) {
                Toast.makeText(this, "Mohon isi kategori dan deskripsi laporan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: ""
            val laporanMap = hashMapOf(
                "userId" to userId,
                "scheduleId" to jadwal.id,
                "ticketId" to jadwal.id, // <-- Menyimpan ticketId (seperti TK64-8483) ke Firestore
                "rute" to "${jadwal.origin} ➔ ${jadwal.destination}",
                "tanggalKeberangkatan" to jadwal.departureDate,
                "jamKeberangkatan" to jadwal.departureTime,
                "kategori" to kategori,
                "deskripsi" to deskripsi,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("laporan_keluhan")
                .add(laporanMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Laporan berhasil dikirim!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal mengirim laporan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }
}