package com.travelia

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.travelia.databinding.ActivityMainBinding
import com.travelia.databinding.DialogBottomSheetArticleBinding
import com.travelia.databinding.DialogBottomSheetPesanBinding
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val listJadwalTerdekat = mutableListOf<TiketModel>()
    private var adapterJadwal: JadwalTerdekatAdapter? = null

    // Variable & Adapter untuk Info Seputar Travel
    private val listArticles = mutableListOf<ArticleModel>()
    private var adapterArticle: ArticleAdapter? = null
    private val articleSlideHandler = Handler(Looper.getMainLooper())
    private val articleSlideRunnable = Runnable {
        if (listArticles.isNotEmpty()) {
            val currentItem = binding.viewPagerArticle.currentItem
            val nextItem = if (currentItem == listArticles.size - 1) 0 else currentItem + 1
            binding.viewPagerArticle.setCurrentItem(nextItem, true)
        }
    }

    // Variable Auto-Slide Handler Jadwal (Selang Waktu 5 Detik)
    private val slideHandler = Handler(Looper.getMainLooper())
    private val slideRunnable = Runnable {
        if (listJadwalTerdekat.isNotEmpty()) {
            val currentItem = binding.viewPagerJadwal.currentItem
            val nextItem = if (currentItem == listJadwalTerdekat.size - 1) 0 else currentItem + 1
            binding.viewPagerJadwal.setCurrentItem(nextItem, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadUserData()
        setupViewPager()
        setupArticleSlider()
        setupMenuListeners()

        // Panggil fungsi terpusat Bottom Navigation helper
        setupBottomNav(this, binding.bottomNavigation.bottomNavigation, R.id.nav_home)
    }

    override fun onResume() {
        super.onResume()
        // Muat ulang jadwal dan artikel setiap kali activity aktif kembali
        muatJadwalTerdekat()
        muatArtikelTravel()
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return

        val uid = user.uid
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val username = if (document.exists()) {
                    document.getString("username") ?: user.displayName ?: "user"
                } else {
                    user.displayName ?: user.email?.substringBefore("@") ?: "user"
                }
                // Tambahkan kembali simbol @ di sini
                binding.tvUsername.text = "@$username"
            }
            .addOnFailureListener {
                val fallbackUsername = user.displayName ?: user.email?.substringBefore("@") ?: "user"
                binding.tvUsername.text = "@$fallbackUsername"
            }

        user.photoUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .into(binding.imgProfile)
        }
    }

    private fun setupViewPager() {
        adapterJadwal = JadwalTerdekatAdapter(listJadwalTerdekat) { tiketDipilih ->
            showBottomSheetPemesanan(tiketDipilih)
        }
        binding.viewPagerJadwal.adapter = adapterJadwal

        binding.viewPagerJadwal.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotsIndicator(position)

                slideHandler.removeCallbacks(slideRunnable)
                slideHandler.postDelayed(slideRunnable, 5000)
            }
        })
    }

    // SET UP SLIDER INFO SEPUTAR TRAVEL
    private fun setupArticleSlider() {
        adapterArticle = ArticleAdapter(listArticles) { articleDipilih ->
            showBottomSheetArtikel(articleDipilih)
        }
        binding.viewPagerArticle.adapter = adapterArticle

        binding.viewPagerArticle.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateArticleDotsIndicator(position)

                articleSlideHandler.removeCallbacks(articleSlideRunnable)
                articleSlideHandler.postDelayed(articleSlideRunnable, 4000)
            }
        })
    }

    private fun setupArticleDotsIndicator(count: Int) {
        binding.layoutDotsArticle.removeAllViews()
        val dots = arrayOfNulls<View>(count)

        for (i in dots.indices) {
            dots[i] = View(this).apply {
                background = getDrawable(R.drawable.dot_indicator_selector)
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(6, 0, 6, 0)
                }
                isSelected = (i == 0)
            }
            binding.layoutDotsArticle.addView(dots[i])
        }
    }

    private fun updateArticleDotsIndicator(currentIndex: Int) {
        val childCount = binding.layoutDotsArticle.childCount
        for (i in 0 until childCount) {
            val imageView = binding.layoutDotsArticle.getChildAt(i)
            imageView.isSelected = (i == currentIndex)
        }
    }

    private fun muatArtikelTravel() {
        db.collection("articles")
            .get()
            .addOnSuccessListener { querySnapshot ->
                listArticles.clear()
                for (doc in querySnapshot.documents) {
                    val title = doc.getString("title") ?: ""
                    val shortDescription = doc.getString("shortDescription") ?: ""
                    val fullDescription = doc.getString("fullDescription") ?: ""
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    val article = ArticleModel(
                        title = title,
                        shortDescription = shortDescription,
                        fullDescription = fullDescription,
                        imageUrl = imageUrl
                    )
                    listArticles.add(article)
                }

                adapterArticle?.notifyDataSetChanged()
                setupArticleDotsIndicator(listArticles.size)

                if (listArticles.isNotEmpty()) {
                    articleSlideHandler.removeCallbacks(articleSlideRunnable)
                    articleSlideHandler.postDelayed(articleSlideRunnable, 4000)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat artikel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // BOTTOM SHEET ARTIKEL
    private fun showBottomSheetArtikel(article: ArticleModel) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogBottomSheetArticleBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        dialogBinding.tvSheetTitle.text = article.title
        dialogBinding.tvSheetDescription.text = article.fullDescription

        Glide.with(this)
            .load(article.imageUrl)
            .placeholder(R.drawable.img_article_hiace)
            .into(dialogBinding.imgSheetArticle)

        dialogBinding.btnCloseSheet.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // FUNGSI BOTTOM SHEET UNTUK CARD JADWAL TERDEKAT
    private fun showBottomSheetPemesanan(tiket: TiketModel) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogBinding = DialogBottomSheetPesanBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        var jumlahKursi = 1
        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

        fun updateUI() {
            val totalHarga = (tiket.harga * jumlahKursi).toLong()

            dialogBinding.tvJumlahKursi.text = jumlahKursi.toString()
            dialogBinding.tvTotalHarga.text = formatRupiah.format(totalHarga)
        }

        dialogBinding.tvSheetRute.text = "${tiket.asal} ➔ ${tiket.tujuan}"
        dialogBinding.tvSheetId.text = "ID: ${tiket.id}"
        dialogBinding.tvSheetTanggal.text = "Tanggal: ${tiket.tanggal}"
        dialogBinding.tvSheetWaktu.text = "Jam: ${tiket.waktu}"
        updateUI()

        dialogBinding.btnMinus.setOnClickListener {
            if (jumlahKursi > 1) {
                jumlahKursi--
                updateUI()
            } else {
                Toast.makeText(this, "Minimal 1 kursi", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnPlus.setOnClickListener {
            if (jumlahKursi < tiket.sisaKursi && jumlahKursi < 14) {
                jumlahKursi++
                updateUI()
            } else {
                Toast.makeText(this, "Melebihi sisa kursi yang tersedia!", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnKonfirmasiBayar.setOnClickListener {
            dialogBinding.btnKonfirmasiBayar.isEnabled = false
            bottomSheetDialog.dismiss()
            simpanTiketKeFirestore(tiket, jumlahKursi)
        }

        bottomSheetDialog.show()
    }

    private fun setupDotsIndicator(count: Int) {
        binding.layoutDots.removeAllViews()
        val dots = arrayOfNulls<View>(count)

        for (i in dots.indices) {
            dots[i] = View(this).apply {
                background = getDrawable(R.drawable.dot_indicator_selector)
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(6, 0, 6, 0)
                }
                isSelected = (i == 0)
            }
            binding.layoutDots.addView(dots[i])
        }
    }

    private fun updateDotsIndicator(currentIndex: Int) {
        val childCount = binding.layoutDots.childCount
        for (i in 0 until childCount) {
            val imageView = binding.layoutDots.getChildAt(i)
            imageView.isSelected = (i == currentIndex)
        }
    }

    private fun muatJadwalTerdekat() {
        db.collection("tickets")
            .get()
            .addOnSuccessListener { querySnapshot ->
                listJadwalTerdekat.clear()

                val tempFiltered = mutableListOf<TiketModel>()
                for (doc in querySnapshot.documents) {
                    val id = doc.id
                    val asal = doc.getString("asal") ?: ""
                    val tujuan = doc.getString("tujuan") ?: ""
                    val tanggal = doc.getString("tanggal") ?: ""
                    val waktu = doc.getString("waktu") ?: ""
                    val waktuMenit = doc.getLong("waktuMenit")?.toInt() ?: 0
                    val harga = doc.getDouble("harga") ?: (doc.getLong("harga")?.toDouble() ?: 0.0)
                    val sisaKursi = doc.getLong("sisaKursi")?.toInt() ?: 0
                    val namaArmada = doc.getString("namaArmada") ?: ""
                    val tipeArmada = doc.getString("tipeArmada") ?: ""

                    if (sisaKursi > 0) {
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
                        tempFiltered.add(tiket)
                    }
                }

                val sortedTop3 = tempFiltered.sortedBy { it.waktuMenit }.take(3)
                listJadwalTerdekat.addAll(sortedTop3)

                adapterJadwal?.notifyDataSetChanged()
                setupDotsIndicator(listJadwalTerdekat.size)

                if (listJadwalTerdekat.isEmpty()) {
                    binding.tvEmptyJadwal.visibility = View.VISIBLE
                    binding.viewPagerJadwal.visibility = View.GONE
                    binding.layoutDots.visibility = View.GONE
                } else {
                    binding.tvEmptyJadwal.visibility = View.GONE
                    binding.viewPagerJadwal.visibility = View.VISIBLE
                    binding.layoutDots.visibility = View.VISIBLE

                    slideHandler.removeCallbacks(slideRunnable)
                    slideHandler.postDelayed(slideRunnable, 5000)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat jadwal terdekat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun simpanTiketKeFirestore(tiket: TiketModel, jumlahKursiDipesan: Int) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val parentTicketDocId = tiket.id
        if (parentTicketDocId.isEmpty()) {
            Toast.makeText(this, "ID Tiket tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        val parentTicketRef = db.collection("tickets").document(parentTicketDocId)
        val userTicketRef = db.collection("user_tickets").document()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(parentTicketRef)
            val currentSisaKursi = snapshot.getLong("sisaKursi") ?: 0L

            if (currentSisaKursi < jumlahKursiDipesan) {
                throw Exception("Sisa kursi tidak mencukupi!")
            }

            transaction.update(parentTicketRef, "sisaKursi", currentSisaKursi - jumlahKursiDipesan)

            val bookingData = hashMapOf(
                "userId" to userId,
                "ticketId" to parentTicketDocId,
                "asal" to tiket.asal,
                "tujuan" to tiket.tujuan,
                "rute" to "${tiket.asal} ➔ ${tiket.tujuan}",
                "tanggal" to tiket.tanggal,
                "waktu" to tiket.waktu,
                "jam" to tiket.waktu,
                "harga" to (tiket.harga * jumlahKursiDipesan),
                "jumlahKursi" to jumlahKursiDipesan,
                "status" to "Belum Bayar",
                "namaArmada" to tiket.namaArmada.ifEmpty { "Travelia Express" },
                "tipeArmada" to "Toyota Hiace Premio",
                "timestamp" to Timestamp.now()
            )

            transaction.set(userTicketRef, bookingData)
        }.addOnSuccessListener {
            Toast.makeText(this, "Pesanan berhasil dibuat!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, TiketSayaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal memesan tiket: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMenuListeners() {
        // Klik Foto Profil atau Username untuk membuka ProfileActivity
        binding.imgProfile.setOnClickListener {
            startActivity(Intent(this, ProfilActivity::class.java))
        }

        binding.tvUsername.setOnClickListener {
            startActivity(Intent(this, ProfilActivity::class.java))
        }

        binding.btnMenuPesanTiket.setOnClickListener {
            startActivity(Intent(this, PesanTiketActivity::class.java))
        }

        binding.btnMenuTiketSaya.setOnClickListener {
            startActivity(Intent(this, TiketSayaActivity::class.java))
        }

        binding.btnMenuJadwal.setOnClickListener {
            startActivity(Intent(this, JadwalActivity::class.java))
        }

        binding.btnMenuRiwayat.setOnClickListener {
            startActivity(Intent(this, RiwayatPesananActivity::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        slideHandler.removeCallbacks(slideRunnable)
        articleSlideHandler.removeCallbacks(articleSlideRunnable)
    }
}