package com.travelia

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityAdminBinding
import java.util.Calendar
import kotlin.random.Random

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val calendar = Calendar.getInstance()

    private val listAdminTiket = mutableListOf<TiketModel>()
    private val listTiketFiltered = mutableListOf<TiketModel>()
    private var adminAdapter: AdminTiketAdapter? = null

    // --- FITUR LAPORAN USER ---
    private val listLaporanUser = mutableListOf<LaporanModel>()
    private var laporanAdapter: AdminLaporanAdapter? = null

    private var isEditMode = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()
        setupRecyclerView()
        setupFormInteractions()
        setupSearchListener()
        setupListeners()
    }

    private fun setupDropdowns() {
        val ruteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ruteKota)
        binding.etAdminAsal.setAdapter(ruteAdapter)
        binding.etAdminTujuan.setAdapter(ruteAdapter)

        val waktuAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jadwalWaktu)
        binding.etAdminWaktu.setAdapter(waktuAdapter)
    }

    private fun setupRecyclerView() {
        // RecyclerView Tiket
        adminAdapter = AdminTiketAdapter(
            listTiketFiltered,
            onEditClick = { tiketYangDiEdit ->
                isikanFormUntukEdit(tiketYangDiEdit)
            },
            onDeleteClick = { tiketYangDihapus ->
                konfirmasiHapusTiket(tiketYangDihapus)
            }
        )
        binding.rvAdminTiket.layoutManager = LinearLayoutManager(this)
        binding.rvAdminTiket.adapter = adminAdapter

        // RecyclerView Laporan User (Jika view rvAdminLaporan tersedia di XML)
        try {
            laporanAdapter = AdminLaporanAdapter(listLaporanUser) { laporanDihapus ->
                konfirmasiHapusLaporan(laporanDihapus)
            }
            binding.rvAdminLaporan.layoutManager = LinearLayoutManager(this)
            binding.rvAdminLaporan.adapter = laporanAdapter
        } catch (_: Exception) {}
    }

    private fun setupSearchListener() {
        binding.etSearchAdmin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTiket(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterTiket(query: String) {
        listTiketFiltered.clear()
        val keyword = query.lowercase().trim()

        if (keyword.isEmpty()) {
            listTiketFiltered.addAll(listAdminTiket)
        } else {
            for (tiket in listAdminTiket) {
                val matchAsal = tiket.asal.lowercase().contains(keyword)
                val matchTujuan = tiket.tujuan.lowercase().contains(keyword)
                val matchId = tiket.id.lowercase().contains(keyword)
                val matchArmada = tiket.namaArmada.lowercase().contains(keyword)
                val matchTanggal = tiket.tanggal.lowercase().contains(keyword)

                if (matchAsal || matchTujuan || matchId || matchArmada || matchTanggal) {
                    listTiketFiltered.add(tiket)
                }
            }
        }
        adminAdapter?.notifyDataSetChanged()
    }

    private fun generateRandomTicketId(): String {
        val part1 = Random.nextInt(0, 100)
        val part2 = Random.nextInt(0, 10000)
        return String.format("TK%02d-%04d", part1, part2)
    }

    private fun setupFormInteractions() {
        binding.btnToggleForm.setOnClickListener {
            if (binding.cardFormInput.visibility == View.GONE) {
                isEditMode = false
                clearForm()

                binding.etIdTiket.setText(generateRandomTicketId())
                binding.etAdminNamaArmada.setText("Travelia Express")
                binding.etAdminTipeArmada.setText("Toyota Hiace Premio")

                binding.etIdTiket.isEnabled = false
                binding.etAdminNamaArmada.isEnabled = false
                binding.etAdminTipeArmada.isEnabled = false

                binding.cardFormInput.visibility = View.VISIBLE
                binding.btnToggleForm.text = "- Sembunyikan Form"
                binding.btnSimpanTiket.text = "Simpan ke Firestore"
            } else {
                binding.cardFormInput.visibility = View.GONE
                binding.btnToggleForm.text = "+ Buat Tiket Baru"
            }
        }

        binding.btnLihatTiket.setOnClickListener {
            toggleListTiket()
        }
        binding.btnToggleListTiket.setOnClickListener {
            toggleListTiket()
        }

        // Action untuk melihat Laporan Kendala (jika tombol btnLihatLaporan ada di XML)
        try {
            binding.btnLihatLaporan.setOnClickListener {
                toggleListLaporan()
            }
        } catch (_: Exception) {}

        binding.etAdminTanggal.setOnClickListener {
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedDate = String.format("%02d/%02d/%d", dayOfMonth, month + 1, year)
                    binding.etAdminTanggal.setText(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.show()
        }
    }

    private fun toggleListTiket() {
        if (binding.containerListTiket.visibility == View.GONE) {
            binding.containerListTiket.visibility = View.VISIBLE
            binding.btnLihatTiket.text = "Tutup"
            muatDaftarTiketDariFirestore()
        } else {
            binding.containerListTiket.visibility = View.GONE
            binding.btnLihatTiket.text = "Lihat Data"
            binding.etSearchAdmin.setText("")
        }
    }

    // Toggle Tampilan List Laporan User
    private fun toggleListLaporan() {
        try {
            if (binding.containerListLaporan.visibility == View.GONE) {
                binding.containerListLaporan.visibility = View.VISIBLE
                binding.btnLihatLaporan.text = "Tutup Laporan"
                muatLaporanDariFirestore()
            } else {
                binding.containerListLaporan.visibility = View.GONE
                binding.btnLihatLaporan.text = "Lihat Laporan"
            }
        } catch (_: Exception) {
            muatLaporanDariFirestore()
        }
    }

    private fun setupListeners() {
        binding.btnLogoutAdmin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        binding.btnSimpanTiket.setOnClickListener {
            simpanTiketKeFirestore()
        }
    }

    // --- FUNGSI MEMUAT LAPORAN DARI FIRESTORE ("laporan_keluhan") ---
    private fun muatLaporanDariFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection("laporan_keluhan")
            .get()
            .addOnSuccessListener { querySnapshot ->
                listLaporanUser.clear()

                for (doc in querySnapshot.documents) {
                    val item = doc.toObject(LaporanModel::class.java)?.copy(id = doc.id)
                    if (item != null) {
                        listLaporanUser.add(item)
                    }
                }

                // Urutkan laporan dari yang paling baru
                listLaporanUser.sortByDescending { it.timestamp }
                laporanAdapter?.notifyDataSetChanged()

                if (listLaporanUser.isEmpty()) {
                    Toast.makeText(this, "Belum ada laporan keluhan dari user", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat laporan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun konfirmasiHapusLaporan(laporan: LaporanModel) {
        AlertDialog.Builder(this)
            .setTitle("Tandai Selesai / Hapus")
            .setMessage("Apakah keluhan untuk rute ${laporan.rute} sudah ditangani dan ingin dihapus?")
            .setPositiveButton("Ya, Hapus") { dialog, _ ->
                FirebaseFirestore.getInstance().collection("laporan_keluhan")
                    .document(laporan.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show()
                        muatLaporanDariFirestore()
                    }
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun isikanFormUntukEdit(tiket: TiketModel) {
        isEditMode = true
        binding.cardFormInput.visibility = View.VISIBLE
        binding.btnToggleForm.text = "- Sembunyikan Form"
        binding.btnSimpanTiket.text = "Update Tiket"

        binding.etIdTiket.setText(tiket.id)
        binding.etIdTiket.isEnabled = false

        binding.etAdminAsal.setText(tiket.asal, false)
        binding.etAdminTujuan.setText(tiket.tujuan, false)
        binding.etAdminTanggal.setText(tiket.tanggal)
        binding.etAdminWaktu.setText(tiket.waktu, false)
        binding.etAdminHarga.setText(tiket.harga.toLong().toString())
        binding.etAdminKursi.setText(tiket.sisaKursi.toString())

        binding.etAdminNamaArmada.setText(if (tiket.namaArmada.isEmpty()) "Travelia Express" else tiket.namaArmada)
        binding.etAdminTipeArmada.setText(if (tiket.tipeArmada.isEmpty()) "Toyota Hiace Premio" else tiket.tipeArmada)

        binding.etAdminNamaArmada.isEnabled = false
        binding.etAdminTipeArmada.isEnabled = false

        Toast.makeText(this, "Mengedit tiket ${tiket.id}", Toast.LENGTH_SHORT).show()
    }

    private fun muatDaftarTiketDariFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection("tickets")
            .get()
            .addOnSuccessListener { querySnapshot ->
                listAdminTiket.clear()

                for (doc in querySnapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val asal = doc.getString("asal") ?: ""
                    val tujuan = doc.getString("tujuan") ?: ""
                    val tanggal = doc.getString("tanggal") ?: ""
                    val waktu = doc.getString("waktu") ?: ""
                    val waktuMenit = doc.getLong("waktuMenit")?.toInt() ?: 0
                    val harga = doc.getDouble("harga") ?: (doc.getLong("harga")?.toDouble() ?: 0.0)
                    val sisaKursi = doc.getLong("sisaKursi")?.toInt() ?: 0
                    val namaArmada = doc.getString("namaArmada") ?: "Travelia Express"
                    val tipeArmada = doc.getString("tipeArmada") ?: "Toyota Hiace Premio"

                    val tiket = TiketModel(
                        id = id,
                        asal = asal,
                        tujuan = tujuan,
                        tanggal = tanggal,
                        waktu = waktu,
                        waktuMenit = waktuMenit,
                        harga = harga,
                        sisaKursi = sisaKursi,
                        namaArmada = namaArmada,
                        tipeArmada = tipeArmada
                    )
                    listAdminTiket.add(tiket)
                }

                listTiketFiltered.clear()
                listTiketFiltered.addAll(listAdminTiket)
                adminAdapter?.notifyDataSetChanged()

                if (listAdminTiket.isEmpty()) {
                    binding.tvTotalTiketCount.text = "Belum ada tiket terdaftar di Firestore."
                } else {
                    binding.tvTotalTiketCount.text = "Total ${listAdminTiket.size} tiket terdaftar di sistem"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat tiket: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun konfirmasiHapusTiket(tiket: TiketModel) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Tiket")
            .setMessage("Apakah Anda yakin ingin menghapus tiket ${tiket.id} (${tiket.asal} ➔ ${tiket.tujuan})?")
            .setPositiveButton("Hapus") { dialog, _ ->
                hapusTiketDariFirestore(tiket)
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun hapusTiketDariFirestore(tiket: TiketModel) {
        val db = FirebaseFirestore.getInstance()
        db.collection("tickets").document(tiket.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Tiket ${tiket.id} berhasil dihapus!", Toast.LENGTH_SHORT).show()
                muatDaftarTiketDariFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus tiket: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun hitungWaktuMenit(waktuStr: String): Int {
        val parts = waktuStr.split(":")
        return if (parts.size == 2) {
            val jam = parts[0].toIntOrNull() ?: 0
            val menit = parts[1].toIntOrNull() ?: 0
            (jam * 60) + menit
        } else {
            0
        }
    }

    private fun simpanTiketKeFirestore() {
        val idTiket = binding.etIdTiket.text.toString().trim()
        val asal = binding.etAdminAsal.text.toString().trim()
        val tujuan = binding.etAdminTujuan.text.toString().trim()
        val tanggal = binding.etAdminTanggal.text.toString().trim()
        val waktu = binding.etAdminWaktu.text.toString().trim()
        val hargaStr = binding.etAdminHarga.text.toString().trim()
        val kursiStr = binding.etAdminKursi.text.toString().trim()
        var namaArmada = binding.etAdminNamaArmada.text.toString().trim()
        var tipeArmada = binding.etAdminTipeArmada.text.toString().trim()

        if (namaArmada.isEmpty()) namaArmada = "Travelia Express"
        if (tipeArmada.isEmpty()) tipeArmada = "Toyota Hiace Premio"

        if (idTiket.isEmpty() || asal.isEmpty() || tujuan.isEmpty() || tanggal.isEmpty() ||
            waktu.isEmpty() || hargaStr.isEmpty() || kursiStr.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi semua field terlebih dahulu!", Toast.LENGTH_SHORT).show()
            return
        }

        if (asal.equals(tujuan, ignoreCase = true)) {
            Toast.makeText(this, "Kota asal dan tujuan tidak boleh sama!", Toast.LENGTH_SHORT).show()
            return
        }

        val harga = hargaStr.toDoubleOrNull() ?: 0.0
        val sisaKursi = kursiStr.toIntOrNull() ?: 0
        val waktuMenit = hitungWaktuMenit(waktu)

        val tiketMap = hashMapOf(
            "id" to idTiket,
            "asal" to asal,
            "tujuan" to tujuan,
            "tanggal" to tanggal,
            "waktu" to waktu,
            "waktuMenit" to waktuMenit,
            "harga" to harga,
            "sisaKursi" to sisaKursi,
            "namaArmada" to namaArmada,
            "tipeArmada" to tipeArmada
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("tickets")
            .document(idTiket)
            .set(tiketMap)
            .addOnSuccessListener {
                val pesan = if (isEditMode) "Tiket $idTiket berhasil diperbarui!" else "Tiket $idTiket berhasil disimpan!"
                Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()
                clearForm()
                if (binding.containerListTiket.visibility == View.VISIBLE) {
                    muatDaftarTiketDariFirestore()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memproses tiket: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun clearForm() {
        isEditMode = false
        binding.etIdTiket.setText("")
        binding.etIdTiket.isEnabled = true
        binding.etAdminAsal.setText("", false)
        binding.etAdminTujuan.setText("", false)
        binding.etAdminTanggal.setText("")
        binding.etAdminWaktu.setText("", false)
        binding.etAdminHarga.setText("")
        binding.etAdminKursi.setText("")
        binding.etAdminNamaArmada.setText("")
        binding.etAdminNamaArmada.isEnabled = true
        binding.etAdminTipeArmada.setText("")
        binding.etAdminTipeArmada.isEnabled = true
        binding.btnSimpanTiket.text = "Simpan ke Firestore"
    }
}