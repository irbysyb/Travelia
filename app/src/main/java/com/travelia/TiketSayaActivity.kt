package com.travelia

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.travelia.databinding.ActivityTiketSayaBinding
import com.travelia.databinding.DialogBottomSheetBatalBinding
import com.travelia.databinding.DialogBottomSheetBayarBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TiketSayaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTiketSayaBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val listTiketSayaMaster = mutableListOf<UserTiketModel>()
    private val listTiketSaya = mutableListOf<UserTiketModel>()
    private var adapterTiketSaya: TiketSayaAdapter? = null
    private val mapGroupDocIds = mutableMapOf<String, MutableList<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityTiketSayaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupRecyclerView()
        setupListeners()
        setupSearchBar()

        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, R.id.nav_tiket)
    }

    override fun onResume() {
        super.onResume()
        muatTiketSaya()
    }

    private fun setupRecyclerView() {
        adapterTiketSaya = TiketSayaAdapter(
            listTiket = listTiketSaya,
            onStatusClick = { tiketDipilih, _ ->
                if (tiketDipilih.status.equals("Belum Bayar", ignoreCase = true)) {
                    showBottomSheetBayar(tiketDipilih)
                } else {
                    Toast.makeText(this, "Tiket ini sudah lunas.", Toast.LENGTH_SHORT).show()
                }
            },
            onHapusClick = { tiketHapus, _ ->
                konfirmasiHapusTiket(tiketHapus)
            }
        )

        binding.rvTiketSaya.layoutManager = LinearLayoutManager(this)
        binding.rvTiketSaya.adapter = adapterTiketSaya
    }

    private fun setupSearchBar() {
        binding.etSearchTiket?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTiket(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterTiket(query: String) {
        val filteredList = if (query.isEmpty()) {
            listTiketSayaMaster
        } else {
            val q = query.trim().lowercase(Locale.getDefault())
            listTiketSayaMaster.filter { tiket ->
                val idMatch = tiket.ticketId?.lowercase(Locale.getDefault())?.contains(q) == true ||
                        tiket.documentId?.lowercase(Locale.getDefault())?.contains(q) == true
                val asalMatch = tiket.asal.lowercase(Locale.getDefault()).contains(q)
                val tujuanMatch = tiket.tujuan.lowercase(Locale.getDefault()).contains(q)

                idMatch || asalMatch || tujuanMatch
            }
        }

        listTiketSaya.clear()
        listTiketSaya.addAll(filteredList)
        adapterTiketSaya?.notifyDataSetChanged()

        if (listTiketSaya.isEmpty()) {
            binding.tvEmptyTiket?.visibility = View.VISIBLE
            binding.rvTiketSaya.visibility = View.GONE
        } else {
            binding.tvEmptyTiket?.visibility = View.GONE
            binding.rvTiketSaya.visibility = View.VISIBLE
        }
    }

    private fun muatTiketSaya() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar?.visibility = View.VISIBLE

        db.collection("user_tickets")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar?.visibility = View.GONE
                listTiketSayaMaster.clear()
                listTiketSaya.clear()
                mapGroupDocIds.clear()

                val mapTiketGabungan = mutableMapOf<String, UserTiketModel>()
                val docIdsAutoDelete = mutableListOf<String>()

                val currentTime = System.currentTimeMillis()
                val thirtyMinutesMillis = 30 * 60 * 1000L

                for (doc in querySnapshot.documents) {
                    val docId = doc.id
                    val ticketId = doc.getString("ticketId") ?: ""
                    val asal = doc.getString("asal") ?: ""
                    val tujuan = doc.getString("tujuan") ?: ""
                    val rawTanggal = doc.getString("tanggal") ?: ""
                    val tanggalFormatted = formatTanggalIndo(rawTanggal)
                    val waktu = doc.getString("waktu") ?: doc.getString("jam") ?: ""
                    val status = doc.getString("status") ?: "Belum Bayar"

                    // --- LOGIKA AUTO-DELETE: Cek apakah sudah lewat 30 menit dari jadwal ---
                    if (cekTiketSudahLewatBatas(rawTanggal, waktu, currentTime, thirtyMinutesMillis)) {
                        if (!status.equals("Belum Bayar", ignoreCase = true)) {
                            docIdsAutoDelete.add(docId)
                            continue
                        }
                    }

                    val ticketGroupKey = if (ticketId.isNotEmpty()) {
                        "${ticketId}_${status.lowercase(Locale.getDefault())}"
                    } else {
                        "${asal}_${tujuan}_${rawTanggal}_${waktu}_${status.lowercase(Locale.getDefault())}"
                    }

                    if (!mapGroupDocIds.containsKey(ticketGroupKey)) {
                        mapGroupDocIds[ticketGroupKey] = mutableListOf()
                    }
                    mapGroupDocIds[ticketGroupKey]?.add(docId)

                    val harga = doc.getDouble("harga") ?: (doc.getLong("harga")?.toDouble() ?: 0.0)
                    val jumlahKursi = doc.getLong("jumlahKursi")?.toInt() ?: 1
                    val namaArmada = doc.getString("namaArmada") ?: "Travelia Express"
                    val tipeArmada = doc.getString("tipeArmada") ?: "Toyota Hiace Premio"

                    if (mapTiketGabungan.containsKey(ticketGroupKey)) {
                        val tiketLama = mapTiketGabungan[ticketGroupKey]!!
                        mapTiketGabungan[ticketGroupKey] = tiketLama.copy(
                            jumlahKursi = tiketLama.jumlahKursi + jumlahKursi,
                            harga = tiketLama.harga + harga
                        )
                    } else {
                        mapTiketGabungan[ticketGroupKey] = UserTiketModel(
                            documentId = docId,
                            ticketId = ticketId.ifEmpty { docId },
                            asal = asal,
                            tujuan = tujuan,
                            tanggal = tanggalFormatted,
                            waktu = waktu,
                            harga = harga,
                            jumlahKursi = jumlahKursi,
                            status = status,
                            namaArmada = namaArmada,
                            tipeArmada = tipeArmada
                        )
                    }
                }

                // --- EKSEKUSI PENGHAPUSAN OTOMATIS DI FIRESTORE ---
                if (docIdsAutoDelete.isNotEmpty()) {
                    val batch = db.batch()
                    for (id in docIdsAutoDelete) {
                        val ref = db.collection("user_tickets").document(id)
                        batch.delete(ref)
                    }
                    batch.commit()
                }

                // --- PENGURUTAN: "Sudah Bayar" di atas, sisanya diurutkan berdasarkan tanggal/jam terdekat ---
                val sortedTiketList = mapTiketGabungan.values.sortedWith(
                    compareByDescending<UserTiketModel> { it.status.equals("Sudah Bayar", ignoreCase = true) }
                        .thenBy { tiket -> parseDateTimeToMillisForSorting(tiket.tanggal, tiket.waktu) }
                )

                listTiketSayaMaster.addAll(sortedTiketList)

                val currentSearchQuery = binding.etSearchTiket?.text?.toString().orEmpty()
                filterTiket(currentSearchQuery)
            }
            .addOnFailureListener { e ->
                binding.progressBar?.visibility = View.GONE
                Toast.makeText(this, "Gagal memuat tiket: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseDateTimeToMillisForSorting(dateStr: String, timeStr: String): Long {
        val fullStr = "$dateStr $timeStr"
        val formats = listOf(
            SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("id", "ID"))
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
        return Long.MAX_VALUE
    }

    private fun cekTiketSudahLewatBatas(tanggalStr: String, waktuStr: String, currentTime: Long, toleranceMillis: Long): Boolean {
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("id", "ID"))
        )

        val fullDateStr = "${tanggalStr.trim()} ${waktuStr.trim()}"
        for (sdf in formats) {
            try {
                sdf.isLenient = false
                val parsedDate = sdf.parse(fullDateStr)
                if (parsedDate != null) {
                    val targetTime = parsedDate.time
                    return currentTime > (targetTime + toleranceMillis)
                }
            } catch (_: Exception) {}
        }
        return false
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

    // --- FUNGSI GENERATE QR CODE ---
    private fun generateQRCode(textForQR: String): Bitmap? {
        val multiFormatWriter = MultiFormatWriter()
        return try {
            val bitMatrix = multiFormatWriter.encode(textForQR, BarcodeFormat.QR_CODE, 300, 300)
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showBottomSheetBayar(tiket: UserTiketModel) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogBottomSheetBayarBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

        dialogBinding.tvSheetRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"
        dialogBinding.tvSheetId.text = "ID: ${tiket.ticketId ?: "-"}"
        dialogBinding.tvSheetTanggal.text = "Tanggal: ${tiket.tanggal}"
        dialogBinding.tvSheetWaktu.text = "Jam: ${tiket.waktu}"
        dialogBinding.tvSheetTotalHarga.text = formatRupiah.format(tiket.harga)

        // --- GENERATE DAN TAMPILKAN QR CODE KE IMAGEVIEW ---
        val qrText = if (!tiket.ticketId.isNullOrEmpty()) tiket.ticketId else "TRAVELIA-PAYMENT"
        val qrBitmap = generateQRCode(qrText)
        if (qrBitmap != null) {
            dialogBinding.imgQrCode.setImageBitmap(qrBitmap)
        }

        dialogBinding.btnBayarSekarang.setOnClickListener {
            bottomSheetDialog.dismiss()
            prosesPembayaranLunas(tiket)
        }

        bottomSheetDialog.show()
    }

    private fun prosesPembayaranLunas(tiket: UserTiketModel) {
        val statusKey = tiket.status.lowercase(Locale.getDefault())
        val ticketGroupKey = if (!tiket.ticketId.isNullOrEmpty()) {
            "${tiket.ticketId}_$statusKey"
        } else {
            "${tiket.asal}_${tiket.tujuan}_${tiket.tanggal}_${tiket.waktu}_$statusKey"
        }

        val docIdsToUpdate = mapGroupDocIds[ticketGroupKey] ?: listOf(tiket.documentId ?: "")

        binding.progressBar?.visibility = View.VISIBLE

        db.runBatch { batch ->
            for (docId in docIdsToUpdate) {
                if (docId.isNotEmpty()) {
                    val docRef = db.collection("user_tickets").document(docId)
                    batch.update(docRef, "status", "Sudah Bayar")
                }
            }
        }.addOnSuccessListener {
            val primaryDocId = tiket.documentId ?: docIdsToUpdate.firstOrNull() ?: ""
            if (primaryDocId.isNotEmpty()) {
                db.collection("user_tickets").document(primaryDocId).get()
                    .addOnSuccessListener { docSnapshot ->
                        if (docSnapshot.exists()) {
                            val dataTiket = docSnapshot.data ?: mutableMapOf()
                            dataTiket["status"] = "Sudah Bayar"

                            db.collection("schedules").document(primaryDocId).set(dataTiket)

                            db.collection("history_tickets").document(primaryDocId).set(dataTiket)
                                .addOnSuccessListener {
                                    binding.progressBar?.visibility = View.GONE
                                    Toast.makeText(this, "Pembayaran Berhasil! Masuk ke Jadwal & Riwayat.", Toast.LENGTH_SHORT).show()
                                    muatTiketSaya()
                                }
                                .addOnFailureListener { e ->
                                    binding.progressBar?.visibility = View.GONE
                                    Toast.makeText(this, "Gagal mencatat riwayat: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            binding.progressBar?.visibility = View.GONE
                            muatTiketSaya()
                        }
                    }
                    .addOnFailureListener {
                        binding.progressBar?.visibility = View.GONE
                        muatTiketSaya()
                    }
            } else {
                binding.progressBar?.visibility = View.GONE
                muatTiketSaya()
            }
        }.addOnFailureListener { e ->
            binding.progressBar?.visibility = View.GONE
            Toast.makeText(this, "Gagal memproses pembayaran: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun konfirmasiHapusTiket(tiket: UserTiketModel) {
        val isExpired = cekTiketExpired(tiket.tanggal, tiket.waktu)
        val isBelumBayar = tiket.status.equals("Belum Bayar", ignoreCase = true)

        if (!isBelumBayar && !isExpired) {
            Toast.makeText(this, "Tiket aktif yang sudah dibayar tidak dapat dibatalkan", Toast.LENGTH_SHORT).show()
            return
        }

        if (tiket.jumlahKursi <= 1 || isExpired) {
            AlertDialog.Builder(this)
                .setTitle(if (isExpired) "Hapus Riwayat Tiket" else "Batalkan Tiket")
                .setMessage("Apakah Anda yakin ingin menghapus tiket perjalanan dari ${tiket.asal} ke ${tiket.tujuan}?")
                .setPositiveButton("Ya") { _, _ ->
                    prosesHapusAtauKurangiTiket(tiket, tiket.jumlahKursi)
                }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            showBottomSheetBatal(tiket)
        }
    }

    private fun showBottomSheetBatal(tiket: UserTiketModel) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogBottomSheetBatalBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        val totalKursiAwal = tiket.jumlahKursi
        val hargaPerKursi = if (totalKursiAwal > 0) tiket.harga / totalKursiAwal else 0.0
        var jumlahKursiDibatalkan = 1
        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

        fun updateUI() {
            dialogBinding.tvJumlahKursi.text = jumlahKursiDibatalkan.toString()
            dialogBinding.tvTotalHarga.text = formatRupiah.format(hargaPerKursi * jumlahKursiDibatalkan)
            dialogBinding.btnMinus.isEnabled = jumlahKursiDibatalkan > 1
            dialogBinding.btnPlus.isEnabled = jumlahKursiDibatalkan < totalKursiAwal
            dialogBinding.btnKonfirmasiBayar.text = "Konfirmasi Pembatalan"
        }

        dialogBinding.tvSheetRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"
        dialogBinding.tvSheetId.text = "ID: ${tiket.ticketId ?: "-"}"
        dialogBinding.tvSheetTanggal.text = "Tanggal: ${tiket.tanggal}"
        dialogBinding.tvSheetWaktu.text = "Jam: ${tiket.waktu}"
        updateUI()

        dialogBinding.btnMinus.setOnClickListener { if (jumlahKursiDibatalkan > 1) { jumlahKursiDibatalkan--; updateUI() } }
        dialogBinding.btnPlus.setOnClickListener { if (jumlahKursiDibatalkan < totalKursiAwal) { jumlahKursiDibatalkan++; updateUI() } }

        dialogBinding.btnKonfirmasiBayar.setOnClickListener {
            bottomSheetDialog.dismiss()
            prosesHapusAtauKurangiTiket(tiket, jumlahKursiDibatalkan)
        }

        bottomSheetDialog.show()
    }

    private fun prosesHapusAtauKurangiTiket(tiket: UserTiketModel, jumlahKursiDibatalkan: Int) {
        val statusKey = tiket.status.lowercase(Locale.getDefault())
        val ticketGroupKey = if (!tiket.ticketId.isNullOrEmpty()) {
            "${tiket.ticketId}_$statusKey"
        } else {
            "${tiket.asal}_${tiket.tujuan}_${tiket.tanggal}_${tiket.waktu}_$statusKey"
        }

        val docIdsToDelete = mapGroupDocIds[ticketGroupKey] ?: mutableListOf(tiket.documentId ?: "")

        binding.progressBar?.visibility = View.VISIBLE
        val primaryDocRef = db.collection("user_tickets").document(tiket.documentId ?: "")

        primaryDocRef.get().addOnSuccessListener { snapshot ->
            val parentTicketId = snapshot.getString("ticketId") ?: ""

            db.runTransaction { transaction ->
                var currentSisaAdmin = 0L
                var adminTicketRef = if (parentTicketId.isNotEmpty()) db.collection("tickets").document(parentTicketId) else null

                if (adminTicketRef != null) {
                    val adminSnap = transaction.get(adminTicketRef)
                    if (adminSnap.exists()) {
                        currentSisaAdmin = adminSnap.getLong("sisaKursi") ?: 0L
                    } else {
                        adminTicketRef = null
                    }
                }

                if (jumlahKursiDibatalkan >= tiket.jumlahKursi) {
                    for (docId in docIdsToDelete) {
                        if (docId.isNotEmpty()) transaction.delete(db.collection("user_tickets").document(docId))
                    }
                } else {
                    val hargaPerKursi = tiket.harga / tiket.jumlahKursi
                    val sisaKursiUser = tiket.jumlahKursi - jumlahKursiDibatalkan

                    transaction.update(primaryDocRef, mapOf("jumlahKursi" to sisaKursiUser, "harga" to hargaPerKursi * sisaKursiUser))
                }

                if (adminTicketRef != null) {
                    transaction.update(adminTicketRef, "sisaKursi", currentSisaAdmin + jumlahKursiDibatalkan)
                }
            }.addOnSuccessListener {
                binding.progressBar?.visibility = View.GONE
                Toast.makeText(this, "Berhasil membatalkan $jumlahKursiDibatalkan kursi!", Toast.LENGTH_SHORT).show()
                muatTiketSaya()
            }.addOnFailureListener { e ->
                binding.progressBar?.visibility = View.GONE
                Toast.makeText(this, "Gagal menghapus tiket: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            binding.progressBar?.visibility = View.GONE
            Toast.makeText(this, "Gagal memproses tiket: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cekTiketExpired(tanggalStr: String, waktuStr: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID")),
            SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID"))
        )

        val fullDateStr = "${tanggalStr.trim()} ${waktuStr.trim()}"
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

        val thirtyMinutesMillis = 30 * 60 * 1000L
        return ticketTime > 0 && currentTime > (ticketTime + thirtyMinutesMillis)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}