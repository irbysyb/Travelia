package com.travelia

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityPesanTiketBinding
import com.travelia.databinding.DialogBottomSheetPesanBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PesanTiketActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPesanTiketBinding
    private val calendar = Calendar.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Variable penampung data hasil query
    private val listHasilTiket = mutableListOf<TiketModel>()
    private var adapterTiket: TiketAdapter? = null

    // Daftar rute kota yang dibatasi sesuai koridor
    private val ruteKota = arrayOf(
        "Banda Aceh",
        "Sigli",
        "Bireuen",
        "Lhokseumawe",
        "Langsa",
        "Stabat",
        "Medan"
    )

    private val jadwalWaktu = arrayOf(
        "01:00", "02:00", "03:00", "04:00", "05:00",
        "06:00", "07:00", "08:00", "09:00", "10:00", "11:00",
        "12:00", "13:00", "14:00", "15:00", "16:00", "17:00",
        "18:00", "19:00", "20:00", "21:00", "22:00", "23:00"
    )

    // Daftar pilihan kriteria Algoritma Greedy
    private val kriteriaGreedy = arrayOf(
        "Harga Termurah",
        "Waktu Terdekat"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityPesanTiketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Penanganan Status Bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupDropdowns()
        setupFormInteractions()
        setupRecyclerView()
        setupListeners()

        // PEMBERSIHAN OTOMATIS: Menghapus tiket yang sudah kedaluwarsa dari database
        bersihkanTiketExpired()

        // Panggil fungsi terpusat Bottom Navigation
        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, null)
    }

    private fun setupDropdowns() {
        val ruteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ruteKota)
        binding.etDari.setAdapter(ruteAdapter)
        binding.etKe.setAdapter(ruteAdapter)

        binding.etDari.setOnItemClickListener { _, _, _, _ ->
            binding.tilDari.error = null
            checkSameRouteValidation()
        }
        binding.etKe.setOnItemClickListener { _, _, _, _ ->
            binding.tilKe.error = null
            checkSameRouteValidation()
        }

        // Pilihan jam bulat 24 jam penuh
        val waktuAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jadwalWaktu)
        binding.etWaktu.setAdapter(waktuAdapter)
        binding.etWaktu.setOnItemClickListener { _, _, _, _ ->
            binding.tilWaktu.error = null
        }

        val kriteriaAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, kriteriaGreedy)
        binding.etKriteria.setAdapter(kriteriaAdapter)
        binding.etKriteria.setText(kriteriaGreedy[0], false)
        binding.etKriteria.setOnItemClickListener { _, _, _, _ ->
            binding.tilKriteria.error = null
        }
    }

    private fun checkSameRouteValidation() {
        val dari = binding.etDari.text.toString().trim()
        val ke = binding.etKe.text.toString().trim()

        if (dari.isNotEmpty() && ke.isNotEmpty() && dari.equals(ke, ignoreCase = true)) {
            Toast.makeText(this, "Kota asal dan tujuan tidak boleh sama!", Toast.LENGTH_SHORT).show()
            binding.etKe.setText("", false)
            binding.tilKe.error = "Kota tujuan tidak boleh sama dengan asal"
        } else {
            binding.tilKe.error = null
        }
    }

    private fun setupFormInteractions() {
        binding.etTanggal.setOnClickListener {
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedDate = String.format("%02d/%02d/%d", dayOfMonth, month + 1, year)
                    binding.etTanggal.setText(selectedDate)
                    binding.etTanggal.error = null
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.show()
        }

        binding.btnSwap.setOnClickListener {
            val dariText = binding.etDari.text.toString()
            val keText = binding.etKe.text.toString()

            binding.etDari.setText(keText, false)
            binding.etKe.setText(dariText, false)

            binding.tilDari.error = null
            binding.tilKe.error = null

            checkSameRouteValidation()
        }
    }

    private fun setupRecyclerView() {
        adapterTiket = TiketAdapter(listHasilTiket) { tiketDipilih ->
            showBottomSheetPemesanan(tiketDipilih)
        }

        binding.rvTiket.layoutManager = LinearLayoutManager(this)
        binding.rvTiket.adapter = adapterTiket
    }

    // FUNGSI BOTTOM SHEET PEMESANAN TIKET
    private fun showBottomSheetPemesanan(tiket: TiketModel) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogBottomSheetPesanBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        val inputKursiAwal = binding.etJumlahKursi.text.toString().toIntOrNull() ?: 1
        var jumlahKursi = if (inputKursiAwal > 0) inputKursiAwal else 1

        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

        fun updateUI() {
            val totalHarga = tiket.harga * jumlahKursi

            dialogBinding.tvJumlahKursi.text = jumlahKursi.toString()
            dialogBinding.tvTotalHarga.text = formatRupiah.format(totalHarga)
        }

        // Tampilkan Detail Tiket
        dialogBinding.tvSheetRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"
        dialogBinding.tvSheetId.text = "ID: ${tiket.id}"
        dialogBinding.tvSheetTanggal.text = "Tanggal: ${tiket.tanggal}"
        dialogBinding.tvSheetWaktu.text = "Jam: ${tiket.waktu}"
        updateUI()

        // Tombol Kurangi Kursi (-)
        dialogBinding.btnMinus.setOnClickListener {
            if (jumlahKursi > 1) {
                jumlahKursi--
                updateUI()
            } else {
                Toast.makeText(this, "Minimal 1 kursi", Toast.LENGTH_SHORT).show()
            }
        }

        // Tombol Tambah Kursi (+)
        dialogBinding.btnPlus.setOnClickListener {
            if (jumlahKursi < tiket.sisaKursi && jumlahKursi < 14) {
                jumlahKursi++
                updateUI()
            } else {
                Toast.makeText(this, "Melebihi sisa kursi yang tersedia!", Toast.LENGTH_SHORT).show()
            }
        }

        // Tombol Konfirmasi Pemesanan
        dialogBinding.btnKonfirmasiBayar.setOnClickListener {
            dialogBinding.btnKonfirmasiBayar.isEnabled = false
            bottomSheetDialog.dismiss()
            eksekusiPemesanan(tiket, jumlahKursi)
        }

        bottomSheetDialog.show()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnCari.setOnClickListener {
            prosesPencarian()
        }

        binding.btnRekomendasi.setOnClickListener {
            if (validateInputs()) {
                prosesRekomendasiGreedy()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        val dari = binding.etDari.text.toString().trim()
        val ke = binding.etKe.text.toString().trim()
        val tanggal = binding.etTanggal.text.toString().trim()
        val waktu = binding.etWaktu.text.toString().trim()
        val kursiStr = binding.etJumlahKursi.text.toString().trim()
        val kriteria = binding.etKriteria.text.toString().trim()

        if (dari.isEmpty()) {
            binding.tilDari.error = "Mohon form ini diisi terlebih dahulu"
            isValid = false
        } else {
            binding.tilDari.error = null
        }

        if (ke.isEmpty()) {
            binding.tilKe.error = "Mohon form ini diisi terlebih dahulu"
            isValid = false
        } else if (!dari.equals(ke, ignoreCase = true)) {
            binding.tilKe.error = null
        }

        if (dari.isNotEmpty() && ke.isNotEmpty() && dari.equals(ke, ignoreCase = true)) {
            binding.tilKe.error = "Kota asal dan tujuan tidak boleh sama"
            isValid = false
        }

        if (tanggal.isEmpty()) {
            binding.etTanggal.error = "Mohon form ini diisi terlebih dahulu"
            isValid = false
        } else {
            binding.etTanggal.error = null
        }

        if (waktu.isEmpty()) {
            binding.tilWaktu.error = "Mohon pilih jam terlebih dahulu"
            isValid = false
        } else {
            binding.tilWaktu.error = null
        }

        if (kursiStr.isEmpty()) {
            binding.etJumlahKursi.error = "Mohon form ini diisi terlebih dahulu"
            isValid = false
        } else {
            val jumlahKursi = kursiStr.toIntOrNull() ?: 0
            if (jumlahKursi <= 0) {
                binding.etJumlahKursi.error = "Jumlah kursi minimal 1"
                isValid = false
            } else if (jumlahKursi > 14) {
                binding.etJumlahKursi.error = "Maksimal pemesanan adalah 14 kursi"
                isValid = false
            } else {
                binding.etJumlahKursi.error = null
            }
        }

        if (kriteria.isEmpty()) {
            binding.tilKriteria.error = "Mohon form ini diisi terlebih dahulu"
            isValid = false
        } else {
            binding.tilKriteria.error = null
        }

        if (!isValid) {
            Toast.makeText(this, "Mohon form ini diisi terlebih dahulu", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun bersihkanTiketExpired() {
        val currentTime = System.currentTimeMillis()
        val sdfInput = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        val sdfAlt = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id", "ID"))

        db.collection("tickets").get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                val tanggal = doc.getString("tanggal") ?: ""
                val waktu = doc.getString("waktu") ?: doc.getString("jam") ?: "00:00"
                val fullDateStr = "$tanggal $waktu"

                val ticketTime = try {
                    sdfInput.parse(fullDateStr)?.time ?: sdfAlt.parse(fullDateStr)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }

                if (ticketTime in 1..<currentTime) {
                    db.collection("tickets").document(doc.id).delete()
                }
            }
        }

        db.collection("user_tickets").get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                val tanggal = doc.getString("tanggal") ?: ""
                val waktu = doc.getString("waktu") ?: doc.getString("jam") ?: "00:00"
                val fullDateStr = "$tanggal $waktu"

                val userTicketTime = try {
                    sdfInput.parse(fullDateStr)?.time ?: sdfAlt.parse(fullDateStr)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }

                if (userTicketTime in 1..<currentTime) {
                    db.collection("user_tickets").document(doc.id).delete()
                }
            }
        }
    }

    private fun eksekusiPemesanan(tiket: TiketModel, jumlahKursiDipesan: Int) {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Silakan login terlebih dahulu!", Toast.LENGTH_SHORT).show()
            return
        }

        val adminTicketId = tiket.id
        if (adminTicketId.isEmpty()) {
            Toast.makeText(this, "ID Tiket tidak valid!", Toast.LENGTH_SHORT).show()
            return
        }

        val adminTicketRef = db.collection("tickets").document(adminTicketId)
        val userTicketRef = db.collection("user_tickets").document()

        db.runTransaction { transaction ->
            val adminSnapshot = transaction.get(adminTicketRef)

            if (!adminSnapshot.exists()) {
                throw Exception("Tiket admin tidak ditemukan!")
            }

            val currentSisaKursi = adminSnapshot.getLong("sisaKursi")
                ?: adminSnapshot.getLong("jumlahKursi") ?: 0L

            if (currentSisaKursi < jumlahKursiDipesan) {
                throw Exception("Maaf, sisa kursi tidak mencukupi!")
            }

            // 1. Kurangi sisaKursi di koleksi tickets (admin)
            transaction.update(adminTicketRef, "sisaKursi", currentSisaKursi - jumlahKursiDipesan)

            // 2. Simpan pesanan baru ke koleksi user_tickets dengan status "Belum Bayar"
            val newTicketData = hashMapOf(
                "userId" to userId,
                "ticketId" to adminTicketId,
                "asal" to tiket.asal,
                "tujuan" to tiket.tujuan,
                "rute" to "${tiket.asal} ➔ ${tiket.tujuan}",
                "tanggal" to binding.etTanggal.text.toString().ifEmpty { tiket.tanggal },
                "waktu" to tiket.waktu,
                "jam" to tiket.waktu,
                "harga" to (tiket.harga * jumlahKursiDipesan),
                "jumlahKursi" to jumlahKursiDipesan,
                "namaArmada" to tiket.namaArmada.ifEmpty { "Travelia Express" },
                "tipeArmada" to "Toyota Hiace Premio",
                "status" to "Belum Bayar",
                "timestamp" to Timestamp.now()
            )

            transaction.set(userTicketRef, newTicketData)
        }.addOnSuccessListener {
            Toast.makeText(this, "Pesanan berhasil dibuat!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, TiketSayaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal memesan tiket: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun prosesPencarian() {
        val dari = binding.etDari.text.toString().trim()
        val ke = binding.etKe.text.toString().trim()
        val jumlahKursiDibutuhkan = binding.etJumlahKursi.text.toString().toIntOrNull() ?: 1

        Toast.makeText(this, "Memuat daftar tiket...", Toast.LENGTH_SHORT).show()

        var query: com.google.firebase.firestore.Query = db.collection("tickets")

        if (dari.isNotEmpty()) {
            query = query.whereEqualTo("asal", dari)
        }
        if (ke.isNotEmpty()) {
            query = query.whereEqualTo("tujuan", ke)
        }

        query.get()
            .addOnSuccessListener { querySnapshot ->
                listHasilTiket.clear()
                val tempList = mutableListOf<TiketModel>()

                for (document in querySnapshot.documents) {
                    val tiket = document.toObject(TiketModel::class.java)?.copy(id = document.id)
                    if (tiket != null) {
                        if (tiket.sisaKursi >= jumlahKursiDibutuhkan) {
                            tempList.add(tiket)
                        }
                    }
                }

                val sdfInput = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
                val sdfAlt = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))

                val sortedList = tempList.sortedWith(compareBy<TiketModel> { tiket ->
                    try {
                        sdfInput.parse(tiket.tanggal) ?: sdfAlt.parse(tiket.tanggal)
                    } catch (e: Exception) {
                        null
                    }
                }.thenBy { it.waktuMenit })

                listHasilTiket.addAll(sortedList)
                adapterTiket?.notifyDataSetChanged()

                if (listHasilTiket.isEmpty()) {
                    binding.tvTitleHasil.visibility = View.GONE
                    Toast.makeText(this, "Tidak ada tiket yang tersedia.", Toast.LENGTH_LONG).show()
                } else {
                    binding.tvTitleHasil.visibility = View.VISIBLE
                    Toast.makeText(this, "Ditemukan ${listHasilTiket.size} tiket!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat tiket: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun prosesRekomendasiGreedy() {
        val dari = binding.etDari.text.toString().trim()
        val ke = binding.etKe.text.toString().trim()
        val tanggal = binding.etTanggal.text.toString().trim()
        val jumlahKursiDibutuhkan = binding.etJumlahKursi.text.toString().toIntOrNull() ?: 1
        val kriteria = binding.etKriteria.text.toString().trim()

        val w1 = if (kriteria == "Harga Termurah") 1.0 else 0.0
        val w2 = if (kriteria == "Harga Termurah") 0.0 else 1.0

        Toast.makeText(this, "Menerapkan Algoritma Greedy ($kriteria)...", Toast.LENGTH_SHORT).show()

        db.collection("tickets")
            .whereEqualTo("asal", dari)
            .whereEqualTo("tujuan", ke)
            .whereEqualTo("tanggal", tanggal)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val listTiketValid = mutableListOf<TiketModel>()

                for (document in querySnapshot.documents) {
                    val tiket = document.toObject(TiketModel::class.java)?.copy(id = document.id)
                    if (tiket != null) {
                        if (tiket.sisaKursi >= jumlahKursiDibutuhkan) {
                            listTiketValid.add(tiket)
                        }
                    }
                }

                if (listTiketValid.isNotEmpty()) {
                    val optimalList = listTiketValid.sortedBy { tiket ->
                        (w1 * tiket.harga) + (w2 * tiket.waktuMenit.toDouble())
                    }

                    listHasilTiket.clear()
                    listHasilTiket.addAll(optimalList)
                    adapterTiket?.notifyDataSetChanged()
                    binding.tvTitleHasil.visibility = View.VISIBLE

                    Toast.makeText(this, "Ditemukan ${optimalList.size} tiket optimal!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Tiket tanggal tersebut kosong, mencari alternatif...", Toast.LENGTH_LONG).show()

                    db.collection("tickets")
                        .whereEqualTo("asal", dari)
                        .whereEqualTo("tujuan", ke)
                        .get()
                        .addOnSuccessListener { altSnapshot ->
                            val listAlternatif = mutableListOf<TiketModel>()
                            for (doc in altSnapshot.documents) {
                                val t = doc.toObject(TiketModel::class.java)?.copy(id = doc.id)
                                if (t != null && t.sisaKursi >= jumlahKursiDibutuhkan) {
                                    listAlternatif.add(t)
                                }
                            }

                            if (listAlternatif.isEmpty()) {
                                listHasilTiket.clear()
                                adapterTiket?.notifyDataSetChanged()
                                binding.tvTitleHasil.visibility = View.GONE
                                Toast.makeText(this, "Maaf, tidak ada tiket alternatif.", Toast.LENGTH_LONG).show()
                            } else {
                                val hargaMax = listAlternatif.maxOfOrNull { it.harga } ?: 1.0
                                val waktuMax = listAlternatif.maxOfOrNull { it.waktuMenit.toDouble() } ?: 1.0

                                val scoredAlternatives = listAlternatif.map { tiket ->
                                    val hNorm = tiket.harga / if (hargaMax == 0.0) 1.0 else hargaMax
                                    val wNorm = tiket.waktuMenit.toDouble() / if (waktuMax == 0.0) 1.0 else waktuMax
                                    val scoreDi = (w1 * hNorm) + (w2 * wNorm)
                                    Pair(tiket, scoreDi)
                                }.sortedBy { it.second }

                                val finalAlternatifList = scoredAlternatives.map { it.first }

                                listHasilTiket.clear()
                                listHasilTiket.addAll(finalAlternatifList)
                                adapterTiket?.notifyDataSetChanged()
                                binding.tvTitleHasil.visibility = View.VISIBLE

                                Toast.makeText(this, "Menampilkan tiket alternatif terbaik!", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
    }
}