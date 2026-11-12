package com.travelia

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityRiwayatPesananBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RiwayatPesananActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatPesananBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val listRiwayat = mutableListOf<RiwayatPesanan>()
    private lateinit var adapter: RiwayatPesananAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatPesananBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Penanganan Status Bar / Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupRecyclerView()
        setupListeners()
        muatDataRiwayat()

        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, null)
    }

    private fun setupRecyclerView() {
        adapter = RiwayatPesananAdapter(listRiwayat) {
            updateDeleteButtonState()
        }

        binding.rvRiwayatPesanan.layoutManager = LinearLayoutManager(this)
        binding.rvRiwayatPesanan.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Toggle Mode Hapus (Menggunakan tvActionDeleteMode di header)
        binding.tvActionDeleteMode.setOnClickListener {
            adapter.isDeleteMode = !adapter.isDeleteMode
            if (adapter.isDeleteMode) {
                binding.layoutBottomAction.visibility = View.VISIBLE
                binding.tvActionDeleteMode.text = "Batal"
            } else {
                binding.layoutBottomAction.visibility = View.GONE
                binding.tvActionDeleteMode.text = "Hapus"
                adapter.deselectAll()
            }
            updateDeleteButtonState()
        }

        // Tombol Pilih Semua (Menggunakan cbSelectAll di footer action)
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                adapter.selectAll()
            } else {
                if (adapter.selectedItems.size == listRiwayat.size) {
                    adapter.deselectAll()
                }
            }
        }

        // Tombol Eksekusi Hapus (Menggunakan btnDeleteSelected)
        binding.btnDeleteSelected.setOnClickListener {
            if (adapter.selectedItems.isNotEmpty()) {
                konfirmasiHapusTiket()
            }
        }
    }

    private fun updateDeleteButtonState() {
        val totalSelected = adapter.selectedItems.size
        binding.btnDeleteSelected.text = if (totalSelected > 0) "Hapus Terpilih ($totalSelected)" else "Hapus Terpilih"
        binding.btnDeleteSelected.isEnabled = totalSelected > 0

        // Sinkronkan status CheckBox "Pilih Semua" di bawah
        if (totalSelected == listRiwayat.size && listRiwayat.isNotEmpty()) {
            if (!binding.cbSelectAll.isChecked) binding.cbSelectAll.isChecked = true
        } else {
            if (binding.cbSelectAll.isChecked) binding.cbSelectAll.isChecked = false
        }
    }

    private fun processAndSortData(rawList: MutableList<RiwayatPesanan>) {
        // 1. Logika Pengelompokan (Grouping) dengan LinkedHashMap
        val groupedMap = LinkedHashMap<String, RiwayatPesanan>()
        for (ticket in rawList) {
            val key = "${ticket.asal}_${ticket.tujuan}_${ticket.tanggal}_${ticket.waktu}_${ticket.tipeArmada}"
            if (groupedMap.containsKey(key)) {
                val existing = groupedMap[key]!!
                existing.jumlahKursi += ticket.jumlahKursi
                existing.harga += ticket.harga
            } else {
                groupedMap[key] = ticket
            }
        }

        val groupedList = groupedMap.values.toList()
        val currentTime = System.currentTimeMillis()
        val thirtyMinutesMillis = 30 * 60 * 1000L

        // 2. Pengurutan Presisi Tinggi:
        // - Tiket Aktif di atas (terdekat ke terjauh)
        // - Tiket Tidak Aktif di bawah (terbaru lewat di atas seperti 3 Nov, terlama lewat di bawah seperti 23 Okt)
        val sortedList = groupedList.sortedWith(Comparator { t1, t2 ->
            val millis1 = parseDateTimeToMillisForSorting(t1.tanggal, t1.waktu)
            val millis2 = parseDateTimeToMillisForSorting(t2.tanggal, t2.waktu)

            val active1 = millis1 == 0L || currentTime <= (millis1 + thirtyMinutesMillis)
            val active2 = millis2 == 0L || currentTime <= (millis2 + thirtyMinutesMillis)

            // Prioritas Utama: Tiket Aktif selalu di atas Tiket Tidak Aktif
            if (active1 != active2) {
                if (active1) -1 else 1
            } else {
                if (active1) {
                    // Keduanya Aktif: Urutkan dari terdekat ke terjauh (Ascending)
                    millis1.compareTo(millis2)
                } else {
                    // Keduanya Tidak Aktif: Urutkan dari terbaru lewat ke terlama (Descending -> millis2 dibanding millis1)
                    millis2.compareTo(millis1)
                }
            }
        })

        listRiwayat.clear()
        listRiwayat.addAll(sortedList)
        adapter.notifyDataSetChanged()

        if (listRiwayat.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvRiwayatPesanan.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvRiwayatPesanan.visibility = View.VISIBLE
        }
    }

    // --- FUNGSI PARSER ANTI-GAGAL DENGAN PENGAMAN BULAN INDONESIA ---
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

    private fun muatDataRiwayat() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Ambil nama lengkap pengguna dari koleksi "users" berdasarkan field "full_name"
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val fullNameUser = userDoc.getString("full_name") ?: "Penumpang"

                // 2. Ambil data riwayat pesanan dari koleksi khusus "history_tickets"
                db.collection("history_tickets")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val rawList = mutableListOf<RiwayatPesanan>()

                        for (doc in snapshot.documents) {
                            val item = doc.toObject(RiwayatPesanan::class.java)?.copy(id = doc.id)
                            if (item != null) {
                                if (item.namaPenumpang.isNullOrEmpty()) {
                                    item.namaPenumpang = fullNameUser
                                }
                                rawList.add(item)
                            }
                        }

                        processAndSortData(rawList)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal memuat riwayat: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                muatTiketTanpaProfil()
            }
    }

    private fun muatTiketTanpaProfil() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("history_tickets")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val rawList = mutableListOf<RiwayatPesanan>()
                for (doc in snapshot.documents) {
                    val item = doc.toObject(RiwayatPesanan::class.java)?.copy(id = doc.id)
                    if (item != null) rawList.add(item)
                }

                processAndSortData(rawList)
            }
    }

    private fun konfirmasiHapusTiket() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Riwayat Pesanan")
            .setMessage("Apakah Anda yakin ingin menghapus riwayat pesanan terpilih?")
            .setPositiveButton("Ya, Hapus") { _, _ ->
                eksekusiHapusSelected()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun eksekusiHapusSelected() {
        val itemsToDelete = adapter.selectedItems.toList()
        var processedCount = 0

        for (item in itemsToDelete) {
            db.collection("history_tickets")
                .whereEqualTo("userId", auth.currentUser?.uid)
                .whereEqualTo("asal", item.asal)
                .whereEqualTo("tujuan", item.tujuan)
                .whereEqualTo("tanggal", item.tanggal)
                .whereEqualTo("waktu", item.waktu)
                .whereEqualTo("tipeArmada", item.tipeArmada)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }

                    batch.commit().addOnSuccessListener {
                        processedCount++
                        if (processedCount == itemsToDelete.size) {
                            Toast.makeText(this, "Berhasil menghapus riwayat pesanan", Toast.LENGTH_SHORT).show()
                            adapter.isDeleteMode = false
                            binding.layoutBottomAction.visibility = View.GONE
                            binding.tvActionDeleteMode.text = "Hapus"
                            binding.cbSelectAll.isChecked = false
                            muatDataRiwayat()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}